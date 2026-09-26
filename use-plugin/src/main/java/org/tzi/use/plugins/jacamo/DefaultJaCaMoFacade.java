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
import org.tzi.use.plugins.jacamo.bridge.BridgeClient;
import org.tzi.use.plugins.jacamo.bridge.BridgeClientState;
import org.tzi.use.plugins.jacamo.bridge.BridgeConnectionConfig;
import org.tzi.use.plugins.jacamo.bridge.BridgeMirrorStateMachine;
import org.tzi.use.plugins.jacamo.bridge.BridgeProtocolException;
import org.tzi.use.plugins.jacamo.bridge.BridgeRuntimeProjector;
import org.tzi.use.plugins.jacamo.bridge.BridgeTransportFactory;
import org.tzi.use.plugins.jacamo.bridge.NativeSemanticAdapter;
import org.tzi.use.plugins.jacamo.binding.BindingEntry;
import org.tzi.use.plugins.jacamo.binding.BindingFile;
import org.tzi.use.plugins.jacamo.binding.BindingStore;
import org.tzi.use.plugins.jacamo.constraint.ConstraintExtractor;
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

/** Stateful application service behind the USE workbench. All parsing/transformation/verification lives here. */
public final class DefaultJaCaMoFacade implements JaCaMoFacade, AutoCloseable {
    public static final DefaultJaCaMoFacade INSTANCE = new DefaultJaCaMoFacade(detectCheckout());

    private final Path checkout;
    private final SemanticAuthority authority;
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
    private RuntimeVerificationEngine runtimeVerification;
    private List<Diagnostic> lastDiagnostics = List.of();
    private long lastFullCheckNanos;

    public DefaultJaCaMoFacade(Path checkout) {
        this(checkout, SemanticAuthority.configured(System.getProperty("use.jacamo.authority")),
                BridgeConnectionConfig::fromSystemProperties, BridgeTransportFactory.localTcp());
    }

    public DefaultJaCaMoFacade(Path checkout, SemanticAuthority authority,
                               Supplier<BridgeConnectionConfig> bridgeConfigurationSource,
                               BridgeTransportFactory bridgeTransportFactory) {
        this.checkout = checkout.toAbsolutePath().normalize();
        this.authority = java.util.Objects.requireNonNull(authority, "authority");
        if (authority != SemanticAuthority.BRIDGE)
            throw new IllegalArgumentException("SEMANTIC_AUTHORITY_REMOVED:" + authority);
        this.bridgeConfigurationSource = java.util.Objects.requireNonNull(bridgeConfigurationSource,
                "bridgeConfigurationSource");
        this.bridgeTransportFactory = java.util.Objects.requireNonNull(bridgeTransportFactory,
                "bridgeTransportFactory");
    }

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

    @Override public synchronized ProjectSummary projectSummary() { return workspace == null ? null : workspace.summary; }
    @Override public synchronized List<SourceRow> sources() { return workspace == null ? List.of() : workspace.sources; }
    @Override public synchronized List<Diagnostic> diagnostics() { return lastDiagnostics; }
    @Override public synchronized List<TraceRow> traces() { return workspace == null ? List.of() : workspace.traces; }
    @Override public synchronized List<org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor> constraints() {
        return workspace == null ? List.of() : workspace.registry.descriptors();
    }

    @Override public synchronized VerificationReport runFullVerification() {
        requireWorkspace();
        long started = System.nanoTime();
        workspace.latest = new DefaultVerificationService().runFullVerification(workspace.direct.system(),
                workspace.registry, workspace.trace);
        lastFullCheckNanos = System.nanoTime() - started;
        return workspace.latest;
    }

    @Override public synchronized VerificationReport latestVerification() {
        if (workspace == null) return null;
        var runtimeLatest = runtimeVerification == null ? null : runtimeVerification.latestReport();
        return runtimeLatest == null ? workspace.latest : runtimeLatest.verification();
    }

