package org.jacamo.bridge.contract.semantic;

import java.util.List;

/** Typed facts copied from the audited Jason 3.3.2 object model. */
public final class JasonSemanticContract {
    private JasonSemanticContract() { }

    public record AgentProgramSemantic(SemanticMetadata metadata, String declarationId,
            String sourceUri, String sourceDigest, PlanLibrarySemantic planLibrary) { }
    public record PlanLibrarySemantic(SemanticMetadata metadata, List<PlanSemantic> plans) {
        public PlanLibrarySemantic { plans=List.copyOf(plans); }
    }
    public record PlanSemantic(SemanticMetadata metadata, int ordinal, String label, String context,
            TriggerSemantic trigger, List<PlanBodyElementSemantic> body) {
        public PlanSemantic { body=List.copyOf(body); }
    }
    public record TriggerSemantic(SemanticMetadata metadata, String operator, String type, String literal) { }
    public record PlanBodyElementSemantic(SemanticMetadata metadata, int ordinal, String bodyType,
            String term, String nextSemanticId) { }
    public record ActionSemantic(SemanticNode value) { }
    public record BeliefSemantic(SemanticNode value) { }
    public record AgentGoalSemantic(SemanticNode value) { }
    public record BeliefRuleSemantic(SemanticNode value) { }
    public record JasonRuntimeSemantic(SemanticNode value) { }
}
