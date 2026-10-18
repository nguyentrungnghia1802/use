package org.vnu.sme.goal.dsl.iscn.mm;

import java.util.List;

/**
 * Result of {@code aggregate label : all|any over elementId;} — whether the property holds
 * across every declared instance, and which instances individually satisfied it.
 */
public record AggregateResult(String label, AggregateMode mode, String elementId,
                               boolean holds, List<String> satisfiedInstances, List<String> allInstances) {
    public AggregateResult {
        satisfiedInstances = List.copyOf(satisfiedInstances);
        allInstances = List.copyOf(allInstances);
    }
}
