package org.vnu.sme.goal.dsl.istar.mm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Semantic invariants of the i* MM.
 *
 * <p>The parser guarantees concrete syntax. This validator guarantees that the resulting
 * {@link GoalModel} is a well-formed i* model before any diagram, scenario, or transformation
 * consumes it.
 */
public final class GoalModelValidator {

    private GoalModelValidator() {}

    public static List<String> validate(GoalModel model) {
        List<String> errors = new ArrayList<>();
        validateElementIdUniqueness(model, errors);
        validateRefinements(model, errors);
        validateGoalTypeRefinements(model, errors);
        validateRefinementKindConsistency(model, errors);
        validateSingleRefinementParent(model, errors);
        validateRefinementAcyclic(model, errors);
        validateContributions(model, errors);
        validateQualifications(model, errors);
        validateNeededBy(model, errors);
        validateAssociations(model, errors);
        validateDependencies(model, errors);
        validateRefinementParentsAreNotDependencyEndpoints(model, errors);
        validateOclContracts(model, errors);
        return List.copyOf(errors);
    }

    private static void validateOclContracts(GoalModel model, List<String> errors) {
        for (Actor actor : model.getActors()) {
            for (IntentionalElement element : actor.elements()) {
                if (element instanceof Goal goal) {
                    validateOclBodies(goal.id(), "condition", goal.conditions(), errors);
                } else if (element instanceof Task task) {
                    validateOclBodies(task.id(), "pre", task.preconditions(), errors);
                    validateOclBodies(task.id(), "post", task.postconditions(), errors);
                }
            }
        }
    }

    private static void validateOclBodies(String elementId, String slot,
                                          List<IStarOclConstraint> contracts,
                                          List<String> errors) {
        for (IStarOclConstraint contract : contracts) {
            if (contract.oclBody() == null || contract.oclBody().isBlank()) {
                errors.add("semantic: element '" + elementId + "' has an empty '" + slot
                        + "' OCL contract");
            }
        }
    }

    private static void validateElementIdUniqueness(GoalModel model, List<String> errors) {
        Map<String, String> firstOwner = new HashMap<>();
        for (Actor actor : model.getActors()) {
            for (IntentionalElement e : actor.elements()) {
                String prevOwner = firstOwner.putIfAbsent(e.id(), actor.name());
                if (prevOwner != null) {
                    errors.add("semantic: element id '" + e.id() + "' is declared more than once (actors '"
                            + prevOwner + "' and '" + actor.name()
                            + "') — element ids must be unique across the model");
                }
            }
        }
    }

    private static void validateRefinements(GoalModel model, List<String> errors) {
        for (Actor actor : model.getActors()) {
            for (Refinement ref : actor.refinements()) {
                if (ref instanceof AndRefinement and && and.children().size() < 2) {
                    errors.add("semantic: and-refine '" + and.parent()
                            + "' in actor '" + actor.name()
                            + "' must have at least 2 children (found "
                            + and.children().size() + ")");
                }
                validateRefinementEnd(model, actor, ref.parent(), "parent", ref, errors);
                for (String child : refinementChildren(ref)) {
                    validateRefinementEnd(model, actor, child, "child", ref, errors);
                }
            }
        }
    }

    private static void validateRefinementEnd(GoalModel model, Actor actor, String elementId,
                                              String role, Refinement ref, List<String> errors) {
        Optional<IntentionalElement> element = model.findElement(elementId);
        if (element.isEmpty()) {
            errors.add("semantic: refinement '" + refinementLabel(ref) + "' in actor '" + actor.name()
                    + "' has unknown " + role + " element '" + elementId + "'");
            return;
        }
        if (model.ownerOf(elementId).filter(actor.name()::equals).isEmpty()) {
            errors.add("semantic: refinement '" + refinementLabel(ref) + "' in actor '" + actor.name()
                    + "' references " + role + " '" + elementId + "' owned by actor '"
                    + model.ownerOf(elementId).orElse("<none>")
                    + "' — a refinement must stay within a single actor (use 'depend' to cross actors)");
        }
        if (!(element.get() instanceof Goal || element.get() instanceof Task)) {
            errors.add("semantic: refinement '" + refinementLabel(ref) + "' in actor '" + actor.name()
                    + "' has " + role + " '" + elementId + "' of kind " + kindOf(element.get())
                    + " — refinements can only connect goals and tasks");
        }
    }

    private static List<String> refinementChildren(Refinement ref) {
        return switch (ref) {
            case AndRefinement and -> and.children();
            case OrRefinement or -> List.of(or.child());
        };
    }