    @Override public synchronized void loadVerificationProfile(Path profile) {
        requireWorkspace();
        if (profile == null || !Files.isRegularFile(profile.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("OCL_USER_PROFILE_IO");
        Path candidate = profile.toAbsolutePath().normalize();
        synchronizeBridge(entry, candidate);
    }

    @Override public synchronized void exportVerificationReport(Path destination) {
        requireWorkspace();
        if (workspace.latest == null) runFullVerification();
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
                    ? new VerificationReportExporter().toJson(workspace.latest)
                    : new VerificationReportExporter().toMarkdown(workspace.latest);
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
            case DISCONNECTED -> workspace == null ? MirrorState.OFFLINE : MirrorState.STALE;
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
        if (bridgeAccepted == null)
            return new AuthorityStatus(authority, readiness, Map.of(), "UNAVAILABLE", "", "", 0,
                    endpoint, bridgeDiagnostic.isBlank() ? "BRIDGE_NOT_SYNCHRONIZED" : bridgeDiagnostic);
        Map<String,String> capabilities = new LinkedHashMap<>();
        bridgeAccepted.capabilities().forEach(value -> capabilities.put(value.name(), value.status().name()));
        return new AuthorityStatus(authority, readiness, capabilities, bridgeAccepted.completeness().name(),
                bridgeAccepted.modelRevision(), bridgeAccepted.sessionId(), bridgeAccepted.generation(), endpoint,
                bridgeDiagnostic);
    }

    @Override public synchronized PerformanceMetrics performanceMetrics() {
        if (workspace == null) return PerformanceMetrics.empty();
        RuntimeStatus runtimeStatus = runtimeStatus();
        Runtime jvm = Runtime.getRuntime();
        return new PerformanceMetrics(workspace.importNanos, workspace.generationNanos, lastFullCheckNanos,
                runtimeStatus.lastLatencyNanos(), jvm.totalMemory() - jvm.freeMemory());
    }

    @Override public synchronized void persistBinding(Path destination, BindingRequest request,
                                                      String selectedTargetId, String reason) {
        requireWorkspace();
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

    private void installWorkspace(Workspace next, Path nextEntry, Path nextProfile) {
        var verification = new RuntimeVerificationEngine(next.direct.system(), next.registry, next.trace);
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
                        synchronized (pending) {
                            BridgeRuntimeProjector projector = projectorReference.get();
                            if (projector == null) {
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
            NativeSemanticAdapter.Result adapted = new NativeSemanticAdapter().adapt(accepted.model(),
                    selectedJcm.getParent(), accepted.projectKey());
            long importNanos = System.nanoTime() - importStarted;
            Workspace next = buildSemantic(selectedJcm, verificationProfile, adapted.model(),
                    adapted.model().diagnostics(), importNanos);
            Set<String> sources = accepted.runtime().endWatermarks().keySet();
            if (sources.isEmpty()) throw new BridgeProtocolException("BRIDGE_RUNTIME_SOURCE_REQUIRED");
            BridgeRuntimeProjector projector = new BridgeRuntimeProjector(sources, adapted, next.trace,
                    next.mutationEngine());
            synchronized (pending) {
                projector.applySnapshot(accepted.runtime());
                projectorReference.set(projector);
                while (!pending.isEmpty()) if (projector.apply(pending.removeFirst())) bridgeProcessed.incrementAndGet();
            }
            BridgeClient previousClient = bridgeClient;
            installWorkspace(next, selectedJcm, verificationProfile);
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

    private Workspace buildSemantic(Path jcmFile, Path verificationProfile,
                                    org.tzi.use.plugins.jacamo.semantic.JaCaMoSemanticModel model,
                                    List<Diagnostic> importDiagnostics, long importNanos) {
        lastDiagnostics = List.copyOf(importDiagnostics);
        long generationStarted = System.nanoTime();
        ActiveBaseline.Selection activeBaseline = new ActiveBaseline().fromCheckout(checkout);
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
        if (workspace == null) throw new IllegalStateException("PROJECT_NOT_IMPORTED");
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
}
