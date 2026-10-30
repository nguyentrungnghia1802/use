package org.tzi.use.plugins.jacamo.ui.use4goal.istar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.vnu.sme.goal.dsl.istar.mm.Actor;
import org.vnu.sme.goal.dsl.istar.mm.AndRefinement;
import org.vnu.sme.goal.dsl.istar.mm.Contribution;
import org.vnu.sme.goal.dsl.istar.mm.Dependency;
import org.vnu.sme.goal.dsl.istar.mm.Goal;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalTaskElement;
import org.vnu.sme.goal.dsl.istar.mm.IStarOclConstraint;
import org.vnu.sme.goal.dsl.istar.mm.IntentionalElement;
import org.vnu.sme.goal.dsl.istar.mm.NeededBy;
import org.vnu.sme.goal.dsl.istar.mm.OrRefinement;
import org.vnu.sme.goal.dsl.istar.mm.Qualification;
import org.vnu.sme.goal.dsl.istar.mm.Quality;
import org.vnu.sme.goal.dsl.istar.mm.Refinement;
import org.vnu.sme.goal.dsl.istar.mm.Resource;
import org.vnu.sme.goal.dsl.istar.mm.Role;
import org.vnu.sme.goal.dsl.istar.mm.Task;

/**
 * MM (GoalModel) -> IStarLayout. Pure computation — no Swing/AWT import.
 *
 * SR mode: actors placed left to right as a boundary containing their owned
 * elements, stacked top to bottom (today's grid layout). The actor's circle
 * glyph is drawn by the renderer at the boundary's top-center — it is not a
 * separate node.
 *
 * SD mode: actors placed left to right as bare circles, no owned elements —
 * only Dependency edges are emitted.
 */
public final class IStarLayoutBuilder {

    private static final int MARGIN    = 30;
    private static final int ACTOR_PAD = 22;
    private static final int ACTOR_HDR = 28;
    private static final int HGAP      = 56;
    private static final int ELEM_GAP  = 12;
    private static final int GW = 126, GH = 36;
    private static final int TW = 108, TH = 44;
    private static final int RW = 108, RH = 26;
    private static final int QW = 118, QH = 46;
    private static final int DEP_Y_GAP = 92;

    /** Diameter of the actor circle glyph — shared by SD (standalone) and SR (boundary badge). */
    public static final int ACTOR_D = 64;

    private IStarLayoutBuilder() {}

    public static IStarLayout build(GoalModel model, IStarViewMode mode) {
        return build(model, mode, false);
    }

    public static IStarLayout build(GoalModel model, IStarViewMode mode, boolean showOclContracts) {
        return mode == IStarViewMode.SD ? buildSD(model) : buildSR(model, showOclContracts);
    }

    // ── SD: actor circles + dependencies only ──────────────────────────────────

    private static IStarLayout buildSD(GoalModel model) {
        Map<String, IStarNode> nodes = new LinkedHashMap<>();
        if (model == null) return new IStarLayout(nodes, List.of(), 900, 600);

        int ax = MARGIN;
        for (Actor actor : model.getActors()) {
            IStarNode aNode = new IStarNode(actor.name(), actor.name(), IStarNodeKind.ACTOR, null, actor instanceof Role);
            aNode.x = ax;
            aNode.y = MARGIN;
            aNode.w = actorDiameter(actor.name());
            aNode.h = aNode.w;
            nodes.put(aNode.id, aNode);
            ax += aNode.w + HGAP;
        }

        List<IStarEdge> edges = new ArrayList<>();
        addAssociations(model, edges);
        int depIndex = 0;
        for (Dependency dep : model.getDependencies()) {
            String depNodeId = addDependumNode(nodes, model, dep, depIndex++, dep.depender(), dep.dependee(), MARGIN + ACTOR_D + 26);
            edges.add(new IStarEdge(dep.depender(), depNodeId, IStarEdgeKind.DEPENDENCY, null, null));
            edges.add(new IStarEdge(depNodeId, dep.dependee(), IStarEdgeKind.DEPENDENCY, null, null));
        }

        int totalW = ax + MARGIN;
        int totalH = nodes.values().stream().mapToInt(n -> n.y + n.h).max().orElse(MARGIN + ACTOR_D) + MARGIN;
        return new IStarLayout(nodes, edges, Math.max(totalW, 900), Math.max(totalH, 600));
    }