    private static void validateGoalTypeRefinements(GoalModel model, List<String> errors) {
        for (Actor actor : model.getActors()) {
            for (Refinement ref : actor.refinements()) {
                Goal parent = model.findElement(ref.parent())
                        .filter(Goal.class::isInstance).map(Goal.class::cast).orElse(null);
                if (parent == null || parent.goalType() == null) continue;
                for (String childId : refinementChildren(ref)) {
                    Goal child = model.findElement(childId)
                            .filter(Goal.class::isInstance).map(Goal.class::cast).orElse(null);
                    if (child == null || child.goalType() == null) continue;
                    if (!GoalTypeRefinementPolicy.permits(parent.goalType(), child.goalType())) {
                        errors.add("semantic: " + child.goalType() + " goal '" + child.id()
                                + "' cannot refine " + parent.goalType() + " goal '" + parent.id()
                                + "' via " + kindTag(ref) + " — allowed child goal types are "
                                + GoalTypeRefinementPolicy.allowedChildren(parent.goalType()));
                    }
                }
            }
        }
    }

    private static void validateRefinementKindConsistency(GoalModel model, List<String> errors) {
        Map<String, Set<String>> kindsByParent = new LinkedHashMap<>();
        for (Actor actor : model.getActors()) {
            for (Refinement ref : actor.refinements()) {
                kindsByParent.computeIfAbsent(ref.parent(), k -> new LinkedHashSet<>()).add(kindTag(ref));
            }
        }
        kindsByParent.forEach((parent, kinds) -> {
            if (kinds.size() > 1) {
                errors.add("semantic: element '" + parent + "' is decomposed by more than one refinement kind "
                        + kinds + " — an element must be refined by exactly one kind (AND or OR)");
            }
        });
    }

    private static void validateSingleRefinementParent(GoalModel model, List<String> errors) {
        Map<String, String> parentByChild = new LinkedHashMap<>();
        for (Actor actor : model.getActors()) {
            for (Refinement refinement : actor.refinements()) {
                for (String child : refinementChildren(refinement)) {
                    String previous = parentByChild.putIfAbsent(child, refinement.parent());
                    if (previous != null && !previous.equals(refinement.parent())) {
                        errors.add("semantic: element '" + child + "' refines both '" + previous
                                + "' and '" + refinement.parent()
                                + "' — activation inheritance requires exactly one refinement parent");
                    }
                }
            }
        }
    }

    private static String kindTag(Refinement ref) {
        return switch (ref) {
            case AndRefinement ignored -> "AND";
            case OrRefinement ignored -> "OR";
        };
    }

    private static void validateRefinementAcyclic(GoalModel model, List<String> errors) {
        Map<String, List<String>> childToParents = new LinkedHashMap<>();
        for (Actor actor : model.getActors()) {
            for (Refinement ref : actor.refinements()) {
                for (String child : refinementChildren(ref)) {
                    childToParents.computeIfAbsent(child, k -> new ArrayList<>()).add(ref.parent());
                }
            }
        }
        Map<String, Integer> color = new HashMap<>();
        for (String node : childToParents.keySet()) {
            if (color.getOrDefault(node, 0) == 0 && hasCycle(node, childToParents, color)) {
                errors.add("semantic: refinement cycle detected involving element '" + node + "'");
                break;
            }
        }
    }

    private static boolean hasCycle(String node, Map<String, List<String>> edges, Map<String, Integer> color) {
        color.put(node, 1);
        for (String next : edges.getOrDefault(node, List.of())) {
            int c = color.getOrDefault(next, 0);
            if (c == 1) return true;
            if (c == 0 && hasCycle(next, edges, color)) return true;
        }
        color.put(node, 2);
        return false;
    }

    private static void validateContributions(GoalModel model, List<String> errors) {
        for (Actor actor : model.getActors()) {
            for (Contribution c : actor.contributions()) {
                model.findElement(c.element()).ifPresentOrElse(source -> {
                    if (!sameOwner(model, actor, c.element())) {
                        errors.add("semantic: contribution source '" + c.element() + "' in actor '" + actor.name()
                                + "' is owned by '" + model.ownerOf(c.element()).orElse("<none>") + "'");
                    }
                }, () -> errors.add("semantic: contribution in actor '" + actor.name()
                        + "' has unknown source element '" + c.element() + "'"));
                model.findElement(c.quality()).ifPresentOrElse(target -> {
                    if (!(target instanceof Quality)) {
                        errors.add("semantic: contribution '" + c.element() + " > " + c.type().label().toLowerCase()
                                + " " + c.quality() + "' in actor '" + actor.name()
                                + "' must target a quality, but '" + c.quality() + "' is a " + kindOf(target));
                    }
                }, () -> errors.add("semantic: contribution in actor '" + actor.name()
                        + "' has unknown target quality '" + c.quality() + "'"));
                if (c.element().equals(c.quality())) {
                    errors.add("semantic: quality '" + c.quality() + "' cannot contribute to itself");
                }
            }
        }
    }

