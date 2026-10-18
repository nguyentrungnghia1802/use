package org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.vnu.sme.goal.verify.conformance.semantics.GoalTaskStatus;
import org.vnu.sme.goal.verify.conformance.semantics.IStarMarking;
import org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation;
import org.vnu.sme.goal.verify.conformance.semantics.QualityStatus;
import org.vnu.sme.goal.dsl.istar.mm.Actor;
import org.vnu.sme.goal.dsl.istar.mm.Agent;
import org.vnu.sme.goal.dsl.istar.mm.AndRefinement;
import org.vnu.sme.goal.dsl.istar.mm.Contribution;
import org.vnu.sme.goal.dsl.istar.mm.Dependency;
import org.vnu.sme.goal.dsl.istar.mm.Goal;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.vnu.sme.goal.dsl.istar.mm.GoalTaskElement;
import org.vnu.sme.goal.dsl.istar.mm.IntentionalElement;
import org.vnu.sme.goal.dsl.istar.mm.OrRefinement;
import org.vnu.sme.goal.dsl.istar.mm.Quality;
import org.vnu.sme.goal.dsl.istar.mm.Refinement;
import org.vnu.sme.goal.dsl.istar.mm.Resource;
import org.vnu.sme.goal.dsl.istar.mm.Role;
import org.vnu.sme.goal.dsl.istar.mm.Task;
import org.vnu.sme.goal.dsl.iscn.mm.AggregateMode;
import org.vnu.sme.goal.dsl.iscn.mm.AggregateResult;
import org.vnu.sme.goal.dsl.iscn.goalmodelinstance.mm.GoalModelInstance;
import org.vnu.sme.goal.dsl.iscn.mm.IStarScenarioModel;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioStmt;

/**
 * Translates a type-level i* model plus a scenario cast into one explicit instance-level
 * goal model, then applies the scenario fire/assign script to that instance model.
 *
 * <p>The important shape is intentionally direct:
 * <ol>
 *   <li>create one concrete actor box per {@code instance ... : ActorType};</li>
 *   <li>find root intentional elements per source actor;</li>
 *   <li>walk root-first/top-down, carrying a binding stack;</li>
 *   <li>AND/OR refinements replicate within the same actor instance's stack;</li>
 *   <li>each occurrence remembers its source element id and binding stack;</li>
 *   <li>only after the instance graph exists do we execute {@code fire}/{@code assign}.</li>
 * </ol>
 */
public final class GoalModelInstanceTranslator {

    @FunctionalInterface
    public interface InstanceContextMatcher {
        boolean sharesContext(String leftActorType, String leftInstanceId,
                              String rightActorType, String rightInstanceId);
    }

    private static final int MAX_DEPENDENCY_STEPS = 10_000;

    private GoalModelInstanceTranslator() {}

    /** P-CTX-01: structured, in-memory topology seam. Existing evaluate behavior is unchanged. */
    public static GoalModelInstanceTopology topology(GoalModel source, IStarScenarioModel scenario,
                                                      InstanceContextMatcher contextMatcher) {
        java.util.Objects.requireNonNull(contextMatcher, "EXPLICIT_CONTEXT_MATCHER_REQUIRED");
        if (!scenario.statements().isEmpty()) throw new IllegalArgumentException("TOPOLOGY_DOES_NOT_EXECUTE_SCRIPT");
        if (scenario.instances().isEmpty() && !source.getActors().isEmpty())
            throw new IllegalArgumentException("EXPLICIT_CAST_REQUIRED");
        Builder builder = new Builder(source, scenario, contextMatcher);
        builder.instantiateActors();
        builder.instantiateIntentionalTrees();
        builder.instantiateDependencyEndpoints();
        builder.instantiateContributions();
        builder.instantiateDependencies();
        GoalModel instanceModel = builder.buildModel();
        GoalModelInstance instance = new GoalModelInstance(instanceModel, builder.actorInstanceOf(), builder.elementInstanceOf());
        Map<String, GoalModelInstanceTopology.OccurrenceBinding> bindings = new LinkedHashMap<>();
        for (Occurrence occurrence : builder.occurrences.values()) {
            var stack = occurrence.stack().entries().stream().map(binding ->
                    new GoalModelInstanceTopology.ActorBinding(binding.actorType(), binding.instanceId())).toList();
            var binding = new GoalModelInstanceTopology.OccurrenceBinding(occurrence.id(), occurrence.sourceId(),
                    occurrence.ownerActorId(), stack);
            if (bindings.putIfAbsent(occurrence.id(), binding) != null)
                throw new IllegalArgumentException("CONTEXTUAL_OCCURRENCE_ID_COLLISION:" + occurrence.id());
        }
        if (bindings.size() != instanceModel.allElements().size())
            throw new IllegalArgumentException("CONTEXTUAL_TOPOLOGY_ID_COLLISION");
        return new GoalModelInstanceTopology(instance, bindings, builder.actorLabels(), builder.nodeLabels());
    }

