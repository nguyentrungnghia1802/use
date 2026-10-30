package org.tzi.use.plugins.jacamo.ui.use4goal.csl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.vnu.sme.goal.dsl.csl.mm.*;

/** Builds a stable four-column Class-style layout: Entity, Role, OrgContext, Enumeration. */
public final class CslLayoutBuilder {
    private static final int MARGIN = 42;
    private static final int COLUMN_GAP = 64;
    private static final int ROW_GAP = 32;
    private static final int MIN_WIDTH = 60;
    private static final int MIN_COLUMN_WIDTH = 140;
    private static final int DETAIL_HEIGHT = 15;

    private CslLayoutBuilder() {}

    public static CslLayout build(CslModel model) {
        return build(model,Map.of());
    }
    /** Dashed enactment possibilities come from validated mapping, not CSL ownership. */
    public static CslLayout build(CslModel model,Map<String,java.util.Set<String>> enactments) {
        Map<String, CslNode> nodes = new LinkedHashMap<>();
        List<CslEdge> edges = new ArrayList<>();
        if (model == null) return new CslLayout(nodes, edges, 900, 600);

        int entityRight = layoutEntities(model, nodes, MARGIN);
        int roleX = entityRight + COLUMN_GAP;
        int roleRight = layoutRoles(model, nodes, roleX);
        int groupX = roleRight + COLUMN_GAP;
        int groupRight = layoutGroups(model, nodes, groupX);
        int enumX = groupRight + COLUMN_GAP;
        int enumRight = layoutEnums(model, nodes, enumX);

        model.generalizations().forEach(value -> edges.add(CslEdge.of(
                id(model, value.specific()), id(model, value.general()), CslEdgeKind.GENERALIZATION,
                null, null, null, false, false)));
        model.relations().forEach(value -> edges.add(CslEdge.of(
                id(model, value.source().type()), id(model, value.target().type()),
                CslEdgeKind.valueOf(value.kind().name()), endLabel(value.source()),
                endLabel(value.target()), value.name(), false, false)));
        // A declaration nested in an OrgCtx is M1 classifier containment. Show
        // it as UML composition without multiplicities: it does not prescribe
        // how many runtime instances of the contained classifier must exist.
        model.orgContexts().forEach(context -> context.members().forEach(member ->
                edges.add(CslEdge.of(groupId(context.name()), id(model, member.type()),
                        CslEdgeKind.COMPOSITION, null, null,
                        CslContainment.relationName(context.name(), member.type()),
                        false, false))));
        model.owners().stream()
                .filter(value -> model.findOrgContext(value.sourceGroup()).isEmpty())
                .forEach(value -> edges.add(CslEdge.of(
                        groupId(value.sourceGroup()), id(model, value.target()), CslEdgeKind.OWNER,
                        null, cardinality(value.multiplicity()), null, false, false)));
        model.compatibilities().forEach(value -> edges.add(CslEdge.of(
                roleId(value.fromRole()), roleId(value.toRole()), CslEdgeKind.COMPATIBILITY,
                null, null, null, value.bidirectional(),
                value.scope() == CslScope.INTER_GROUP)));
        enactments.forEach((context,roles)->roles.forEach(role-> {
            if(model.findOrgContext(context).isEmpty()||model.findRole(role).isEmpty())throw new IllegalArgumentException("UNRESOLVED_ENACTMENT_VIEW");
            edges.add(CslEdge.of(groupId(context),roleId(role),CslEdgeKind.ENACTMENT,null,null,"may enact",false,false));
        }));
        assignParallelRoutes(edges);

        int bottom = nodes.values().stream().mapToInt(value -> value.y + value.h).max().orElse(600);
        return new CslLayout(nodes, edges, Math.max(enumRight + MARGIN, 1000), Math.max(bottom + MARGIN, 650));
    }

    private static int layoutEntities(CslModel model, Map<String, CslNode> nodes, int x) {
        int y = MARGIN, right = x;
        for (CslEntity value : model.entities()) {
            CslNode node = node(entityId(value.name()), value.name(), CslNodeKind.ENTITY, "entity",
                    attributeLines(value.attributes()));
            node.x = x; node.y = y; nodes.put(node.id, node);
            y += node.h + ROW_GAP; right = Math.max(right, node.x + node.w);
        }
        return Math.max(right, x + MIN_COLUMN_WIDTH);
    }