    private static void validateQualifications(GoalModel model, List<String> errors) {
        for (Actor actor : model.getActors()) {
            for (Qualification q : actor.qualifications()) {
                model.findElement(q.quality()).ifPresentOrElse(source -> {
                    if (!(source instanceof Quality)) {
                        errors.add("semantic: qualification in actor '" + actor.name()
                                + "' must originate from a quality, but '" + q.quality()
                                + "' is a " + kindOf(source));
                    }
                }, () -> errors.add("semantic: qualification in actor '" + actor.name()
                        + "' has unknown quality '" + q.quality() + "'"));
                model.findElement(q.element()).ifPresentOrElse(target -> {
                    if (!(target instanceof Goal || target instanceof Task || target instanceof Resource)) {
                        errors.add("semantic: qualification '" + q.quality() + " > qualifies " + q.element()
                                + "' in actor '" + actor.name()
                                + "' must target a goal, task or resource, but '" + q.element()
                                + "' is a " + kindOf(target));
                    }
                }, () -> errors.add("semantic: qualification has unknown target element '" + q.element() + "'"));
            }
        }
    }

    private static void validateNeededBy(GoalModel model, List<String> errors) {
        for (Actor actor : model.getActors()) {
            for (NeededBy nb : actor.neededBys()) {
                model.findElement(nb.resource()).ifPresentOrElse(source -> {
                    if (!(source instanceof Resource)) {
                        errors.add("semantic: needed-by in actor '" + actor.name()
                                + "' must originate from a resource, but '" + nb.resource()
                                + "' is a " + kindOf(source));
                    }
                }, () -> errors.add("semantic: needed-by in actor '" + actor.name()
                        + "' has unknown source resource '" + nb.resource() + "'"));
                model.findElement(nb.task()).ifPresentOrElse(target -> {
                    if (!(target instanceof Task)) {
                        errors.add("semantic: needed-by '" + nb.resource() + " > needed-by " + nb.task()
                                + "' in actor '" + actor.name() + "' must target a task, but '"
                                + nb.task() + "' is a " + kindOf(target));
                    }
                }, () -> errors.add("semantic: needed-by has unknown target task '" + nb.task() + "'"));
            }
        }
    }