    // ── SR: actor boundary containing owned elements ────────────────────────────

    private static IStarLayout buildSR(GoalModel model, boolean showOclContracts) {
        Map<String, IStarNode> nodes = new LinkedHashMap<>();
        if (model == null) return new IStarLayout(nodes, List.of(), 900, 600);
        // A boundary's circle is centered ABOVE its top edge. Leave generic
        // label-dependent headroom so capability-role names are not clipped.
        int top = MARGIN + model.getActors().stream().mapToInt(a->Math.max(ACTOR_D,a.name().length()*8+32)/2).max().orElse(ACTOR_D/2);

        int ax = MARGIN;
        for (Actor actor : model.getActors()) {
            IStarNode aNode = new IStarNode(actor.name(), actor.name(), IStarNodeKind.ACTOR, null,
                    actor instanceof Role, true, false);
            aNode.x = ax;
            aNode.y = top;
            nodes.put(aNode.id, aNode);

            int cy   = top + ACTOR_HDR + ACTOR_PAD;
            int maxW = 0;
            List<IStarNode> children = new ArrayList<>();

            for (IntentionalElement elem : actor.elements()) {
                IStarNodeKind kind;
                int w, h;
                switch (elem) {
                    case Goal     g -> { kind = IStarNodeKind.GOAL;     w = GW; h = GH; }
                    case Task     t -> { kind = IStarNodeKind.TASK;     w = TW; h = TH; }
                    case Resource r -> { kind = IStarNodeKind.RESOURCE; w = RW; h = RH; }
                    case Quality  q -> { kind = IStarNodeKind.QUALITY;  w = QW; h = QH; }
                }
                List<String> contracts = showOclContracts && elem instanceof GoalTaskElement gt
                        ? contractLines(gt) : List.of();
                String goalType = elem instanceof Goal goal && goal.goalType() != null
                        ? displayGoalType(goal.goalType()) : null;
                if (goalType != null) h += 14;
                if (!contracts.isEmpty()) {
                    w = Math.max(w, 250);
                    h += 18 + contracts.size() * 14;
                }
                IStarNode n = new IStarNode(elem.id(), elem.id(), kind, actor.name(), false,
                        false, false, contracts, goalType);
                n.w = w; n.h = h;
                n.x = ax + ACTOR_PAD;
                n.y = cy;
                cy += n.h + ELEM_GAP;
                maxW = Math.max(maxW, n.w);
                children.add(n);
                nodes.put(n.id, n);
            }

            aNode.w = Math.max(maxW + ACTOR_PAD * 2, actorDiameter(actor.name()) + ACTOR_PAD);
            aNode.h = cy - MARGIN + ACTOR_PAD;
            for (IStarNode cn : children) cn.x = ax + (aNode.w - cn.w) / 2;

            ax += aNode.w + HGAP;
        }

        List<IStarEdge> edges = new ArrayList<>();
        addAssociations(model, edges);
        for (Actor actor : model.getActors()) {
            for (Refinement ref : actor.refinements()) {
                switch (ref) {
                    case AndRefinement and -> {
                        for (String childId : and.children())
                            edges.add(new IStarEdge(childId, and.parent(), IStarEdgeKind.AND_REFINE, "AND", null));
                    }
                    case OrRefinement or ->
                        edges.add(new IStarEdge(or.child(), or.parent(), IStarEdgeKind.OR_REFINE, "OR", null));
                }
            }
            for (Contribution c : actor.contributions())
                edges.add(new IStarEdge(c.element(), c.quality(), IStarEdgeKind.CONTRIBUTION, c.type().label(), c.type()));
            for (Qualification q : actor.qualifications())
                edges.add(new IStarEdge(q.quality(), q.element(), IStarEdgeKind.QUALIFICATION, null, null));
            for (NeededBy nb : actor.neededBys())
                edges.add(new IStarEdge(nb.resource(), nb.task(), IStarEdgeKind.NEEDED_BY, null, null));
        }
        for (Dependency dep : model.getDependencies()) {
            // Anchor to explicit boundary-opening elements. If omitted, fall back
            // to the first intentional element inside each actor, not the actor boundary.
            String fromId = dep.dependerElmt() != null ? dep.dependerElmt() : firstElementId(model, dep.depender());
            String toId   = dep.dependeeElmt() != null ? dep.dependeeElmt() : firstElementId(model, dep.dependee());
            String depNodeId = addDependumNode(nodes, model, dep, edges.size(), fromId, toId, 0);
            edges.add(new IStarEdge(fromId, depNodeId, IStarEdgeKind.DEPENDENCY, null, null));
            edges.add(new IStarEdge(depNodeId, toId, IStarEdgeKind.DEPENDENCY, null, null));
        }

        int totalW = ax + MARGIN;
        int maxH = nodes.values().stream()
                .filter(n -> n.kind == IStarNodeKind.ACTOR)
                .mapToInt(n -> n.y + n.h).max().orElse(500) + MARGIN;

        return new IStarLayout(nodes, edges, Math.max(totalW, 900), Math.max(maxH, 600));
    }