    /** P-CTX-01: delegates to the existing dependency algorithm; admission owns its bounds. */
    public static IStarMarking propagateDependencies(GoalModel instanceModel, IStarMarking marking) {
        return saturateDependencies(instanceModel, marking);
    }

    public static GoalModelInstanceEvaluation evaluate(GoalModel source, IStarScenarioModel scenario) {
        return evaluate(source, scenario, (leftType, leftId, rightType, rightId) -> true);
    }

    public static GoalModelInstanceEvaluation evaluate(GoalModel source, IStarScenarioModel scenario,
                                                        InstanceContextMatcher contextMatcher) {
        Builder builder = new Builder(source, scenario, contextMatcher);
        builder.instantiateActors();
        builder.instantiateIntentionalTrees();
        builder.instantiateDependencyEndpoints();
        builder.instantiateContributions();
        builder.instantiateDependencies();
        GoalModel instanceModel = builder.buildModel();
        GoalModelInstance goalModelInstance = new GoalModelInstance(instanceModel,
                builder.actorInstanceOf(), builder.elementInstanceOf());

        IStarMarking marking = executeTrace(instanceModel, scenario, builder.occurrencesBySource(),
                builder.instanceActorType(), builder.instancesByType());
        List<AggregateResult> aggregates = evaluateAggregates(source, scenario, marking,
                builder.occurrencesBySource(), builder.instanceActorType(), builder.instancesByType());

        return new GoalModelInstanceEvaluation(goalModelInstance, marking, aggregates,
                builder.actorLabels(), builder.nodeLabels(), builder.firstScopeActorType());
    }

    private static final class Builder {
        private final GoalModel source;
        private final IStarScenarioModel scenario;
        private final InstanceContextMatcher contextMatcher;
        private final Map<String, Actor> actors = new LinkedHashMap<>();
        private final Map<String, String> ownerOf = new LinkedHashMap<>();
        private final Map<String, String> instanceActorType = new LinkedHashMap<>();
        private final Map<String, List<String>> instancesByType = new LinkedHashMap<>();
        private final Map<String, ActorBucket> buckets = new LinkedHashMap<>();
        private final Map<String, List<Refinement>> refinementsByParent = new LinkedHashMap<>();
        private final Set<String> childIds = new LinkedHashSet<>();
        private final Set<String> dependencyTargetIds = new LinkedHashSet<>();
        private final Map<OccurrenceKey, Occurrence> occurrences = new LinkedHashMap<>();
        private final Map<String, List<Occurrence>> occurrencesBySource = new LinkedHashMap<>();
        private final Map<String, String> actorLabels = new LinkedHashMap<>();
        private final Map<String, String> nodeLabels = new LinkedHashMap<>();
        private final Map<String, Actor> actorInstanceOf = new LinkedHashMap<>();
        private final Map<String, IntentionalElement> elementInstanceOf = new LinkedHashMap<>();
        private final Set<String> refinementKeys = new LinkedHashSet<>();
        private final Set<String> dependencyKeys = new LinkedHashSet<>();

