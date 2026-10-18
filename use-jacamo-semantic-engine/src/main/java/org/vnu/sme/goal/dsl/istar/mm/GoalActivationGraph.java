package org.vnu.sme.goal.dsl.istar.mm;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parent relation used solely for activation inheritance. Besides refinement parenthood,
 * an incoming social dependency makes the depender element the activation parent of the
 * dependee goal; such a goal is only an actor-local root, not an activation root.
 */
public final class GoalActivationGraph {
    private final Map<String, String> parentByChild;
    private final Set<String> roots;

    private GoalActivationGraph(Map<String, String> parentByChild, Set<String> roots) {
        this.parentByChild = Map.copyOf(parentByChild);
        this.roots = Set.copyOf(roots);
    }

    public static GoalActivationGraph of(GoalModel model) {
        Map<String, String> parents = new LinkedHashMap<>();
        for (Actor actor : model.getActors()) {
            for (Refinement refinement : actor.refinements()) {
                for (String child : children(refinement)) parents.putIfAbsent(child, refinement.parent());
            }
        }
        for (Dependency dependency : model.getDependencies()) {
            if (dependency.dependerElmt() == null || dependency.dependeeElmt() == null) continue;
            boolean dependerCanDemand = model.findElement(dependency.dependerElmt())
                    .filter(GoalTaskElement.class::isInstance).isPresent();
            boolean dependeeIsExecutable = model.findElement(dependency.dependeeElmt())
                    .filter(GoalTaskElement.class::isInstance).isPresent();
            if (dependerCanDemand && dependeeIsExecutable) {
                parents.putIfAbsent(dependency.dependeeElmt(), dependency.dependerElmt());
            }
        }
        Set<String> roots = new LinkedHashSet<>();
        model.allElements().values().stream().filter(Goal.class::isInstance)
                .map(IntentionalElement::id).filter(id -> !parents.containsKey(id)).forEach(roots::add);
        return new GoalActivationGraph(parents, roots);
    }

    public Optional<String> parentOf(String childId) {
        return Optional.ofNullable(parentByChild.get(childId));
    }

    public boolean isRoot(String goalId) {
        return roots.contains(goalId);
    }

    public Set<String> roots() {
        return roots;
    }

    public static java.util.List<String> children(Refinement refinement) {
        return switch (refinement) {
            case AndRefinement and -> and.children();
            case OrRefinement or -> java.util.List.of(or.child());
        };
    }
}
