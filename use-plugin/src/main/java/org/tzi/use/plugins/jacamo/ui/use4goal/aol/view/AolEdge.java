package org.tzi.use.plugins.jacamo.ui.use4goal.aol.view;

import java.util.Objects;

public record AolEdge(String fromId, String toId, AolEdgeKind kind, String label) {
    public AolEdge {
        Objects.requireNonNull(fromId, "fromId");
        Objects.requireNonNull(toId, "toId");
        Objects.requireNonNull(kind, "kind");
    }

    public static AolEdge subgroup(String subgroupId, String parentGroupId) {
        return new AolEdge(subgroupId, parentGroupId, AolEdgeKind.SUBGROUP_COMPOSITION, null);
    }

    public static AolEdge play(String playId, String groupId) {
        return new AolEdge(playId, groupId, AolEdgeKind.PLAY_COMPOSITION, null);
    }

    public static AolEdge entity(String entityId, String groupId) {
        return new AolEdge(entityId, groupId, AolEdgeKind.ENTITY_AGGREGATION, null);
    }

    public static AolEdge playedBy(String playId, String agentId) {
        return new AolEdge(playId, agentId, AolEdgeKind.PLAYED_BY, "played by");
    }

    public static AolEdge generalization(String childRoleId, String parentRoleId) {
        return new AolEdge(childRoleId, parentRoleId, AolEdgeKind.ROLE_GENERALIZATION, "specializes");
    }

    public static AolEdge link(String fromId, String toId, String relationName) {
        return new AolEdge(fromId, toId, AolEdgeKind.LINK, relationName);
    }
}