        Builder(GoalModel source, IStarScenarioModel scenario, InstanceContextMatcher contextMatcher) {
            this.source = source;
            this.scenario = scenario;
            this.contextMatcher = contextMatcher;
            for (Actor actor : source.getActors()) {
                actors.put(actor.name(), actor);
                for (IntentionalElement e : actor.elements()) ownerOf.put(e.id(), actor.name());
                for (Refinement r : actor.refinements()) {
                    refinementsByParent.computeIfAbsent(r.parent(), ignored -> new ArrayList<>()).add(r);
                    switch (r) {
                        case AndRefinement and -> childIds.addAll(and.children());
                        case OrRefinement or -> childIds.add(or.child());
                    }
                }
            }
            for (Dependency d : source.getDependencies()) {
                if (d.dependeeElmt() != null) dependencyTargetIds.add(d.dependeeElmt());
            }
            if (scenario.instances().isEmpty()) {
                for (Actor actor : source.getActors()) addInstance(actor.name(), actor.name());
            } else {
                for (ScenarioInstance instance : scenario.instances()) {
                    addInstance(instance.name(), instance.actorType());
                }
            }
        }

        private void addInstance(String instanceId, String actorType) {
            instanceActorType.put(instanceId, actorType);
            instancesByType.computeIfAbsent(actorType, ignored -> new ArrayList<>()).add(instanceId);
        }

        void instantiateActors() {
            for (var e : instanceActorType.entrySet()) {
                String actorId = actorInstanceId(e.getValue(), e.getKey());
                buckets.put(actorId, new ActorBucket(actorId));
                actorLabels.put(actorId, e.getValue() + " [" + e.getKey() + "]");
                Actor sourceActor = actors.get(e.getValue());
                if (sourceActor != null) actorInstanceOf.put(actorId, sourceActor);
            }
        }

        void instantiateIntentionalTrees() {
            for (Actor actor : source.getActors()) {
                for (String instanceId : instancesByType.getOrDefault(actor.name(), List.of())) {
                    BindingStack stack = BindingStack.root(actor.name(), instanceId);
                    for (IntentionalElement root : rootElements(actor)) {
                        walk(root.id(), stack);
                    }
                }
            }
        }

        private List<IntentionalElement> rootElements(Actor actor) {
            List<IntentionalElement> roots = new ArrayList<>();
            for (IntentionalElement element : actor.elements()) {
                if (!childIds.contains(element.id()) && !dependencyTargetIds.contains(element.id())) roots.add(element);
            }
            return roots;
        }

        private Occurrence instantiateFromContext(String elementId, BindingStack stack) {
            if (refinementsByParent.containsKey(elementId)) return walk(elementId, stack);
            return occurrence(elementId, stack);
        }

        private Occurrence walk(String elementId, BindingStack stack) {
            Occurrence parent = occurrence(elementId, stack);
            for (Refinement r : refinementsByParent.getOrDefault(elementId, List.of())) {
                switch (r) {
                    case AndRefinement and -> {
                        List<String> children = new ArrayList<>();
                        for (String child : and.children()) {
                            children.add(walk(child, stack).id());
                        }
                        addRefinement(parent.ownerActorId(), new AndRefinement(parent.id(), children));
                    }
                    case OrRefinement or -> {
                        Occurrence child = walk(or.child(), stack);
                        addRefinement(parent.ownerActorId(), new OrRefinement(parent.id(), child.id()));
                    }
                }
            }
            return parent;
        }

        private void addRefinement(String ownerActorId, Refinement refinement) {
            String key = switch (refinement) {
                case AndRefinement and -> ownerActorId + "|AND|" + and.parent() + "|" + String.join(",", and.children());
                case OrRefinement or -> ownerActorId + "|OR|" + or.parent() + "|" + or.child();
            };
            if (refinementKeys.add(key)) bucket(ownerActorId).refinements.add(refinement);
        }

        void instantiateDependencyEndpoints() {
            for (Dependency d : source.getDependencies()) {
                List<String> ids = nonNull(d.dependerElmt(), d.dependeeElmt());
                if (ids.isEmpty()) continue;
                for (BindingStack stack : compatibleStacks(ids)) {
                    for (String id : ids) instantiateFromContext(id, stack);
                }
            }
        }

        private List<BindingStack> compatibleStacks(List<String> sourceIds) {
            List<BindingStack> out = new ArrayList<>();
            buildCompatibleStacks(sourceIds, 0, BindingStack.empty(), out);
            return out;
        }

