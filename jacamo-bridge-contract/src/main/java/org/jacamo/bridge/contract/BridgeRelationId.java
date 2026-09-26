package org.jacamo.bridge.contract;

import java.util.List;

/** Context-preserving relation identity, including ordered endpoints and authority evidence. */
public record BridgeRelationId(
        String relationKind, List<BridgeEntityId> endpoints,
        String authorityEvidenceId, String incarnation) {
    public BridgeRelationId {
        relationKind = ContractSupport.required(relationKind, "relationKind");
        endpoints = ContractSupport.list(endpoints);
        if (endpoints.isEmpty()) throw new ContractException("relation endpoints are required");
        authorityEvidenceId = ContractSupport.required(authorityEvidenceId, "authorityEvidenceId");
        incarnation = ContractSupport.required(incarnation, "incarnation");
    }
    public String canonical() {
        String tuple = relationKind + "\n" + endpoints.stream().map(BridgeEntityId::canonical)
                .reduce("", (left, right) -> left + right + "\n") + authorityEvidenceId + "\n" + incarnation;
        return "brid:v1:" + ContractSupport.sha256(tuple);
    }
}
