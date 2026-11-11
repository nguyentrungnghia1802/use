package org.tzi.use.semantic.engine.api;

/** Versioned semantic choices. No mapping rules or mutable runtime are owned here. */
public enum EngineSemanticsProfile {
    USE4GOAL_OBSERVED_V1,
    /** Versioned admission extension; original Boolean recurrence is unchanged. */
    DERIVED_AND_RESOURCE_V1,
    /** Opt-in unary realization and empty capability-role specialization. */
    ROLE_SPECIALIZATION_V1;

    public org.vnu.sme.goal.dsl.istar.mm.GoalModelValidationPolicy validationPolicy() {
        return switch(this) {
            case USE4GOAL_OBSERVED_V1 -> org.vnu.sme.goal.dsl.istar.mm.GoalModelValidationPolicy.STRICT_ORIGINAL;
            case DERIVED_AND_RESOURCE_V1 -> org.vnu.sme.goal.dsl.istar.mm.GoalModelValidationPolicy.DERIVED_AND_DEPENDEE_V1;
            case ROLE_SPECIALIZATION_V1 -> org.vnu.sme.goal.dsl.istar.mm.GoalModelValidationPolicy.ROLE_SPECIALIZATION_V1;
        };
    }
    public boolean externalRelations() { return this != USE4GOAL_OBSERVED_V1; }
    public boolean roleSpecialization() {return this==ROLE_SPECIALIZATION_V1;}
    public java.util.Set<String> admissionPatchIds() {
        return this==ROLE_SPECIALIZATION_V1?java.util.Set.of("P-IADM-01","P-IADM-02"):this==DERIVED_AND_RESOURCE_V1?java.util.Set.of("P-IADM-01"):java.util.Set.of();
    }

    public String upstreamCommit() { return "6f3e73801239fd8bfb94ca8d04ba531b903c0ac9"; }
    public String hostUseVersion() { return "7.5.0"; }
    public String antlrVersion() { return "4.9.3"; }
    public int maxOccurrences() { return 4096; }
    public int maxDefinitionDepth() { return 256; }
    public int maxDefinitionEdges() { return 16384; }
    public int maxPendingInputs() { return 1024; }
    public int originalPropagationStepLimit() { return 10_000; }
}
