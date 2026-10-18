package org.tzi.use.semantic.engine.api;

import java.util.Set;

/** Library features actually supplied by the selected original source, not live-profile admission. */
public final class EngineCapabilities {
    private EngineCapabilities() { }
    public static final Set<String> ORIGINAL_CORE = Set.of("CSL_V4_FRONTEND", "ISTAR_FRONTEND",
            "GOAL_MARKING", "HISTORICAL_TASK_MARKING", "AND_OR_PROPAGATION",
            "CONTEXTUAL_DEPENDENCY_INSTANTIATION");
    public static final Set<String> UNIMPLEMENTED_GRAMMAR_SEMANTICS = Set.of("DECLARED_GOAL_ACTIVATION",
            "FORALL_REFINEMENT", "PICK_REFINEMENT", "TOP_LEVEL_ACTOR_ASSOCIATION_NOT_BUILT",
            "UNPRESERVED_DESCRIPTION_OR_CONDITION_LANGUAGE");
    public static final String PRODUCTION_OCL_BACKEND = "NATIVE_USE_7_5";
    public static final String LEGACY_SUBSET_EVALUATOR = "COMPILE_CLOSURE_ONLY_NON_PRODUCTION";
}
