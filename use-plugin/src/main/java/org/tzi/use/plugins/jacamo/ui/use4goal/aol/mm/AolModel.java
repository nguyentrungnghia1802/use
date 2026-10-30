package org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm;

import java.util.List;
import java.util.Objects;
import java.util.Map;

/** @deprecated Legacy AOL v1 Agent-based state; use {@code AcceptedAolState}. */
@Deprecated(forRemoval = false)
public record AolModel(String version, String name, String cslFile,
                       List<String> agents, Map<String, String> agentProfileRoles,
                       Map<String, Map<String, String>> agentAttributeValues,
                       List<AolGroupInstance> groupInstances,
                       List<AolEntityInstance> topLevelEntities, List<AolLink> links) {
    public AolModel {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(cslFile, "cslFile");
        agents = List.copyOf(agents);
        agentProfileRoles = Map.copyOf(agentProfileRoles);
        agentAttributeValues = agentAttributeValues.entrySet().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        e -> Map.copyOf(e.getValue())));
        groupInstances = List.copyOf(groupInstances);
        topLevelEntities = List.copyOf(topLevelEntities);
        links = List.copyOf(links);
    }
}
