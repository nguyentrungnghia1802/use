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
import org.tzi.use.plugins.jacamo.bridge.BridgeTransportFactory;
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
import org.tzi.use.plugins.jacamo.runtime.MirrorState;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.tzi.use.plugins.jacamo.verification.ConstraintRegistry;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.plugins.jacamo.verification.VerificationReport;
import org.tzi.use.plugins.jacamo.verification.VerificationReportExporter;
import org.tzi.use.main.Session;
import org.tzi.use.uml.sys.MSystem;

/** Stateful application service behind the USE workbench. All parsing/transformation/verification lives here. */
public final class DefaultJaCaMoFacade implements JaCaMoFacade, AutoCloseable {
    public static final DefaultJaCaMoFacade INSTANCE = new DefaultJaCaMoFacade(detectCheckout());
    private static final Map<Session,java.lang.ref.WeakReference<DefaultJaCaMoFacade>> SESSION_FACADES=new java.util.WeakHashMap<>();

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
    private volatile NativeWorkspace nativeWorkspace;
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
        // The native projection is the single production implementation.
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
        this.projectionMode = NativeProjectionMode.configured(System.getProperty(NativeProjectionMode.PROPERTY));
        this.session = session;
    }

    public static synchronized DefaultJaCaMoFacade forSession(Session session) {
        java.util.Objects.requireNonNull(session,"session");
        var reference=SESSION_FACADES.get(session);var current=reference==null?null:reference.get();
        if(current==null || current.closed){current=new DefaultJaCaMoFacade(detectCheckout(),session);SESSION_FACADES.put(session,new java.lang.ref.WeakReference<>(current));}
        return current;
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
        // The startup ACK releases reasoning. Verify its authoritative cut and
        // refresh control readiness against the now-running owner before LIVE.
        resyncRuntime();
    }
    private org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeControlService requireRuntimeControl() {
        requireNotReplaying();var current=nativeWorkspace;
        if(current==null || current.control==null)throw new IllegalStateException("CONTROL_ACTIVE_RUNTIME_UNAVAILABLE");return current.control;
    }
    @Override public org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeControlService.View runtimeControlState(){if(stepReplayStatus()!=null)return null;var current=nativeWorkspace;return current==null || current.control==null?null:current.control.state();}
    @Override public java.util.concurrent.CompletableFuture<org.jacamo.bridge.contract.RuntimeControlContract.Status> pauseRuntime(String reason){return requireRuntimeControl().requestPause(reason);}
    @Override public java.util.concurrent.CompletableFuture<org.jacamo.bridge.contract.RuntimeControlContract.Status> resumeRuntime(){return requireRuntimeControl().requestResume();}
    @Override public List<org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationViolation> runtimeViolations(){if(stepReplayStatus()!=null)return List.of();var current=nativeWorkspace;return current==null || current.control==null?List.of():current.control.violations();}
    @Override public org.tzi.use.plugins.jacamo.codegrounded.runtime.GoalViewSnapshot goalView(){var replay=stepReplay;if(replay!=null && replay.active())return replay.goalView();var current=nativeWorkspace;
        return current==null || current.runtimeProjector==null?org.tzi.use.plugins.jacamo.codegrounded.runtime.GoalViewSnapshot.empty():
                org.tzi.use.plugins.jacamo.codegrounded.runtime.GoalViewSnapshot.read(current.runtimeProjector.coordinator(),traces(),runtimeViolations(),runtimeControlState());}
    @Override public String sourceExcerpt(Path file,int line) {
        Path exact=file.toAbsolutePath().normalize();
        var interval=verificationSnapshot().profile();
        boolean registeredProfile=interval!=null && Path.of(interval.profile().sourceFile()).toAbsolutePath().normalize().equals(exact);
        if(!registeredProfile && sources().stream().noneMatch(s->s.path().toAbsolutePath().normalize().equals(exact)) && traces().stream()
                .noneMatch(t->t.sourcePath()!=null && t.sourcePath().toAbsolutePath().normalize().equals(exact)))return "SOURCE_NOT_REGISTERED: "+exact;
        // An installed OCL interval owns immutable accepted bytes. The file may
        // have changed or disappeared since compilation; current disk text is
        // not the source of the recorded condition.
        try(var reader=registeredProfile
                ? new java.io.BufferedReader(new java.io.StringReader(interval.profile().source()))
                : Files.newBufferedReader(exact,StandardCharsets.UTF_8)) {
            int from=line>0?Math.max(1,line-6):1,to=line>0?line+10:30,index=0;var result=new StringBuilder(exact.toString()).append(line>0?":"+line:" (line unavailable)").append('\n');
            if(registeredProfile)result.append("Accepted OCL source SHA-256: ").append(interval.profile().sourceHash()).append('\n');
            for(String text;(text=reader.readLine())!=null && ++index<=to;)if(index>=from)result.append(index).append(": ").append(text,0,Math.min(text.length(),2000)).append('\n');
            return result.toString();
        } catch(java.io.IOException unavailable){return "SOURCE_UNAVAILABLE: "+exact+" / "+unavailable.getMessage();}
    }
    @Override public void showObjectDiagram() {
        requireNativeWorkspace();var window=org.tzi.use.gui.main.MainWindow.instance();
        if(window==null || session==null || session.system()!=nativeWorkspace.pipeline.state().system())throw new IllegalStateException("ACTIVE_USE_GUI_UNAVAILABLE");
        nativeWorkspace.runtimeProjector.coordinator().read(()->{
            var view=new org.tzi.use.gui.views.diagrams.objectdiagram.NewObjectDiagramView(window,session.system());
            var frame=new org.tzi.use.gui.main.ViewFrame("Object diagram",view,"ObjectDiagram.gif");
            frame.getContentPane().setLayout(new java.awt.BorderLayout());frame.getContentPane().add(view,java.awt.BorderLayout.CENTER);
            window.getObjectDiagrams().add(view);frame.addInternalFrameListener(new javax.swing.event.InternalFrameAdapter(){
                @Override public void internalFrameClosed(javax.swing.event.InternalFrameEvent event){window.getObjectDiagrams().remove(view);}});
            window.addNewViewFrame(frame);return null;
        });
    }
    @Override public List<org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.RuntimeConstraint> runtimeConstraints(){if(stepReplayStatus()!=null)return List.of();var current=nativeWorkspace;return current==null || current.control==null?List.of():current.control.constraints();}
    @Override public void configureRuntimeConstraint(String id,org.tzi.use.plugins.jacamo.codegrounded.constraint.RuntimeConstraintPolicy policy){requireRuntimeControl().approve(id,policy);}
    @Override public org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot failingSnapshot(){if(stepReplayStatus()!=null)return null;var current=nativeWorkspace;return current==null || current.runtimeProjector==null?null:current.runtimeProjector.coordinator().snapshots().failure();}
    @Override public org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot confirmationSnapshot(){if(stepReplayStatus()!=null)return null;var current=nativeWorkspace;return current==null || current.runtimeProjector==null?null:current.runtimeProjector.coordinator().snapshots().confirmation();}
    @Override public org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot previousFailureSnapshot(){if(stepReplayStatus()!=null)return null;var current=nativeWorkspace;return current==null || current.runtimeProjector==null?null:current.runtimeProjector.coordinator().snapshots().previousFailure();}
    @Override public org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot lastPassingBeforeFailure(){if(stepReplayStatus()!=null)return null;var current=nativeWorkspace;return current==null || current.runtimeProjector==null?null:current.runtimeProjector.coordinator().snapshots().passingBeforeFailure();}
    @Override public void cancelRuntimeStartup() { workflow.cancel(); }
    @Override public Map<String,Object> runtimePerformanceMetrics() {
        var current=nativeWorkspace;if(stepReplayStatus()!=null || current==null || current.runtimeProjector==null)return Map.of();
        return Map.of("verification",current.runtimeProjector.coordinator().performanceMetrics(),"control",current.control==null?Map.of():current.control.performanceMetrics());
    }

    @Override public synchronized ProjectSummary rebuild() {
        requireWorkspace();
        return synchronizeBridge(entry, userProfile);
    }

    @Override public ProjectSummary projectSummary() {
        var nativeState = nativeWorkspace;return nativeState == null ? null : nativeState.summary;
    }
    @Override public List<SourceRow> sources() {
        var nativeState = nativeWorkspace;return nativeState == null ? List.of() : nativeState.sources;
    }
    @Override public List<Diagnostic> diagnostics() {
        var result=new ArrayList<>(lastDiagnostics);var observed=runtimeVerificationResult();
        if(observed!=null) {
            if(!observed.diagnostic().isBlank())result.add(new Diagnostic("RUNTIME_SYNCHRONIZATION",org.tzi.use.plugins.jacamo.diagnostics.Severity.ERROR,
                    org.tzi.use.plugins.jacamo.diagnostics.Phase.RUNTIME,null,null,null,observed.diagnostic(),observed.eventId(),
                    "Restore an authoritative synchronized cut before verification or control."));
            observed.outcomes().stream().filter(o->o.outcome()==VerificationOutcome.ERROR || o.outcome()==VerificationOutcome.SKIPPED
                    && (o.diagnostic().contains("CAPABILITY") || o.diagnostic().contains("UNSUPPORTED") || o.diagnostic().contains("SOURCE_INCOMPLETE")))
                    .forEach(o->result.add(new Diagnostic(o.outcome()==VerificationOutcome.ERROR?"OCL_ERROR_OR_UNDEFINED":"VERIFICATION_CAPABILITY_UNAVAILABLE",
                            o.outcome()==VerificationOutcome.ERROR?org.tzi.use.plugins.jacamo.diagnostics.Severity.ERROR:org.tzi.use.plugins.jacamo.diagnostics.Severity.WARNING,
                            org.tzi.use.plugins.jacamo.diagnostics.Phase.VERIFICATION,null,o.constraintId(),null,
                            o.diagnostic().isBlank()?o.outcome().name():o.diagnostic(),o.expression(),
                            "Inspect the exact constraint and required source/capability; this result grants no pause authority.")));
        }
        var control=runtimeControlState();
        if(control!=null && !control.diagnostic().isBlank())result.add(new Diagnostic("RUNTIME_CONTROL",
                control.capable()?org.tzi.use.plugins.jacamo.diagnostics.Severity.ERROR:org.tzi.use.plugins.jacamo.diagnostics.Severity.WARNING,
                org.tzi.use.plugins.jacamo.diagnostics.Phase.RUNTIME,null,null,null,control.diagnostic(),control.state().name(),
                "Revalidate control ownership and authoritative resync; domain state is not repaired."));
        return result.stream().distinct().toList();
    }
    @Override public List<TraceRow> traces() {
        var nativeState = nativeWorkspace;return nativeState == null ? List.of() : nativeState.traces;
    }
    @Override public List<org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor> constraints() {
        if(stepReplayStatus()!=null)return List.of(); // recorded outcomes/profile carry their own attribution
        var nativeState = nativeWorkspace;if (nativeState == null) return List.of();
        List<org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor> descriptors = new ArrayList<>(nativeState.constraints);
        if (nativeState.runtimeProjector != null) nativeState.runtimeProjector.coordinator().read(() -> {
            var profile = nativeState.runtimeProjector.coordinator().constraints().profile();
            if (profile != null) for (var constraint : profile.constraints()) {
                var invariant = nativeState.pipeline.model().model().classInvariants().stream()
                        .filter(item -> item.qualifiedName().equals(constraint.constraintId())).findFirst().orElseThrow();
                Path source = Path.of(constraint.sourceFile());
                descriptors.add(new org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor("EXTERNAL:" + constraint.constraintId(),
                        constraint.constraintId(), constraint.contextClass(), null, org.tzi.use.plugins.jacamo.verification.ConstraintKind.INV,
                        ConstraintOrigin.USER, source, null,
                        List.copyOf(constraint.requiredRules()), constraint.enabled(), invariant.bodyExpression().toString()));
            }
            return null;
        });
        return List.copyOf(descriptors);
    }

    @Override public synchronized VerificationReport runFullVerification() {
        requireNotReplaying();
        requireWorkspace();
        return runNativeVerification(nativeWorkspace);
    }

    @Override public VerificationReport latestVerification() {
        if(stepReplayStatus()!=null) {
            var result=verificationSnapshot().result();
            if(result==null)return null;
            var values=result.outcomes().stream().map(value->new org.tzi.use.plugins.jacamo.verification.VerificationResult(
                    value.constraintId(),value.outcome(),value.contextObject().isBlank()?null:value.contextObject(),value.diagnostic(),value.expression(),List.of(),null,List.of())).toList();
            return new VerificationReport("1.0.0",result.resultHash(),result.verifiedAt(),"RECORDED_REPLAY",true,values,
                    Map.of("stateHash",result.stateHash(),"stateVersion",Long.toString(verificationSnapshot().currentVersion()),
                            "scope","OBSERVED_SUPPORTED_PROJECTION_ONLY","coverage",result.coverage(),"freshness",result.freshness()));
        }
        var nativeState = nativeWorkspace;
        if (nativeState != null && nativeState.runtimeProjector != null) {
            var result = nativeState.runtimeProjector.coordinator().verificationSnapshot().result();
            return result == null ? null : nativeReport(nativeState, result);
        }
        if (nativeState != null) return nativeState.latest;
        return null;
    }

    @Override public synchronized void loadVerificationProfile(Path profile) {
        requireNotReplaying();
        requireWorkspace();
        if (profile == null) throw new IllegalArgumentException("OCL_USER_PROFILE_IO");
        nativeWorkspace.runtimeProjector.coordinator().loadProfile(profile);workflow.profileReady();
        userProfile = profile.toAbsolutePath().normalize();
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
            if(current.control!=null)current.control.disconnected();
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
        var mirror = bridgeMirror; var nativeState = nativeWorkspace; var accepted = bridgeAccepted;
        BridgeClientState state = mirror == null ? BridgeClientState.DISCONNECTED : mirror.state();
        MirrorState mirrorState = switch (state) {
            case DISCONNECTED -> nativeWorkspace == null ? MirrorState.OFFLINE : MirrorState.STALE;
            case NEGOTIATING -> MirrorState.CONNECTING;
            case MODEL_SYNC, SNAPSHOT_SYNC -> MirrorState.SYNCING;
            case LIVE -> MirrorState.LIVE;
            case RESYNC_REQUIRED, STALE -> MirrorState.STALE;
        };
        if (nativeState != null && nativeState.runtimeProjector != null) {
            var coordinator = nativeState.runtimeProjector.coordinator();
            var control=nativeState.control==null?null:nativeState.control.state();
            return coordinator.read(() -> {
            var cut=coordinator.verificationSnapshot();
            MirrorState synchronizedState=mirrorState;
            if(mirrorState==MirrorState.LIVE && (cut.metadata()!=null
                    && cut.metadata().lifecycle()==org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot.SynchronizationState.SYNCING
                    || control!=null && (control.state()==org.jacamo.bridge.contract.RuntimeControlContract.State.RESUME_REQUESTED
                        || control.state()==org.jacamo.bridge.contract.RuntimeControlContract.State.RUNNING && !control.liveReady())))
                synchronizedState=MirrorState.SYNCING;
            var result = coordinator.latest(); var observed = coordinator.lastObservation();
            long rejected = coordinator.history().stream().filter(item -> item.freshness().equals("STALE")).count();
            return new RuntimeStatus(result.freshness().equals("STALE") ? MirrorState.STALE : synchronizedState,
                    coordinator.backlog(), coordinator.highWatermark(), bridgeProcessed.get(), rejected,
                    result.count(VerificationOutcome.ERROR), coordinator.journal().hasGap() ? 1 : 0,
                    bridgeLastSync, observed.eventId() + " / " + observed.checkpointId(), result.durationNanos(), result.stateVersion(),
                    (int) coordinator.history().stream().mapToLong(item -> item.count(VerificationOutcome.FAIL)).sum());
            });
        }
        return new RuntimeStatus(mirrorState, 0, 0, bridgeProcessed.get(), 0,
                state == BridgeClientState.RESYNC_REQUIRED ? 1 : 0, 0, bridgeLastSync, "", 0,
                accepted == null ? 0 : accepted.generation(), 0);
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
        var nativeState = nativeWorkspace;if (nativeState == null) return PerformanceMetrics.empty();
        RuntimeStatus runtimeStatus = runtimeStatus();
        Runtime jvm = Runtime.getRuntime();
        long importNanos = nativeState.importNanos;long generationNanos = nativeState.generationNanos;
        return new PerformanceMetrics(importNanos, generationNanos, lastFullCheckNanos,
                runtimeStatus.lastLatencyNanos(), jvm.totalMemory() - jvm.freeMemory());
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

    private ProjectSummary synchronizeBridge(Path selectedJcm, Path verificationProfile) {
        requireNotReplaying();
        BridgeConnectionConfig configuration = configuredBridge == null ? bridgeConfigurationSource.get() : configuredBridge;
        configuredBridge = configuration;
        BridgeMirrorStateMachine candidateMirror = new BridgeMirrorStateMachine(8_192);
        ArrayDeque<RuntimeEvent> pending = new ArrayDeque<>();
        AtomicReference<NativeRuntimeProjector> nativeProjectorReference = new AtomicReference<>();
        AtomicReference<NativeRuntimeProjector> newlyCreatedNativeProjector = new AtomicReference<>();
        AtomicReference<BridgeClient.CoverageFailure> nativeCoverageFailure = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean deliveryActive = new java.util.concurrent.atomic.AtomicBoolean(true);
        BridgeClient candidate = null;
        try {
            candidate = new BridgeClient(bridgeTransportFactory.open(configuration), candidateMirror,
                    configuration.distributionSha256(), configuration.requiredCapabilities(),
                    configuration.maxBufferedEvents(), event -> {
                        if (!deliveryActive.get()) return;
                        synchronized (pending) {
                            if (!deliveryActive.get()) return;
                            NativeRuntimeProjector projector = nativeProjectorReference.get();
                            if (projector == null) {
                                if (pending.size() >= configuration.maxBufferedEvents())
                                    throw new BridgeProtocolException("BRIDGE_FACADE_BUFFER_OVERFLOW");
                                pending.addLast(event);
                            } else {
                                projector.apply(event);bridgeProcessed.incrementAndGet();
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
            long importNanos = System.nanoTime() - importStarted;
            NativeWorkspace previous = nativeWorkspace;
            boolean compatibleResync = previous != null && previous.runtimeProjector != null && bridgeAccepted != null
                    && bridgeAccepted.modelRevision().equals(accepted.modelRevision());
            boolean sameOwner=compatibleResync && bridgeAccepted.sessionId().equals(accepted.sessionId())
                    && bridgeAccepted.generation()==accepted.generation();
            var savedProfile = previous == null || previous.runtimeProjector == null ? null
                    : previous.runtimeProjector.coordinator().read(() -> previous.runtimeProjector.coordinator().constraints().profile());
            NativeWorkspace next = compatibleResync ? previous : buildNativeSemantic(selectedJcm, accepted.model(), importNanos);
            NativeRuntimeProjector nativeProjector = compatibleResync ? previous.runtimeProjector
                    : new NativeRuntimeProjector(next.pipeline, accepted.sessionId(), accepted.generation(),
                            accepted.modelRevision(), RuntimeVerificationCoordinator.createEphemeralDirectory(runtimeRoot()));
            if (!compatibleResync) newlyCreatedNativeProjector.set(nativeProjector);
            nativeProjector.coordinator().runtimeCapabilities(accepted.capabilities().stream().collect(
                    java.util.stream.Collectors.toMap(org.jacamo.bridge.contract.Capability::name,c->c.status().name())),accepted.sourceVersions());
            if (!compatibleResync && savedProfile != null)
                nativeProjector.coordinator().loadSavedProfile(savedProfile);
            if (previous != null) previous.stopRuntimeDelivery.run();
            if(compatibleResync && !sameOwner && previous.control!=null){previous.control.close();previous.control=null;}
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
                {if(previous.control!=null)previous.control.close();previous.runtimeProjector.close();}
            nativeWorkspace = next;
            entry = selectedJcm;
            bridgeClient = candidate;
            bridgeMirror = candidateMirror;
            bridgeAccepted = accepted;
            bridgeLastSync = accepted.runtime().captureEndedAt();
            bridgeDiagnostic = "";
            if(next.control==null)next.control=createRuntimeControl(next,accepted);
            next.control.observationConnected();
            if(next.control.state().capable() && (next.control.state().state()==org.jacamo.bridge.contract.RuntimeControlContract.State.RUNNING || !sameOwner))
                next.control.refresh().exceptionally(error->{bridgeDiagnostic="CONTROL_STATUS_UNAVAILABLE:"+error.getMessage();return null;});
            return next.summary;
        } catch (RuntimeException error) {
            deliveryActive.set(false);
            if (candidate != null) candidate.close();
            NativeRuntimeProjector created = newlyCreatedNativeProjector.get();
            if (created != null && (nativeWorkspace == null || nativeWorkspace.runtimeProjector != created)) created.close();
            bridgeDiagnostic = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            if (nativeWorkspace != null && nativeWorkspace.runtimeProjector != null)
                nativeWorkspace.runtimeProjector.coordinator().coverageGap("BRIDGE_RESYNC_FAILED:" + bridgeDiagnostic);
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

    private void closeNativeRuntimeArtifacts() {
        NativeWorkspace current = nativeWorkspace;
        if (current == null) return;
        if(current.control!=null){current.control.close();current.control=null;}
        current.stopRuntimeDelivery.run();
        if (current.runtimeProjector != null) {
            current.runtimeProjector.close();
            current.runtimeProjector = null;
        }
    }
    private org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeControlService createRuntimeControl(NativeWorkspace owner,BridgeClient.Accepted accepted) {
        var port=new org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeControlService.Port() {
            @Override public org.jacamo.bridge.contract.RuntimeControlContract.Status control(org.jacamo.bridge.contract.RuntimeControlContract.Request request){
                var client=bridgeClient;if(client==null)throw new IllegalStateException("CONTROL_BRIDGE_DISCONNECTED");return client.control(request);
            }
            @Override public org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot authoritativeResync(){
                resyncRuntime();if(nativeWorkspace!=owner)throw new IllegalStateException("CONTROL_WORKSPACE_REPLACED");return owner.runtimeProjector.coordinator().verificationSnapshot();
            }
            @Override public boolean ownsRuntime(String sessionId,long generation,String revision){
                var actual=bridgeAccepted;return !closed && nativeWorkspace==owner && actual!=null && actual.sessionId().equals(sessionId)
                        && actual.generation()==generation && actual.modelRevision().equals(revision);
            }
            @Override public boolean liveAvailable(){var mirror=bridgeMirror;return bridgeClient!=null && mirror!=null && mirror.state()==BridgeClientState.LIVE
                    && (!workflow.status().managed() || workflow.status().state().equals("LIVE"));}
        };
        boolean capable=accepted.capabilities().stream().anyMatch(c->c.name().equals(org.jacamo.bridge.contract.RuntimeControlContract.CAPABILITY)
                && c.status()==org.jacamo.bridge.contract.CapabilityStatus.COMPLETE);
        return new org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeControlService(owner.runtimeProjector.coordinator(),port,()->owner.traces,
                accepted.sessionId(),accepted.generation(),accepted.modelRevision(),capable,Long.getLong("use.jacamo.control.ackTimeoutMillis",15000L));
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
                        null,
                        pipeline.model().constraints().stream().filter(spec -> spec.name().equals(invariant.name()))
                                .findFirst().orElseThrow().requiredRuleIds(), invariant.isActive(),
                        invariant.bodyExpression().toString())).toList());
        pipeline.model().skippedConstraints().forEach(spec -> constraints.add(
                new org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor(
                        "NATIVE:SKIPPED:" + spec.name(), spec.name(), spec.targetContext(), null,
                        org.tzi.use.plugins.jacamo.verification.ConstraintKind.INV,
                        org.tzi.use.plugins.jacamo.verification.ConstraintOrigin.CORE, jcmFile,
                        null,
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
                outcome.constraintId(), outcome.outcome(), outcome.contextObject().isBlank()?null:outcome.contextObject(), outcome.diagnostic(), outcome.expression(), List.of(), null, List.of()));
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
        var nativeState = nativeWorkspace;
        if (nativeState != null) return nativeState.pipeline.state().system();
        return null;
    }

    private void requireWorkspace() {
        if (nativeWorkspace == null) throw new IllegalStateException("PROJECT_NOT_IMPORTED");
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

    private static final class NativeWorkspace {
        private final ProjectSummary summary;
        private final List<SourceRow> sources;
        private final List<TraceRow> traces;
        private final List<org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor> constraints;
        private final CodeGroundedNativePipeline.Result pipeline;
        private final long importNanos;
        private final long generationNanos;
        private NativeRuntimeProjector runtimeProjector;
        private org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeControlService control;
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
