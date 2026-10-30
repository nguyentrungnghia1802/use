package org.tzi.use.plugins.jacamo.ui.use4goal.aol.view;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm.AolEntityInstance;
import org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm.AolGroupInstance;
import org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm.AolLink;
import org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm.AolModel;
import org.tzi.use.plugins.jacamo.ui.use4goal.aol.mm.AolPlay;
import org.tzi.use.plugins.jacamo.ui.use4goal.state.AcceptedAolState;

/**
 * Recursive tree layout: unlike CSL's type-level layout (roles/entities live in one shared
 * pool referenced by possibly many groups), an AOL instance tree has no sharing -- each play
 * and entity instance belongs to exactly one group instance -- so each group instance's
 * subtree width is computed bottom-up once, then positions are assigned top-down centered
 * within that reserved width.
 */
public final class AolLayoutBuilder {
    private static final int MARGIN = 36;
    private static final int NODE_GAP = 26;
    private static final int SUBTREE_GAP = 50;
    private static final int ROW_GAP = 44;
    private static final int AGENT_ROW_TOP_GAP = 70;

    private static final int AGENT_MIN_WIDTH = 110;
    private static final int GROUP_MIN_WIDTH = 170;
    private static final int PLAY_MIN_WIDTH = 160;
    private static final int ENTITY_MIN_WIDTH = 160;
    private static final int DETAIL_HEIGHT = 16;

    private AolLayoutBuilder() {}

    public static AolLayout build(AolModel model) {
        Map<String, AolNode> nodes = new LinkedHashMap<>();
        List<AolEdge> edges = new ArrayList<>();
        if (model == null) return new AolLayout(nodes, edges, 900, 600);

        // Maps a user-authored instance id (play/entity/group instance id, globally unique --
        // see AolModelFactory) to its synthesized diagram node id, so 'link' edges (which are
        // not scoped to any Group) can be resolved after the group tree is laid out.
        Map<String, String> userIdIndex = new LinkedHashMap<>();

        int agentX = MARGIN;
        int agentRowBottom = MARGIN;
        for (String agent : model.agents()) {
            List<String> details = new ArrayList<>();
            String profile = model.agentProfileRoles().get(agent);
            if (profile != null) details.add("profile = " + profile);
            model.agentAttributeValues().getOrDefault(agent, Map.of()).forEach(
                    (key, value) -> details.add(key + " = " + value));
            AolNode node = node(agentId(agent), agent, AolNodeKind.AGENT, "agent", details);
            node.x = agentX;
            node.y = MARGIN;
            nodes.put(node.id, node);
            userIdIndex.put(agent, node.id);
            agentX += node.w + NODE_GAP;
            agentRowBottom = Math.max(agentRowBottom, node.y + node.h);
        }

        int treeTop = agentRowBottom + AGENT_ROW_TOP_GAP;
        int cursorX = MARGIN;
        for (AolGroupInstance root : model.groupInstances()) {
            GroupSubtree subtree = buildSubtree(root, "", userIdIndex);
            place(subtree, cursorX, treeTop, nodes, edges, null);
            playedByEdges(subtree, edges, userIdIndex, nodes);
            cursorX += subtree.width + SUBTREE_GAP;
        }

        int entityRowTop = nodes.values().stream().mapToInt(n -> n.y + n.h).max().orElse(treeTop) + ROW_GAP;
        int entityX = MARGIN;
        for (AolEntityInstance entity : model.topLevelEntities()) {
            List<String> details = new ArrayList<>();
            entity.attributeValues().forEach((k, v) -> details.add(k + " = " + v));
            AolNode node = node(topLevelEntityId(entity.instanceId()),
                    entity.instanceId() + ": " + entity.entityType(),
                    AolNodeKind.ENTITY_INSTANCE, "entity instance", details);
            node.x = entityX;
            node.y = entityRowTop;
            nodes.put(node.id, node);
            userIdIndex.put(entity.instanceId(), node.id);
            entityX += node.w + NODE_GAP;
        }

        for (AolLink link : model.links()) {
            String fromId = userIdIndex.get(link.sourceInstanceId());
            if (fromId == null) continue;
            for (String targetId : link.targetInstanceIds()) {
                String toId = userIdIndex.get(targetId);
                if (toId != null) {
                    if ("play".equals(link.relationName())) {
                        edges.add(new AolEdge(fromId, toId, AolEdgeKind.PLAYED_BY, "play"));
                    } else if ("specializes".equals(link.relationName())) {
                        edges.add(AolEdge.generalization(toId, fromId));
                    } else if (link.relationName().contains("_contains_")) {
                        edges.add(AolEdge.play(toId, fromId));
                    } else {
                        edges.add(AolEdge.link(fromId, toId, link.relationName()));
                    }
                }
            }
        }

        int maxRight = nodes.values().stream().mapToInt(n -> n.x + n.w).max().orElse(900);
        int maxBottom = nodes.values().stream().mapToInt(n -> n.y + n.h).max().orElse(600);
        return new AolLayout(nodes, edges, Math.max(maxRight + MARGIN, 700), Math.max(maxBottom + MARGIN, 500));
    }

