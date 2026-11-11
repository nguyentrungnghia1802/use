package org.jacamo.bridge.contract.semantic;

import java.util.List;
import org.jacamo.bridge.contract.CapabilityStatus;

/** Metadata shared by every typed semantic DTO. */
public record SemanticMetadata(
        String semanticId,
        String sourceKind,
        String sourceJavaFqcn,
        EvidenceAuthority evidenceAuthority,
        Fidelity fidelity,
        CapabilityStatus capabilityStatus,
        List<SourceEvidence> evidence,
        List<String> diagnostics) {
    public SemanticMetadata {
        semanticId = SemanticSupport.required(semanticId, "semanticId");
        sourceKind = SemanticSupport.required(sourceKind, "sourceKind");
        sourceJavaFqcn = SemanticSupport.required(sourceJavaFqcn, "sourceJavaFqcn");
        evidenceAuthority = SemanticSupport.required(evidenceAuthority, "evidenceAuthority");
        fidelity = SemanticSupport.required(fidelity, "fidelity");
        capabilityStatus = SemanticSupport.required(capabilityStatus, "capabilityStatus");
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
