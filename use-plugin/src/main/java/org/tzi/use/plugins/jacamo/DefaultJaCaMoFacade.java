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
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationCoordinator;
import org.tzi.use.plugins.jacamo.codegrounded.rule.CodeGroundedRuleCatalog;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter;
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
    private final NativeProjectionMode projectionMode;
    private final Session session;
    private final Supplier<BridgeConnectionConfig> bridgeConfigurationSource;
    private final BridgeTransportFactory bridgeTransportFactory;
    private volatile BridgeConnectionConfig configuredBridge;
    private volatile BridgeClient bridgeClient;
    private volatile BridgeMirrorStateMachine bridgeMirror;
    private volatile BridgeClient.Accepted bridgeAccepted;
    private volatile String bridgeDiagnostic = "";
    private volatile java.time.Instant bridgeLastSync;
    private final AtomicLong bridgeProcessed = new AtomicLong();
    private Path entry;
    private Path userProfile;
    private volatile Workspace workspace;
    private volatile NativeWorkspace nativeWorkspace;
    private volatile RuntimeVerificationEngine runtimeVerification;
    private volatile List<Diagnostic> lastDiagnostics = List.of();
    private volatile long lastFullCheckNanos;
    private final ManagedRuntimeWorkflow workflow = new ManagedRuntimeWorkflow();
    private volatile org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeReplayStepController stepReplay;
    private volatile boolean closed;
    private volatile boolean replayOpening;

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
        // The production constructor must never opt into the compatibility projector implicitly.
        // Callers that need the frozen V2 path must pass PipelineMode.LEGACY_V2 explicitly.
        this(checkout, authority, bridgeConfigurationSource, bridgeTransportFactory,
                PipelineMode.CODE_GROUNDED_NATIVE, null);
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
        // Freeze the launch selection for this facade, including subsequent rebuilds/resyncs.
        this.projectionMode = pipelineMode == PipelineMode.CODE_GROUNDED_NATIVE
                ? NativeProjectionMode.configured(System.getProperty(NativeProjectionMode.PROPERTY))
                : NativeProjectionMode.AUTO;
        this.session = session;
    }

    public static DefaultJaCaMoFacade forSession(Session session) {
        return new DefaultJaCaMoFacade(detectCheckout(), java.util.Objects.requireNonNull(session, "session"));
    }

    public PipelineMode pipelineMode() { return pipelineMode; }
    public NativeProjectionMode projectionMode() { return projectionMode; }
    MSystem materializedSystem() { return currentSystem(); }

    // Read-only UI methods must never hold the facade monitor while a writer waits for USE's EDT.
    // Published workspace pointers are immutable installations; formal reads use their coordinator.
    @Override public String status() {
        AuthorityStatus state = authorityStatus();
        return "JaCaMo Bridge " + state.readiness() + (state.modelRevision().isBlank() ? ""
                : " model=" + state.modelRevision()) + (state.diagnostic().isBlank() ? ""
                : " diagnostic=" + state.diagnostic());
    }

    @Override public synchronized ProjectSummary importProject(Path jcmFile) {
        requireNotReplaying();
        if (jcmFile == null || !jcmFile.getFileName().toString().toLowerCase().endsWith(".jcm")) {
            workflow.importFailed("IMPORT_JCM_REQUIRED");
            throw new IllegalArgumentException("IMPORT_JCM_REQUIRED");
        }
        Path candidate = jcmFile.toAbsolutePath().normalize();
        workflow.importing();
        try {
            var imported = synchronizeBridge(candidate, null);
            workflow.imported(candidate, authorityStatus(), session != null && session.system() == currentSystem());
            return imported;
        } catch (RuntimeException failure) { workflow.importFailed(failure.getMessage()); throw failure; }
    }

    @Override public WorkflowStatus workflowStatus() {
        if (stepReplayStatus()!=null) return new WorkflowStatus("REPLAY",false,false,"","RECORDED_OFFLINE; OBSERVATION_DISCONNECTED","","");
        var state=workflow.status();
        if(state.startAvailable()) {
            var current=nativeWorkspace; var accepted=authorityStatus();
            var snapshot=verificationSnapshot();
            if(current==null || session==null || session.system()!=currentSystem()
                    || accepted.readiness()!=BridgeClientState.LIVE || !workflow.owns(accepted)
                    || snapshot.result()==null || !snapshot.result().freshness().equals("CURRENT_OBSERVED"))
                return new WorkflowStatus(state.state(),false,state.managed(),state.runId(),
                        "STARTUP_NATIVE_SESSION_OR_BRIDGE_NOT_CURRENT",state.bootstrapAt(),state.startedAt());
        }
        return state;
    }
    @Override public org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot verificationSnapshot() {
        var replay=stepReplay;
        if(replay!=null && replay.active())return replay.verificationSnapshot();
        var current = nativeWorkspace;
        return current == null || current.runtimeProjector == null
                ? org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot.empty()
                : current.runtimeProjector.coordinator().verificationSnapshot();
    }
    @Override public void startRuntime() {
        requireNotReplaying();
        requireNativeWorkspace();
        var state=workflowStatus();
        if(!state.startAvailable()) throw new IllegalStateException("START_RUNTIME_UNAVAILABLE:"+state.diagnostic());
        workflow.start();
    }
    @Override public void cancelRuntimeStartup() { workflow.cancel(); }

    @Override public synchronized ProjectSummary rebuild() {
        requireWorkspace();
        return synchronizeBridge(entry, userProfile);
    }

    @Override public ProjectSummary projectSummary() {
        var nativeState = nativeWorkspace; var legacyState = workspace;
        return nativeState != null ? nativeState.summary : legacyState == null ? null : legacyState.summary;
    }
    @Override public List<SourceRow> sources() {
        var nativeState = nativeWorkspace; var legacyState = workspace;
        return nativeState != null ? nativeState.sources : legacyState == null ? List.of() : legacyState.sources;
    }
    @Override public List<Diagnostic> diagnostics() { return lastDiagnostics; }
    @Override public List<TraceRow> traces() {
        var nativeState = nativeWorkspace; var legacyState = workspace;
        return nativeState != null ? nativeState.traces : legacyState == null ? List.of() : legacyState.traces;
    }
    @Override public List<org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor> constraints() {
        if(stepReplayStatus()!=null)return List.of(); // recorded outcomes/profile carry their own attribution
        var nativeState = nativeWorkspace; var legacyState = workspace;
        if (nativeState == null) return legacyState == null ? List.of() : legacyState.registry.descriptors();
        List<org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor> descriptors = new ArrayList<>(nativeState.constraints);
        if (nativeState.runtimeProjector != null) nativeState.runtimeProjector.coordinator().read(() -> {
            var profile = nativeState.runtimeProjector.coordinator().constraints().profile();
            if (profile != null) for (var constraint : profile.constraints()) {
                var invariant = nativeState.pipeline.model().model().classInvariants().stream()
                        .filter(item -> item.qualifiedName().equals(constraint.constraintId())).findFirst().orElseThrow();
                Path source = Path.of(constraint.sourceFile());
                descriptors.add(new org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor("EXTERNAL:" + constraint.constraintId(),
                        constraint.constraintId(), constraint.contextClass(), null, org.tzi.use.plugins.jacamo.verification.ConstraintKind.INV,
                        ConstraintOrigin.USER, source, new org.tzi.use.plugins.jacamo.project.SourceSpan(source, 1, 1, 1, 1),
                        List.copyOf(constraint.requiredRules()), constraint.enabled(), invariant.bodyExpression().toString()));
            }
            return null;
        });
        return List.copyOf(descriptors);
    }

    @Override public synchronized VerificationReport runFullVerification() {
        requireNotReplaying();
        requireWorkspace();
        if (nativeWorkspace != null) return runNativeVerification(nativeWorkspace);
        long started = System.nanoTime();
        workspace.latest = new DefaultVerificationService().runFullVerification(workspace.direct.system(),
                workspace.registry, workspace.trace);
        lastFullCheckNanos = System.nanoTime() - started;
        return workspace.latest;
    }

    @Override public VerificationReport latestVerification() {
        if(stepReplayStatus()!=null) {
            var result=verificationSnapshot().result();
            if(result==null)return null;
            var values=result.outcomes().stream().map(value->new org.tzi.use.plugins.jacamo.verification.VerificationResult(
                    value.constraintId(),value.outcome(),value.contextClass(),value.diagnostic(),value.expression(),List.of(),null,List.of())).toList();
            return new VerificationReport("1.0.0",result.resultHash(),result.verifiedAt(),"RECORDED_REPLAY",true,values,
                    Map.of("stateHash",result.stateHash(),"stateVersion",Long.toString(verificationSnapshot().currentVersion()),
                            "scope","OBSERVED_SUPPORTED_PROJECTION_ONLY","coverage",result.coverage(),"freshness",result.freshness()));
        }
        var nativeState = nativeWorkspace; var legacyState = workspace; var runtime = runtimeVerification;
        if (nativeState != null && nativeState.runtimeProjector != null) {
            var result = nativeState.runtimeProjector.coordinator().verificationSnapshot().result();
            return result == null ? null : nativeReport(nativeState, result);
        }
        if (nativeState != null) return nativeState.latest;
        if (legacyState == null) return null;
        var runtimeLatest = runtime == null ? null : runtime.latestReport();
        return runtimeLatest == null ? legacyState.latest : runtimeLatest.verification();
    }

    @Override public synchronized void loadVerificationProfile(Path profile) {
        requireNotReplaying();
        requireWorkspace();
        if (nativeWorkspace != null) {
            if (profile == null) throw new IllegalArgumentException("OCL_USER_PROFILE_IO");
            nativeWorkspace.runtimeProjector.coordinator().loadProfile(profile);
            workflow.profileReady();
            userProfile = profile.toAbsolutePath().normalize();
            return;
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

    @Override public synchronized void exportNativeUse(Path destination) {
        var replay=stepReplay;
        if(replay!=null&&replay.active()) { replay.read(system->new NativeUseExporter().export(system.model(),destination)); return; }
        requireNativeWorkspace();
        nativeWorkspace.runtimeProjector.coordinator().read(() -> new NativeUseExporter().export(nativeWorkspace.pipeline.model().model(), destination));
    }

    @Override public synchronized void exportNativeSoil(Path destination) {
        var replay=stepReplay;
        if(replay!=null&&replay.active()) { replay.read(system->new NativeUseSoilExporter().export(system,destination)); return; }
        requireNativeWorkspace();
        nativeWorkspace.runtimeProjector.coordinator().read(() -> new NativeUseSoilExporter().export(nativeWorkspace.pipeline.state().system(), destination));
    }

    @Override public RuntimeVerificationResult runtimeVerificationResult() {
        if(stepReplayStatus()!=null)return verificationSnapshot().result();
        var nativeState = nativeWorkspace;
        return nativeState == null || nativeState.runtimeProjector == null ? null : nativeState.runtimeProjector.coordinator().verificationSnapshot().result();
    }
    @Override public List<RuntimeVerificationResult> runtimeVerificationHistory() {
        var replay=stepReplay;
        if(replay!=null && replay.active())return replay.historyTail().entries().stream().map(value->value.result()).toList();
        var nativeState = nativeWorkspace;
        return nativeState == null || nativeState.runtimeProjector == null ? List.of() : nativeState.runtimeProjector.coordinator().history();
    }
    @Override public synchronized void exportRuntimeReplay(Path directory) {
        requireNotReplaying();
        requireNativeWorkspace();
        new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay().exportBundle(
                nativeWorkspace.runtimeProjector, nativeWorkspace.pipeline.export().useText(), directory);
    }

    @Override public void openStepReplay(Path recording) {
        if(javax.swing.SwingUtilities.isEventDispatchThread())throw new IllegalStateException("REPLAY_REQUIRES_WORKER");
        org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeReplayStepController controller;
        Runnable settleDelivery;
        synchronized(this) {
            if(closed)throw new IllegalStateException("WORKBENCH_CLOSED");
            if(session==null)throw new IllegalStateException("REPLAY_SESSION_REQUIRED");
            if(bridgeClient!=null)throw new IllegalStateException("REPLAY_REQUIRES_OBSERVATION_DISCONNECT: Export replay before Disconnect (stop observation, not producer)");
            MSystem owned=stepReplay!=null && stepReplay.active() ? stepReplay.system()
                    : nativeWorkspace==null ? null : nativeWorkspace.pipeline.state().system();
            if(session.hasSystem() && session.system()!=owned)
                throw new IllegalStateException("REPLAY_SESSION_WORKSPACE_NOT_OWNED: import in this Workbench or use an empty Session; foreign live delivery cannot be settled here");
            if(replayOpening)throw new IllegalStateException("REPLAY_NAVIGATION_BUSY");
            if(stepReplay==null)stepReplay=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeReplayStepController(session);
            controller=stepReplay;
            settleDelivery=nativeWorkspace==null ? () -> { } : nativeWorkspace.stopRuntimeDelivery;
            replayOpening=true;
        }
        // No facade monitor across worker/EDT barriers. A failed candidate never replaces Session.
        try {
            // Disconnect may have run on EDT, where waiting would deadlock an in-flight native writer.
            // Open is a worker boundary: settle that writer before constructing/selecting replay.
            settleDelivery.run();
            controller.open(recording);
        } finally {replayOpening=false;}
    }
    @Override public void resetStepReplay(){requireReplay().reset();}
    @Override public void previousStepReplay(){requireReplay().previous();}
    @Override public void nextStepReplay(){requireReplay().next();}
    @Override public org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeReplayStepController.Status stepReplayStatus(){
        var replay=stepReplay;return replay==null?null:replay.status();
    }
    @Override public boolean stepReplayBusy(){var replay=stepReplay;return replayOpening||(replay!=null&&replay.busy());}
    private org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeReplayStepController requireReplay(){
        var replay=stepReplay;if(replay==null)throw new IllegalStateException("REPLAY_NOT_OPEN");return replay;
    }
    private void requireNotReplaying(){
        if(closed)throw new IllegalStateException("WORKBENCH_CLOSED");
        var replay=stepReplay;if(replayOpening||(replay!=null&&(replay.active()||replay.busy()))
                || (session!=null && session.hasSystem() && session.system().isReadOnly()))
            throw new IllegalStateException("RECORDED_REPLAY_READ_ONLY: close Workbench to end replay; no automatic Return Live");
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
        requireNotReplaying();
        requireWorkspace();
        synchronizeBridge(entry, userProfile);
    }

    @Override public synchronized void disconnectRuntime() {
        if(stepReplayStatus()!=null)return; // already offline; do not modify the original recording/system
        // Disconnect is observation loss, not producer stop or permission to discard its evidence/profile.
        var current=nativeWorkspace;
        if(current!=null && current.runtimeProjector!=null) {
            current.stopRuntimeDelivery.run();
            current.runtimeProjector.coordinator().coverageGap("BRIDGE_DISCONNECTED");
        }
        closeBridgeClient();
        bridgeMirror = null;
        bridgeDiagnostic = "BRIDGE_DISCONNECTED";
    }

    @Override public synchronized void resyncRuntime() {
        requireNotReplaying();
        requireWorkspace();
        synchronizeBridge(entry, userProfile);
    }

    @Override public RuntimeStatus runtimeStatus() {
        var replay=stepReplay;
        if(replay!=null&&replay.active()) {
            var result=replay.verificationSnapshot().result();var state=replay.status();
            return new RuntimeStatus(MirrorState.REPLAY,0,0,0,0,0,0,null,state.event(),0,state.stateVersion(),
                    result==null?0:(int)result.count(VerificationOutcome.FAIL));
        }
        var mirror = bridgeMirror; var nativeState = nativeWorkspace; var runtime = runtimeVerification; var accepted = bridgeAccepted;
        BridgeClientState state = mirror == null ? BridgeClientState.DISCONNECTED : mirror.state();
        MirrorState mirrorState = switch (state) {
            case DISCONNECTED -> workspace == null && nativeWorkspace == null ? MirrorState.OFFLINE : MirrorState.STALE;
            case NEGOTIATING -> MirrorState.CONNECTING;
            case MODEL_SYNC, SNAPSHOT_SYNC -> MirrorState.SYNCING;
            case LIVE -> MirrorState.LIVE;
            case RESYNC_REQUIRED, STALE -> MirrorState.STALE;
        };
        int violations = runtime == null ? 0 : (int) runtime.reports().stream()
                .flatMap(report -> report.verification().results().stream())
                .filter(result -> result.outcome() == VerificationOutcome.FAIL).count();
        if (nativeState != null && nativeState.runtimeProjector != null) {
            var coordinator = nativeState.runtimeProjector.coordinator();
            return coordinator.read(() -> {
            var result = coordinator.latest(); var observed = coordinator.lastObservation();
            long rejected = coordinator.history().stream().filter(item -> item.freshness().equals("STALE")).count();
            return new RuntimeStatus(result.freshness().equals("STALE") ? MirrorState.STALE : mirrorState,
                    coordinator.backlog(), coordinator.highWatermark(), bridgeProcessed.get(), rejected,
                    result.count(VerificationOutcome.ERROR), coordinator.journal().hasGap() ? 1 : 0,
                    bridgeLastSync, observed.eventId() + " / " + observed.checkpointId(), result.durationNanos(), result.stateVersion(),
                    (int) coordinator.history().stream().mapToLong(item -> item.count(VerificationOutcome.FAIL)).sum());
            });
        }
        return new RuntimeStatus(mirrorState, 0, 0, bridgeProcessed.get(), 0,
                state == BridgeClientState.RESYNC_REQUIRED ? 1 : 0, 0, bridgeLastSync, "", 0,
                accepted == null ? 0 : accepted.generation(), violations);
    }

    @Override public AuthorityStatus authorityStatus() {
        var replay=stepReplay;
        if(replay!=null&&replay.active()) {
            var result=replay.verificationSnapshot().result();
            return new AuthorityStatus(SemanticAuthority.BRIDGE,BridgeClientState.DISCONNECTED,Map.of(),result.coverage(),
                    result.modelRevision(),result.sessionId(),result.generation(),"","RECORDED_REPLAY; NOT LIVE; OBSERVED_SUPPORTED_PROJECTION_ONLY");
        }
        BridgeConnectionConfig configuration = configuredBridge;
        var mirror = bridgeMirror; var client = bridgeClient; var accepted = bridgeAccepted;
        String endpoint = configuration == null ? "" : configuration.displayEndpoint();
        BridgeClientState readiness = mirror == null ? BridgeClientState.DISCONNECTED : mirror.state();
        String diagnostic = bridgeDiagnostic;
        if (client != null && !client.diagnostic().isBlank()) diagnostic = client.diagnostic();
        if (accepted == null)
            return new AuthorityStatus(authority, readiness, Map.of(), "UNAVAILABLE", "", "", 0,
                    endpoint, diagnostic.isBlank() ? "BRIDGE_NOT_SYNCHRONIZED" : diagnostic);
        Map<String,String> capabilities = new LinkedHashMap<>();
        accepted.capabilities().forEach(value -> capabilities.put(value.name(), value.status().name()));
        return new AuthorityStatus(authority, readiness, capabilities, accepted.completeness().name(),
                accepted.modelRevision(), accepted.sessionId(), accepted.generation(), endpoint,
                diagnostic);
    }

    @Override public FormalStateStatus formalStateStatus() {
        var replay=stepReplay;
        if(replay!=null&&replay.active())return replay.read(this::readFormalStateStatus);
        var nativeState = nativeWorkspace;
        if (nativeState != null && nativeState.runtimeProjector != null)
            return nativeState.runtimeProjector.coordinator().read(() -> readFormalStateStatus(nativeState.pipeline.state().system()));
        return readFormalStateStatus(currentSystem());
    }
    private FormalStateStatus readFormalStateStatus(MSystem system) {
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

    @Override public PerformanceMetrics performanceMetrics() {
        var nativeState = nativeWorkspace; var legacyState = workspace;
        if (legacyState == null && nativeState == null) return PerformanceMetrics.empty();
        RuntimeStatus runtimeStatus = runtimeStatus();
        Runtime jvm = Runtime.getRuntime();
        long importNanos = nativeState != null ? nativeState.importNanos : legacyState.importNanos;
        long generationNanos = nativeState != null ? nativeState.generationNanos : legacyState.generationNanos;
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
        if(closed)return;closed=true;
        if(stepReplay!=null)stepReplay.close();
        if (!workflow.status().state().equals("LIVE")) workflow.cancel();
        closeNativeRuntimeArtifacts();
        closeBridgeClient();
        bridgeMirror = null;
        bridgeAccepted = null;
    }
    @Override public org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeHistoryPage runtimeHistoryTail() {
        var replay=stepReplay;if(replay!=null&&replay.active())return replay.historyTail();
        var current = nativeWorkspace;
        return current == null || current.runtimeProjector == null ? org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeHistoryPage.empty()
                : current.runtimeProjector.coordinator().journal().tailPage(128);
    }
    @Override public org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeHistoryPage runtimeHistoryPage(long offset, int limit) {
        var replay=stepReplay;if(replay!=null&&replay.active())return replay.historyPage(offset,limit);
        var current = nativeWorkspace;
        if (current == null || current.runtimeProjector == null) throw new IllegalStateException("PROJECT_NOT_IMPORTED");
        return current.runtimeProjector.coordinator().journal().page(offset, limit);
    }
    @Override public org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReanalysis.Report reanalyzeRuntime(Path bundle, Path output) {
        var current=nativeWorkspace;
        if(current==null || current.runtimeProjector==null) throw new IllegalStateException("PROJECT_NOT_IMPORTED");
        var coordinator=current.runtimeProjector.coordinator();
        var profile=coordinator.read(()->coordinator.constraints().profile());
        if(profile==null) throw new IllegalStateException("NATIVE_PROFILE_NOT_LOADED");
        if(output.toAbsolutePath().normalize().startsWith(coordinator.directory())) throw new IllegalArgumentException("REANALYSIS_OUTPUT_INSIDE_LIVE_RUNTIME");
        return new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReanalysis().analyze(bundle,
                profile.sourceFile(),profile.source(),profile.constraints().stream().collect(java.util.stream.Collectors.toMap(
                        org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.RegisteredConstraint::constraintId,
                        org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.RegisteredConstraint::enabled)),
                profile.constraints().stream().collect(java.util.stream.Collectors.toMap(
                        org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.RegisteredConstraint::constraintId,
                        org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.RegisteredConstraint::negated)),output);
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
        requireNotReplaying();
        BridgeConnectionConfig configuration = configuredBridge == null ? bridgeConfigurationSource.get() : configuredBridge;
        configuredBridge = configuration;
        BridgeMirrorStateMachine candidateMirror = new BridgeMirrorStateMachine(8_192);
        ArrayDeque<RuntimeEvent> pending = new ArrayDeque<>();
        AtomicReference<BridgeRuntimeProjector> projectorReference = new AtomicReference<>();
        AtomicReference<NativeRuntimeProjector> nativeProjectorReference = new AtomicReference<>();
        AtomicReference<NativeRuntimeProjector> newlyCreatedNativeProjector = new AtomicReference<>();
        AtomicReference<BridgeClient.CoverageFailure> nativeCoverageFailure = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean deliveryActive = new java.util.concurrent.atomic.AtomicBoolean(true);
        BridgeClient candidate = null;
        try {
            candidate = new BridgeClient(bridgeTransportFactory.open(configuration), candidateMirror,
                    configuration.distributionSha256(), configuration.requiredCapabilities(),
                    configuration.maxBufferedEvents(), event -> {
                        if (pipelineMode == PipelineMode.CODE_GROUNDED_NATIVE) {
                            if (!deliveryActive.get()) return;
                            synchronized (pending) {
                                if (!deliveryActive.get()) return;
                                NativeRuntimeProjector projector = nativeProjectorReference.get();
                                if (projector == null) {
                                    // Evidence-only events are journalled too; they never increment stateVersion.
                                    if (pending.size() >= configuration.maxBufferedEvents())
                                        throw new BridgeProtocolException("BRIDGE_FACADE_BUFFER_OVERFLOW");
                                    pending.addLast(event);
                                } else {
                                    projector.apply(event);
                                    bridgeProcessed.incrementAndGet();
                                }
                            }
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
                    }, (event, diagnostic) -> {
                        if (!deliveryActive.get()) return;
                        nativeCoverageFailure.set(new BridgeClient.CoverageFailure(event, diagnostic));
                        var projector = nativeProjectorReference.get();
                        if (projector != null) javax.swing.SwingUtilities.invokeLater(() -> {
                            // A queued callback from a superseded subscription cannot poison the new generation.
                            if (deliveryActive.get()) projector.coordinator().coverageGap(event, diagnostic);
                        });
                    });
            long importStarted = System.nanoTime();
            BridgeClient.Accepted accepted = candidate.synchronize();
            validateBridgeSelection(selectedJcm, accepted);
            if (pipelineMode == PipelineMode.CODE_GROUNDED_NATIVE) {
                long importNanos = System.nanoTime() - importStarted;
                NativeWorkspace previous = nativeWorkspace;
                boolean compatibleResync = previous != null && previous.runtimeProjector != null && bridgeAccepted != null
                        && bridgeAccepted.modelRevision().equals(accepted.modelRevision());
                var savedProfile = previous == null || previous.runtimeProjector == null ? null
                        : previous.runtimeProjector.coordinator().read(() -> previous.runtimeProjector.coordinator().constraints().profile());
                NativeWorkspace next = compatibleResync ? previous : buildNativeSemantic(selectedJcm, accepted.model(), importNanos);
                NativeRuntimeProjector nativeProjector = compatibleResync ? previous.runtimeProjector
                        : new NativeRuntimeProjector(next.pipeline, accepted.sessionId(), accepted.generation(),
                                accepted.modelRevision(), RuntimeVerificationCoordinator.createEphemeralDirectory(runtimeRoot()));
                if (!compatibleResync) newlyCreatedNativeProjector.set(nativeProjector);
                if (!compatibleResync && savedProfile != null)
                    nativeProjector.coordinator().loadSavedProfile(savedProfile);
                if (previous != null) previous.stopRuntimeDelivery.run();
                BridgeClient previousClient = bridgeClient;
                if (previousClient != null) previousClient.close();
                synchronized (pending) {
                    nativeProjector.applySnapshot(accepted.runtime(), accepted.sessionId(), accepted.generation());
                    nativeProjectorReference.set(nativeProjector);
                    while (!pending.isEmpty()) {
                        nativeProjector.apply(pending.removeFirst());
                        bridgeProcessed.incrementAndGet();
                    }
                }
                var failure = nativeCoverageFailure.get();
                if (failure != null) nativeProjector.coordinator().coverageGap(failure.event(), failure.diagnostic());
                next.runtimeProjector = nativeProjector;
                next.stopRuntimeDelivery = () -> {
                    deliveryActive.set(false);
                    // A worker entering replay waits for an already-admitted delivery to settle.
                    // UI shutdown must not deadlock behind a delivery waiting for this same EDT.
                    if(!javax.swing.SwingUtilities.isEventDispatchThread())synchronized(pending){ }
                };
                if (!compatibleResync && session != null) new NativeUseSessionActivator().activate(session, next.pipeline);
                if (!compatibleResync && previous != null && previous.runtimeProjector != null)
                    previous.runtimeProjector.close();
                nativeWorkspace = next;
                workspace = null;
                entry = selectedJcm;
                runtimeVerification = null;
                bridgeClient = candidate;
                bridgeMirror = candidateMirror;
                bridgeAccepted = accepted;
                bridgeLastSync = accepted.runtime().captureEndedAt();
                bridgeDiagnostic = "";
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
            deliveryActive.set(false);
            if (candidate != null) candidate.close();
            NativeRuntimeProjector created = newlyCreatedNativeProjector.get();
            if (created != null && (nativeWorkspace == null || nativeWorkspace.runtimeProjector != created)) created.close();
            bridgeDiagnostic = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            if (pipelineMode == PipelineMode.CODE_GROUNDED_NATIVE && nativeWorkspace != null && nativeWorkspace.runtimeProjector != null)
                nativeWorkspace.runtimeProjector.coordinator().coverageGap("BRIDGE_RESYNC_FAILED:" + bridgeDiagnostic);
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

    private void closeNativeRuntimeArtifacts() {
        NativeWorkspace current = nativeWorkspace;
        if (current == null) return;
        current.stopRuntimeDelivery.run();
        if (current.runtimeProjector != null) {
            current.runtimeProjector.close();
            current.runtimeProjector = null;
        }
        runtimeVerification = null;
    }

    private NativeWorkspace buildNativeSemantic(Path jcmFile, org.jacamo.bridge.contract.ModelSnapshot snapshot,
                                                long importNanos) {
        long generationStarted = System.nanoTime();
        CodeGroundedNativePipeline.Result pipeline = new CodeGroundedNativePipeline().build(snapshot, projectionMode);
        long generationNanos = System.nanoTime() - generationStarted;
        MSystem system = pipeline.state().system();
        Map<String,Long> counts = new LinkedHashMap<>();
        long jcmDeclarations = pipeline.source().snapshot().agentDeclarations().size()
                + pipeline.source().snapshot().workspaceDeclarations().size()
                + pipeline.source().snapshot().artifactDeclarations().size()
                + pipeline.source().snapshot().organizationDeployments().size()
                + pipeline.source().snapshot().groupDeployments().size()
                + pipeline.source().snapshot().schemeDeployments().size()
                + pipeline.source().snapshot().institutionDeployments().size();
        counts.put("JCM", 1L + jcmDeclarations);
        long plans = pipeline.source().programs().stream().mapToLong(value -> value.planLibrary().plans().size()).sum();
        long body = pipeline.source().programs().stream().flatMap(value -> value.planLibrary().plans().stream())
                .mapToLong(value -> value.body().size()).sum();
        long actions = pipeline.source().programs().stream().mapToLong(value -> value.actions().size()).sum();
        long beliefs = pipeline.source().programs().stream().mapToLong(value -> value.beliefs().size()).sum();
        long goals = pipeline.source().programs().stream().mapToLong(value -> value.goals().size()).sum();
        long beliefRules = pipeline.source().programs().stream().mapToLong(value -> value.beliefRules().size()).sum();
        counts.put("JASON", (long) pipeline.source().programs().size() * 2 + plans * 2 + body
                + actions + beliefs + goals + beliefRules);
        long cartago = pipeline.source().snapshot().cartagoEnvironments().stream().mapToLong(value -> 1L
                + value.workspaces().size() + value.artifactTypes().size() + value.artifacts().size()
                + value.operations().size() + value.backingOperations().size() + value.guards().size()
                + value.liveProperties().size() + value.propertySnapshots().size() + value.artifactInfos().size()
                + value.signals().size() + value.agents().size()).sum();
        long moise = pipeline.source().snapshot().moiseOrganizations().stream().mapToLong(organization -> {
            var structural = organization.structuralSpecification();
            var functional = organization.functionalSpecification();
            // These are typed source records, including explicit containers and relation helpers,
            // not a claim that every record is a separate live JaCaMo domain object.
            return 4L + structural.roles().size() + structural.groups().size()
                    + structural.roleRelations().size() + structural.links().size() + structural.compatibilities().size()
                    + structural.groupRoleCardinalities().size() + structural.subGroupCardinalities().size()
                    + functional.schemes().size() + functional.schemeMissionCardinalities().size()
                    + functional.schemes().stream().mapToLong(scheme ->
                            (long) scheme.missions().size() + scheme.goals().size() + scheme.plans().size()).sum()
                    + organization.normativeSpecification().norms().size();
        }).sum();
        counts.put("CARTAGO", cartago); counts.put("MOISE", moise);
        counts.put("CROSS", (long) pipeline.source().snapshot().exactBindings().size());
        String catalogHash = sha256(new CodeGroundedRuleCatalog().rules().toString());
        ProjectSummary summary = new ProjectSummary(jcmFile, jcmFile.getParent(), pipeline.source().project().name(),
                snapshot.sources().size(), counts, "CODE_GROUNDED_NATIVE-1.0.0",
                pipeline.model().structuralHash(), "CodeGroundedRuleCatalog", CodeGroundedRuleCatalog.VERSION, catalogHash,
                "NATIVE_CURRENT", system.model().classes().size(), system.state().numObjects(),
                pipeline.state().structureValid(), 0, 0);
        List<SourceRow> sources;
        try {
            sources = List.of(new SourceRow(jcmFile, "JCM", Files.size(jcmFile),
                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(Files.readAllBytes(jcmFile)))));
        } catch (Exception error) {
            throw new IllegalStateException("NATIVE_SOURCE_EVIDENCE_FAILED: " + jcmFile, error);
        }
        List<TraceRow> traces = pipeline.trace().records().stream().map(record -> nativeTraceRow(record, jcmFile)).toList();
        List<org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor> constraints = new ArrayList<>(system.model()
                .classInvariants().stream().map(invariant -> new org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor(
                        "NATIVE:" + invariant.qualifiedName(), invariant.name(), invariant.cls().name(), null,
                        org.tzi.use.plugins.jacamo.verification.ConstraintKind.INV,
                        org.tzi.use.plugins.jacamo.verification.ConstraintOrigin.CORE, jcmFile,
                        new org.tzi.use.plugins.jacamo.project.SourceSpan(jcmFile, 1, 1, 1, 1),
                        pipeline.model().constraints().stream().filter(spec -> spec.name().equals(invariant.name()))
                                .findFirst().orElseThrow().requiredRuleIds(), invariant.isActive(),
                        invariant.bodyExpression().toString())).toList());
        pipeline.model().skippedConstraints().forEach(spec -> constraints.add(
                new org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor(
                        "NATIVE:SKIPPED:" + spec.name(), spec.name(), spec.targetContext(), null,
                        org.tzi.use.plugins.jacamo.verification.ConstraintKind.INV,
                        org.tzi.use.plugins.jacamo.verification.ConstraintOrigin.CORE, jcmFile,
                        new org.tzi.use.plugins.jacamo.project.SourceSpan(jcmFile, 1, 1, 1, 1),
                        spec.requiredRuleIds(), false, spec.oclBody())));
        NativeWorkspace next = new NativeWorkspace(summary, sources, traces, constraints, pipeline,
                importNanos, generationNanos);
        lastDiagnostics = List.of();
        return next;
    }

    static TraceRow nativeTraceRow(org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceRecord record,
                                   Path jcmFile) {
        Path path = null;
        int line = 0;
        if (!record.sourceEvidence().isEmpty()) {
            var evidence = record.sourceEvidence().getFirst();
            line = evidence.startLine();
            String uri = evidence.sourceUri();
            if (uri.startsWith("project:/")) path = jcmFile.toAbsolutePath().getParent()
                    .resolve(uri.substring("project:/".length())).normalize();
            else if (uri.startsWith("file:")) path = Path.of(java.net.URI.create(uri)).toAbsolutePath().normalize();
            // A jar entry is a URI, not a local Path. Retain it in sourceEvidence for the inspector.
        }
        return new TraceRow(record.sourceIdentity(), record.sourceJavaFqcn(), record.targetIdentity(), record.targetKind(),
                record.ruleId(), record.fidelity().name(), record.capabilityStatus().name(), path, line,
                record.ruleId().substring(0, 1), record.evidenceAuthority().name(), record.diagnostics(), record.sourceEvidence());
    }

    private VerificationReport runNativeVerification(NativeWorkspace nativeState) {
        if (nativeState.runtimeProjector == null) throw new IllegalStateException("NATIVE_RUNTIME_COORDINATOR_REQUIRED");
        var result = nativeState.runtimeProjector.coordinator().manualVerify();
        lastFullCheckNanos = result.durationNanos();
        return nativeReport(nativeState, result);
    }

    private VerificationReport nativeReport(NativeWorkspace nativeState, RuntimeVerificationResult runtime) {
        if (runtime == null) return nativeState.latest;
        List<org.tzi.use.plugins.jacamo.verification.VerificationResult> results = new ArrayList<>();
        for (var outcome : runtime.outcomes()) results.add(new org.tzi.use.plugins.jacamo.verification.VerificationResult(
                outcome.constraintId(), outcome.outcome(), outcome.contextClass(), outcome.diagnostic(), outcome.expression(), List.of(), null, List.of()));
        nativeState.constraints.stream().filter(descriptor -> descriptor.id().startsWith("NATIVE:SKIPPED:")).forEach(descriptor ->
                results.add(new org.tzi.use.plugins.jacamo.verification.VerificationResult(descriptor.id(), VerificationOutcome.SKIPPED,
                        descriptor.context(), "SKIPPED_CAPABILITY", descriptor.oclSource(), List.of(), null, List.of())));
        return new VerificationReport("1.0.0", runtime.resultHash(), runtime.verifiedAt(), "RUNTIME_NATIVE", true, results,
                Map.of("nativeModelSha256", nativeState.pipeline.model().structuralHash(), "stateHash", runtime.stateHash(),
                        "stateVersion", Long.toString(runtime.stateVersion()), "sessionId", runtime.sessionId(),
                        "generation", Long.toString(runtime.generation()), "modelRevision", runtime.modelRevision(),
                        "constraintSetHash", runtime.constraintSetHash(), "coverage", runtime.coverage(), "freshness", runtime.freshness(),
                        "eventId", runtime.eventId()));
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private MSystem currentSystem() {
        var replay=stepReplay;if(replay!=null&&replay.active())return replay.system();
        var nativeState = nativeWorkspace; var legacyState = workspace;
        if (nativeState != null) return nativeState.pipeline.state().system();
        return legacyState == null ? null : legacyState.direct.system();
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

    private void requireNativeWorkspace() {
        if (nativeWorkspace == null || nativeWorkspace.runtimeProjector == null)
            throw new IllegalStateException("NATIVE_EXPORT_REQUIRES_CODE_GROUNDED_NATIVE");
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

    private Path runtimeRoot() {
        String configured = System.getProperty("use.jacamo.runtime.root", "").trim();
        if (!configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        return checkout.resolve("use-plugin").resolve("target").resolve("jacamo-runtime");
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
        private NativeRuntimeProjector runtimeProjector;
        private Runnable stopRuntimeDelivery = () -> { };
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

}
