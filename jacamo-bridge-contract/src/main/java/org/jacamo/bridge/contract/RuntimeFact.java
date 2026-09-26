package org.jacamo.bridge.contract;

import java.util.List;
import java.util.Map;

public record RuntimeFact(BridgeEntityId id, RuntimeFactKind kind, Map<String, Object> values,
                          List<BridgeRelationId> relations, ProjectionStatus projectionStatus,
                          Completeness completeness, List<Evidence> evidence) {
    public RuntimeFact {
        if (id == null || kind == null || projectionStatus == null || completeness == null)
            throw new ContractException("runtime fact identity/kind/status is required");
        values = ContractSupport.canonicalObject(values);
        relations = ContractSupport.list(relations);
        evidence = ContractSupport.list(evidence);
        if (projectionStatus == ProjectionStatus.UNAVAILABLE && completeness != Completeness.UNAVAILABLE)
            throw new ContractException("unavailable projection requires unavailable completeness");
    }
}
