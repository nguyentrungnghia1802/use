package org.tzi.use.plugins.jacamo;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.tzi.use.plugins.jacamo.bridge.BridgeClient;
import org.tzi.use.plugins.jacamo.bridge.BridgeClientState;
import org.tzi.use.plugins.jacamo.bridge.BridgeConnectionConfig;
import org.tzi.use.plugins.jacamo.bridge.BridgeMirrorStateMachine;
import org.tzi.use.plugins.jacamo.bridge.BridgeProtocolException;
import org.tzi.use.plugins.jacamo.bridge.BridgeRuntimeProjector;
import org.tzi.use.plugins.jacamo.bridge.BridgeTransportFactory;
import org.tzi.use.plugins.jacamo.bridge.BridgeVerificationGate;
import org.tzi.use.plugins.jacamo.bridge.NativeSemanticAdapter;
import org.tzi.use.plugins.jacamo.binding.BindingEntry;
import org.tzi.use.plugins.jacamo.binding.BindingFile;
import org.tzi.use.plugins.jacamo.binding.BindingStore;
import org.tzi.use.plugins.jacamo.constraint.ConstraintExtractor;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.NativeUseSessionActivator;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.NativeProfileCompatibilityPreflight;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.mapping.MappingModel;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlan;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.runtime.MirrorState;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.semantic.Dimension;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.tzi.use.plugins.jacamo.verification.ConstraintRegistry;
import org.tzi.use.plugins.jacamo.verification.DefaultVerificationService;
import org.tzi.use.plugins.jacamo.verification.RuntimeVerificationEngine;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.plugins.jacamo.verification.VerificationReport;
import org.tzi.use.plugins.jacamo.verification.VerificationReportExporter;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;
import org.tzi.use.main.Session;
import org.tzi.use.uml.sys.MSystem;

/** Stateful application service behind the USE workbench. All parsing/transformation/verification lives here. */
public final class DefaultJaCaMoFacade implements JaCaMoFacade, AutoCloseable {
    public static final DefaultJaCaMoFacade INSTANCE = new DefaultJaCaMoFacade(detectCheckout());

    private final Path checkout;
    private final SemanticAuthority authority;
    private final PipelineMode pipelineMode;
    private final Session session;
    private final Supplier<BridgeConnectionConfig> bridgeConfigurationSource;
    private final BridgeTransportFactory bridgeTransportFactory;
    private BridgeConnectionConfig configuredBridge;
    private BridgeClient bridgeClient;
    private BridgeMirrorStateMachine bridgeMirror;
    private BridgeClient.Accepted bridgeAccepted;
    private String bridgeDiagnostic = "";
    private java.time.Instant bridgeLastSync;
    private final AtomicLong bridgeProcessed = new AtomicLong();
    private Path entry;
    private Path userProfile;
    private Workspace workspace;
    private NativeWorkspace nativeWorkspace;
    private RuntimeVerificationEngine runtimeVerification;
    private List<Diagnostic> lastDiagnostics = List.of();
    private long lastFullCheckNanos;

    public DefaultJaCaMoFacade(Path checkout) {
        this(checkout, SemanticAuthority.configured(System.getProperty("use.jacamo.authority")),
                BridgeConnectionConfig::fromSystemProperties, BridgeTransportFactory.localTcp(),
                PipelineMode.CODE_GROUNDED_NATIVE, null);
    }

    public DefaultJaCaMoFacade(Path checkout, Session session) {
        this(checkout, SemanticAuthority.configured(System.getProperty("use.jacamo.authority")),
                BridgeConnectionConfig::fromSystemProperties, BridgeTransportFactory.localTcp(),
                PipelineMode.CODE_GROUNDED_NATIVE, session);
    }

    public DefaultJaCaMoFacade(Path checkout, SemanticAuthority authority,
                               Supplier<BridgeConnectionConfig> bridgeConfigurationSource,
                               BridgeTransportFactory bridgeTransportFactory) {
        this(checkout, authority, bridgeConfigurationSource, bridgeTransportFactory, PipelineMode.LEGACY_V2, null);
    }

    public DefaultJaCaMoFacade(Path checkout, SemanticAuthority authority,
                               Supplier<BridgeConnectionConfig> bridgeConfigurationSource,
                               BridgeTransportFactory bridgeTransportFactory, PipelineMode pipelineMode,
                               Session session) {
        this.checkout = checkout.toAbsolutePath().normalize();
        this.authority = java.util.Objects.requireNonNull(authority, "authority");
        if (authority != SemanticAuthority.BRIDGE)
            throw new IllegalArgumentException("SEMANTIC_AUTHORITY_REMOVED:" + authority);
        this.bridgeConfigurationSource = java.util.Objects.requireNonNull(bridgeConfigurationSource,
                "bridgeConfigurationSource");
        this.bridgeTransportFactory = java.util.Objects.requireNonNull(bridgeTransportFactory,
                "bridgeTransportFactory");
        this.pipelineMode = java.util.Objects.requireNonNull(pipelineMode, "pipelineMode");
        this.session = session;
    }

