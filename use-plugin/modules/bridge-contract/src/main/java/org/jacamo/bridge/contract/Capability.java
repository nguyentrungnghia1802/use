package org.jacamo.bridge.contract;

import java.util.List;

public record Capability(String name, CapabilityStatus status, List<String> evidenceIds, String reason) {
    public Capability {
        name = ContractSupport.required(name, "capability name");
        if (status == null) throw new ContractException("capability status is required");
        evidenceIds = ContractSupport.list(evidenceIds);
        reason = reason == null ? "" : reason;
        if (status != CapabilityStatus.COMPLETE && reason.isBlank())
            throw new ContractException("partial/unavailable capability requires a reason: " + name);
    }
}
