package org.tzi.use.plugins.jacamo.dsl.oal.mm;

import java.util.Map;
import java.util.Objects;

public record AolEntityInstance(String entityType, String instanceId, Map<String, String> attributeValues) {
    public AolEntityInstance {
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(instanceId, "instanceId");
        attributeValues = Map.copyOf(attributeValues);
    }
}
