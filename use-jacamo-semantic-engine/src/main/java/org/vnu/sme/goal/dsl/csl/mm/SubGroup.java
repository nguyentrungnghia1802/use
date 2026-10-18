package org.vnu.sme.goal.dsl.csl.mm;

/** YAML SubGroup membership with its own cardinality. */
public interface SubGroup extends CardinalityTarget {
    CslGroup group();
    CslCardinality cardinality();
}
