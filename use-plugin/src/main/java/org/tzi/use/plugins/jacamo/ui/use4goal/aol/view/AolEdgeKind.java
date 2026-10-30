package org.tzi.use.plugins.jacamo.ui.use4goal.aol.view;

public enum AolEdgeKind {
    /** subgroup instance -> parent group instance (composition: deleting the parent deletes it). */
    SUBGROUP_COMPOSITION,
    /** play -> group instance it occurs in (composition). */
    PLAY_COMPOSITION,
    /** entity instance -> group instance that aggregates it (hollow diamond, non-exclusive). */
    ENTITY_AGGREGATION,
    /** play -> the agent that plays it (dashed, "played by" -- the agent is the persistent identity). */
    PLAYED_BY,
    /** role instance -> parent role instance (Role generalization/specialization). */
    ROLE_GENERALIZATION,
    /** instantiates an CSL relation (association/aggregation/composition) between any two
     *  instances -- independent of Group nesting, e.g. a play "knows" a free-standing entity. */
    LINK;

    public boolean isComposition() {
        return this == SUBGROUP_COMPOSITION || this == PLAY_COMPOSITION;
    }
}
