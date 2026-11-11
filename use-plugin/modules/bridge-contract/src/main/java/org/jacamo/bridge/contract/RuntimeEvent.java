package org.jacamo.bridge.contract;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record RuntimeEvent(String eventId, String sessionId, long generation, String modelRevision,
                           String subsystem, String sourceId, long sourceSequence, Instant observedAt,
                           RuntimeEventKind kind, RuntimeFactKind factKind, ProjectionStatus projectionStatus,
                           BridgeEntityId entityId, BridgeRelationId relationId,
                           String correlationId, String causationId, Map<String, Object> before,
                           Map<String, Object> after, SourceWatermark watermark, Completeness completeness,
                           List<Evidence> evidence) {
    public RuntimeEvent {
        eventId = ContractSupport.required(eventId, "eventId");
        sessionId = ContractSupport.required(sessionId, "sessionId");
        modelRevision = ContractSupport.required(modelRevision, "modelRevision");
        subsystem = ContractSupport.required(subsystem, "subsystem");
        sourceId = ContractSupport.required(sourceId, "sourceId");
        if (generation < 0 || sourceSequence < 0) throw new ContractException("negative generation/sequence");
        if (observedAt == null || kind == null || watermark == null || completeness == null)
            throw new ContractException("event time/kind/watermark/completeness is required");
        if (kind != RuntimeEventKind.GAP && kind != RuntimeEventKind.MODEL_REVISION_CHANGED
                && (factKind == null || projectionStatus == null))
            throw new ContractException("state event fact kind/projection status is required");
        if (entityId == null && relationId == null && kind != RuntimeEventKind.GAP)
            throw new ContractException("event must identify an entity or relation");
        correlationId = correlationId == null ? "" : correlationId;
        causationId = causationId == null ? "" : causationId;
        before = ContractSupport.canonicalObject(before);
        after = ContractSupport.canonicalObject(after);
        evidence = ContractSupport.list(evidence);
    }
}
