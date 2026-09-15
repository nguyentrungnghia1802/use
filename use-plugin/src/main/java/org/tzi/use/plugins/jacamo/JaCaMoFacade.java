package org.tzi.use.plugins.jacamo;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.runtime.MirrorState;
import org.tzi.use.plugins.jacamo.runtime.RuntimeConnector;
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
    default void exportVerificationReport(Path destination) { throw new UnsupportedOperationException("PROJECT_NOT_IMPORTED"); }
    default void configureRuntime(RuntimeConnector connector, URI endpoint, int queueCapacity) {
        throw new UnsupportedOperationException("RUNTIME_NOT_CONFIGURED");
    }
    default void connectRuntime() { throw new UnsupportedOperationException("RUNTIME_NOT_CONFIGURED"); }
    default void disconnectRuntime() { throw new UnsupportedOperationException("RUNTIME_NOT_CONFIGURED"); }
    default void resyncRuntime() { throw new UnsupportedOperationException("RUNTIME_NOT_CONFIGURED"); }
    default RuntimeStatus runtimeStatus() { return RuntimeStatus.offline(); }
    default void persistBinding(Path destination, BindingRequest request, String selectedTargetId, String reason) {
        throw new UnsupportedOperationException("PROJECT_NOT_IMPORTED");
    }

    record ProjectSummary(Path entry, Path projectRoot, String projectId, int sourceCount,
                          Map<String, Long> dimensionCounts, String mappingId, String mappingStatus,
                          int generatedClasses, int generatedObjects, boolean structureValid,
                          int warningCount, int errorCount) {
        public ProjectSummary { dimensionCounts = Map.copyOf(dimensionCounts); }
    }
    record SourceRow(Path path, String kind, long bytes, String sha256) { }
    record TraceRow(String semanticId, String sourceKind, String targetUseId, String targetKind,
                    String mappingRule, String projectionRule, String status, Path sourcePath,
                    int sourceLine, String dimension) { }
    record RuntimeStatus(MirrorState state, int queueDepth, int highWatermark, long processed,
                         long rejected, long failed, long dropped, Instant lastSync, String lastEvent,
                         long lastLatencyNanos, long snapshotVersion, int violationCount) {
        public static RuntimeStatus offline() {
            return new RuntimeStatus(MirrorState.OFFLINE, 0, 0, 0, 0, 0, 0, null, "", 0, 0, 0);
        }
    }
    record BindingRequest(String sourceId, String owner, String type, Path sourcePath, String sourceHash,
                          List<BindingCandidate> candidates) {
        public BindingRequest { candidates = List.copyOf(candidates); }
    }
    record BindingCandidate(String semanticId, String owner, String type, Path sourcePath) { }
}
