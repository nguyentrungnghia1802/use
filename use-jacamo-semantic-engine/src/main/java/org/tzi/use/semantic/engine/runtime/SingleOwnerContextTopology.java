package org.tzi.use.semantic.engine.runtime;

import java.util.*;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;
import org.vnu.sme.goal.dsl.iscn.goalmodelinstance.mm.GoalModelInstance;
import org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.*;

/** New neutral adapter, not a patched upstream API. A bijective execution M1
 * instantiation for a proved single-owner context. Each definition element has
 * one occurrence; all original refinement/dependency endpoints stay attached.
 * No recurrence, propagation, observation or domain rule is implemented here. */
final class SingleOwnerContextTopology {
    private SingleOwnerContextTopology() { }
    static GoalModelInstanceTopology build(GoalModel source,List<ScenarioInstance> cast,GoalModelInstanceTranslator.InstanceContextMatcher matcher) {
        var instances=new TreeMap<String,ScenarioInstance>();var names=new HashSet<String>();
        for(var item:cast)if(source.findActor(item.actorType()).isEmpty()||!names.add(item.name())||instances.putIfAbsent(item.actorType(),item)!=null)throw new IllegalArgumentException("SINGLE_OWNER_CONTEXT_REQUIRES_UNIQUE_ACTOR_INSTANCES");
        if(instances.size()!=source.getActors().size())throw new IllegalArgumentException("SINGLE_OWNER_CONTEXT_REQUIRES_COMPLETE_CAST");
        for(var first:cast)for(var second:cast)if(!matcher.sharesContext(first.actorType(),first.name(),second.actorType(),second.name()))throw new IllegalArgumentException("SINGLE_OWNER_CONTEXT_NOT_PROVEN");
        var ids=new HashMap<String,String>();var actors=new HashMap<String,String>();
        for(var actor:source.getActors()) {
            String instance=instances.get(actor.name()).name();actors.put(actor.name(),actor.name()+"__"+instance);
            for(var element:actor.elements())ids.put(element.id(),element.id()+"__"+instance);
        }
        var model=new GoalModel(source.getName()+"_SingleOwnerContext");var actorSources=new LinkedHashMap<String,Actor>();var elementSources=new LinkedHashMap<String,IntentionalElement>();
        var occurrences=new LinkedHashMap<String,GoalModelInstanceTopology.OccurrenceBinding>();var actorLabels=new TreeMap<String,String>();var nodeLabels=new TreeMap<String,String>();
        for(var actor:source.getActors()) {
            var instance=instances.get(actor.name());String owner=actors.get(actor.name());var elements=new ArrayList<IntentionalElement>();
            for(var element:actor.elements()) {
                String id=ids.get(element.id());elements.add(copy(element,id));elementSources.put(id,element);
                var binding=new GoalModelInstanceTopology.OccurrenceBinding(id,element.id(),owner,List.of(new GoalModelInstanceTopology.ActorBinding(actor.name(),instance.name())));
                if(occurrences.putIfAbsent(id,binding)!=null)throw new IllegalArgumentException("SINGLE_OWNER_CONTEXT_ID_COLLISION");
                nodeLabels.put(id,element.id()+" ["+instance.name()+"]");
            }
            var refinements=new ArrayList<Refinement>();
            for(var refinement:actor.refinements()) {
                if(refinement instanceof AndRefinement and)refinements.add(new AndRefinement(ids.get(and.parent()),and.children().stream().map(ids::get).toList()));
                else if(refinement instanceof OrRefinement or)refinements.add(new OrRefinement(ids.get(or.parent()),ids.get(or.child())));
            }
            var contributions=actor.contributions().stream().map(c->new Contribution(ids.get(c.element()),c.type(),ids.get(c.quality()))).toList();
            var qualifications=actor.qualifications().stream().map(q->new Qualification(ids.get(q.quality()),ids.get(q.element()))).toList();
            var needed=actor.neededBys().stream().map(n->new NeededBy(ids.get(n.resource()),ids.get(n.task()))).toList();
            var associations=actor.associations().stream().map(a->new Association(owner,a.kind(),actors.get(a.target()))).toList();
            Actor actual=actor instanceof Role?new Role(owner,elements,refinements,contributions,qualifications,needed,associations):new Agent(owner,elements,refinements,contributions,qualifications,needed,associations);
            model.addActor(actual);actorSources.put(owner,actor);actorLabels.put(owner,actor.name()+" ["+instance.name()+"]");
        }
        for(var dependency:source.getDependencies())model.addDependency(new Dependency(actors.get(dependency.depender()),ids.get(dependency.dependerElmt()),copy(dependency.dependum(),dependency.dependum().id()+"__"+instances.get(dependency.depender()).name()+"__"+instances.get(dependency.dependee()).name()),actors.get(dependency.dependee()),ids.get(dependency.dependeeElmt())));
        if(occurrences.size()!=source.allElements().size()||model.allElements().size()!=occurrences.size())throw new IllegalArgumentException("SINGLE_OWNER_CONTEXT_NOT_BIJECTIVE");
        return new GoalModelInstanceTopology(new GoalModelInstance(model,actorSources,elementSources),occurrences,actorLabels,nodeLabels);
    }
    private static IntentionalElement copy(IntentionalElement element,String id) {
        return switch(element) {
            case Goal goal->new Goal(id,goal.goalType(),goal.conditions().isEmpty()?null:new IStarOclConstraint(goal.conditions().getFirst().oclBody()));
            case Task task->new Task(id,task.preconditions().isEmpty()?null:new IStarOclConstraint(task.preconditions().getFirst().oclBody()),task.postconditions().isEmpty()?null:new IStarOclConstraint(task.postconditions().getFirst().oclBody()));
            case Resource ignored->new Resource(id);
            case Quality ignored->new Quality(id);
        };
    }
}