        private void buildCompatibleStacks(List<String> sourceIds, int index, BindingStack current,
                                           List<BindingStack> out) {
            if (index == sourceIds.size()) {
                out.add(current);
                return;
            }
            List<Occurrence> candidates = occurrencesBySource.getOrDefault(sourceIds.get(index), List.of());
            if (candidates.isEmpty()) {
                buildCompatibleStacks(sourceIds, index + 1, current, out);
                return;
            }
            for (Occurrence occurrence : candidates) {
                BindingStack merged = current.merge(occurrence.stack(), contextMatcher);
                if (merged != null) buildCompatibleStacks(sourceIds, index + 1, merged, out);
            }
        }

        void instantiateContributions() {
            for (Actor actor : source.getActors()) {
                for (Contribution c : actor.contributions()) {
                    List<Occurrence> elementOccurrences = occurrencesBySource.getOrDefault(c.element(), List.of());
                    for (Occurrence sourceOccurrence : elementOccurrences) {
                        Occurrence quality = occurrence(c.quality(), sourceOccurrence.stack());
                        bucket(sourceOccurrence.ownerActorId()).contributions.add(
                                new Contribution(sourceOccurrence.id(), c.type(), quality.id()));
                    }
                }
            }
        }

        void instantiateDependencies() {
            for (Dependency d : source.getDependencies()) {
                List<String> ids = nonNull(d.dependerElmt(), d.dependeeElmt());
                for (BindingStack stack : compatibleStacks(ids)) {
                    String dependerActor = actorIdFor(d.depender(), stack);
                    String dependeeActor = actorIdFor(d.dependee(), stack);
                    String dependerElement = d.dependerElmt() == null ? null : instantiateFromContext(d.dependerElmt(), stack).id();
                    String dependeeElement = d.dependeeElmt() == null ? null : instantiateFromContext(d.dependeeElmt(), stack).id();
                    String dependumId = occurrenceId(d.dependum().id(), stack);
                    addDependency(dependerActor, new Dependency(dependerActor, dependerElement,
                            copyElement(d.dependum(), dependumId), dependeeActor, dependeeElement));
                }
            }
        }

        private void addDependency(String dependerActor, Dependency dependency) {
            String key = String.join("|",
                    dependency.depender(),
                    dependency.dependerElmt() == null ? "" : dependency.dependerElmt(),
                    dependency.dependum().id(),
                    dependency.dependee(),
                    dependency.dependeeElmt() == null ? "" : dependency.dependeeElmt());
            if (dependencyKeys.add(key)) bucket(dependerActor).dependencies.add(dependency);
        }

        private Occurrence occurrence(String sourceId, BindingStack stack) {
            IntentionalElement sourceElement = source.findElement(sourceId).orElseThrow();
            String ownerType = ownerOf.get(sourceId);
            String ownerInstance = ownerInstanceFor(ownerType, stack);
            String ownerActorId = actorInstanceId(ownerType, ownerInstance);
            BindingStack normalized = sourceElement instanceof Quality
                    ? BindingStack.root(ownerType, ownerInstance)
                    : stack.withOwner(ownerType, ownerInstance);
            OccurrenceKey key = new OccurrenceKey(sourceId, normalized.bindings());
            Occurrence existing = occurrences.get(key);
            if (existing != null) return existing;

            String id = occurrenceId(sourceId, normalized);
            Occurrence created = new Occurrence(id, sourceId, ownerActorId, normalized);
            occurrences.put(key, created);
            occurrencesBySource.computeIfAbsent(sourceId, ignored -> new ArrayList<>()).add(created);
            bucket(ownerActorId).elements.put(id, copyElement(sourceElement, id));
            elementInstanceOf.put(id, sourceElement);
            nodeLabels.put(id, sourceId + " [" + normalized.labelSuffix() + "]");
            return created;
        }

        private String ownerInstanceFor(String ownerType, BindingStack stack) {
            String exact = stack.get(ownerType);
            if (exact != null) return exact;
            String nearest = stack.nearestInstance();
            if (nearest != null && actorTypeOf(nearest).equals(ownerType)) return nearest;
            List<String> instances = instancesByType.getOrDefault(ownerType, List.of());
            if (!instances.isEmpty()) return instances.get(0);
            return ownerType;
        }

        private String actorIdFor(String actorType, BindingStack stack) {
            String instance = stack.get(actorType);
            if (instance == null) {
                List<String> instances = instancesByType.getOrDefault(actorType, List.of());
                instance = instances.isEmpty() ? actorType : instances.get(0);
            }
            return actorInstanceId(actorType, instance);
        }

