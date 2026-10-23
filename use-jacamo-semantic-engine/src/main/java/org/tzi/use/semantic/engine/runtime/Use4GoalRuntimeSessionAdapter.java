package org.tzi.use.semantic.engine.runtime;

import java.util.*;
import org.tzi.use.semantic.engine.api.*;
import org.vnu.sme.goal.dsl.iscn.mm.*;
import org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.*;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.verify.conformance.semantics.*;

/** Synchronous neutral recurrence session. A plugin owner serializes calls and owns observation quality. */
public final class Use4GoalRuntimeSessionAdapter implements AutoCloseable {
    private final String token=UUID.randomUUID().toString(),castRevision;
    private final OwnedGoalModel definition;
    private final OwnedGoalModel instantiatedDefinition;
    private final String definitionIdentity;
    private final GoalModelInstanceTopology topology;
    private final Set<String> derived;
    private final EngineSemanticsProfile profile;
    private final EngineTopologyPolicy topologyPolicy;
    private final Map<String,Set<String>> requiredResources;
    private Map<String,GoalMarking> goals=new LinkedHashMap<>();
    private Map<String,TaskMarking> tasks=new LinkedHashMap<>();
    private Map<String,QualityStatus> qualities=new LinkedHashMap<>();
    private Map<String,EngineOccurrenceKey> keys=new LinkedHashMap<>();
    private long epoch,boundary;private boolean closed;
    public Use4GoalRuntimeSessionAdapter(GoalModel source,List<ScenarioInstance> cast,
            GoalModelInstanceTranslator.InstanceContextMatcher matcher,String castRevision) {
        this(source,cast,matcher,castRevision,EngineSemanticsProfile.USE4GOAL_OBSERVED_V1);
    }
    public Use4GoalRuntimeSessionAdapter(GoalModel source,List<ScenarioInstance> cast,
            GoalModelInstanceTranslator.InstanceContextMatcher matcher,String castRevision,EngineSemanticsProfile profile) {
        this(source,cast,matcher,castRevision,profile,EngineTopologyPolicy.ORIGINAL_CONTEXT_PATHS);
    }
    public Use4GoalRuntimeSessionAdapter(GoalModel source,List<ScenarioInstance> cast,
            GoalModelInstanceTranslator.InstanceContextMatcher matcher,String castRevision,EngineSemanticsProfile profile,EngineTopologyPolicy topologyPolicy) {
        this.profile=Objects.requireNonNull(profile);
        this.topologyPolicy=Objects.requireNonNull(topologyPolicy);
        var admitted=RuntimeModelAdmission.admit(source,cast,matcher,profile);definition=new OwnedGoalModel(source,profile);
        Objects.requireNonNull(castRevision);
        if(castRevision.isBlank())throw new IllegalArgumentException("EXPLICIT_CAST_REVISION_REQUIRED");
        this.castRevision=castRevision;
        // A policy change changes history ownership, even for identical M1 bytes.
        // Cast rebuilds may still seed exact stable keys under the same policy.
        definitionIdentity=definitionIdentity(definition.hash(),profile,topologyPolicy);
        topology=topologyPolicy==EngineTopologyPolicy.ORIGINAL_CONTEXT_PATHS?GoalModelInstanceTranslator.topology(source,new IStarScenarioModel("live","<in-memory>",cast,List.of()),matcher):SingleOwnerContextTopology.build(source,cast,matcher);
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
    public EngineTopologyPolicy topologyPolicy(){return topologyPolicy;}
    public GoalModelInstanceTopology topology(){requireOpen();assertDefinitions();return topology;}
    public synchronized EngineCheckpoint checkpoint(){requireOpen();return new EngineCheckpoint(token,epoch,definitionIdentity,castRevision,boundary,goals,tasks,qualities,keys);}
    /** New neutral adapter seam, not an upstream API. A topology rebuild may keep
     * only exact structured occurrences. Different role stacks/episodes are ignored. */
    public synchronized void seedStableHistory(EngineCheckpoint previous,Map<String,String> episodes) {
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
    private record LeafImage(EngineSemanticsProfile profile,EngineTopologyPolicy topologyPolicy,EngineCheckpoint history,Map<String,LeafDefinition> definitions) { }
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
    public void seedCompatibleLeafHistory(Use4GoalRuntimeSessionAdapter previous,Map<String,String> episodes) {
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
    public synchronized void restore(EngineCheckpoint image) {
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
        for(var id:image.qualities().keySet())if(!(model.findElement(id).orElse(null) instanceof Quality))throw new IllegalArgumentException("ENGINE_CHECKPOINT_QUALITY_INVALID");
        var expectedKeys=new HashSet<String>(image.goals().keySet());expectedKeys.addAll(image.tasks().keySet());
        if(!expectedKeys.equals(image.keys().keySet()))throw new IllegalArgumentException("ENGINE_CHECKPOINT_KEYS_INVALID");
        for(var entry:image.keys().entrySet()) {
            var binding=topology.occurrences().get(entry.getKey());
            if(binding==null||!binding.sourceElementId().equals(entry.getValue().sourceElementId())||!binding.stack().equals(entry.getValue().bindingStack()))
                throw new IllegalArgumentException("ENGINE_CHECKPOINT_BINDING_INVALID");
        }
        goals=new LinkedHashMap<>(image.goals());tasks=new LinkedHashMap<>(image.tasks());qualities=new LinkedHashMap<>(image.qualities());keys=new LinkedHashMap<>(image.keys());boundary=image.boundary();
    }
    /** Atomically stages history: no field is replaced if validation or original propagation fails. */
    public synchronized EngineStepResult step(EngineInputs input) {
        requireOpen();assertDefinitions();var model=topology.instance().model();
        validateResourceInputs(input);
        var nextGoals=new LinkedHashMap<>(goals);var nextTasks=new LinkedHashMap<>(tasks);
        var nextQualities=new LinkedHashMap<>(qualities);var nextKeys=new LinkedHashMap<>(keys);
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
        for(var entry:input.qualities().entrySet()) {
            if(!(model.findElement(entry.getKey()).orElse(null) instanceof Quality)||derived.contains(entry.getKey()))throw new IllegalArgumentException("ENGINE_INPUT_NOT_OBSERVED_QUALITY:"+entry.getKey());
            if(entry.getValue()==null)deferred.add(entry.getKey());
            else nextQualities.put(entry.getKey(),entry.getValue()?QualityStatus.TRUE:QualityStatus.FALSE);
        }
        for(var element:model.allElements().values()) {
            if(element instanceof Goal goal&&!goal.conditions().isEmpty()&&!input.goals().containsKey(goal.id()))deferred.add(goal.id());
            if(element instanceof Task task&&!task.constraints().isEmpty()&&!input.tasks().containsKey(task.id()))deferred.add(task.id());
        }
        IStarMarking aggregate=IStarMarking.initial(model);
        for(var entry:nextGoals.entrySet())aggregate=aggregate.withGoalMarking(entry.getKey(),entry.getValue());
        for(var entry:nextTasks.entrySet())aggregate=aggregate.withTaskMarking(entry.getKey(),entry.getValue());
        for(var entry:nextQualities.entrySet())aggregate=aggregate.withQuality(entry.getKey(),entry.getValue());
        aggregate=IStarPropagation.closePending(model,aggregate);calls.add("IStarPropagation.closePending");
        // The public assign API also invokes original saturation. This is a
        // derived copy only; status reconstruction never overwrites raw leaf history.
        if(!aggregate.goalTaskStatuses().isEmpty()) {
            String anchor=aggregate.goalTaskStatuses().keySet().iterator().next();
            aggregate=IStarPropagation.assignGoalTask(model,aggregate,anchor,aggregate.goalTaskStatus(anchor));calls.add("IStarPropagation.assignGoalTask:aggregate-only");
        } else if(!aggregate.qualityStatuses().isEmpty()) {
            String anchor=aggregate.qualityStatuses().keySet().iterator().next();
            aggregate=IStarPropagation.assignQuality(model,aggregate,anchor,aggregate.qualityStatus(anchor));calls.add("IStarPropagation.assignQuality:aggregate-only");
        }
        aggregate=GoalModelInstanceTranslator.propagateDependencies(model,aggregate);calls.add("GoalModelInstanceTranslator.propagateDependencies");
        aggregate=IStarPropagation.closePending(model,aggregate);
        assertDefinitions();long nextBoundary=Math.addExact(boundary,1);
        goals=nextGoals;tasks=nextTasks;qualities=nextQualities;keys=nextKeys;boundary=nextBoundary;
        return new EngineStepResult(boundary,checkpoint(),aggregate,deferred,calls);
    }
    private void validateResourceInputs(EngineInputs input) {
        for(var key:input.resources().keySet()) {
            var binding=topology.occurrences().get(key.taskOccurrenceId());
            if(binding==null||!input.tasks().containsKey(key.taskOccurrenceId())
                    ||!requiredResources.getOrDefault(binding.sourceElementId(),Set.of()).contains(key.resourceSourceId()))
                throw new IllegalArgumentException("ENGINE_RESOURCE_BINDING_UNRESOLVED:"+key);
        }
    }
    private boolean resourcesReady(String task,String episode,EngineInputs input) {
        String source=topology.occurrences().get(task).sourceElementId();
        for(String resource:requiredResources.getOrDefault(source,Set.of())) {
            var value=input.resources().get(new EngineInputs.ResourceKey(task,resource));
            if(value==null||!Objects.equals(episode,value.episode())||!Boolean.TRUE.equals(value.available()))return false;
        }
        return true;
    }
    private EngineOccurrenceKey key(String id,String episode){var binding=topology.occurrences().get(id);return new EngineOccurrenceKey(binding.sourceElementId(),binding.stack(),episode);}
    private static String definitionIdentity(String hash,EngineSemanticsProfile profile,EngineTopologyPolicy topologyPolicy) {
        if(profile==EngineSemanticsProfile.USE4GOAL_OBSERVED_V1&&topologyPolicy==EngineTopologyPolicy.ORIGINAL_CONTEXT_PATHS)return hash;
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(
                    (profile.name()+":"+(topologyPolicy==EngineTopologyPolicy.ORIGINAL_CONTEXT_PATHS?"":topologyPolicy.name()+":")+hash).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private void assertDefinitions(){definition.assertUnchanged();instantiatedDefinition.assertUnchanged();}
    public synchronized void reset(){requireOpen();epoch++;boundary=0;goals.clear();tasks.clear();qualities.clear();keys.clear();}
    private void requireOpen(){if(closed)throw new IllegalStateException("ENGINE_SESSION_CLOSED");}
    @Override public synchronized void close(){if(!closed){goals.clear();tasks.clear();qualities.clear();keys.clear();closed=true;}}
}
