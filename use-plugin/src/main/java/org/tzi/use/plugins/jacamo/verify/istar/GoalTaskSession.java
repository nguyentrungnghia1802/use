package org.tzi.use.plugins.jacamo.verify.istar;

import org.vnu.sme.goal.dsl.istar_marking.mm.GoalModelInstanceTopology.ActorBinding;
import org.tzi.use.plugins.jacamo.verify.istar.GoalTaskSession.History.OccurrenceKey;
import org.vnu.sme.goal.verify.istar.IStarMarking;
import org.vnu.sme.goal.verify.istar.IStarMarking.TaskMarking;
import org.vnu.sme.goal.verify.istar.IStarMarking.GoalMarking;
import java.util.*;
import org.tzi.use.plugins.jacamo.verify.api.*;
import org.vnu.sme.goal.dsl.istar_marking.mm.*;
import org.vnu.sme.goal.dsl.istar_marking.mm.GoalModelInstanceTopology;
import org.vnu.sme.goal.dsl.istar_marking.parser.*;
import org.vnu.sme.goal.dsl.istar.mm.*;

/** Synchronous neutral recurrence session. A plugin owner serializes calls and owns observation quality. */
public final class GoalTaskSession implements AutoCloseable {
    private final String token=UUID.randomUUID().toString(),castRevision;
    private final OwnedGoalModel definition;
    private final OwnedGoalModel instantiatedDefinition;
    private final String definitionIdentity;
    private final GoalModelInstanceTopology topology;
    private final Set<String> derived;
    private final EngineSemanticsProfile profile;
    private final TopologyPolicy topologyPolicy;
    private final Map<String,Set<String>> requiredResources;
    private Map<String,GoalMarking> goals=new LinkedHashMap<>();
    private Map<String,TaskMarking> tasks=new LinkedHashMap<>();
    private Map<String,OccurrenceKey> keys=new LinkedHashMap<>();
    private long epoch,boundary;private boolean closed;
    public GoalTaskSession(GoalModel source,List<ScenarioInstance> cast,
            GoalModelInstanceTranslator.InstanceContextMatcher matcher,String castRevision) {
        this(source,cast,matcher,castRevision,EngineSemanticsProfile.USE4GOAL_OBSERVED_V1);
    }
    public GoalTaskSession(GoalModel source,List<ScenarioInstance> cast,
            GoalModelInstanceTranslator.InstanceContextMatcher matcher,String castRevision,EngineSemanticsProfile profile) {
        this(source,cast,matcher,castRevision,profile,TopologyPolicy.ORIGINAL_CONTEXT_PATHS);
    }
    public GoalTaskSession(GoalModel source,List<ScenarioInstance> cast,
            GoalModelInstanceTranslator.InstanceContextMatcher matcher,String castRevision,EngineSemanticsProfile profile,TopologyPolicy topologyPolicy) {
        this.profile=Objects.requireNonNull(profile);
        this.topologyPolicy=Objects.requireNonNull(topologyPolicy);
        var admitted=GoalModelAdmission.admit(source,cast,matcher,profile,topologyPolicy);definition=new OwnedGoalModel(source,profile);
        Objects.requireNonNull(castRevision);
        if(castRevision.isBlank())throw new IllegalArgumentException("EXPLICIT_CAST_REVISION_REQUIRED");
        this.castRevision=castRevision;
        // A policy change changes history ownership, even for identical M1 bytes.
        // Cast rebuilds may still seed exact stable keys under the same policy.
        definitionIdentity=definitionIdentity(definition.hash(),profile,topologyPolicy);
        topology=topologyPolicy==TopologyPolicy.ORIGINAL_CONTEXT_PATHS?GoalModelInstanceTranslator.topology(source,new IStarScenarioModel("live","<in-memory>",cast,List.of()),matcher):GoalModelTopology.singleOwner(source,cast,matcher);
        instantiatedDefinition=new OwnedGoalModel(topology.instance().model(),profile);
        if(topology.occurrences().size()>admitted.occurrenceUpperBound()||topology.occurrences().size()>profile.maxOccurrences())
            throw new IllegalStateException("ENGINE_INSTANTIATED_BOUND_EXCEEDED");
        var computed=new HashSet<String>();topology.occurrences().forEach((id,b)->{if(admitted.derivedSourceIds().contains(b.sourceElementId()))computed.add(id);});
        derived=Set.copyOf(computed);
        var resources=new LinkedHashMap<String,Set<String>>();
        for(var actor:source.getActors())for(var relation:actor.neededBys())
            resources.computeIfAbsent(relation.task(),k->new LinkedHashSet<>()).add(relation.resource());
        var frozen=new LinkedHashMap<String,Set<String>>();resources.forEach((k,v)->frozen.put(k,Set.copyOf(v)));
        requiredResources=Map.copyOf(frozen);
    }
    public EngineSemanticsProfile profile(){return profile;}
    public TopologyPolicy topologyPolicy(){return topologyPolicy;}
    /** Read-only admission result for the actual expanded graph. */
    public boolean isDerivedOccurrence(String id){requireOpen();assertDefinitions();return derived.contains(id);}
    public GoalModelInstanceTopology topology(){requireOpen();assertDefinitions();return topology;}
    public synchronized History checkpoint(){requireOpen();return new History(token,epoch,definitionIdentity,castRevision,boundary,goals,tasks,keys);}
    /** New neutral adapter seam, not an upstream API. A topology rebuild may keep
     * only exact structured occurrences. Different role stacks/episodes are ignored. */
    public synchronized void seedStableHistory(History previous,Map<String,String> episodes) {
        requireOpen();assertDefinitions();if(boundary!=0||!goals.isEmpty()||!tasks.isEmpty())throw new IllegalStateException("ENGINE_SEED_REQUIRES_FRESH_SESSION");
        if(!definitionIdentity.equals(previous.definitionHash()))throw new IllegalArgumentException("ENGINE_SEED_DEFINITION_MISMATCH");
        for(var entry:previous.keys().entrySet()) {
            var binding=topology.occurrences().get(entry.getKey());var key=entry.getValue();
            if(binding==null||!binding.sourceElementId().equals(key.sourceElementId())||!binding.stack().equals(key.bindingStack())||!key.episode().equals(episodes.get(entry.getKey())))continue;
            if(previous.goals().containsKey(entry.getKey()))goals.put(entry.getKey(),previous.goals().get(entry.getKey()));
            if(previous.tasks().containsKey(entry.getKey()))tasks.put(entry.getKey(),previous.tasks().get(entry.getKey()));
            if(goals.containsKey(entry.getKey())||tasks.containsKey(entry.getKey()))keys.put(entry.getKey(),key);
        }
    }
    private record LeafDefinition(String kind,String actor,String actorKind,GoalType goalType,List<String> pre,List<String> post,Set<String> resources) { }
    private record LeafImage(EngineSemanticsProfile profile,TopologyPolicy topologyPolicy,History history,Map<String,LeafDefinition> definitions) { }
    private LeafDefinition leafDefinition(String id) {
        if(derived.contains(id))return null;
        var binding=topology.occurrences().get(id);if(binding==null)return null;
        var model=definition.model();var element=model.findElement(binding.sourceElementId()).orElseThrow();
        var actor=model.findActor(model.ownerOf(element.id()).orElseThrow()).orElseThrow();
        if(element instanceof Goal goal)return new LeafDefinition(Goal.class.getName(),actor.name(),actor.getClass().getName(),goal.goalType(),goal.conditions().stream().map(IStarOclConstraint::oclBody).toList(),List.of(),Set.of());
        if(element instanceof Task task)return new LeafDefinition(Task.class.getName(),actor.name(),actor.getClass().getName(),null,task.preconditions().stream().map(IStarOclConstraint::oclBody).toList(),task.postconditions().stream().map(IStarOclConstraint::oclBody).toList(),requiredResources.getOrDefault(element.id(),Set.of()));
        return null;
    }
    private synchronized LeafImage leafImage() {
        requireOpen();assertDefinitions();var definitions=new HashMap<String,LeafDefinition>();
        for(String id:keys.keySet()){var leaf=leafDefinition(id);if(leaf!=null)definitions.put(id,leaf);}
        return new LeafImage(profile,topologyPolicy,checkpoint(),Map.copyOf(definitions));
    }
    /** Neutral adapter seam for a changed execution M1. Only identical leaf
     * contracts, resources, structured owners and caller-proven episodes move.
     * Derived/aggregate markings are recomputed; no source-specific rule lives
     * here. The original whole-definition seed fence remains unchanged. */
    public void seedCompatibleLeafHistory(GoalTaskSession previous,Map<String,String> episodes) {
        var image=Objects.requireNonNull(previous).leafImage();
        synchronized(this) {
            requireOpen();assertDefinitions();if(boundary!=0||!goals.isEmpty()||!tasks.isEmpty())throw new IllegalStateException("ENGINE_SEED_REQUIRES_FRESH_SESSION");
            if(profile!=image.profile()||topologyPolicy!=image.topologyPolicy())throw new IllegalArgumentException("ENGINE_SEED_POLICY_MISMATCH");
            for(var entry:image.history().keys().entrySet()) {
                String id=entry.getKey();var binding=topology.occurrences().get(id);var key=entry.getValue();var leaf=leafDefinition(id);
                if(binding==null||leaf==null||!leaf.equals(image.definitions().get(id))||!binding.sourceElementId().equals(key.sourceElementId())||!binding.stack().equals(key.bindingStack())||!key.episode().equals(episodes.get(id)))continue;
                if(image.history().goals().containsKey(id))goals.put(id,image.history().goals().get(id));
                if(image.history().tasks().containsKey(id))tasks.put(id,image.history().tasks().get(id));
                if(goals.containsKey(id)||tasks.containsKey(id))keys.put(id,key);
            }
        }
    }
    public synchronized void restore(History image) {
        requireOpen();if(!image.ownerToken().equals(token)||image.epoch()!=epoch||!image.definitionHash().equals(definitionIdentity)||!image.castRevision().equals(castRevision))
            throw new IllegalArgumentException("ENGINE_CHECKPOINT_OWNER_MISMATCH");
        if(image.boundary()<0)throw new IllegalArgumentException("ENGINE_CHECKPOINT_BOUNDARY_INVALID");
        var model=topology.instance().model();
        for(var entry:image.goals().entrySet()) {
            var element=model.findElement(entry.getKey()).orElse(null);
            if(!(element instanceof Goal goal)||entry.getValue().type()!=GoalMarking.initial(goal.goalType()).type())
                throw new IllegalArgumentException("ENGINE_CHECKPOINT_GOAL_TYPE_INVALID");
        }
        for(var entry:image.tasks().entrySet())if(!(model.findElement(entry.getKey()).orElse(null) instanceof Task)||entry.getValue().postSeen()&&!entry.getValue().preSeen())
            throw new IllegalArgumentException("ENGINE_CHECKPOINT_TASK_INVALID");
        var expectedKeys=new HashSet<String>(image.goals().keySet());expectedKeys.addAll(image.tasks().keySet());
        if(!expectedKeys.equals(image.keys().keySet()))throw new IllegalArgumentException("ENGINE_CHECKPOINT_KEYS_INVALID");
        for(var entry:image.keys().entrySet()) {
            var binding=topology.occurrences().get(entry.getKey());
            if(binding==null||!binding.sourceElementId().equals(entry.getValue().sourceElementId())||!binding.stack().equals(entry.getValue().bindingStack()))
                throw new IllegalArgumentException("ENGINE_CHECKPOINT_BINDING_INVALID");
        }
        goals=new LinkedHashMap<>(image.goals());tasks=new LinkedHashMap<>(image.tasks());keys=new LinkedHashMap<>(image.keys());boundary=image.boundary();
    }
    /** Atomically stages history: no field is replaced if validation or original propagation fails. */
    public synchronized StepResult step(Inputs input) {
        requireOpen();assertDefinitions();var model=topology.instance().model();
        validateResourceInputs(input);
        var nextGoals=new LinkedHashMap<>(goals);var nextTasks=new LinkedHashMap<>(tasks);
        var nextKeys=new LinkedHashMap<>(keys);
        var deferred=new HashSet<String>();var calls=new ArrayList<String>();
        for(var entry:input.goals().entrySet()) {
            String id=entry.getKey();var element=model.findElement(id).orElseThrow(()->new IllegalArgumentException("ENGINE_INPUT_TARGET_UNRESOLVED:"+id));
            if(!(element instanceof Goal goal)||derived.contains(id))throw new IllegalArgumentException("ENGINE_INPUT_NOT_OBSERVED_GOAL:"+id);
            var value=entry.getValue();var key=key(id,value.episode());
            GoalMarking previous=key.equals(nextKeys.get(id))?nextGoals.getOrDefault(id,GoalMarking.initial(goal.goalType())):GoalMarking.initial(goal.goalType());
            nextKeys.put(id,key);
            if(Boolean.FALSE.equals(value.active())){previous=GoalMarking.initial(goal.goalType());calls.add("GoalMarking.initial:known-deactivation");}
            else if(value.active()==null||value.condition()==null)deferred.add(id);
            else {previous=previous.update(value.active(),value.condition());calls.add("GoalMarking.update");}
            nextGoals.put(id,previous);
        }
        for(var entry:input.tasks().entrySet()) {
            String id=entry.getKey();var element=model.findElement(id).orElseThrow(()->new IllegalArgumentException("ENGINE_INPUT_TARGET_UNRESOLVED:"+id));
            if(!(element instanceof Task)||derived.contains(id))throw new IllegalArgumentException("ENGINE_INPUT_NOT_OBSERVED_TASK:"+id);
            var value=entry.getValue();var key=key(id,value.episode());
            TaskMarking previous=key.equals(nextKeys.get(id))?nextTasks.getOrDefault(id,TaskMarking.initial()):TaskMarking.initial();nextKeys.put(id,key);
            if(Boolean.FALSE.equals(value.required())){ /* Original required=false retains history; no fabricated pre/post. */ }
            else if(value.required()==null||value.pre()==null||value.post()==null||!resourcesReady(id,value.episode(),input))deferred.add(id);
            else {previous=previous.update(value.required(),value.pre(),value.post());calls.add("TaskMarking.update");}
            nextTasks.put(id,previous);
        }
        for(var element:model.allElements().values()) {
            if(element instanceof Goal goal&&!goal.conditions().isEmpty()&&!input.goals().containsKey(goal.id()))deferred.add(goal.id());
            if(element instanceof Task task&&!task.constraints().isEmpty()&&!input.tasks().containsKey(task.id()))deferred.add(task.id());
        }
        IStarMarking aggregate=IStarMarking.initial(model);
        for(var entry:nextGoals.entrySet())aggregate=aggregate.withGoalMarking(entry.getKey(),entry.getValue());
        for(var entry:nextTasks.entrySet())aggregate=aggregate.withTaskMarking(entry.getKey(),entry.getValue());
        aggregate=IStarMarking.closePending(model,aggregate);calls.add("IStarMarking.closePending");
        // The public assign API also invokes original saturation. This is a
        // derived copy only; status reconstruction never overwrites raw leaf history.
        if(!aggregate.goalTaskStatuses().isEmpty()) {
            String anchor=aggregate.goalTaskStatuses().keySet().iterator().next();
            aggregate=IStarMarking.assignGoalTask(model,aggregate,anchor,aggregate.goalTaskStatus(anchor));calls.add("IStarMarking.assignGoalTask:aggregate-only");
        }
        aggregate=GoalModelInstanceTranslator.propagateDependencies(model,aggregate);calls.add("GoalModelInstanceTranslator.propagateDependencies");
        aggregate=IStarMarking.closePending(model,aggregate);
        assertDefinitions();long nextBoundary=Math.addExact(boundary,1);
        goals=nextGoals;tasks=nextTasks;keys=nextKeys;boundary=nextBoundary;
        return new StepResult(boundary,checkpoint(),aggregate,deferred,calls);
    }
    private void validateResourceInputs(Inputs input) {
        for(var key:input.resources().keySet()) {
            var binding=topology.occurrences().get(key.taskOccurrenceId());
            if(binding==null||!input.tasks().containsKey(key.taskOccurrenceId())
                    ||!requiredResources.getOrDefault(binding.sourceElementId(),Set.of()).contains(key.resourceSourceId()))
                throw new IllegalArgumentException("ENGINE_RESOURCE_BINDING_UNRESOLVED:"+key);
        }
    }
    private boolean resourcesReady(String task,String episode,Inputs input) {
        String source=topology.occurrences().get(task).sourceElementId();
        for(String resource:requiredResources.getOrDefault(source,Set.of())) {
            var value=input.resources().get(new Inputs.ResourceKey(task,resource));
            if(value==null||!Objects.equals(episode,value.episode())||!Boolean.TRUE.equals(value.available()))return false;
        }
        return true;
    }
    private OccurrenceKey key(String id,String episode){var binding=topology.occurrences().get(id);return new OccurrenceKey(binding.sourceElementId(),binding.stack(),episode);}
    private static String definitionIdentity(String hash,EngineSemanticsProfile profile,TopologyPolicy topologyPolicy) {
        if(profile==EngineSemanticsProfile.USE4GOAL_OBSERVED_V1&&topologyPolicy==TopologyPolicy.ORIGINAL_CONTEXT_PATHS)return hash;
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                    (profile.name()+":"+(topologyPolicy==TopologyPolicy.ORIGINAL_CONTEXT_PATHS?"":topologyPolicy.name()+":")+hash).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private void assertDefinitions(){definition.assertUnchanged();instantiatedDefinition.assertUnchanged();}
    public synchronized void reset(){requireOpen();epoch++;boundary=0;goals.clear();tasks.clear();keys.clear();}
    private void requireOpen(){if(closed)throw new IllegalStateException("ENGINE_SESSION_CLOSED");}
    @Override public synchronized void close(){if(!closed){goals.clear();tasks.clear();keys.clear();closed=true;}}


    /** Explicit topology identity, independent of source language and transport. */
    public enum TopologyPolicy {
        ORIGINAL_CONTEXT_PATHS,
        /** Exactly one proved instance per actor in one context; no path copies. */
        SINGLE_OWNER_CONTEXT_V1
    }


    /** Raw original history, derived original aggregate and deferred inputs are deliberately separate. */
    public record StepResult(long boundary,History history,IStarMarking aggregate,Set<String> deferred,
            List<String> originalApiCalls) {
        public StepResult {deferred=Set.copyOf(deferred);originalApiCalls=List.copyOf(originalApiCalls);}
    }

    /** Null Boolean means no known input. Coverage diagnostics stay outside the Boolean marking rules. */
    public record Inputs(Map<String,GoalInput> goals,Map<String,TaskInput> tasks,
                               Map<ResourceKey,ResourceInput> resources) {
        public record GoalInput(String episode,Boolean active,Boolean condition) { }
        public record TaskInput(String episode,Boolean required,Boolean pre,Boolean post) { }
        /** External resource evidence is scoped to the exact task occurrence and source relation. */
        public record ResourceKey(String taskOccurrenceId,String resourceSourceId) {
            public ResourceKey {requireText(taskOccurrenceId);requireText(resourceSourceId);}
        }
        public record ResourceInput(String episode,Boolean available,String evidenceRef) {
            public ResourceInput {
                requireText(episode);
                if(available!=null)requireText(evidenceRef);
            }
        }
        public Inputs(Map<String,GoalInput> goals,Map<String,TaskInput> tasks) {
            this(goals,tasks,Map.of());
        }
        public Inputs {goals=Map.copyOf(goals);tasks=Map.copyOf(tasks);resources=Map.copyOf(resources);}
        private static void requireText(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("ENGINE_RESOURCE_IDENTITY_REQUIRED");}
        public static Inputs empty(){return new Inputs(Map.of(),Map.of());}
    }

    /** Immutable staged history only. No native model/state, socket, or UI ownership. */
    public record History(String ownerToken,long epoch,String definitionHash,String castRevision,long boundary,
            Map<String,GoalMarking> goals,Map<String,TaskMarking> tasks,
            Map<String,OccurrenceKey> keys) {
        public History {goals=Map.copyOf(goals);tasks=Map.copyOf(tasks);keys=Map.copyOf(keys);}


        /** Complete structured occurrence identity within a run/program owner, including its activation episode. */
        public record OccurrenceKey(String sourceElementId,List<ActorBinding> bindingStack,String episode) {
            public OccurrenceKey {
                Objects.requireNonNull(sourceElementId);bindingStack=List.copyOf(bindingStack);
                if(episode==null||episode.isBlank())throw new IllegalArgumentException("EXPLICIT_EPISODE_REQUIRED");
            }
        }
    }
}
