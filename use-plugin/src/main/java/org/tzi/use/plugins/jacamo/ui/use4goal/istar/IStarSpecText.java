package org.tzi.use.plugins.jacamo.ui.use4goal.istar;

import java.util.List;
import java.util.Map;

import org.vnu.sme.goal.dsl.istar.mm.Actor;
import org.vnu.sme.goal.dsl.istar.mm.Agent;
import org.vnu.sme.goal.dsl.istar.mm.AndRefinement;
import org.vnu.sme.goal.dsl.istar.mm.Contribution;
import org.vnu.sme.goal.dsl.istar.mm.Dependency;
import org.vnu.sme.goal.dsl.istar.mm.Goal;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.vnu.sme.goal.dsl.istar.mm.IntentionalElement;
import org.vnu.sme.goal.dsl.istar.mm.GoalTaskElement;
import org.vnu.sme.goal.dsl.istar.mm.NeededBy;
import org.vnu.sme.goal.dsl.istar.mm.OrRefinement;
import org.vnu.sme.goal.dsl.istar.mm.Qualification;
import org.vnu.sme.goal.dsl.istar.mm.Quality;
import org.vnu.sme.goal.dsl.istar.mm.Refinement;
import org.vnu.sme.goal.dsl.istar.mm.Resource;
import org.vnu.sme.goal.dsl.istar.mm.Task;

/**
 * Renders an SR diagram's content as prose/text instead of a picture: same information an
 * {@link IStarView} window draws (actors, their intentional elements with badges, refinements,
 * contributions, dependencies), so someone can read the scenario instead of looking at it.
 * Takes exactly what {@code IStarView.openUseDesktop}/{@code openPopupWindow} take (a
 * {@link GoalModel} plus badge/label overrides), so it works for a plain .istar view, an .iscn
 * scenario diagram, or a .use+.soil trace checkpoint alike.
 */
public final class IStarSpecText {

    private IStarSpecText() {}

    public static String generate(GoalModel model, Map<String, NodeBadge> badges,
                                  Map<String, String> actorLabels, Map<String, String> nodeLabels) {
        StringBuilder sb = new StringBuilder();
        sb.append("Specification of \"").append(model.getName()).append("\"\n");
        sb.append("=".repeat(("Specification of \"" + model.getName() + "\"").length())).append("\n\n");

        for (Actor actor : model.getActors()) {
            String kind = (actor instanceof Agent) ? "agent" : "role";
            sb.append("Actor ").append(label(actorLabels, actor.name())).append(" (").append(kind).append(")\n");

            for (IntentionalElement e : actor.elements()) {
                sb.append("  ").append(kindOf(e)).append(" ").append(label(nodeLabels, e.id()));
                NodeBadge badge = badges.get(e.id());
                if (badge != null) sb.append(" = ").append(badge.tooltip());
                sb.append("\n");
                if (e instanceof Goal goal) {
                    appendContracts(sb, "condition", goal.conditions());
                } else if (e instanceof Task task) {
                    appendContracts(sb, "pre", task.preconditions());
                    appendContracts(sb, "post", task.postconditions());
                }
            }

            if (!actor.refinements().isEmpty()) {
                sb.append("  refinements:\n");
                for (Refinement r : actor.refinements()) sb.append("    ").append(refinementLine(r, nodeLabels)).append("\n");
            }
            if (!actor.contributions().isEmpty()) {
                sb.append("  contributions:\n");
                for (Contribution c : actor.contributions()) {
                    sb.append("    ").append(label(nodeLabels, c.element())).append(" --").append(c.type()).append("--> ")
                            .append(label(nodeLabels, c.quality())).append("\n");
                }
            }
            if (!actor.qualifications().isEmpty()) {
                sb.append("  qualifications:\n");
                for (Qualification q : actor.qualifications()) {
                    sb.append("    ").append(label(nodeLabels, q.quality())).append(" qualifies ")
                            .append(label(nodeLabels, q.element())).append("\n");
                }
            }
            if (!actor.neededBys().isEmpty()) {
                sb.append("  needed-by:\n");
                for (NeededBy nb : actor.neededBys()) {
                    sb.append("    ").append(label(nodeLabels, nb.resource())).append(" needed-by ")
                            .append(label(nodeLabels, nb.task())).append("\n");
                }
            }
            sb.append("\n");
        }

        List<Dependency> deps = model.getDependencies();
        if (!deps.isEmpty()) {
            sb.append("Dependencies\n");
            for (Dependency d : deps) {
                sb.append("  ").append(label(actorLabels, d.depender()));
                if (d.dependerElmt() != null) sb.append(".").append(label(nodeLabels, d.dependerElmt()));
                sb.append(" --").append(kindOf(d.dependum())).append(" ")
                        .append(label(nodeLabels, d.dependum().id())).append("--> ")
                        .append(label(actorLabels, d.dependee()));
                if (d.dependeeElmt() != null) sb.append(".").append(label(nodeLabels, d.dependeeElmt()));
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private static void appendContracts(StringBuilder sb, String label,
                                        List<org.vnu.sme.goal.dsl.istar.mm.IStarOclConstraint> contracts) {
        contracts.forEach(c -> sb.append("    ").append(label).append(" {[ ")
                .append(c.oclBody()).append(" ]}\n"));
    }

    private static String kindOf(IntentionalElement e) {
        return switch (e) {
            case Goal g -> "goal";
            case Task t -> "task";
            case Quality q -> "quality";
            case Resource r -> "resource";
        };
    }

    private static String refinementLine(Refinement r, Map<String, String> nodeLabels) {
        return switch (r) {
            case AndRefinement and -> label(nodeLabels, and.parent()) + " <-AND- "
                    + and.children().stream().map(c -> label(nodeLabels, c)).reduce((a, b) -> a + ", " + b).orElse("");
            case OrRefinement or -> label(nodeLabels, or.parent()) + " <-OR- " + label(nodeLabels, or.child());
        };
    }

    private static String label(Map<String, String> labels, String id) {
        return labels.getOrDefault(id, id);
    }
}
