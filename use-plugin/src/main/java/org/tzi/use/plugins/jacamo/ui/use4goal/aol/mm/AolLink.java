package org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm;

import java.util.List;
import java.util.Objects;

/** One instantiation of an CSL relation (association/aggregation/composition) between two instances. */
public record AolLink(String relationName, String sourceInstanceId, List<String> targetInstanceIds) {
    public AolLink {
        Objects.requireNonNull(relationName, "relationName");
        Objects.requireNonNull(sourceInstanceId, "sourceInstanceId");
        targetInstanceIds = List.copyOf(targetInstanceIds);
    }
}