    private static void addAssociations(GoalModel model, List<IStarEdge> edges) {
        for(Actor actor:model.getActors())for(var relation:actor.associations())
            edges.add(new IStarEdge(actor.name(), relation.target(), IStarEdgeKind.ASSOCIATION, relation.kind().toString().toLowerCase().replace('_','-'), null));
    }

    private static String addDependumNode(Map<String, IStarNode> nodes, GoalModel model, Dependency dep,
                                          int index, String fromId, String toId, int yFallback) {
        IStarNodeKind kind = dependumKind(model, dep);
        int[] size = sizeFor(kind);
        IStarNode from = nodes.get(fromId);
        IStarNode to = nodes.get(toId);

        IntentionalElement dependum = dep.dependum();
        IStarNode n = new IStarNode("dependum:" + index + ":" + dependum.id(), dependum.id(),
                kind, null, false, false, true);
        n.w = size[0];
        n.h = size[1];
        if (from != null && to != null) {
            int cx = (from.x + from.w / 2 + to.x + to.w / 2) / 2;
            int cy = (from.y + from.h / 2 + to.y + to.h / 2) / 2;
            n.x = cx - n.w / 2;
            n.y = cy - n.h / 2;
        } else {
            n.x = MARGIN + index * (n.w + ELEM_GAP);
            n.y = yFallback > 0 ? yFallback + index * DEP_Y_GAP : MARGIN + index * DEP_Y_GAP;
        }
        nodes.put(n.id, n);
        return n.id;
    }

    private static IStarNodeKind dependumKind(GoalModel model, Dependency dep) {
        return switch (dep.dependum()) {
            case Goal ignored -> IStarNodeKind.GOAL;
            case Task ignored -> IStarNodeKind.TASK;
            case Resource ignored -> IStarNodeKind.RESOURCE;
            case Quality ignored -> IStarNodeKind.QUALITY;
        };
    }

    private static String firstElementId(GoalModel model, String actorName) {
        if (model != null) {
            for (Actor actor : model.getActors()) {
                if (actor.name().equals(actorName) && !actor.elements().isEmpty()) {
                    return actor.elements().get(0).id();
                }
            }
        }
        return actorName;
    }

    private static int actorDiameter(String label) {
        int textWidth = label == null ? 0 : label.length() * 8;
        return Math.max(ACTOR_D, textWidth + 24);
    }

    private static String displayGoalType(org.vnu.sme.goal.dsl.istar.mm.GoalType type) {
        String lower = type.name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static List<String> contractLines(GoalTaskElement element) {
        List<String> result = new ArrayList<>();
        if (element instanceof Goal goal) {
            goal.conditions().forEach(c -> result.add(contractLine("condition", c)));
        } else if (element instanceof Task task) {
            task.preconditions().forEach(c -> result.add(contractLine("pre", c)));
            task.postconditions().forEach(c -> result.add(contractLine("post", c)));
        }
        return List.copyOf(result);
    }

    private static String contractLine(String label, IStarOclConstraint constraint) {
        String body = constraint.oclBody() == null ? "" : constraint.oclBody().replaceAll("\\s+", " ").trim();
        return label + ": " + body;
    }

    private static int[] sizeFor(IStarNodeKind kind) {
        return switch (kind) {
            case GOAL -> new int[]{GW, GH};
            case TASK -> new int[]{TW, TH};
            case RESOURCE -> new int[]{RW, RH};
            case QUALITY -> new int[]{QW, QH};
            case ACTOR -> new int[]{ACTOR_D, ACTOR_D};
        };
    }
}
