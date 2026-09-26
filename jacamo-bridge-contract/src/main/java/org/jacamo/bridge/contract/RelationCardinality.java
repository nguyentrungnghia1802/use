package org.jacamo.bridge.contract;

import java.util.List;

/** Cardinality is relation-scoped; it is never collapsed into a Role or Group scalar. */
public record RelationCardinality(BridgeRelationId id, BridgeEntityId context, BridgeEntityId member,
                                  int min, int max, List<Evidence> evidence) {
    public RelationCardinality {
        if (id == null || context == null || member == null) throw new ContractException("cardinality identity is required");
        if (min < 0 || (max != -1 && max < min)) throw new ContractException("invalid cardinality bounds");
        evidence = ContractSupport.list(evidence);
    }
}
