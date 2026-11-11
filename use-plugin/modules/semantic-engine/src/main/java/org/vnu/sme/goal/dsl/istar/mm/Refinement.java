package org.vnu.sme.goal.dsl.istar.mm;

/**
 * iStar 2.0 refinement links (both go from children → parent):
 *  - {@link AndRefinement}: all children must be satisfied (T-shaped arrowhead at parent)
 *  - {@link OrRefinement}:  at least one child satisfies parent (solid arrow at parent)
 */
public sealed interface Refinement
        permits AndRefinement, OrRefinement {

    String parent();
}
