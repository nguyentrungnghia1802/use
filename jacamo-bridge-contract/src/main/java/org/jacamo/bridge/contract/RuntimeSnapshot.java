package org.jacamo.bridge.contract;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RuntimeSnapshot(String snapshotId, String modelRevision, Instant captureStartedAt,
                              Instant captureEndedAt, Map<String, SourceWatermark> startWatermarks,
                              Map<String, SourceWatermark> endWatermarks, int validationAttempts,
                              List<RuntimeFact> facts, Map<String, Completeness> sourceCompleteness,
                              String stateFingerprint) {
    public RuntimeSnapshot {
        snapshotId = ContractSupport.required(snapshotId, "snapshotId");
        modelRevision = ContractSupport.required(modelRevision, "modelRevision");
        if (captureStartedAt == null || captureEndedAt == null || captureEndedAt.isBefore(captureStartedAt))
            throw new ContractException("invalid capture interval");
        startWatermarks = ContractSupport.map(startWatermarks);
        endWatermarks = ContractSupport.map(endWatermarks);
        if (validationAttempts < 1) throw new ContractException("validationAttempts must be positive");
        facts = ContractSupport.list(facts);
        sourceCompleteness = ContractSupport.map(sourceCompleteness);
        stateFingerprint = ContractSupport.required(stateFingerprint, "stateFingerprint");
    }
}
