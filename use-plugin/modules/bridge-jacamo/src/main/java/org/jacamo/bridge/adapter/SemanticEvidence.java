package org.jacamo.bridge.adapter;

import java.util.List;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.Evidence;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.jacamo.bridge.contract.semantic.SourceEvidence;

final class SemanticEvidence {
    private SemanticEvidence() { }

    static SourceEvidence source(Evidence evidence, EvidenceAuthority authority, String fqcn,
                                 String semanticId, int startLine, int endLine,
                                 Fidelity fidelity, CapabilityStatus capability, List<String> diagnostics) {
        return new SourceEvidence(authority, evidence.sourceUri(), evidence.sourceDigest(), fqcn, semanticId,
                Math.max(0,startLine), Math.max(Math.max(0,startLine),endLine), "", "", 0,
                fidelity, capability, diagnostics);
    }

    static SemanticMetadata metadata(String semanticId, String sourceKind, String fqcn,
                                     EvidenceAuthority authority, Fidelity fidelity,
                                     CapabilityStatus capability, Evidence evidence,
                                     int startLine, int endLine, List<String> diagnostics) {
        return new SemanticMetadata(semanticId, sourceKind, fqcn, authority, fidelity, capability,
                List.of(source(evidence,authority,fqcn,semanticId,startLine,endLine,fidelity,capability,diagnostics)),
                diagnostics);
    }
}
