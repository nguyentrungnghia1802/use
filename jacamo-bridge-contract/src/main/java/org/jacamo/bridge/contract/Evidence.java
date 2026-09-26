package org.jacamo.bridge.contract;

public record Evidence(String evidenceId, String authority, String sourceUri, String sourceDigest, String detail) {
    public Evidence {
        evidenceId = ContractSupport.required(evidenceId, "evidenceId");
        authority = ContractSupport.required(authority, "authority");
        sourceUri = ContractSupport.required(sourceUri, "sourceUri");
        sourceDigest = ContractSupport.required(sourceDigest, "sourceDigest");
        detail = detail == null ? "" : detail;
    }
}