    public static AolLayout build(AcceptedAolState state) {
        Map<String, AolNode> nodes = new LinkedHashMap<>();
        List<AolEdge> edges = new ArrayList<>();
        if (state == null) return new AolLayout(nodes, edges, 900, 600);

        for (var object : state.objects().values().stream()
                .sorted(Comparator.comparing(AcceptedAolState.ObjectValue::id)).toList()) {
            List<String> details = object.attributes().entrySet().stream()
                    .map(entry -> entry.getKey() + " = " + entry.getValue()).toList();
            AolNodeKind kind = switch (object.kind()) {
                case GROUP -> AolNodeKind.GROUP_INSTANCE;
                case ROLE -> AolNodeKind.PLAY;
                case ENTITY -> AolNodeKind.ENTITY_INSTANCE;
                case AGENT -> AolNodeKind.AGENT;
            };
            AolNode item = node(stateId(object.id()), object.displayName(),
                    kind, object.kind().name().toLowerCase() + " object | identity="+object.id(), details);
            nodes.put(item.id, item);
        }
        state.associationLinks().forEach(link -> edges.add(AolEdge.link(
                stateId(link.sourceId()), stateId(link.targetId()), link.relationName())));
        state.playLinks().forEach(link -> edges.add(new AolEdge(
                stateId(link.parentRoleId()), stateId(link.childRoleId()), AolEdgeKind.PLAYED_BY, "play")));

        int[] size = placeStateNodes(nodes, edges);
        return new AolLayout(nodes, edges, size[0], size[1]);
    }

    private static int[] placeStateNodes(Map<String, AolNode> nodes, List<AolEdge> edges) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        Set<String> linked = new LinkedHashSet<>();
        for (AolEdge edge : edges) {
            if (!nodes.containsKey(edge.fromId()) || !nodes.containsKey(edge.toId())) continue;
            // A witnessed participation overlay is not a containment hierarchy.
            if ("participates (execution)".equals(edge.label())) continue;
            linked.add(edge.fromId());
            linked.add(edge.toId());
            String parent="enacts".equals(edge.label())?edge.toId():edge.fromId();
            String child="enacts".equals(edge.label())?edge.fromId():edge.toId();
            indegree.merge(child, 1, Integer::sum);
            indegree.putIfAbsent(parent, 0);
            outgoing.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(child);
        }

        Map<String, Integer> level = new LinkedHashMap<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        linked.stream().filter(id -> indegree.getOrDefault(id, 0) == 0).sorted().forEach(id -> {
            level.put(id, 0);
            pending.add(id);
        });
        while (!pending.isEmpty()) {
            String source = pending.remove();
            for (String target : outgoing.getOrDefault(source, List.of())) {
                level.merge(target, level.get(source) + 1, Math::max);
                int remaining = indegree.merge(target, -1, Integer::sum);
                if (remaining == 0) pending.add(target);
            }
        }
        int cycleLevel = level.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
        linked.stream().filter(id -> !level.containsKey(id)).sorted()
                .forEach(id -> level.put(id, cycleLevel));

