package org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm;

import java.util.Map;
import java.util.Objects;

/** One (Agent, Role, GroupInstance) triple -- the Agent is the persistent identity, not the Role. */
public record AolPlay(String roleType, String instanceId, String agentId, Map<String, String> attributeValues) {
    public AolPlay {
        Objects.requireNonNull(roleType, "roleType");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(agentId, "agentId");
        attributeValues = Map.copyOf(attributeValues);
    }
}
