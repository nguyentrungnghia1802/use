package org.tzi.use.plugins.jacamo.verify.istar;

import java.util.*;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.istar.parser.ScopedElementNames;
import org.vnu.sme.goal.dsl.istar_marking.mm.ScenarioInstance;
import org.vnu.sme.goal.dsl.istar_marking.mm.GoalModelInstance;
import org.vnu.sme.goal.dsl.istar_marking.mm.GoalModelInstanceTopology;
import org.vnu.sme.goal.dsl.istar_marking.parser.*;

/** Prepares iStar participation views and occurrence topology for Goal/Task evaluation. */
public final class GoalModelTopology {
    private GoalModelTopology() { }
    /** One occurrence per definition element in a proved single-owner context. */
    static GoalModelInstanceTopology singleOwner(GoalModel source,List<ScenarioInstance> cast,GoalModelInstanceTranslator.InstanceContextMatcher matcher) {
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
            Actor actual=actor instanceof GenericActor?new GenericActor(owner,elements,refinements,contributions,qualifications,needed,associations):actor instanceof Role?new Role(owner,elements,refinements,contributions,qualifications,needed,associations):new Agent(owner,elements,refinements,contributions,qualifications,needed,associations);
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

    /** Same-named Goal views share one occurrence through participates-in; no authored dependency is added. */
    public static final class ParticipationViews {
        private final GoalModel execution;
        private final Map<String,String> aliases;
        public ParticipationViews(GoalModel source) {
            var matches=new LinkedHashMap<String,String>();
            for(var actor:source.getActors())for(var relation:actor.associations())if(relation.kind()==AssocKind.PARTICIPATES_IN) {
                if(!relation.actor().equals(actor.name())||!(actor instanceof Agent||actor instanceof GenericActor))throw new IllegalArgumentException("PARTICIPATION_PLAYER_MUST_BE_AGENT_OR_GENERIC_ACTOR");
                var role=source.findActor(relation.target()).orElseThrow();
                if(!(role instanceof Role))throw new IllegalArgumentException("PARTICIPATION_TARGET_MUST_BE_ROLE");
                for(var element:role.elements())if(element instanceof Goal requested) {
                    String name=ScopedElementNames.localName(role.name(),requested.id());
                    var candidates=actor.elements().stream().filter(Goal.class::isInstance).map(Goal.class::cast).filter(g->ScopedElementNames.localName(actor.name(),g.id()).equals(name)).toList();
                    if(candidates.isEmpty())continue;
                    if(candidates.size()!=1)throw new IllegalArgumentException("PARTICIPATION_GOAL_AMBIGUOUS:"+name);
                    var supplied=candidates.getFirst();
                    if(type(requested)!=type(supplied))throw new IllegalArgumentException("PARTICIPATION_GOAL_TYPE_MISMATCH:"+name);
                    if(!requested.conditions().isEmpty()||role.refinements().stream().anyMatch(r->r.parent().equals(requested.id())||org.vnu.sme.goal.dsl.istar.mm.GoalActivationGraph.children(r).contains(requested.id())))throw new IllegalArgumentException("PARTICIPATION_ROLE_GOAL_MUST_BE_AN_UNIMPLEMENTED_LEAF:"+name);
                    String previous=matches.putIfAbsent(requested.id(),supplied.id());
                    if(previous!=null&&!previous.equals(supplied.id()))throw new IllegalArgumentException("PARTICIPATION_REQUIRES_EXPLICIT_MULTI_PLAYER_QUANTIFIER:"+name);
                }
            }
            aliases=Map.copyOf(matches);execution=new GoalModel(source.getName()+"_ParticipationViews");
            for(var actor:source.getActors()) {
                var elements=actor.elements().stream().filter(e->!aliases.containsKey(e.id())).toList();
                var associations=actor.associations().stream().filter(a->a.kind()!=AssocKind.PARTICIPATES_IN).toList();
                Actor copy=actor instanceof GenericActor?new GenericActor(actor.name(),elements,actor.refinements(),actor.contributions(),actor.qualifications(),actor.neededBys(),associations):actor instanceof Role?new Role(actor.name(),elements,actor.refinements(),actor.contributions(),actor.qualifications(),actor.neededBys(),associations):new Agent(actor.name(),elements,actor.refinements(),actor.contributions(),actor.qualifications(),actor.neededBys(),associations);
                execution.addActor(copy);
            }
            for(var d:source.getDependencies()) {
                String from=canonical(d.dependerElmt()),to=canonical(d.dependeeElmt());
                execution.addDependency(new Dependency(from==null?d.depender():source.ownerOf(from).orElseThrow(),from,d.dependum(),to==null?d.dependee():source.ownerOf(to).orElseThrow(),to));
            }
        }
        public GoalModel executionModel(){return execution;}
        public Map<String,String> aliases(){return aliases;}
        public String canonical(String id){return id==null?null:aliases.getOrDefault(id,id);}
        public List<String> viewsOf(String id){var views=new ArrayList<String>();views.add(id);aliases.forEach((view,target)->{if(target.equals(id))views.add(view);});return List.copyOf(views);}
        private static GoalType type(Goal goal){return goal.goalType()==null?GoalType.ACHIEVE:goal.goalType();}
    }
}
