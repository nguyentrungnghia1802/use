package org.vnu.sme.goal.dsl.csl.mm;

/** YAML-facing name for the CSL attribute definition. */
public interface AttributeDefinition {
    String name();
    CslDataType type();
    /** True only for multiplicity [0..1]; absence of this modifier means [1]. */
    boolean optional();
    boolean mutable();
}
