package org.tzi.use.plugins.jacamo;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.bridge.BridgeConnectionConfig;
import org.tzi.use.plugins.jacamo.bridge.BridgeClientState;
import org.tzi.use.plugins.jacamo.runtime.MirrorState;
import org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor;
import org.tzi.use.plugins.jacamo.verification.VerificationReport;

/** UI-facing boundary. Swing components consume view records and never execute the semantic pipeline directly. */
@FunctionalInterface
public interface JaCaMoFacade {
    String status();

    default ProjectSummary importProject(Path jcmFile) { throw new UnsupportedOperationException("IMPORT_NOT_CONFIGURED"); }
    default ProjectSummary rebuild() { throw new UnsupportedOperationException("PROJECT_NOT_IMPORTED"); }
    default ProjectSummary projectSummary() { return null; }
    default List<SourceRow> sources() { return List.of(); }
    default List<Diagnostic> diagnostics() { return List.of(); }
    default List<TraceRow> traces() { return List.of(); }
    default List<ConstraintDescriptor> constraints() { return List.of(); }
    default VerificationReport runFullVerification() { throw new UnsupportedOperationException("PROJECT_NOT_IMPORTED"); }
    default VerificationReport latestVerification() { return null; }
    default void loadVerificationProfile(Path profile) { throw new UnsupportedOperationException("PROJECT_NOT_IMPORTED"); }
    default WorkflowStatus workflowStatus() { return new WorkflowStatus("NOT_IMPORTED", false, false, "", "OBSERVE_ONLY", "", ""); }
    default void startRuntime() { throw new UnsupportedOperationException("START_RUNTIME_UNAVAILABLE:OBSERVE_ONLY"); }
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeControlService.View runtimeControlState(){return null;}
    default java.util.concurrent.CompletableFuture<org.jacamo.bridge.contract.RuntimeControlContract.Status> pauseRuntime(String reason){throw new UnsupportedOperationException("CONTROL_UNAVAILABLE");}
    default java.util.concurrent.CompletableFuture<org.jacamo.bridge.contract.RuntimeControlContract.Status> resumeRuntime(){throw new UnsupportedOperationException("CONTROL_UNAVAILABLE");}
    default List<org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationViolation> runtimeViolations(){return List.of();}
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.GoalViewSnapshot goalView(){return org.tzi.use.plugins.jacamo.codegrounded.runtime.GoalViewSnapshot.empty();}
    default String sourceExcerpt(Path file,int line){return "Source unavailable: "+file+(line>0?":"+line:" (line unavailable)");}
    default void showObjectDiagram(){throw new UnsupportedOperationException("ACTIVE_USE_GUI_UNAVAILABLE");}
    default List<org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.RuntimeConstraint> runtimeConstraints(){return List.of();}
    default void configureRuntimeConstraint(String id,org.tzi.use.plugins.jacamo.codegrounded.constraint.RuntimeConstraintPolicy policy){throw new UnsupportedOperationException("CONTROL_UNAVAILABLE");}
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot failingSnapshot(){return null;}
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot confirmationSnapshot(){return null;}
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot previousFailureSnapshot(){return null;}
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot lastPassingBeforeFailure(){return null;}
    default Map<String,Object> runtimePerformanceMetrics(){return Map.of();}
    default void cancelRuntimeStartup() { }
    default void exportVerificationReport(Path destination) { throw new UnsupportedOperationException("PROJECT_NOT_IMPORTED"); }
    default void exportNativeUse(Path destination) { throw new UnsupportedOperationException("PROJECT_NOT_IMPORTED"); }
    default void exportNativeSoil(Path destination) { throw new UnsupportedOperationException("PROJECT_NOT_IMPORTED"); }
    default void configureBridge(BridgeConnectionConfig configuration) {
        throw new UnsupportedOperationException("BRIDGE_NOT_CONFIGURED");
    }
    default void connectRuntime() { throw new UnsupportedOperationException("RUNTIME_NOT_CONFIGURED"); }
    default void disconnectRuntime() { throw new UnsupportedOperationException("RUNTIME_NOT_CONFIGURED"); }
    default void resyncRuntime() { throw new UnsupportedOperationException("RUNTIME_NOT_CONFIGURED"); }
    default RuntimeStatus runtimeStatus() { return RuntimeStatus.offline(); }
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult runtimeVerificationResult() { return null; }
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot verificationSnapshot() {
        return new org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot(0, runtimeVerificationResult(), null);
    }
    default List<org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult> runtimeVerificationHistory() { return List.of(); }
    default void exportRuntimeReplay(Path directory) { throw new UnsupportedOperationException("NATIVE_RUNTIME_NOT_CONFIGURED"); }
    default void openStepReplay(Path recording) { throw new UnsupportedOperationException("STEP_REPLAY_NOT_CONFIGURED"); }
    default void resetStepReplay() { throw new UnsupportedOperationException("REPLAY_NOT_OPEN"); }
    default void previousStepReplay() { throw new UnsupportedOperationException("REPLAY_NOT_OPEN"); }
    default void nextStepReplay() { throw new UnsupportedOperationException("REPLAY_NOT_OPEN"); }
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeReplayStepController.Status stepReplayStatus() { return null; }
    default boolean stepReplayBusy() { return false; }
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay.ReplayReport replayRuntime(Path bundle) {
        return new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay().replay(bundle);
    }
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReanalysis.Report reanalyzeRuntime(Path bundle, Path output) {
        throw new UnsupportedOperationException("NATIVE_PROFILE_NOT_LOADED");
    }
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeHistoryPage runtimeHistoryTail() {
        return org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeHistoryPage.empty();
    }
    default org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeHistoryPage runtimeHistoryPage(long offset, int limit) {
        throw new UnsupportedOperationException("PERSISTED_HISTORY_NOT_CONFIGURED");
    }
    default AuthorityStatus authorityStatus() { return AuthorityStatus.offline(); }
    /** Deterministic read-only evidence for the currently materialized USE model and state. */
    default FormalStateStatus formalStateStatus() { return FormalStateStatus.empty(); }
    /** Returns the latest measured pipeline and runtime values; zero means that no measurement is available yet. */
    default PerformanceMetrics performanceMetrics() { return PerformanceMetrics.empty(); }

