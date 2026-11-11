package org.jacamo.bridge.contract.semantic;

import java.util.List;
import org.jacamo.bridge.contract.CapabilityStatus;

/** Source provenance carried across the process boundary without live framework objects. */
public record SourceEvidence(
        EvidenceAuthority authority,
        String sourceUri,
        String sourceDigest,
        String sourceJavaFqcn,
        String sourceSemanticId,
        int startLine,
        int endLine,
        String runtimeIdentity,
        String sessionId,
        long generation,
        Fidelity fidelity,
        CapabilityStatus capabilityStatus,
        List<String> diagnostics) {
    public SourceEvidence {
        authority = SemanticSupport.required(authority, "authority");
        sourceUri = SemanticSupport.text(sourceUri);
        sourceDigest = SemanticSupport.text(sourceDigest);
        sourceJavaFqcn = SemanticSupport.text(sourceJavaFqcn);
        sourceSemanticId = SemanticSupport.required(sourceSemanticId, "sourceSemanticId");
        if (startLine < 0 || endLine < startLine) throw new IllegalArgumentException("invalid source span");
        runtimeIdentity = SemanticSupport.text(runtimeIdentity);
        sessionId = SemanticSupport.text(sessionId);
        fidelity = SemanticSupport.required(fidelity, "fidelity");
        capabilityStatus = SemanticSupport.required(capabilityStatus, "capabilityStatus");
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
