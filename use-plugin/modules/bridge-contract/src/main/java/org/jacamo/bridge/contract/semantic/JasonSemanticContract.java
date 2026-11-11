package org.jacamo.bridge.contract.semantic;

import java.util.List;

/** Typed facts copied from the audited Jason 3.3.2 object model. */
public final class JasonSemanticContract {
    private JasonSemanticContract() { }

    public record AgentProgramSemantic(SemanticMetadata metadata, String declarationId,
            String sourceUri, String sourceDigest, PlanLibrarySemantic planLibrary,
            List<ActionSemantic> actions, List<BeliefSemantic> beliefs,
            List<AgentGoalSemantic> goals, List<BeliefRuleSemantic> beliefRules) {
        public AgentProgramSemantic {
            actions = List.copyOf(actions == null ? List.of() : actions);
            beliefs = List.copyOf(beliefs == null ? List.of() : beliefs);
            goals = List.copyOf(goals == null ? List.of() : goals);
            beliefRules = List.copyOf(beliefRules == null ? List.of() : beliefRules);
        }

        /** Compatibility constructor for Phase-1 snapshots without the Phase-2 Jason facts. */
        public AgentProgramSemantic(SemanticMetadata metadata, String declarationId,
                String sourceUri, String sourceDigest, PlanLibrarySemantic planLibrary) {
            this(metadata, declarationId, sourceUri, sourceDigest, planLibrary,
                    List.of(), List.of(), List.of(), List.of());
        }
    }
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
    /** Exact action projection from one official PlanBody node. */
    public record ActionSemantic(SemanticMetadata metadata, String planBodySemanticId,
            String term, String functor, int arity, String kind) {
        public ActionSemantic {
            planBodySemanticId = SemanticSupport.required(planBodySemanticId, "planBodySemanticId");
            term = SemanticSupport.text(term);
            functor = SemanticSupport.text(functor);
            kind = SemanticSupport.required(kind, "kind");
            if (arity < 0) throw new IllegalArgumentException("arity");
        }
    }

    /** Exact initial-belief literal copied from Agent.getInitialBels(). */
    public record BeliefSemantic(SemanticMetadata metadata, int ordinal, String literal) {
        public BeliefSemantic {
            if (ordinal < 0) throw new IllegalArgumentException("ordinal");
            literal = SemanticSupport.required(literal, "literal");
        }
    }

    /** Exact initial achievement goal exposed by Agent.getInitialGoals(). */
    public record AgentGoalSemantic(SemanticMetadata metadata, int ordinal, String literal, String goalKind) {
        public AgentGoalSemantic {
            if (ordinal < 0) throw new IllegalArgumentException("ordinal");
            literal = SemanticSupport.required(literal, "literal");
            goalKind = SemanticSupport.required(goalKind, "goalKind");
        }
    }

    /** Exact Jason rule copied from an initial-belief rule. */
    public record BeliefRuleSemantic(SemanticMetadata metadata, int ordinal, String head, String body) {
        public BeliefRuleSemantic {
            if (ordinal < 0) throw new IllegalArgumentException("ordinal");
            head = SemanticSupport.required(head, "head");
            body = SemanticSupport.required(body, "body");
        }
    }
    public record JasonRuntimeSemantic(SemanticNode value) { }
}