    public static DefaultJaCaMoFacade forSession(Session session) {
        return new DefaultJaCaMoFacade(detectCheckout(), java.util.Objects.requireNonNull(session, "session"));
    }

    public PipelineMode pipelineMode() { return pipelineMode; }
    MSystem materializedSystem() { return currentSystem(); }

    @Override public synchronized String status() {
        AuthorityStatus state = authorityStatus();
        return "JaCaMo Bridge " + state.readiness() + (state.modelRevision().isBlank() ? ""
                : " model=" + state.modelRevision()) + (state.diagnostic().isBlank() ? ""
                : " diagnostic=" + state.diagnostic());
    }

    @Override public synchronized ProjectSummary importProject(Path jcmFile) {
        if (jcmFile == null || !jcmFile.getFileName().toString().toLowerCase().endsWith(".jcm"))
            throw new IllegalArgumentException("IMPORT_JCM_REQUIRED");
        Path candidate = jcmFile.toAbsolutePath().normalize();
        return synchronizeBridge(candidate, null);
    }

    @Override public synchronized ProjectSummary rebuild() {
        requireWorkspace();
        return synchronizeBridge(entry, userProfile);
    }

    @Override public synchronized ProjectSummary projectSummary() {
        return nativeWorkspace != null ? nativeWorkspace.summary : workspace == null ? null : workspace.summary;
    }
    @Override public synchronized List<SourceRow> sources() {
        return nativeWorkspace != null ? nativeWorkspace.sources : workspace == null ? List.of() : workspace.sources;
    }
    @Override public synchronized List<Diagnostic> diagnostics() { return lastDiagnostics; }
    @Override public synchronized List<TraceRow> traces() {
        return nativeWorkspace != null ? nativeWorkspace.traces : workspace == null ? List.of() : workspace.traces;
    }
    @Override public synchronized List<org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor> constraints() {
        return nativeWorkspace != null ? nativeWorkspace.constraints
                : workspace == null ? List.of() : workspace.registry.descriptors();
    }

    @Override public synchronized VerificationReport runFullVerification() {
        requireWorkspace();
        if (nativeWorkspace != null) return runNativeVerification(nativeWorkspace);
        long started = System.nanoTime();
        workspace.latest = new DefaultVerificationService().runFullVerification(workspace.direct.system(),
                workspace.registry, workspace.trace);
        lastFullCheckNanos = System.nanoTime() - started;
        return workspace.latest;
    }

    @Override public synchronized VerificationReport latestVerification() {
        if (nativeWorkspace != null) return nativeWorkspace.latest;
        if (workspace == null) return null;
        var runtimeLatest = runtimeVerification == null ? null : runtimeVerification.latestReport();
        return runtimeLatest == null ? workspace.latest : runtimeLatest.verification();
    }

