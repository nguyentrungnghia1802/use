package org.tzi.use.semantic.engine.runtime;

import java.util.*;
import org.tzi.use.semantic.engine.api.*;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;
import org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.GoalModelInstanceTranslator.InstanceContextMatcher;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.verify.conformance.semantics.*;
import static org.tzi.use.semantic.engine.api.EngineDiagnostic.*;

/** Conservative, versioned operational subset. No patches to original recurrence or propagation. */
public final class RuntimeModelAdmission {
    public record Admitted(Set<String> derivedSourceIds,long occurrenceUpperBound,int activationDepth) {
        public Admitted {derivedSourceIds=Set.copyOf(derivedSourceIds);}
    }
    private RuntimeModelAdmission() { }
    public static Admitted admit(GoalModel model,List<ScenarioInstance> cast,InstanceContextMatcher matcher) {
        return admit(model,cast,matcher,EngineSemanticsProfile.USE4GOAL_OBSERVED_V1);
    }
    public static Admitted admit(GoalModel model,List<ScenarioInstance> cast,InstanceContextMatcher matcher,EngineSemanticsProfile profile) {
        Objects.requireNonNull(matcher,"EXPLICIT_CONTEXT_MATCHER_REQUIRED");
        var limits=Objects.requireNonNull(profile);
        long elements=model.getActors().stream().mapToLong(a->a.elements().size()).sum();
        if(elements>limits.maxOccurrences()||cast.size()>limits.maxOccurrences())reject("ENGINE_GRAPH_SIZE_LIMIT",model.getName());
        // The original validator uses recursive DFS. Bound API-built definitions
        // before calling it, just as the text frontend bounds its parse tree.
        preflightGraph(model);
        var diagnostics=OwnedGoalModel.validateDefinition(model,"<runtime-model>",profile);
        if(!diagnostics.isEmpty())throw new EngineAdmissionException(diagnostics);
        var counts=new HashMap<String,Integer>();var instances=new HashSet<String>();
        for(var instance:cast) {
            if(instance.name()==null||instance.name().isBlank()||!instances.add(instance.name())||model.findActor(instance.actorType()).isEmpty())
                reject("CAST_IDENTITY_INVALID",String.valueOf(instance));
            counts.merge(instance.actorType(),1,Integer::sum);
        }
        for(var actor:model.getActors())if(!counts.containsKey(actor.name()))reject("EXPLICIT_COMPLETE_CAST_REQUIRED",actor.name());
        for(var first:cast)for(var second:cast)if(!matcher.sharesContext(first.actorType(),first.name(),second.actorType(),second.name()))
            reject("CAST_CONTEXT_NOT_PROVEN",first.name()+":"+second.name());
        boolean crossActor=model.getDependencies().size()>0||model.getActors().stream().flatMap(a->a.contributions().stream())
                .anyMatch(c->!model.ownerOf(c.element()).equals(model.ownerOf(c.quality())));
        long bound=crossActor?Math.max(1,elements):0;
        for(var actor:model.getActors()) {
            int count=counts.get(actor.name());
            if(crossActor) {
                // A source node may occur with partial and complete binding stacks.
                // Each actor is absent or bound to one of count instances.
                if(bound>limits.maxOccurrences()/(count+1))reject("ENGINE_CONTEXT_CROSS_PRODUCT_LIMIT",actor.name());
                bound*=count+1;
            } else bound+=(long)actor.elements().size()*count;
            if(bound>limits.maxOccurrences())reject("ENGINE_CONTEXT_CROSS_PRODUCT_LIMIT",actor.name());
            if(!model.getDependencies().isEmpty()&&count>1)reject("AMBIGUOUS_ORIGINAL_OWNER_FALLBACK",actor.name());
            if(!actor.qualifications().isEmpty()||(!profile.externalRelations()&&(!actor.associations().isEmpty()||!actor.neededBys().isEmpty())))
                reject("RELATION_WITHOUT_ADMITTED_LIVE_SEMANTICS",actor.name());
            // Context proof was required for every exact cast pair above. The
            // original propagation does not execute these metadata relations.
            if(profile.externalRelations()) {
                if(actor.associations().stream().anyMatch(a->a.kind()!=AssocKind.PARTICIPATES_IN))
                    reject("ASSOCIATION_WITHOUT_ADMITTED_LIVE_SEMANTICS",actor.name());
                for(var needed:actor.neededBys())
                    if(!(model.findElement(needed.resource()).orElse(null) instanceof Resource)
                            ||!(model.findElement(needed.task()).orElse(null) instanceof Task))
                        reject("RESOURCE_GATE_ENDPOINT_UNSUPPORTED",needed.task());
            }
        }
        var children=new LinkedHashMap<String,List<String>>();var activation=new LinkedHashMap<String,Set<String>>();
        var activationParents=new HashMap<String,String>();var derived=new HashSet<String>();
        var qualityPolarity=new HashMap<String,ContributionType>();int edgeCount=0;
        for(var actor:model.getActors()) {
            for(var refinement:actor.refinements()) {
                var list=GoalActivationGraph.children(refinement);
                children.computeIfAbsent(refinement.parent(),k->new ArrayList<>()).addAll(list);edgeCount+=list.size();
                for(var child:list)addEdge(activation,activationParents,refinement.parent(),child);
                var parent=model.findElement(refinement.parent()).orElseThrow();
                if(parent instanceof Goal goal && goal.conditions().isEmpty() || parent instanceof Task)derived.add(parent.id());
                if(parent instanceof Task task && !task.constraints().isEmpty())reject("TASK_PARENT_WITH_DIRECT_CONTRACT",task.id());
            }
            for(var contribution:actor.contributions()) {
                edgeCount++;
                if(contribution.type()!=ContributionType.MAKE&&contribution.type()!=ContributionType.BREAK)
                    reject("NONSUFFICIENT_CONTRIBUTION_TRACE_ONLY",contribution.quality());
                if(!(model.findElement(contribution.element()).orElseThrow() instanceof GoalTaskElement))
                    reject("CONTRIBUTION_SOURCE_NOT_EXECUTABLE",contribution.element());
                var prior=qualityPolarity.putIfAbsent(contribution.quality(),contribution.type());
                derived.add(contribution.quality());
                if(prior!=null&&prior!=contribution.type())reject("OPPOSING_SUFFICIENT_CONTRIBUTIONS",contribution.quality());
                if(!model.ownerOf(contribution.element()).equals(model.ownerOf(contribution.quality()))&&counts.values().stream().anyMatch(c->c>1))
                    reject("AMBIGUOUS_ORIGINAL_OWNER_FALLBACK",contribution.quality());
            }
        }
        for(var dependency:model.getDependencies()) {
            edgeCount++;
            if(dependency.dependerElmt()==null||dependency.dependeeElmt()==null)reject("DEPENDENCY_ENDPOINTS_REQUIRED",dependency.dependum().id());
            if(!(model.findElement(dependency.dependerElmt()).orElseThrow() instanceof GoalTaskElement)
                    ||!(model.findElement(dependency.dependeeElmt()).orElseThrow() instanceof GoalTaskElement))
                reject("DEPENDENCY_ENDPOINT_DOMAIN_UNSUPPORTED",dependency.dependum().id());
            var depender=model.findElement(dependency.dependerElmt()).orElseThrow();
            if(depender instanceof Goal goal&&!goal.conditions().isEmpty()||depender instanceof Task task&&!task.constraints().isEmpty())
                reject("DEPENDENCY_TARGET_DIRECT_CONTRACT_CONFLICT",depender.id());
            derived.add(dependency.dependerElmt());
            addEdge(activation,activationParents,dependency.dependerElmt(),dependency.dependeeElmt());
        }
        if(edgeCount>limits.maxDefinitionEdges())reject("ENGINE_EDGE_LIMIT",model.getName());
        var order=topological(model.allElements().keySet(),activation);var depths=new HashMap<String,Integer>();int maxDepth=0;
        for(String node:order){int depth=depths.getOrDefault(node,1);maxDepth=Math.max(maxDepth,depth);for(String child:activation.getOrDefault(node,Set.of()))depths.merge(child,depth+1,Math::max);}
        if(maxDepth>limits.maxDefinitionDepth())reject("ENGINE_GRAPH_DEPTH_LIMIT",Integer.toString(maxDepth));
        var possible=new HashMap<String,Set<GoalTaskStatus>>();
        Collections.reverse(order);
        for(String id:order) {
            var element=model.findElement(id).orElseThrow();
            if(!(element instanceof GoalTaskElement))continue;
            Set<GoalTaskStatus> statuses;
            if(children.containsKey(id)&&derived.contains(id)) {
                statuses=EnumSet.of(GoalTaskStatus.UNKNOWN,GoalTaskStatus.FULFILLED);
                for(String child:children.get(id))statuses.addAll(possible.getOrDefault(child,Set.of()));
            } else if(derived.contains(id))statuses=EnumSet.of(GoalTaskStatus.UNKNOWN,GoalTaskStatus.FULFILLED);
            else if(element instanceof Task)statuses=EnumSet.of(GoalTaskStatus.UNKNOWN,GoalTaskStatus.PENDING,GoalTaskStatus.FULFILLED);
            else {Goal goal=(Goal)element;statuses=EnumSet.of(GoalTaskStatus.UNKNOWN,GoalTaskStatus.FULFILLED);
                GoalType type=goal.goalType()==null?GoalType.ACHIEVE:goal.goalType();
                if(type!=GoalType.MAINTAIN)statuses.add(GoalTaskStatus.PENDING);
                if(type==GoalType.MAINTAIN||type==GoalType.SUSTAIN)statuses.add(GoalTaskStatus.VIOLATED);
            }
            if(derived.contains(id))for(var status:statuses)if(!representable(element,status))reject("DERIVED_STATUS_NOT_REPRESENTABLE",id+":"+status);
            possible.put(id,Set.copyOf(statuses));
        }
        return new Admitted(derived,bound,maxDepth);
    }
    private static void preflightGraph(GoalModel model) {
        var limits=EngineSemanticsProfile.USE4GOAL_OBSERVED_V1;
        var edges=new LinkedHashMap<String,Set<String>>();int edgeCount=0;
        for(var actor:model.getActors())for(var refinement:actor.refinements()) {
            var children=GoalActivationGraph.children(refinement);edgeCount+=children.size();
            edges.computeIfAbsent(refinement.parent(),key->new LinkedHashSet<>()).addAll(children);
        }
        for(var dependency:model.getDependencies()) {
            edgeCount++;
            if(dependency.dependerElmt()!=null&&dependency.dependeeElmt()!=null)
                edges.computeIfAbsent(dependency.dependerElmt(),key->new LinkedHashSet<>()).add(dependency.dependeeElmt());
        }
        edgeCount+=model.getActors().stream().mapToInt(a->a.contributions().size()).sum();
        if(edgeCount>limits.maxDefinitionEdges())reject("ENGINE_EDGE_LIMIT",model.getName());
        var order=topological(model.allElements().keySet(),edges);var depths=new HashMap<String,Integer>();
        for(var node:order) {
            int depth=depths.getOrDefault(node,1);
            if(depth>limits.maxDefinitionDepth())reject("ENGINE_GRAPH_DEPTH_LIMIT",Integer.toString(depth));
            for(var child:edges.getOrDefault(node,Set.of()))depths.merge(child,depth+1,Math::max);
        }
    }
    private static boolean representable(IntentionalElement element,GoalTaskStatus status) {
        if(element instanceof Goal goal)return GoalMarking.fromStatus(goal.goalType(),status).status()==status;
        try{return TaskMarking.fromStatus(status).status()==status;}catch(IllegalArgumentException unsupported){return false;}
    }
    private static void addEdge(Map<String,Set<String>> edges,Map<String,String> parents,String parent,String child) {
        String previous=parents.putIfAbsent(child,parent);
        if(previous!=null&&!previous.equals(parent))reject("AMBIGUOUS_ACTIVATION_PARENT",child);
        edges.computeIfAbsent(parent,k->new LinkedHashSet<>()).add(child);
    }
    private static ArrayList<String> topological(Set<String> nodes,Map<String,Set<String>> edges) {
        var degree=new LinkedHashMap<String,Integer>();nodes.forEach(n->degree.put(n,0));
        edges.values().forEach(children->children.forEach(child->degree.merge(child,1,Integer::sum)));
        var queue=new ArrayDeque<String>();degree.forEach((n,d)->{if(d==0)queue.add(n);});var order=new ArrayList<String>();
        while(!queue.isEmpty()){String node=queue.remove();order.add(node);for(String child:edges.getOrDefault(node,Set.of()))if(degree.merge(child,-1,Integer::sum)==0)queue.add(child);}
        if(order.size()!=degree.size())reject("ACTIVATION_DEPENDENCY_CYCLE",String.join(",",nodes));return order;
    }
    private static void reject(String code,String target){throw new EngineAdmissionException(List.of(EngineDiagnostic.unsupported(Stage.CAPABILITY,code,target,"Outside USE4GOAL_OBSERVED_V1 operational subset")));}
}