        private String actorTypeOf(String instanceId) {
            return instanceActorType.getOrDefault(instanceId, instanceId);
        }

        private ActorBucket bucket(String actorId) {
            return buckets.computeIfAbsent(actorId, ActorBucket::new);
        }

        GoalModel buildModel() {
            GoalModel out = new GoalModel(source.getName() + "Scenario");
            for (var e : instanceActorType.entrySet()) {
                String actorType = e.getValue();
                String actorId = actorInstanceId(actorType, e.getKey());
                ActorBucket bucket = bucket(actorId);
                Actor sourceActor = actors.get(actorType);
                Actor actor = sourceActor instanceof Role
                        ? new Role(actorId, new ArrayList<>(bucket.elements.values()), bucket.refinements,
                                bucket.contributions, List.of(), List.of(), List.of())
                        : new Agent(actorId, new ArrayList<>(bucket.elements.values()), bucket.refinements,
                                bucket.contributions, List.of(), List.of(), List.of());
                out.addActor(actor);
                for (Dependency d : bucket.dependencies) out.addDependency(d);
            }
            return out;
        }

        Map<String, List<Occurrence>> occurrencesBySource() { return occurrencesBySource; }
        Map<String, String> instanceActorType() { return instanceActorType; }
        Map<String, List<String>> instancesByType() { return instancesByType; }
        Map<String, String> actorLabels() { return actorLabels; }
        Map<String, String> nodeLabels() { return nodeLabels; }
        Map<String, Actor> actorInstanceOf() { return actorInstanceOf; }
        Map<String, IntentionalElement> elementInstanceOf() { return elementInstanceOf; }

        Map<String, String> firstScopeActorType() {
            Map<String, String> out = new LinkedHashMap<>();
            for (var e : occurrences.entrySet()) {
                String first = e.getValue().stack().firstActorType();
                if (first != null) out.put(e.getValue().sourceId(), first);
            }
            return out;
        }
    }

    private static IStarMarking executeTrace(GoalModel model, IStarScenarioModel scenario,
                                             Map<String, List<Occurrence>> occurrencesBySource,
                                             Map<String, String> instanceActorType,
                                             Map<String, List<String>> instancesByType) {
        IStarMarking marking = IStarMarking.initial(model);
        for (ScenarioStmt stmt : scenario.statements()) {
            switch (stmt) {
                case ScenarioStmt.Fire f -> {
                    for (Occurrence occurrence : resolve(f.instanceId(), f.elementId(), f.objectInstanceId(),
                            occurrencesBySource, instanceActorType, instancesByType)) {
                        marking = IStarPropagation.fire(model, marking, occurrence.id());
                        marking = saturateDependencies(model, marking);
                    }
                }
                case ScenarioStmt.Assign a -> {
                    for (Occurrence occurrence : resolve(a.instanceId(), a.elementId(), null,
                            occurrencesBySource, instanceActorType, instancesByType)) {
                        marking = applyAssign(model, marking, occurrence.id(), a.statusValue());
                        marking = saturateDependencies(model, marking);
                    }
                }
                case ScenarioStmt.Aggregate ignored -> { }
            }
        }
        return IStarPropagation.closePending(model, saturateDependencies(model, marking));
    }

    private static List<Occurrence> resolve(String subjectInstanceId, String elementId, String objectInstanceId,
                                            Map<String, List<Occurrence>> occurrencesBySource,
                                            Map<String, String> instanceActorType,
                                            Map<String, List<String>> instancesByType) {
        List<Occurrence> candidates = occurrencesBySource.getOrDefault(elementId, List.of());
        if (subjectInstanceId == null && objectInstanceId == null) return candidates;

        List<Occurrence> out = new ArrayList<>();
        String subjectType = instanceActorType.get(subjectInstanceId);
        String objectType = instanceActorType.get(objectInstanceId);
        for (Occurrence occurrence : candidates) {
            boolean subjectMatches = subjectInstanceId == null
                    || occurrence.stack().matches(subjectType, subjectInstanceId)
                    || occurrence.ownerInstanceId(instanceActorType).equals(subjectInstanceId);
            boolean objectMatches = objectInstanceId == null
                    || occurrence.stack().matches(objectType, objectInstanceId);
            if (subjectMatches && objectMatches) out.add(occurrence);
        }
        if (!out.isEmpty()) return out;

        // Backward-compatible fallback: a qualified singleton action still targets the one
        // occurrence if the model has no bound copy for that instance.
        if (subjectInstanceId != null && objectInstanceId == null) {
            String actorType = instanceActorType.get(subjectInstanceId);
            if (instancesByType.getOrDefault(actorType, List.of()).size() == 1) return candidates;
        }
        return out;
    }