    private static int layoutRoles(CslModel model, Map<String, CslNode> nodes, int x) {
        int y = MARGIN, right = x;
        for (CslRole value : model.roles()) {
            CslNode node = node(roleId(value.name()), value.name(), CslNodeKind.ROLE,
                    "role", attributeLines(value.attributes()));
            node.x = x; node.y = y; nodes.put(node.id, node);
            y += node.h + ROW_GAP; right = Math.max(right, node.x + node.w);
        }
        return Math.max(right, x + MIN_COLUMN_WIDTH);
    }

    private static int layoutGroups(CslModel model, Map<String, CslNode> nodes, int x) {
        int y = MARGIN, right = x;
        for (CslGroup value : model.groups()) {
            CslNodeKind kind = value.isOrganizationalContext()
                    ? CslNodeKind.ORG_CONTEXT : CslNodeKind.GROUP;
            String subtitle = value.isOrganizationalContext() ? "orgContext" : "group";
            CslNode node = node(groupId(value.name()), value.name(), kind, subtitle,
                    attributeLines(value.attributes()));
            node.x = x; node.y = y; nodes.put(node.id, node);
            y += node.h + ROW_GAP; right = Math.max(right, node.x + node.w);
        }
        return Math.max(right, x + MIN_COLUMN_WIDTH);
    }

    private static int layoutEnums(CslModel model, Map<String, CslNode> nodes, int x) {
        int y = MARGIN, right = x;
        for (CslEnum value : model.enums()) {
            CslNode node = node(enumId(value.name()), value.name(), CslNodeKind.ENUM, "enumeration",
                    value.literals());
            node.x = x; node.y = y; nodes.put(node.id, node);
            y += node.h + ROW_GAP; right = Math.max(right, node.x + node.w);
        }
        return Math.max(right, x + MIN_COLUMN_WIDTH);
    }

    private static CslNode node(String id, String label, CslNodeKind kind, String subtitle, List<String> details) {
        CslNode node = new CslNode(id, label, kind, subtitle, details);
        int longest = Math.max(label.length(), details.stream().mapToInt(String::length).max().orElse(0));
        node.w = Math.max(MIN_WIDTH, Math.min(340, longest * 7 + 28));
        if(kind == CslNodeKind.ROLE)node.w = Math.max(node.w,label.length()*10+52);
        node.h = (kind==CslNodeKind.ORG_CONTEXT||kind==CslNodeKind.GROUP?55:40) + (details.isEmpty() ? 10 : details.size() * DETAIL_HEIGHT + 10);
        return node;
    }

    private static List<String> attributeLines(List<CslAttribute> attributes) {
        return attributes.stream().map(value -> value.name() + " : " + value.type().sourceName()).toList();
    }

    private static String id(CslModel model, String name) {
        if (model.findEntity(name).isPresent()) return entityId(name);
        if (model.findRole(name).isPresent()) return roleId(name);
        return groupId(name);
    }

    private static void assignParallelRoutes(List<CslEdge> edges) {
        Map<String, List<Integer>> routes = new LinkedHashMap<>();
        for (int i = 0; i < edges.size(); i++) {
            CslEdge edge = edges.get(i);
            String first = edge.fromId().compareTo(edge.toId()) <= 0 ? edge.fromId() : edge.toId();
            String second = edge.fromId().compareTo(edge.toId()) <= 0 ? edge.toId() : edge.fromId();
            routes.computeIfAbsent(first + "\u0000" + second, ignored -> new ArrayList<>()).add(i);
        }
        for (List<Integer> indexes : routes.values()) {
            for (int route = 0; route < indexes.size(); route++) {
                int index = indexes.get(route);
                edges.set(index, edges.get(index).withRoute(route, indexes.size()));
            }
        }
    }

    private static String cardinality(CslCardinality value) {
        if (value.min() == 0 && value.max().isEmpty()) return "*";
        if (value.max().isPresent() && value.min() == value.max().getAsInt()) return Integer.toString(value.min());
        return value.min() + ".." + (value.max().isPresent() ? value.max().getAsInt() : "*");
    }

    private static String endLabel(CslEndpoint endpoint) {
        String multiplicity = cardinality(endpoint.multiplicity());
        return endpoint.roleName().map(role -> multiplicity + "  " + role).orElse(multiplicity);
    }

    private static String roleId(String value) { return "role::" + value; }
    private static String entityId(String value) { return "entity::" + value; }
    private static String groupId(String value) { return "group::" + value; }
    private static String enumId(String value) { return "enum::" + value; }
}