    private static void validateAssociations(GoalModel model, List<String> errors) {
        Map<AssocKind, Map<String, List<String>>> graphs = new HashMap<>();
        for (Actor actor : model.getActors()) {
            for (Association a : actor.associations()) {
                String relation = a.kind() == AssocKind.IS_A ? "is-a" : "participates-in";
                if (a.actor().equals(a.target())) {
                    errors.add("semantic: actor '" + a.actor() + "' cannot " + relation + " itself");
                    continue;
                }
                if (model.findActor(a.actor()).isEmpty())
                    errors.add("semantic: unknown actor '" + a.actor() + "' in '" + relation + "' relation");
                if (model.findActor(a.target()).isEmpty())
                    errors.add("semantic: unknown actor '" + a.target() + "' in '" + relation + "' relation");
                graphs.computeIfAbsent(a.kind(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(a.actor(), k -> new ArrayList<>()).add(a.target());
            }
        }
        graphs.forEach((kind, graph) -> {
            Map<String, Integer> color = new HashMap<>();
            for (String node : graph.keySet()) {
                if (color.getOrDefault(node, 0) == 0 && hasCycle(node, graph, color)) {
                    errors.add("semantic: actor " + (kind == AssocKind.IS_A ? "is-a" : "participates-in")
                            + " cycle detected involving actor '" + node + "'");
                    break;
                }
            }
        });
    }

    private static void validateDependencies(GoalModel model, List<String> errors) {
        for (Dependency d : model.getDependencies()) {
            validateActor(model, d.depender(), "depender", d, errors);
            validateActor(model, d.dependee(), "dependee", d, errors);
            validateEndpointElement(model, d.depender(), d.dependerElmt(), "depender", d, errors);
            validateEndpointElement(model, d.dependee(), d.dependeeElmt(), "dependee", d, errors);
            validateDependum(model, d, errors);

            if (d.depender().equals(d.dependee())) {
                errors.add("semantic: dependency '" + dependencyLabel(d)
                        + "' has the same actor '" + d.depender() + "' as both depender and dependee");
            }
        }
    }

    /**
     * A refinement parent is an internal rationale node. Dependencies are
     * actor-boundary interfaces and therefore may only attach to leaf
     * intentional elements, on both the depender and dependee side.
     */
    private static void validateRefinementParentsAreNotDependencyEndpoints(
            GoalModel model, List<String> errors) {
        Set<String> refinementParents = new LinkedHashSet<>();
        for (Actor actor : model.getActors()) {
            for (Refinement refinement : actor.refinements()) {
                model.findElement(refinement.parent())
                        .filter(GoalTaskElement.class::isInstance)
                        .ifPresent(element -> refinementParents.add(element.id()));
            }
        }
        for (Dependency dependency : model.getDependencies()) {
            rejectRefinementParentEndpoint(dependency.dependerElmt(), "depender",
                    dependency, refinementParents, errors);
            rejectRefinementParentEndpoint(dependency.dependeeElmt(), "dependee",
                    dependency, refinementParents, errors);
        }
    }

    private static void rejectRefinementParentEndpoint(
            String elementId, String endpointRole, Dependency dependency,
            Set<String> refinementParents, List<String> errors) {
        if (elementId == null || !refinementParents.contains(elementId)) return;
        errors.add("semantic: dependency '" + dependencyLabel(dependency)
                + "' uses refinement parent '" + elementId + "' as its " + endpointRole
                + " endpoint — dependencies may only attach to leaf intentional elements");
    }

    private static void validateActor(GoalModel model, String actorId, String role,
                                      Dependency d, List<String> errors) {
        if (model.findActor(actorId).isEmpty()) {
            errors.add("semantic: dependency '" + dependencyLabel(d) + "' has unknown " + role
                    + " actor '" + actorId + "'");
        }
    }

    private static void validateEndpointElement(GoalModel model, String actorId, String elementId,
                                                String role, Dependency d, List<String> errors) {
        if (elementId == null) return;
        Optional<Actor> actor = model.findActor(actorId);
        Optional<IntentionalElement> element = model.findElement(elementId);
        if (element.isEmpty()) {
            errors.add("semantic: dependency '" + dependencyLabel(d) + "' has unknown " + role
                    + " endpoint element '" + elementId + "'");
            return;
        }
        if (actor.isPresent() && model.ownerOf(elementId).filter(actorId::equals).isEmpty()) {
            errors.add("semantic: dependency '" + dependencyLabel(d) + "' has " + role
                    + " endpoint '" + actorId + "." + elementId + "', but element is owned by '"
                    + model.ownerOf(elementId).orElse("<none>") + "'");
        }
    }

    private static void validateDependum(GoalModel model, Dependency d, List<String> errors) {
        IntentionalElement dependum = d.dependum();
        if (dependum == null) {
            errors.add("semantic: dependency '" + dependencyLabel(d)
                    + "' must composition-own exactly one dependum intentional element");
        } else if (dependum.id() == null || dependum.id().isBlank()) {
            errors.add("semantic: dependency '" + dependencyLabel(d)
                    + "' owns a dependum with an empty id");
        }
    }

    private static boolean sameOwner(GoalModel model, Actor actor, String elementId) {
        return model.ownerOf(elementId).filter(actor.name()::equals).isPresent();
    }

    private static String refinementLabel(Refinement ref) {
        return switch (ref) {
            case AndRefinement and -> and.children() + " > " + and.parent();
            case OrRefinement or -> or.child() + " > or " + or.parent();
        };
    }

    private static String kindOf(IntentionalElement element) {
        if (element instanceof Goal) return "goal";
        if (element instanceof Task) return "task";
        if (element instanceof Resource) return "resource";
        if (element instanceof Quality) return "quality";
        return element.getClass().getSimpleName();
    }

    private static String dependencyLabel(Dependency d) {
        return d.depender() + endpointSuffix(d.dependerElmt()) + " -> "
                + (d.dependum() == null ? "<missing-dependum>"
                        : kindOf(d.dependum()) + " " + d.dependum().id()) + " -> "
                + d.dependee() + endpointSuffix(d.dependeeElmt());
    }

    private static String endpointSuffix(String elementId) {
        return elementId == null ? "" : "." + elementId;
    }
}
