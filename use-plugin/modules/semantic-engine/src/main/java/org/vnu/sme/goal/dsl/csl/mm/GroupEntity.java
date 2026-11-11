package org.vnu.sme.goal.dsl.csl.mm;

/** YAML GroupEntity composition (EntityDefinition + typed cardinality). */
public interface GroupEntity {
    String entityName();
    CslCardinality cardinality();
}
