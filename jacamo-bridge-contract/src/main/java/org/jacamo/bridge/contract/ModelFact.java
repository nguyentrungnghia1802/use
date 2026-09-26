package org.jacamo.bridge.contract;

import java.util.List;
import java.util.Map;

/** Immutable official/source model fact with exact references and provenance. */
public record ModelFact(String factKind, BridgeEntityId id, Map<String, String> attributes,
                        Map<String, List<BridgeEntityId>> references,
                        CapabilityStatus completeness, List<Evidence> evidence) {
    public ModelFact {
        factKind = ContractSupport.required(factKind, "factKind");
        if (id == null || completeness == null) throw new ContractException("model fact identity/completeness is required");
        attributes = ContractSupport.map(attributes);
        references = ContractSupport.map(references);
        evidence = ContractSupport.list(evidence);
    }
}
