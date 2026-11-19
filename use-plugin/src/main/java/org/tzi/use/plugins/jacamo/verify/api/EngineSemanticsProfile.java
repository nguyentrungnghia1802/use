package org.tzi.use.plugins.jacamo.verify.api;

import java.util.Set;
import org.vnu.sme.goal.dsl.istar.mm.Actor;
import org.vnu.sme.goal.dsl.istar.mm.AssocKind;
import org.vnu.sme.goal.dsl.istar.mm.Association;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalModelValidationPolicy;
import org.vnu.sme.goal.dsl.istar.mm.Role;

/** Versioned semantic choices. No mapping rules or mutable runtime are owned here. */
public enum EngineSemanticsProfile {
    USE4GOAL_OBSERVED_V1,
    /** Versioned admission extension; original Boolean recurrence is unchanged. */
    DERIVED_AND_RESOURCE_V1,
    /** Opt-in unary realization and empty capability-role specialization. */
    ROLE_SPECIALIZATION_V1;

    /** Capabilities supplied by the installed engines. */
    public static final Set<String> ORIGINAL_CORE = Set.of("CSL_V4_FRONTEND", "ISTAR_FRONTEND",
            "GOAL_MARKING", "HISTORICAL_TASK_MARKING", "AND_OR_PROPAGATION",
            "CONTEXTUAL_DEPENDENCY_INSTANTIATION");
    public static final Set<String> UNIMPLEMENTED_GRAMMAR_SEMANTICS = Set.of("DECLARED_GOAL_ACTIVATION",
            "FORALL_REFINEMENT", "PICK_REFINEMENT", "TOP_LEVEL_ACTOR_ASSOCIATION_NOT_BUILT",
            "UNPRESERVED_DESCRIPTION_OR_CONDITION_LANGUAGE");
    public static final String PRODUCTION_OCL_BACKEND = "NATIVE_USE_7_5";
    public static final String LEGACY_SUBSET_EVALUATOR = "COMPILE_CLOSURE_ONLY_NON_PRODUCTION";

    public GoalModelValidationPolicy validationPolicy() {
        return switch(this) {
            case USE4GOAL_OBSERVED_V1 -> GoalModelValidationPolicy.STRICT_ORIGINAL;
            case DERIVED_AND_RESOURCE_V1 -> GoalModelValidationPolicy.DERIVED_AND_DEPENDEE_V1;
            case ROLE_SPECIALIZATION_V1 -> GoalModelValidationPolicy.ROLE_SPECIALIZATION_V1;
        };
    }
    public boolean externalRelations() { return this != USE4GOAL_OBSERVED_V1; }
    public boolean roleSpecialization() {return this==ROLE_SPECIALIZATION_V1;}
    public Set<String> admissionPatchIds() {
        return this==ROLE_SPECIALIZATION_V1?Set.of("P-IADM-01","P-IADM-02"):this==DERIVED_AND_RESOURCE_V1?Set.of("P-IADM-01"):Set.of();
    }

    public String upstreamCommit() { return "6f3e73801239fd8bfb94ca8d04ba531b903c0ac9"; }
    public String hostUseVersion() { return "7.5.0"; }
    public String antlrVersion() { return "4.9.3"; }
    public int maxOccurrences() { return 4096; }
    public int maxDefinitionDepth() { return 256; }
    public int maxDefinitionEdges() { return 16384; }
    public int maxPendingInputs() { return 1024; }
    public int originalPropagationStepLimit() { return 10_000; }

    /** Definition-only capability roles have no independent intentional occurrence.
     * Inherited mutable domain attributes stay in the same CSL role instance.
     * Nonempty intentional inheritance is deliberately outside this profile. */
    public static final class RoleSpecialization {
        private RoleSpecialization() { }
        public static boolean definitionOnly(GoalModel model,Actor actor) {
            return actor instanceof Role && actor.elements().isEmpty() && actor.refinements().isEmpty()
                    && actor.neededBys().isEmpty() && actor.contributions().isEmpty() && actor.qualifications().isEmpty()
                    && actor.associations().stream().allMatch(a->a.kind()==AssocKind.IS_A)
                    && model.getActors().stream().flatMap(a->a.associations().stream()).anyMatch(a->a.kind()==AssocKind.IS_A&&a.target().equals(actor.name()));
        }
        public static boolean admitted(GoalModel model,Actor owner,Association relation) {
            return owner instanceof Role && relation.actor().equals(owner.name()) && relation.kind()==AssocKind.IS_A
                    && model.findActor(relation.target()).filter(a->definitionOnly(model,a)).isPresent();
        }
    }
}