    private static IStarMarking applyAssign(GoalModel model, IStarMarking marking, String id, String statusValue) {
        return switch (statusValue) {
            case "Fulfilled" -> IStarPropagation.assignGoalTask(model, marking, id, GoalTaskStatus.FULFILLED);
            case "Pending" -> IStarPropagation.assignGoalTask(model, marking, id, GoalTaskStatus.PENDING);
            case "True" -> IStarPropagation.assignQuality(model, marking, id, QualityStatus.TRUE);
            case "False" -> IStarPropagation.assignQuality(model, marking, id, QualityStatus.FALSE);
            default -> marking;
        };
    }

    private static IStarMarking saturateDependencies(GoalModel model, IStarMarking marking) {
        IStarMarking current = marking;
        boolean changed = true;
        int steps = 0;
        while (changed && steps++ < MAX_DEPENDENCY_STEPS) {
            changed = false;
            for (Dependency d : model.getDependencies()) {
                if (d.dependerElmt() == null || d.dependeeElmt() == null) continue;
                if (current.goalTaskStatus(d.dependerElmt()) == GoalTaskStatus.FULFILLED) continue;
                if (current.goalTaskStatus(d.dependeeElmt()) == GoalTaskStatus.FULFILLED) {
                    current = IStarPropagation.assignGoalTask(model, current, d.dependerElmt(), GoalTaskStatus.FULFILLED);
                    changed = true;
                }
            }
        }
        return current;
    }

    private static List<AggregateResult> evaluateAggregates(GoalModel source, IStarScenarioModel scenario,
                                                            IStarMarking marking,
                                                            Map<String, List<Occurrence>> occurrencesBySource,
                                                            Map<String, String> instanceActorType,
                                                            Map<String, List<String>> instancesByType) {
        List<AggregateResult> out = new ArrayList<>();
        for (ScenarioStmt stmt : scenario.statements()) {
            if (!(stmt instanceof ScenarioStmt.Aggregate a)) continue;
            String actorType = a.actorType() != null ? a.actorType()
                    : firstScopeType(occurrencesBySource.getOrDefault(a.elementId(), List.of()));
            List<String> universe = actorType == null
                    ? new ArrayList<>(instanceActorType.keySet())
                    : instancesByType.getOrDefault(actorType, List.of());
            out.add(evaluateAggregate(a.label(), a.mode(), a.elementId(), actorType, universe,
                    source, marking, occurrencesBySource));
        }
        return out;
    }

    private static AggregateResult evaluateAggregate(String label, AggregateMode mode, String elementId,
                                                     String actorType, List<String> universe, GoalModel source,
                                                     IStarMarking marking,
                                                     Map<String, List<Occurrence>> occurrencesBySource) {
        IntentionalElement elem = source.findElement(elementId).orElse(null);
        List<String> satisfied = new ArrayList<>();
        for (String instanceId : universe) {
            boolean positive = false;
            for (Occurrence occurrence : occurrencesBySource.getOrDefault(elementId, List.of())) {
                if ((actorType == null || occurrence.stack().matches(actorType, instanceId))
                        && isPositive(elem, occurrence.id(), marking)) {
                    positive = true;
                    break;
                }
            }
            if (positive) satisfied.add(instanceId);
        }
        boolean holds = mode == AggregateMode.ALL ? satisfied.size() == universe.size() : !satisfied.isEmpty();
        return new AggregateResult(label, mode, elementId, holds, satisfied, universe);
    }

    private static boolean isPositive(IntentionalElement elem, String id, IStarMarking marking) {
        if (elem == null) return false;
        return switch (elem) {
            case Goal g -> marking.goalTaskStatus(id) == GoalTaskStatus.FULFILLED;
            case Task t -> marking.goalTaskStatus(id) == GoalTaskStatus.FULFILLED;
            case Quality q -> marking.qualityStatus(id) == QualityStatus.TRUE;
            case Resource r -> false;
        };
    }

