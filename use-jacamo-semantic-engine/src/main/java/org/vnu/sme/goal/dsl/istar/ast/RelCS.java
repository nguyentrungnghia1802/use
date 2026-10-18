package org.vnu.sme.goal.dsl.istar.ast;

/**
 * One inline relation suffix on an intentional element declaration:
 * {@code '>' relKind? target}. One variant per grammar {@code relKind}
 * alternative, plus the no-keyword (AND-refinement) case.
 */
public sealed interface RelCS {

    String target();

    /** '>' target (no keyword) = AND-refinement; '>' 'or' target = OR-refinement. */
    record RefineCS(String target, boolean or) implements RelCS {}

    /** '>' make|help|hurt|break target */
    record ContributesCS(String target, String type) implements RelCS {}

    /** '>' 'qualifies' target */
    record QualifiesCS(String target) implements RelCS {}

    /** '>' 'needed-by' target */
    record NeededByCS(String target) implements RelCS {}

}
