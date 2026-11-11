package org.jacamo.bridge.contract;

import java.util.List;

public record UnresolvedFact(String kind, String stableKey, CapabilityStatus status, String reason, List<Evidence> evidence) {
    public UnresolvedFact {
        kind = ContractSupport.required(kind, "unresolved kind");
        stableKey = ContractSupport.required(stableKey, "stableKey");
        if (status == null || status == CapabilityStatus.COMPLETE) throw new ContractException("unresolved fact cannot be complete");
        reason = ContractSupport.required(reason, "reason");
        evidence = ContractSupport.list(evidence);
    }
}
