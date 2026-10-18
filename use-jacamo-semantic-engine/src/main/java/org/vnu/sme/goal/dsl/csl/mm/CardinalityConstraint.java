package org.vnu.sme.goal.dsl.csl.mm;

public interface CardinalityConstraint {
    CardinalityTarget target();
    CslCardinality cardinality();
}
