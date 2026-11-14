package org.tzi.use.plugins.jacamo.verify.runtime;

import java.util.*;
import org.tzi.use.plugins.jacamo.dsl.istar.parser.ScopedElementNames;
import org.vnu.sme.goal.dsl.istar.mm.*;

/** Same-named Goal views share one occurrence through participates-in; no authored dependency is added. */
public final class ParticipationGoalViews {
    private final GoalModel execution;
    private final Map<String,String> aliases;
    public ParticipationGoalViews(GoalModel source) {
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
