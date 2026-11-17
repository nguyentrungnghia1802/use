package org.vnu.sme.goal.dsl.csl_marking.mm;

import java.util.Map;
import java.util.Objects;

public record AolEntityInstance(String entityType, String instanceId, Map<String, String> attributeValues) {
    public AolEntityInstance {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(instanceId, "instanceId");
        attributeValues = Map.copyOf(attributeValues);
    }
}
