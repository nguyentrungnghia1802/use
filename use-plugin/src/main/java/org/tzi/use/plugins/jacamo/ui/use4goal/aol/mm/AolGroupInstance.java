package org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AolGroupInstance(String typeName, String instanceId,
                               List<AolGroupInstance> subgroups,
                               List<AolPlay> plays,
                               List<AolEntityInstance> entities,
                               Map<String, String> attributeValues) {
    public AolGroupInstance {
        Objects.requireNonNull(typeName, "typeName");
        Objects.requireNonNull(instanceId, "instanceId");
        subgroups = List.copyOf(subgroups);
        plays = List.copyOf(plays);
        entities = List.copyOf(entities);
        attributeValues = Map.copyOf(attributeValues);
    }
}