    private static String firstScopeType(List<Occurrence> occurrences) {
        for (Occurrence occurrence : occurrences) {
            String first = occurrence.stack().firstActorType();
            if (first != null) return first;
        }
        return null;
    }

    private static List<String> nonNull(String... values) {
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value != null && !out.contains(value)) out.add(value);
        }
        return out;
    }

    private static String actorInstanceId(String actorType, String instanceId) {
        return actorType + "__" + instanceId;
    }

    private static String occurrenceId(String sourceId, BindingStack stack) {
        if (stack.bindings().isEmpty()) return sourceId;
        return sourceId + "__" + String.join("__", stack.bindings().values());
    }

    private static IntentionalElement copyElement(IntentionalElement elem, String id) {
        return switch (elem) {
            case Goal g -> new Goal(id, g.goalType(),
                    g.conditions().stream().findFirst().orElse(null));
            case Task t -> new Task(id,
                    t.preconditions().stream().findFirst().orElse(null),
                    t.postconditions().stream().findFirst().orElse(null));
            case Resource r -> new Resource(id);
            case Quality q -> new Quality(id);
        };
    }

    private record ActorBucket(String actorId, Map<String, IntentionalElement> elements,
                               List<Refinement> refinements, List<Contribution> contributions,
                               List<Dependency> dependencies) {
        ActorBucket(String actorId) {
            this(actorId, new LinkedHashMap<>(), new ArrayList<>(), new ArrayList<>(),
                    new ArrayList<>());
        }
    }

    private record Occurrence(String id, String sourceId, String ownerActorId, BindingStack stack) {
        String ownerInstanceId(Map<String, String> instanceActorType) {
            int marker = ownerActorId.lastIndexOf("__");
            return marker < 0 ? ownerActorId : ownerActorId.substring(marker + 2);
        }
    }

    private record OccurrenceKey(String sourceId, Map<String, String> bindings) {
        OccurrenceKey {
            bindings = Map.copyOf(bindings);
        }
    }

    private record BindingStack(List<Binding> entries) {
        BindingStack {
            entries = List.copyOf(entries);
        }

        static BindingStack empty() {
            return new BindingStack(List.of());
        }

        static BindingStack root(String actorType, String instanceId) {
            return empty().push(actorType, instanceId);
        }

        BindingStack push(String actorType, String instanceId) {
            List<Binding> next = new ArrayList<>(entries);
            next.add(new Binding(actorType, instanceId));
            return new BindingStack(next);
        }

        BindingStack withOwner(String actorType, String instanceId) {
            if (get(actorType) != null) return this;
            return push(actorType, instanceId);
        }

        BindingStack merge(BindingStack other, InstanceContextMatcher contextMatcher) {
            BindingStack merged = this;
            for (Binding binding : other.entries) {
                String current = merged.get(binding.actorType());
                if (current != null && !current.equals(binding.instanceId())) return null;
                if (current == null) {
                    for (Binding existing : merged.entries) {
                        if (!contextMatcher.sharesContext(existing.actorType(), existing.instanceId(),
                                binding.actorType(), binding.instanceId())) return null;
                    }
                    merged = merged.push(binding.actorType(), binding.instanceId());
                }
            }
            return merged;
        }

        String get(String actorType) {
            if (actorType == null) return null;
            for (int i = entries.size() - 1; i >= 0; i--) {
                Binding binding = entries.get(i);
                if (binding.actorType().equals(actorType)) return binding.instanceId();
            }
            return null;
        }

        boolean matches(String actorType, String instanceId) {
            return instanceId != null && instanceId.equals(get(actorType));
        }

        String nearestInstance() {
            return entries.isEmpty() ? null : entries.get(entries.size() - 1).instanceId();
        }

        String firstActorType() {
            return entries.isEmpty() ? null : entries.get(0).actorType();
        }

        Map<String, String> bindings() {
            Map<String, String> out = new LinkedHashMap<>();
            for (Binding binding : entries) out.put(binding.actorType(), binding.instanceId());
            return out;
        }

        String labelSuffix() {
            return String.join(",", bindings().values());
        }
    }

    private record Binding(String actorType, String instanceId) {}
}
