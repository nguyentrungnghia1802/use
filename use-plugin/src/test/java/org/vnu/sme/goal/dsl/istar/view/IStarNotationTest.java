package org.vnu.sme.goal.dsl.istar.view;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.vnu.sme.goal.dsl.istar.mm.Agent;
import org.vnu.sme.goal.dsl.istar.mm.AndRefinement;
import org.vnu.sme.goal.dsl.istar.mm.Dependency;
import org.vnu.sme.goal.dsl.istar.mm.GenericActor;
import org.vnu.sme.goal.dsl.istar.mm.Goal;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.vnu.sme.goal.dsl.istar.mm.OrRefinement;
import org.vnu.sme.goal.dsl.istar.mm.Role;
import org.vnu.sme.goal.dsl.istar.mm.Task;

import static org.junit.jupiter.api.Assertions.*;

class IStarNotationTest {
    @Test
    void refinementsPointFromChildToParentAndDependenciesUseDeclaredEndpoints() {
        GoalModel model = notationModel();
        var sr = IStarLayoutBuilder.build(model, IStarViewMode.SR);
        assertTrue(sr.edges.contains(new IStarEdge("Prepare", "Ready", IStarEdgeKind.AND_REFINE, null, null)));
        assertTrue(sr.edges.contains(new IStarEdge("Revise", "Resolved", IStarEdgeKind.OR_REFINE, null, null)));
        assertDependency(sr, "Resolved", "Review");
        assertDependency(IStarLayoutBuilder.build(model, IStarViewMode.SD), "Owner", "Reviewer");
    }

    @Test
    void actorLevelDependencyDoesNotAttachToAnArbitraryGoal() {
        GoalModel model = notationModel();
        model.getDependencies().getFirst().setDependerElmt(null);
        assertDependency(IStarLayoutBuilder.build(model, IStarViewMode.SR), "Owner", "Review");
    }

    @Test
    void aLongActorHeaderLeavesSpaceAboveItsIntentionalElements() {
        var model = new GoalModel("Notation");
        String name = "OrganizationAgentWithLongName";
        model.addActor(new Agent(name, List.of(new Goal("Ready", null)),
                List.of(), List.of(), List.of(), List.of(), List.of()));
        var layout = IStarLayoutBuilder.build(model, IStarViewMode.SR);
        var actor = layout.nodes.get(name);
        assertTrue(layout.nodes.get("Ready").y > actor.y + IStarLayoutBuilder.actorDiameter(name) / 2);
    }

    @Test
    void roleAndAgentDrawDifferentMarksAndActorIsAnUnmarkedCircle() {
        var nodes = IStarLayoutBuilder.build(notationModel(), IStarViewMode.SD).nodes;
        assertMarks(nodes.get("Owner"), false, true);
        assertMarks(nodes.get("Reviewer"), true, false);
        assertMarks(nodes.get("External"), false, false);
    }

    private static GoalModel notationModel() {
        var model = new GoalModel("Notation");
        model.addActor(new Role("Owner", List.of(new Goal("Ready", null), new Goal("Resolved", null),
                new Task("Prepare"), new Task("Revise")),
                List.of(new AndRefinement("Ready", List.of("Prepare")), new OrRefinement("Resolved", "Revise")),
                List.of(), List.of(), List.of(), List.of()));
        model.addActor(new Agent("Reviewer", List.of(new Task("Review")), List.of(), List.of(), List.of(), List.of(), List.of()));
        model.addActor(new GenericActor("External", List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        model.addDependency(new Dependency("Owner", "Resolved", new Goal("Reviewed", null), "Reviewer", "Review"));
        return model;
    }

    private static void assertDependency(IStarLayout layout, String depender, String dependee) {
        var dependum = layout.nodes.values().stream().filter(node -> node.dependum).findFirst().orElseThrow();
        assertEquals(IStarNodeKind.GOAL, dependum.kind);
        assertTrue(layout.edges.contains(new IStarEdge(depender, dependum.id, IStarEdgeKind.DEPENDENCY, null, null)));
        assertTrue(layout.edges.contains(new IStarEdge(dependum.id, dependee, IStarEdgeKind.DEPENDENCY, null, null)));
    }

    private static void assertMarks(IStarNode item, boolean chord, boolean arc) {
        var image = new BufferedImage(140, 140, BufferedImage.TYPE_INT_RGB);
        var node = new IStarDiagramNode(item, new IStarDiagramOptions(), new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        node.setPosition(10, 10);
        node.setExactBounds(item.w, item.h);
        var graphic = image.createGraphics();
        try {
            graphic.setColor(Color.WHITE);
            graphic.fillRect(0, 0, image.getWidth(), image.getHeight());
            node.onDraw(graphic);
        } finally {
            graphic.dispose();
        }
        var circle = node.actorCircle();
        int x = (int) circle.getCenterX();
        assertEquals(chord, inkAt(image, x, (int) Math.round(circle.getY() + circle.getHeight() * 0.31)), item.label);
        assertEquals(arc, inkAt(image, x, (int) Math.round(circle.getY() + circle.getHeight() * 0.91)), item.label);
    }

    private static boolean inkAt(BufferedImage image, int x, int y) {
        for (int sample = y - 1; sample <= y + 1; sample++) {
            if (image.getRGB(x, sample) == Color.BLACK.getRGB()) return true;
        }
        return false;
    }
}