    record ProjectSummary(Path entry, Path projectRoot, String projectId, int sourceCount,
                          Map<String, Long> dimensionCounts,
                          String metamodelVersion, String metamodelSha256,
                          String mappingId, String mappingVersion, String mappingSha256, String mappingStatus,
                          int generatedClasses, int generatedObjects, boolean structureValid,
                          int warningCount, int errorCount) {
        public ProjectSummary { dimensionCounts = Map.copyOf(dimensionCounts); }
    }
    record SourceRow(Path path, String kind, long bytes, String sha256) { }
    record WorkflowStatus(String state, boolean startAvailable, boolean managed, String runId,
                          String diagnostic, String bootstrapAt, String startedAt) { }
    record TraceRow(String semanticId, String sourceKind, String targetUseId, String targetKind,
                    String mappingRule, String projectionRule, String status, Path sourcePath,
                    int sourceLine, String dimension, String evidenceAuthority, List<String> traceDiagnostics,
                    List<org.jacamo.bridge.contract.semantic.SourceEvidence> sourceEvidence) {
        public TraceRow {
            evidenceAuthority = evidenceAuthority == null ? "" : evidenceAuthority;
            traceDiagnostics = List.copyOf(traceDiagnostics == null ? List.of() : traceDiagnostics);
            sourceEvidence = List.copyOf(sourceEvidence == null ? List.of() : sourceEvidence);
        }
        public TraceRow(String semanticId, String sourceKind, String targetUseId, String targetKind,
                        String mappingRule, String projectionRule, String status, Path sourcePath,
                        int sourceLine, String dimension, String evidenceAuthority, List<String> traceDiagnostics) {
            this(semanticId, sourceKind, targetUseId, targetKind, mappingRule, projectionRule, status,
                    sourcePath, sourceLine, dimension, evidenceAuthority, traceDiagnostics, List.of());
        }
        public TraceRow(String semanticId, String sourceKind, String targetUseId, String targetKind,
                        String mappingRule, String projectionRule, String status, Path sourcePath,
                        int sourceLine, String dimension) {
            this(semanticId, sourceKind, targetUseId, targetKind, mappingRule, projectionRule, status,
                    sourcePath, sourceLine, dimension, "", List.of());
        }
    }
    record RuntimeStatus(MirrorState state, int queueDepth, int highWatermark, long processed,
                         long rejected, long failed, long dropped, Instant lastSync, String lastEvent,
                         long lastLatencyNanos, long snapshotVersion, int violationCount) {
        public static RuntimeStatus offline() {
            return new RuntimeStatus(MirrorState.OFFLINE, 0, 0, 0, 0, 0, 0, null, "", 0, 0, 0);
        }
    }
    record AuthorityStatus(SemanticAuthority authority, BridgeClientState readiness,
                           Map<String,String> capabilities, String completeness, String modelRevision,
                           String sessionId, long generation, String endpoint, String diagnostic) {
        public AuthorityStatus { capabilities=Map.copyOf(capabilities); }
        public static AuthorityStatus offline() {
            return new AuthorityStatus(SemanticAuthority.BRIDGE, BridgeClientState.DISCONNECTED,
                    Map.of(), "UNAVAILABLE", "", "", 0, "", "BRIDGE_NOT_CONFIGURED");
        }
    }
    record FormalStateStatus(int classCount, int associationCount, int objectCount, int linkCount,
                             String sha256) {
        public FormalStateStatus {
            if (classCount < 0 || associationCount < 0 || objectCount < 0 || linkCount < 0)
                throw new IllegalArgumentException("FORMAL_STATE_COUNT_INVALID");
            sha256 = sha256 == null ? "" : sha256;
            if (!sha256.isEmpty() && !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("FORMAL_STATE_SHA256_INVALID");
        }
        public static FormalStateStatus empty() { return new FormalStateStatus(0, 0, 0, 0, ""); }
    }
    /**
     * Durations are observed nanoseconds, not performance guarantees. Runtime latency spans connector receipt through
     * its reported verification result; used memory is the current JVM heap sample in bytes.
     */
    record PerformanceMetrics(long importNanos, long generationNanos, long fullCheckNanos,
                              long runtimeLastLatencyNanos, long usedMemoryBytes) {
        public static PerformanceMetrics empty() { return new PerformanceMetrics(0, 0, 0, 0, 0); }
    }
}