        Map<Integer, List<AolNode>> rowsByLevel = new LinkedHashMap<>();
        level.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry ->
                rowsByLevel.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>())
                        .add(nodes.get(entry.getKey())));
        List<List<AolNode>> rows = new ArrayList<>();
        // Entity attributes need room; mixing them with a cast of roles creates
        // a very wide row. Keep each graph level, splitting only presentation rows.
        for(var values:rowsByLevel.values()){
            var entities=values.stream().filter(n->n.kind==AolNodeKind.ENTITY_INSTANCE).toList();
            var others=values.stream().filter(n->n.kind!=AolNodeKind.ENTITY_INSTANCE).toList();
            if(!entities.isEmpty())rows.add(entities);if(!others.isEmpty())rows.add(others);
        }

        List<AolNode> isolated = nodes.values().stream().filter(node -> !linked.contains(node.id)).toList();
        List<AolNode> row = new ArrayList<>();
        int rowWidth = 0;
        for (AolNode node : isolated) {
            int nextWidth = rowWidth + (row.isEmpty() ? 0 : NODE_GAP) + node.w;
            if (!row.isEmpty() && nextWidth > 900 - 2 * MARGIN) {
                rows.add(row);
                row = new ArrayList<>();
                rowWidth = 0;
            }
            row.add(node);
            rowWidth += (rowWidth == 0 ? 0 : NODE_GAP) + node.w;
        }
        if (!row.isEmpty()) rows.add(row);

        int contentWidth = rows.stream().mapToInt(AolLayoutBuilder::rowWidth).max().orElse(0);
        int y = MARGIN;
        int maxRight = 0;
        for (List<AolNode> stateRow : rows) {
            int x = MARGIN + (contentWidth - rowWidth(stateRow)) / 2;
            int rowHeight = stateRow.stream().mapToInt(node -> node.h).max().orElse(0);
            for (AolNode node : stateRow) {
                node.x = x;
                node.y = y;
                maxRight = Math.max(maxRight, x + node.w);
                x += node.w + NODE_GAP;
            }
            y += rowHeight + ROW_GAP;
        }
        return new int[] { Math.max(maxRight + MARGIN, 700), Math.max(y, 500) };
    }

    private static final class GroupSubtree {
        AolNode groupNode;
        List<AolNode> children = new ArrayList<>();
        List<GroupSubtree> subgroups = new ArrayList<>();
        Map<String, String> playAgents = new LinkedHashMap<>();
        int subgroupsRowWidth;
        int width;
    }

    private static GroupSubtree buildSubtree(AolGroupInstance g, String path, Map<String, String> userIdIndex) {
        String groupPath = path + "/" + g.instanceId();
        GroupSubtree subtree = new GroupSubtree();
        List<String> groupDetails = new ArrayList<>();
        g.attributeValues().forEach((k, v) -> groupDetails.add(k + " = " + v));
        subtree.groupNode = node(groupId(groupPath), g.instanceId() + ": " + g.typeName(),
                AolNodeKind.GROUP_INSTANCE, "group instance", groupDetails);
        userIdIndex.put(g.instanceId(), subtree.groupNode.id);

        for (AolPlay play : g.plays()) {
            String id = playId(groupPath, play.instanceId());
            List<String> details = new ArrayList<>();
            play.attributeValues().forEach((k, v) -> details.add(k + " = " + v));
            subtree.children.add(node(id, play.instanceId() + ": " + play.roleType(),
                    AolNodeKind.PLAY, "play", details));
            subtree.playAgents.put(id, play.agentId());
            userIdIndex.put(play.instanceId(), id);
        }
        for (AolEntityInstance entity : g.entities()) {
            String id = entityId(groupPath, entity.instanceId());
            List<String> details = new ArrayList<>();
            entity.attributeValues().forEach((k, v) -> details.add(k + " = " + v));
            subtree.children.add(node(id, entity.instanceId() + ": " + entity.entityType(),
                    AolNodeKind.ENTITY_INSTANCE, "entity instance", details));
            userIdIndex.put(entity.instanceId(), id);
        }
        for (AolGroupInstance sub : g.subgroups()) {
            subtree.subgroups.add(buildSubtree(sub, groupPath, userIdIndex));
        }

        int childRowWidth = rowWidth(subtree.children);
        subtree.subgroupsRowWidth = subtree.subgroups.stream().mapToInt(s -> s.width).sum()
                + Math.max(0, subtree.subgroups.size() - 1) * SUBTREE_GAP;
        subtree.width = Math.max(Math.max(childRowWidth, subtree.subgroupsRowWidth), subtree.groupNode.w);
        return subtree;
    }

    private static void place(GroupSubtree subtree, int leftX, int topY,
                              Map<String, AolNode> nodes, List<AolEdge> edges, String parentGroupId) {
        AolNode g = subtree.groupNode;
        g.x = leftX + (subtree.width - g.w) / 2;
        g.y = topY;
        nodes.put(g.id, g);
        if (parentGroupId != null) edges.add(AolEdge.subgroup(g.id, parentGroupId));

        int childRowWidth = rowWidth(subtree.children);
        int childX = leftX + (subtree.width - childRowWidth) / 2;
        int childY = topY + g.h + ROW_GAP;
        int rowHeight = 0;
        for (AolNode child : subtree.children) {
            child.x = childX;
            child.y = childY;
            nodes.put(child.id, child);
            childX += child.w + NODE_GAP;
            rowHeight = Math.max(rowHeight, child.h);
            if (child.kind == AolNodeKind.PLAY) edges.add(AolEdge.play(child.id, g.id));
            else edges.add(AolEdge.entity(child.id, g.id));
        }

        int subgroupY = subtree.children.isEmpty() ? childY : childY + rowHeight + ROW_GAP;
        int subX = leftX + (subtree.width - subtree.subgroupsRowWidth) / 2;
        for (GroupSubtree sub : subtree.subgroups) {
            place(sub, subX, subgroupY, nodes, edges, g.id);
            subX += sub.width + SUBTREE_GAP;
        }
    }

    private static void playedByEdges(GroupSubtree subtree, List<AolEdge> edges,
                                      Map<String, String> userIdIndex, Map<String, AolNode> nodes) {
        subtree.playAgents.forEach((playNodeId, agent) -> {
            String targetAgentId = userIdIndex.get(agent);
            if (targetAgentId == null) {
                String defaultAgentId = agentId(agent);
                if (nodes.containsKey(defaultAgentId)) {
                    targetAgentId = defaultAgentId;
                } else {
                    AolNode agentNode = node(defaultAgentId, agent, AolNodeKind.AGENT, "agent", List.of());
                    agentNode.x = MARGIN;
                    agentNode.y = MARGIN;
                    nodes.put(defaultAgentId, agentNode);
                    userIdIndex.put(agent, defaultAgentId);
                    targetAgentId = defaultAgentId;
                }
            }
            edges.add(AolEdge.playedBy(playNodeId, targetAgentId));
        });
        subtree.subgroups.forEach(sub -> playedByEdges(sub, edges, userIdIndex, nodes));
    }

    private static int rowWidth(List<AolNode> row) {
        if (row.isEmpty()) return 0;
        return row.stream().mapToInt(n -> n.w).sum() + (row.size() - 1) * NODE_GAP;
    }

    private static AolNode node(String id, String label, AolNodeKind kind, String subtitle, List<String> details) {
        AolNode node = new AolNode(id, label, kind, subtitle, details);
        int longest = Math.max(label.length(), details.stream().mapToInt(String::length).max().orElse(0));
        node.w = switch (kind) {
            case AGENT -> Math.max(AGENT_MIN_WIDTH, Math.min(260, longest * 7 + 30));
            case GROUP_INSTANCE -> Math.max(GROUP_MIN_WIDTH, Math.min(320, longest * 7 + 30));
            case PLAY -> Math.max(PLAY_MIN_WIDTH, Math.min(360, longest * 7 + 28));
            case ENTITY_INSTANCE -> Math.max(ENTITY_MIN_WIDTH, Math.min(360, longest * 7 + 28));
        };
        node.h = switch (kind) {
            case AGENT -> 44;
            case GROUP_INSTANCE -> 56;
            case PLAY, ENTITY_INSTANCE -> 40 + Math.max(1, details.size()) * DETAIL_HEIGHT + 10;
        };
        return node;
    }

    private static String agentId(String name) { return "agent::" + name; }
    private static String groupId(String path) { return "group::" + path; }
    private static String playId(String groupPath, String id) { return "play::" + groupPath + "/" + id; }
    private static String entityId(String groupPath, String id) { return "entity::" + groupPath + "/" + id; }
    private static String topLevelEntityId(String id) { return "entity::top/" + id; }
    private static String stateId(String id) { return "state::" + id; }
}
