package org.vnu.sme.goal.dsl.istar.ast;

import java.util.List;

/**
 * Sealed hierarchy for the body items of an actor in iStar 2.0.
 * Only intentional elements (+ the two actor-to-actor statements) are
 * declared here; every element-to-element relation is attached inline as
 * this element's own {@link RelCS} list — fields store only what appears
 * in the token stream, no semantics here.
 */
public sealed interface ElementBodyCS {

    /** goal <id> [: goalType] rel* [ocl: raw ;] */
    record GoalCS(String id, String goalType, List<RelCS> rels,
                  List<IStarOclConstraintCS> constraints) implements ElementBodyCS {}

    /** task <id> rel* [ocl: raw ;] */
    record TaskCS(String id, List<RelCS> rels,
                  List<IStarOclConstraintCS> constraints) implements ElementBodyCS {}

    /** resource <id> rel* */
    record ResourceCS(String id, List<RelCS> rels) implements ElementBodyCS {}

    /** quality <id> rel* */
    record QualityCS(String id, List<RelCS> rels) implements ElementBodyCS {}

    /** <actor> is-a <target> */
    record IsACS(String actor, String target) implements ElementBodyCS {}

    /** <actor> participates-in <target> */
    record ParticipatesCS(String actor, String target) implements ElementBodyCS {}
}