    @Override public synchronized void loadVerificationProfile(Path profile) {
        requireWorkspace();
        if (nativeWorkspace != null) {
            if (profile == null || !Files.isRegularFile(profile.toAbsolutePath().normalize()))
                throw new IllegalArgumentException("OCL_USER_PROFILE_IO");
            var preflight = new NativeProfileCompatibilityPreflight().inspect(profile,
                    nativeWorkspace.pipeline.model().model());
            if (!preflight.compatible())
                throw new IllegalArgumentException("NATIVE_PROFILE_INCOMPATIBLE: "
                        + String.join(",", preflight.diagnostics()));
            throw new UnsupportedOperationException("NATIVE_PROFILE_INSTALL_NOT_IN_CURRENT_NATIVE_SCOPE");
        }
        if (profile == null || !Files.isRegularFile(profile.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("OCL_USER_PROFILE_IO");
        Path candidate = profile.toAbsolutePath().normalize();
        synchronizeBridge(entry, candidate);
    }

    @Override public synchronized void exportVerificationReport(Path destination) {
        requireWorkspace();
        VerificationReport report = latestVerification();
        if (report == null) report = runFullVerification();
        Path temporary = null;
        try {
            Path output = destination.toAbsolutePath().normalize();
            String name = output.getFileName() == null ? "" : output.getFileName().toString().toLowerCase();
            if (!name.endsWith(".json") && !name.endsWith(".md"))
                throw new IllegalArgumentException("REPORT_EXPORT_EXTENSION: use .json or .md");
            rejectSymbolicPath(output);
            Path parent = output.getParent();
            if (parent != null) Files.createDirectories(parent);
            String content = name.endsWith(".json")
                    ? new VerificationReportExporter().toJson(report)
                    : new VerificationReportExporter().toMarkdown(report);
            temporary = Files.createTempFile(parent, ".jacamo-report-", ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } catch (Exception exception) {
            Exception cleanup = cleanupTemporaryReport(temporary, exception);
            boolean reportDiagnostic = exception instanceof IllegalArgumentException
                    && exception.getMessage() != null && exception.getMessage().startsWith("REPORT_EXPORT_");
            if (reportDiagnostic && cleanup == null) throw (IllegalArgumentException) exception;
            String message = reportDiagnostic ? exception.getMessage()
                    : "REPORT_EXPORT_FAILED: " + exception.getMessage();
            if (cleanup != null) message += "; REPORT_EXPORT_CLEANUP_FAILED: " + cleanup.getMessage();
            throw new IllegalArgumentException(message, exception);
        }
    }

    static Exception cleanupTemporaryReport(Path temporary, Exception primary) {
        if (temporary == null) return null;
        try {
            Files.deleteIfExists(temporary);
            return null;
        } catch (Exception cleanup) {
            primary.addSuppressed(cleanup);
            return cleanup;
        }
    }

    @Override public synchronized void configureBridge(BridgeConnectionConfig configuration) {
        configuredBridge = java.util.Objects.requireNonNull(configuration, "configuration");
        bridgeDiagnostic = "";
    }

    @Override public synchronized void connectRuntime() {
        requireWorkspace();
        synchronizeBridge(entry, userProfile);
    }

    @Override public synchronized void disconnectRuntime() {
        closeBridgeClient();
    }

    @Override public synchronized void resyncRuntime() {
        requireWorkspace();
        synchronizeBridge(entry, userProfile);
    }

    @Override public synchronized RuntimeStatus runtimeStatus() {
        BridgeClientState state = bridgeMirror == null ? BridgeClientState.DISCONNECTED : bridgeMirror.state();
        MirrorState mirrorState = switch (state) {
            case DISCONNECTED -> workspace == null && nativeWorkspace == null ? MirrorState.OFFLINE : MirrorState.STALE;
            case NEGOTIATING -> MirrorState.CONNECTING;
            case MODEL_SYNC, SNAPSHOT_SYNC -> MirrorState.SYNCING;
            case LIVE -> MirrorState.LIVE;
            case RESYNC_REQUIRED, STALE -> MirrorState.STALE;
        };
        int violations = runtimeVerification == null ? 0 : (int) runtimeVerification.reports().stream()
                .flatMap(report -> report.verification().results().stream())
                .filter(result -> result.outcome() == VerificationOutcome.FAIL).count();
        return new RuntimeStatus(mirrorState, 0, 0, bridgeProcessed.get(), 0,
                state == BridgeClientState.RESYNC_REQUIRED ? 1 : 0, 0, bridgeLastSync, "", 0,
                bridgeAccepted == null ? 0 : bridgeAccepted.generation(), violations);
    }

    @Override public synchronized AuthorityStatus authorityStatus() {
        BridgeConnectionConfig configuration = configuredBridge;
        String endpoint = configuration == null ? "" : configuration.displayEndpoint();
        BridgeClientState readiness = bridgeMirror == null ? BridgeClientState.DISCONNECTED : bridgeMirror.state();
        String diagnostic = bridgeDiagnostic;
        if (bridgeClient != null && !bridgeClient.diagnostic().isBlank()) diagnostic = bridgeClient.diagnostic();
        if (bridgeAccepted == null)
            return new AuthorityStatus(authority, readiness, Map.of(), "UNAVAILABLE", "", "", 0,
                    endpoint, diagnostic.isBlank() ? "BRIDGE_NOT_SYNCHRONIZED" : diagnostic);
        Map<String,String> capabilities = new LinkedHashMap<>();
        bridgeAccepted.capabilities().forEach(value -> capabilities.put(value.name(), value.status().name()));
        return new AuthorityStatus(authority, readiness, capabilities, bridgeAccepted.completeness().name(),
                bridgeAccepted.modelRevision(), bridgeAccepted.sessionId(), bridgeAccepted.generation(), endpoint,
                diagnostic);
    }

    @Override public synchronized FormalStateStatus formalStateStatus() {
        MSystem system = currentSystem();
        if (system == null) return FormalStateStatus.empty();
        var state = system.state();
        List<String> rows = new ArrayList<>();
        system.model().classes().forEach(value -> rows.add("class|" + value.name()));
        system.model().associations().forEach(value -> rows.add("association|" + value.name()));
        state.allObjects().forEach(object -> {
            rows.add("object|" + object.name() + "|" + object.cls().name());
            object.state(state).attributeValueMap().forEach((attribute, value) ->
                    rows.add("attribute|" + object.name() + "|" + attribute.name() + "|" + value));
        });
        state.allLinks().forEach(link -> rows.add("link|" + link));
        rows.sort(String::compareTo);
        try {
            String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", rows).getBytes(StandardCharsets.UTF_8)));
            return new FormalStateStatus(system.model().classes().size(), system.model().associations().size(),
                    state.numObjects(), state.allLinks().size(), digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override public synchronized PerformanceMetrics performanceMetrics() {
        if (workspace == null && nativeWorkspace == null) return PerformanceMetrics.empty();
        RuntimeStatus runtimeStatus = runtimeStatus();
        Runtime jvm = Runtime.getRuntime();
        long importNanos = nativeWorkspace != null ? nativeWorkspace.importNanos : workspace.importNanos;
        long generationNanos = nativeWorkspace != null ? nativeWorkspace.generationNanos : workspace.generationNanos;
        return new PerformanceMetrics(importNanos, generationNanos, lastFullCheckNanos,
                runtimeStatus.lastLatencyNanos(), jvm.totalMemory() - jvm.freeMemory());
    }

    @Override public synchronized void persistBinding(Path destination, BindingRequest request,
                                                      String selectedTargetId, String reason) {
        requireWorkspace();
        if (nativeWorkspace != null)
            throw new UnsupportedOperationException("NATIVE_BINDING_PERSISTENCE_NOT_IN_CURRENT_NATIVE_SCOPE");
        BindingCandidate candidate = request.candidates().stream()
                .filter(value -> value.semanticId().equals(selectedTargetId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("BINDING_TARGET_NOT_A_CANDIDATE"));
        BindingEntry entry = BindingEntry.active(request.sourceId(), candidate.semanticId(), "EXPLICIT_BINDING",
                reason == null || reason.isBlank() ? "Explicit user selection" : reason,
                request.sourceHash(), "USE JaCaMo Plugin UI");
        BindingStore store = new BindingStore();
        List<BindingEntry> entries = new ArrayList<>();
        Path output = destination.toAbsolutePath().normalize();
        if (Files.isRegularFile(output)) entries.addAll(store.read(output, workspace.sourceHashes).entries().stream()
                .filter(existing -> !existing.source().equals(request.sourceId())).toList());
        entries.add(entry);
        store.write(output, new BindingFile("1.0.0", entries));
    }

    @Override public synchronized void close() {
        closeBridgeClient();
    }

    private RuntimeVerificationEngine createRuntimeVerification(Workspace next,
                                                                BridgeMirrorStateMachine mirror,
                                                                BridgeClient.Accepted accepted) {
        BridgeVerificationGate gate = new BridgeVerificationGate();
        var admission = (java.util.function.BiFunction<org.tzi.use.plugins.jacamo.runtime.RuntimeEvent,
                Set<RuntimeFactKind>, BridgeVerificationGate.Assessment>) (event, requiredKinds) -> {
            String snapshotId = mirror.snapshotId() == null ? accepted.runtime().snapshotId() : mirror.snapshotId();
            String eventId = event == null ? "" : event.eventId();
            String correlationId = event == null ? "" : event.correlationId();
            var context = new BridgeVerificationGate.Context(accepted.sessionId(), accepted.generation(),
                    accepted.modelRevision(), snapshotId, eventId, correlationId,
                    bridgeConstraintHash(next.registry), accepted.capabilities().stream()
                            .map(org.jacamo.bridge.contract.Capability::name).collect(java.util.stream.Collectors.toSet()));
            return gate.assess(mirror.state(), mirror.facts().values(), requiredKinds, context);
        };
        return new RuntimeVerificationEngine(next.direct.system(), next.registry, next.trace,
                new DefaultVerificationService(), admission);
    }

    private void installWorkspace(Workspace next, Path nextEntry, Path nextProfile,
                                  RuntimeVerificationEngine verification) {
        nativeWorkspace = null;
        workspace = next;
        entry = nextEntry;
        userProfile = nextProfile;
        runtimeVerification = verification;
        next.latest = null;
        // Static verification predates the authoritative runtime snapshot.
        runFullVerification();
    }

    private ProjectSummary synchronizeBridge(Path selectedJcm, Path verificationProfile) {
        BridgeConnectionConfig configuration = configuredBridge == null ? bridgeConfigurationSource.get() : configuredBridge;
        configuredBridge = configuration;
        BridgeMirrorStateMachine candidateMirror = new BridgeMirrorStateMachine(8_192);
        ArrayDeque<RuntimeEvent> pending = new ArrayDeque<>();
        AtomicReference<BridgeRuntimeProjector> projectorReference = new AtomicReference<>();
        BridgeClient candidate = null;
        try {
            candidate = new BridgeClient(bridgeTransportFactory.open(configuration), candidateMirror,
                    configuration.distributionSha256(), configuration.requiredCapabilities(),
                    configuration.maxBufferedEvents(), event -> {
                        if (pipelineMode == PipelineMode.CODE_GROUNDED_NATIVE) {
                            // Runtime projection is intentionally outside the current static native phase. The Bridge mirror remains the
                            // evidence authority, while no historical V2 runtime mapper may mutate the native system.
                            bridgeProcessed.incrementAndGet();
                            return;
                        }
                        synchronized (pending) {
                            BridgeRuntimeProjector projector = projectorReference.get();
                            if (projector == null) {
                                // The authoritative mirror already retains and validates evidence-only events. They
                                // cannot mutate USE, so retaining every high-rate operation observation while the
                                // semantic workspace is built only manufactures a facade buffer overflow.
                                if (!BridgeRuntimeProjector.requiresMaterialization(event)) return;
                                if (pending.size() >= configuration.maxBufferedEvents())
                                    throw new BridgeProtocolException("BRIDGE_FACADE_BUFFER_OVERFLOW");
                                pending.addLast(event);
                            } else if (projector.apply(event)) {
                                bridgeProcessed.incrementAndGet();
                            }
                        }
                    });
            long importStarted = System.nanoTime();
            BridgeClient.Accepted accepted = candidate.synchronize();
            validateBridgeSelection(selectedJcm, accepted);
            if (pipelineMode == PipelineMode.CODE_GROUNDED_NATIVE) {
                long importNanos = System.nanoTime() - importStarted;
                NativeWorkspace next = buildNativeSemantic(selectedJcm, accepted.model(), importNanos);
                if (session != null) new NativeUseSessionActivator().activate(session, next.pipeline);
                BridgeClient previousClient = bridgeClient;
                nativeWorkspace = next;
                workspace = null;
                entry = selectedJcm;
                userProfile = null;
                runtimeVerification = null;
                bridgeClient = candidate;
                bridgeMirror = candidateMirror;
                bridgeAccepted = accepted;
                bridgeLastSync = accepted.runtime().captureEndedAt();
                bridgeDiagnostic = "";
                if (previousClient != null) previousClient.close();
                return next.summary;
            }
            NativeSemanticAdapter.Result adapted = new NativeSemanticAdapter().adapt(accepted.model(),
                    selectedJcm.getParent(), accepted.projectKey());
            long importNanos = System.nanoTime() - importStarted;
            Workspace next = buildSemantic(selectedJcm, verificationProfile, adapted.model(),
                    adapted.model().diagnostics(), importNanos);
            Set<String> sources = accepted.runtime().endWatermarks().keySet();
            if (sources.isEmpty()) throw new BridgeProtocolException("BRIDGE_RUNTIME_SOURCE_REQUIRED");
            RuntimeMutationEngine mutationEngine = next.mutationEngine();
            RuntimeVerificationEngine verification = createRuntimeVerification(next, candidateMirror, accepted);
            verification.stateChanged(MirrorState.LIVE);
            BridgeRuntimeProjector projector = new BridgeRuntimeProjector(sources, adapted, next.trace,
                    mutationEngine, verification);
            synchronized (pending) {
                projector.applySnapshot(accepted.runtime());
                projectorReference.set(projector);
                while (!pending.isEmpty()) if (projector.apply(pending.removeFirst())) bridgeProcessed.incrementAndGet();
            }
            BridgeClient previousClient = bridgeClient;
            installWorkspace(next, selectedJcm, verificationProfile, verification);
            bridgeClient = candidate;
            bridgeMirror = candidateMirror;
            bridgeAccepted = accepted;
            bridgeLastSync = accepted.runtime().captureEndedAt();
            bridgeDiagnostic = "";
            if (previousClient != null) previousClient.close();
            return workspace.summary;
        } catch (RuntimeException error) {
            if (candidate != null) candidate.close();
            bridgeDiagnostic = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            throw error;
        }
    }

    private String bridgeConstraintHash(ConstraintRegistry registry) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(CanonicalJson.encode(registry.fingerprints())));
        } catch (Exception error) {
            throw new IllegalStateException("BRIDGE_CONSTRAINT_FINGERPRINT_FAILED", error);
        }
    }

    private void validateBridgeSelection(Path selectedJcm, BridgeClient.Accepted accepted) {
        try {
            String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(selectedJcm)));
            boolean matched = accepted.model().sources().stream().anyMatch(source ->
                    "JCM".equals(source.attributes().get("kind")) && digest.equals(source.attributes().get("digest")));
            if (!matched) throw new BridgeProtocolException("BRIDGE_PROJECT_SELECTION_MISMATCH");
            boolean projectKeyMatched = accepted.model().sources().stream()
                    .anyMatch(source -> source.id().scope().equals(accepted.projectKey()));
            if (!projectKeyMatched) throw new BridgeProtocolException("BRIDGE_PROJECT_KEY_MISMATCH");
        } catch (BridgeProtocolException error) {
            throw error;
        } catch (Exception error) {
            throw new BridgeProtocolException("BRIDGE_PROJECT_SELECTION_UNREADABLE", error);
        }
    }

    private void closeBridgeClient() {
        if (bridgeClient != null) {
            bridgeClient.close();
            bridgeClient = null;
        }
    }

    private NativeWorkspace buildNativeSemantic(Path jcmFile, org.jacamo.bridge.contract.ModelSnapshot snapshot,
                                                long importNanos) {
        long generationStarted = System.nanoTime();
        CodeGroundedNativePipeline.Result pipeline = new CodeGroundedNativePipeline().build(snapshot);
        long generationNanos = System.nanoTime() - generationStarted;
        MSystem system = pipeline.state().system();
        Map<String,Long> counts = new LinkedHashMap<>();
        counts.put("JCM", 1L);
        long plans = pipeline.source().programs().stream().mapToLong(value -> value.planLibrary().plans().size()).sum();
        long body = pipeline.source().programs().stream().flatMap(value -> value.planLibrary().plans().stream())
                .mapToLong(value -> value.body().size()).sum();
        long actions = pipeline.source().programs().stream().mapToLong(value -> value.actions().size()).sum();
        long beliefs = pipeline.source().programs().stream().mapToLong(value -> value.beliefs().size()).sum();
        long goals = pipeline.source().programs().stream().mapToLong(value -> value.goals().size()).sum();
        long beliefRules = pipeline.source().programs().stream().mapToLong(value -> value.beliefRules().size()).sum();
        counts.put("JASON", (long) pipeline.source().programs().size() * 2 + plans * 2 + body
                + actions + beliefs + goals + beliefRules);
        counts.put("CARTAGO", 0L); counts.put("MOISE", 0L); counts.put("CROSS", 0L);
        String catalogHash = sha256(new CodeGroundedRuleCatalog().rules().toString());
        ProjectSummary summary = new ProjectSummary(jcmFile, jcmFile.getParent(), pipeline.source().project().name(),
                snapshot.sources().size(), counts, "CODE_GROUNDED_NATIVE-1.0.0",
                pipeline.model().structuralHash(), "CodeGroundedRuleCatalog", "1.0.0", catalogHash,
                "PHASE_2", system.model().classes().size(), system.state().numObjects(),
                pipeline.state().structureValid(), 0, 0);
        List<SourceRow> sources;
        try {
            sources = List.of(new SourceRow(jcmFile, "JCM", Files.size(jcmFile),
                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(Files.readAllBytes(jcmFile)))));
        } catch (Exception error) {
            throw new IllegalStateException("NATIVE_SOURCE_EVIDENCE_FAILED: " + jcmFile, error);
        }
        List<TraceRow> traces = pipeline.trace().records().stream().map(record -> new TraceRow(
                record.sourceIdentity(), record.sourceJavaFqcn(), record.targetIdentity(), record.targetKind(),
                record.ruleId(), record.fidelity().name(), record.capabilityStatus().name(), jcmFile, 0,
                record.ruleId().substring(0, 1), record.evidenceAuthority().name(), record.diagnostics())).toList();
        List<org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor> constraints = system.model()
                .classInvariants().stream().map(invariant -> new org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor(
                        "NATIVE:" + invariant.name(), invariant.name(), invariant.cls().name(), null,
                        org.tzi.use.plugins.jacamo.verification.ConstraintKind.INV,
                        org.tzi.use.plugins.jacamo.verification.ConstraintOrigin.CORE, jcmFile,
                        new org.tzi.use.plugins.jacamo.project.SourceSpan(jcmFile, 1, 1, 1, 1),
                        nativeConstraintDependencies(invariant.name()), invariant.isActive(),
                        invariant.bodyExpression().toString())).toList();
        NativeWorkspace next = new NativeWorkspace(summary, sources, traces, constraints, pipeline,
                importNanos, generationNanos);
        lastDiagnostics = List.of();
        runNativeVerification(next);
        return next;
    }

    private VerificationReport runNativeVerification(NativeWorkspace nativeState) {
        long started = System.nanoTime();
        StringWriterPair validation = validateNative(nativeState.pipeline.state().system());
        List<org.tzi.use.plugins.jacamo.verification.VerificationResult> results = new ArrayList<>();
        results.add(new org.tzi.use.plugins.jacamo.verification.VerificationResult("USE_STRUCTURE",
                validation.structureValid ? VerificationOutcome.PASS : VerificationOutcome.FAIL, null,
                validation.output, "", List.of(), null, List.of()));
        for (var descriptor : nativeState.constraints)
            results.add(new org.tzi.use.plugins.jacamo.verification.VerificationResult(descriptor.id(),
                    validation.invariantsValid ? VerificationOutcome.PASS : VerificationOutcome.FAIL, null,
                    validation.output, descriptor.oclSource(), List.of(), null, List.of()));
        nativeState.latest = VerificationReport.offline(java.util.UUID.randomUUID().toString(),
                validation.structureValid, results, Map.of("nativeModelSha256", nativeState.pipeline.model().structuralHash(),
                        "exportSha256", nativeState.pipeline.export().recompiledStructuralHash()));
        lastFullCheckNanos = System.nanoTime() - started;
        return nativeState.latest;
    }

    private StringWriterPair validateNative(MSystem system) {
        java.io.StringWriter output = new java.io.StringWriter();
        java.io.PrintWriter writer = new java.io.PrintWriter(output, true);
        boolean structureValid = system.state().checkStructure(writer);
        boolean invariantsValid = system.state().check(writer, false, true, true, List.of());
        return new StringWriterPair(structureValid, invariantsValid, output.toString());
    }

    private List<String> nativeConstraintDependencies(String name) {
        return switch (name) {
            case "A17OrderConsistent" -> List.of("A17");
            case "A19OrderConsistent" -> List.of("A19");
            case "A20NextAgreesWithA19" -> List.of("A19", "A20");
            default -> List.of();
        };
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private MSystem currentSystem() {
        if (nativeWorkspace != null) return nativeWorkspace.pipeline.state().system();
        return workspace == null ? null : workspace.direct.system();
    }

    private Workspace buildSemantic(Path jcmFile, Path verificationProfile,
                                    org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel model,
                                    List<Diagnostic> importDiagnostics, long importNanos) {
        lastDiagnostics = List.copyOf(importDiagnostics);
        long generationStarted = System.nanoTime();
        ActiveBaseline.Selection activeBaseline = new ActiveBaseline().active(checkout);
        MappingModel mapping = activeBaseline.mapping();
        var baseline = new TransformationPlanner().plan(model, mapping);
        var structure = new VerificationSemanticLayer().apply(baseline, mapping,
                new VerificationProfileLoader().loadActive(mapping)).transformation();
        InstancePlan instances = new InstancePlanner().plan(model, mapping, structure);
        var constraints = new ConstraintExtractor().extract(model, structure, Map.of());
        OclProfileLoader profiles = new OclProfileLoader();
        List<OclProfileLoader.LoadedProfile> loaded = new ArrayList<>();
        loaded.add(profiles.loadCore());
        Path project = model.projectRoot().path();
        Path caseProfile = project.resolve("verification/" + model.projectId() + ".ocl");
        if (Files.isRegularFile(caseProfile)) loaded.add(profiles.loadCase(project, project.relativize(caseProfile)));
        if (verificationProfile != null)
            loaded.add(profiles.loadUser(verificationProfile.getParent(), verificationProfile.getFileName()));
        var generated = new OclGenerator().generate(model.projectId(), structure, constraints, loaded);
        String commands = new TextBackend().generate(model.projectId(), structure, instances).initialCommands();
        DirectUseBackend.Result direct = new DirectUseBackend().materialize(
                new TextBackend.GeneratedArtifacts(generated.useModel(), commands), instances);
        TraceIndex trace = new TraceBuilder().build(model, mapping, structure, instances);
        List<ConstraintRegistry.RegisteredProfile> registrations = new ArrayList<>();
        for (OclProfileLoader.LoadedProfile profile : loaded) {
            ConstraintOrigin origin = profile.origin().toString().contains("jacamo-core")
                    ? ConstraintOrigin.CORE : profile.origin().equals(verificationProfile)
                    ? ConstraintOrigin.USER : ConstraintOrigin.CASE;
            registrations.add(ConstraintRegistry.profile(origin, profile));
        }
        ConstraintRegistry registry = ConstraintRegistry.load(direct.system().model(), generated, registrations);
        long generationNanos = System.nanoTime() - generationStarted;
        long verificationStarted = System.nanoTime();
        VerificationReport latest = new DefaultVerificationService().runFullVerification(direct.system(), registry, trace);
        lastFullCheckNanos = System.nanoTime() - verificationStarted;
        List<Diagnostic> diagnostics = new ArrayList<>(importDiagnostics);
        diagnostics.addAll(direct.diagnostics());
        lastDiagnostics = List.copyOf(diagnostics);
        Map<Dimension, Long> counts = new EnumMap<>(Dimension.class);
        for (Dimension dimension : Dimension.values()) counts.put(dimension, model.elements().stream()
                .filter(element -> element.kind().dimension() == dimension).count());
        Map<String, Long> dimensionCounts = new LinkedHashMap<>();
        counts.forEach((key, value) -> dimensionCounts.put(key.name(), value));
        long warnings = diagnostics.stream().filter(value -> value.severity().name().equals("WARNING")).count();
        long errors = diagnostics.stream().filter(value -> value.severity().name().matches("ERROR|FATAL")).count();
        ProjectSummary summary = new ProjectSummary(jcmFile, project, model.projectId(),
                model.sourceIndex().size(), dimensionCounts,
                ActiveBaseline.VERSION, activeBaseline.hashes().get(ActiveBaseline.ECORE),
                mapping.mappingId(), mapping.schemaVersion(), activeBaseline.hashes().get(ActiveBaseline.MAPPING),
                mapping.status(),
                structure.classes().size(), instances.objects().size(), direct.structureValid(),
                Math.toIntExact(warnings), Math.toIntExact(errors));
        List<SourceRow> sources = model.sourceIndex().values().stream().map(source ->
                new SourceRow(source.path(), source.kind().name(), source.byteLength(), source.sha256())).toList();
        List<TraceRow> traces = trace.records().stream().map(record -> new TraceRow(record.sourceSemanticId(),
                record.sourceKind(), record.targetUseId(), record.targetKind(), record.mappingRuleId(),
                record.projectionRuleId(), record.status().name(),
                record.sourceSpan() == null ? null : record.sourceSpan().path(),
                record.sourceSpan() == null ? 0 : record.sourceSpan().startLine(),
                dimension(record.sourceSemanticId()))).toList();
        Map<String, String> sourceHashes = model.elements().stream().collect(java.util.stream.Collectors.toMap(
                element -> element.id().value(), element -> element.provenance().getFirst().sourceHash()));
        return new Workspace(summary, sources, List.copyOf(diagnostics), traces, direct, trace, registry, latest,
                sourceHashes, importNanos, generationNanos, structure, instances);
    }

    private String dimension(String semanticId) {
        String[] parts = semanticId.split(":", 6);
        return parts.length > 2 ? parts[2].toUpperCase() : "UNKNOWN";
    }

    private void requireWorkspace() {
        if (workspace == null && nativeWorkspace == null) throw new IllegalStateException("PROJECT_NOT_IMPORTED");
    }

    private void rejectSymbolicPath(Path output) throws java.io.IOException {
        Path current = output.getRoot();
        for (Path part : output) {
            current = current == null ? part : current.resolve(part);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) continue;
            BasicFileAttributes attributes = Files.readAttributes(current, BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther())
                throw new IllegalArgumentException("REPORT_EXPORT_SYMLINK: " + current
                        + "; choose a non-linked destination");
        }
    }

    private static Path detectCheckout() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("use-plugin/Core")) || Files.isDirectory(candidate.resolve("Core")))
                return candidate;
        }
        return current;
    }

    private static final class Workspace {
        private final ProjectSummary summary;
        private final List<SourceRow> sources;
        private final List<Diagnostic> diagnostics;
        private final List<TraceRow> traces;
        private final DirectUseBackend.Result direct;
        private final TraceIndex trace;
        private final ConstraintRegistry registry;
        private final Map<String, String> sourceHashes;
        private final long importNanos;
        private final long generationNanos;
        private VerificationReport latest;
        private final org.tzi.use.plugins.jacamo.mapping.TransformationPlan structure;
        private final org.tzi.use.plugins.jacamo.materialization.InstancePlan instances;
        private RuntimeMutationEngine mutationEngine() {
            return new RuntimeMutationEngine(direct.system(), trace, new org.tzi.use.plugins.jacamo.runtime.RuntimeMappingLoader().loadDefault(),
                    new org.tzi.use.plugins.jacamo.runtime.TraceRuntimeTargetAdapter(trace), structure, instances);
        }

        private Workspace(ProjectSummary summary, List<SourceRow> sources, List<Diagnostic> diagnostics,
                          List<TraceRow> traces, DirectUseBackend.Result direct, TraceIndex trace,
                          ConstraintRegistry registry, VerificationReport latest, Map<String, String> sourceHashes,
                          long importNanos, long generationNanos, org.tzi.use.plugins.jacamo.mapping.TransformationPlan structure,
                          org.tzi.use.plugins.jacamo.materialization.InstancePlan instances) {
            this.structure = structure; this.instances = instances;
            this.summary = summary;
            this.sources = sources;
            this.diagnostics = diagnostics;
            this.traces = traces;
            this.direct = direct;
            this.trace = trace;
            this.registry = registry;
            this.latest = latest;
            this.sourceHashes = Map.copyOf(sourceHashes);
            this.importNanos = importNanos;
            this.generationNanos = generationNanos;
        }
    }

    private static final class NativeWorkspace {
        private final ProjectSummary summary;
        private final List<SourceRow> sources;
        private final List<TraceRow> traces;
        private final List<org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor> constraints;
        private final CodeGroundedNativePipeline.Result pipeline;
        private final long importNanos;
        private final long generationNanos;
        private VerificationReport latest;

        private NativeWorkspace(ProjectSummary summary, List<SourceRow> sources, List<TraceRow> traces,
                                List<org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor> constraints,
                                CodeGroundedNativePipeline.Result pipeline, long importNanos,
                                long generationNanos) {
            this.summary = summary;
            this.sources = List.copyOf(sources);
            this.traces = List.copyOf(traces);
            this.constraints = List.copyOf(constraints);
            this.pipeline = pipeline;
            this.importNanos = importNanos;
            this.generationNanos = generationNanos;
        }
    }

    private record StringWriterPair(boolean structureValid, boolean invariantsValid, String output) { }
}
