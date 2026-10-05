package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.*;
import org.tzi.use.plugins.jacamo.JaCaMoFacade.TraceRow;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.sys.*;

/** Presentation DTO copied from the coordinator's active USE state. It owns no model or evaluator. */
public record GoalViewSnapshot(List<Scheme> schemes,String synchronization,String control,String diagnostic) {
    public record Source(String file,int line,String semanticId,String rule) { }
    public record Agent(String object,String name,List<String> exactIdentities,List<String> roleContexts) {
        public Agent {exactIdentities=List.copyOf(exactIdentities);roleContexts=List.copyOf(roleContexts);}
    }
    public record Mission(String object,String id,List<Agent> committedAgents) {
        public Mission {committedAgents=List.copyOf(committedAgents);}
    }
    public record Goal(String object,String semanticId,String specId,String id,String state,String evidence,String operator,Integer order,
            String parent,List<String> children,List<Mission> missions,List<Agent> committedAgents,List<Agent> achievedAgents,
            List<Source> sources,List<VerificationViolation> violations,String verification) {
        public Goal {children=List.copyOf(children);missions=List.copyOf(missions);committedAgents=List.copyOf(committedAgents);
            achievedAgents=List.copyOf(achievedAgents);sources=List.copyOf(sources);violations=List.copyOf(violations);}
    }
    public record Scheme(String object,String name,String semanticId,String specId,String runtimeIdentity,boolean runtime,
            String arguments,List<String> responsibleGroups,List<Goal> goals) {
        public Scheme {responsibleGroups=List.copyOf(responsibleGroups);goals=List.copyOf(goals);}
    }
    public GoalViewSnapshot {schemes=List.copyOf(schemes);}
    public static GoalViewSnapshot empty(){return new GoalViewSnapshot(List.of(),"MODEL_READY","UNAVAILABLE","No active Goal state");}
    public static GoalViewSnapshot read(RuntimeVerificationCoordinator coordinator,List<TraceRow> traces,List<VerificationViolation> violations,
            RuntimeControlService.View control) {
        return coordinator.read(()->{
            MSystem system=coordinator.system();MSystemState state=system.state();var result=new ArrayList<Scheme>();
            var aliases=coordinator.objectIdentityAliases();var latest=coordinator.latest();
            var failingContexts=new HashMap<String,Set<String>>();
            if(latest!=null)latest.outcomes().stream().filter(o->o.outcome()==org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL)
                    .forEach(o->failingContexts.put(o.constraintId(),coordinator.constraints().failingContexts(o)));
            var groups=new LinkedHashMap<MObject,List<MObject>>();
            var schemeClass=system.model().getClass("Scheme");var goalClass=system.model().getClass("OrganizationalGoal");
            if(schemeClass==null || goalClass==null)return empty();
            for(var scheme:state.objectsOfClassAndSubClasses(schemeClass).stream().sorted(Comparator.comparing(MObject::name)).toList())
                groups.put(scheme,related(system,"schemeGoals","Scheme","OrganizationalGoal",scheme,0));
            Set<MObject> assigned=new HashSet<>();groups.values().forEach(assigned::addAll);
            var specificationGoals=state.objectsOfClassAndSubClasses(goalClass).stream().filter(g->!assigned.contains(g)).toList();
            groups.put(null,specificationGoals);
            for(var entry:groups.entrySet()) {
                var scheme=entry.getKey();var bySpec=new TreeMap<String,List<MObject>>();
                entry.getValue().forEach(g->bySpec.computeIfAbsent(text(g,state,"schemeSpecSemanticId"),unused->new ArrayList<>()).add(g));
                if(scheme!=null && bySpec.isEmpty())bySpec.put(text(scheme,state,"specSemanticId"),List.of());
                for(var scope:bySpec.entrySet()) {
                    var goals=new ArrayList<Goal>();
                    for(var goal:scope.getValue().stream().sorted(Comparator.comparing(MObject::name)).toList()) {
                        String identity=text(goal,state,"semanticId");
                        var missionObjects=related(system,"missionGoals","Mission","OrganizationalGoal",goal,1);
                        var missions=missionObjects.stream().map(m->new Mission(m.name(),text(m,state,"id"),
                                related(system,"committedTo","Agent","Mission",m,1).stream().map(a->agent(system,a,aliases)).toList())).toList();
                        var source=traces.stream().filter(t->t.semanticId().equals(identity) || t.semanticId().equals(text(goal,state,"specSemanticId"))
                                || t.targetUseId().equals(goal.name())).map(t->new Source(t.sourcePath()==null?"unavailable":t.sourcePath().toString(),t.sourceLine(),t.semanticId(),t.mappingRule())).distinct().toList();
                        var relevant=violations.stream().filter(v->v.involvedObjects().containsKey(goal.name()) || (scheme!=null && v.involvedObjects().containsKey(scheme.name()))
                                || missionObjects.stream().anyMatch(m->v.involvedObjects().containsKey(m.name()))).toList();
                        var parents=related(system,"subGoals","OrganizationalGoal","OrganizationalGoal",goal,1);
                        var children=related(system,"subGoals","OrganizationalGoal","OrganizationalGoal",goal,0).stream()
                                .sorted(Comparator.comparingInt(g->{Integer ordinal=integer(g,state,"orderInParent");return ordinal==null?Integer.MAX_VALUE:ordinal;})).map(MObject::name).toList();
                        String evidence=text(goal,state,"stateEvidence"),runtimeState=text(goal,state,"runtimeState");
                        boolean stateAvailable=scheme!=null && coordinator.goalStateAvailable(text(scheme,state,"runtimeIdentity"));
                        var applicable=latest==null?List.<org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.Outcome>of():
                                latest.outcomes().stream().filter(o->o.contextClass().equals(goal.cls().name()) && (o.contextObject().isBlank() || o.contextObject().equals(goal.name()))).toList();
                        String verification=latest!=null && latest.freshness().equals("STALE")?"STALE":applicable.isEmpty()?"NOT_RUN":
                                applicable.stream().anyMatch(o->failingContexts.getOrDefault(o.constraintId(),Set.of()).contains(goal.name()))?"FAIL":
                                applicable.stream().anyMatch(o->o.outcome()==org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL && failingContexts.getOrDefault(o.constraintId(),Set.of()).isEmpty())?"CONTEXT_UNCONFIRMED":
                                applicable.stream().anyMatch(o->o.outcome()==org.tzi.use.plugins.jacamo.verification.VerificationOutcome.ERROR)?"ERROR":
                                applicable.stream().anyMatch(o->o.outcome()==org.tzi.use.plugins.jacamo.verification.VerificationOutcome.SKIPPED)?"SKIPPED":"PASS";
                        goals.add(new Goal(goal.name(),identity,text(goal,state,"specSemanticId"),text(goal,state,"id"),
                                !stateAvailable || runtimeState.isBlank()?"UNKNOWN / UNAVAILABLE":runtimeState,
                                !stateAvailable?"UNAVAILABLE"+(runtimeState.isBlank()?"":"; last observed "+runtimeState+" via "+evidence):evidence.isBlank()?"UNAVAILABLE":evidence,
                                text(goal,state,"decompositionOperator"),integer(goal,state,"orderInParent"),parents.isEmpty()?"":parents.getFirst().name(),children,missions,
                                related(system,"goalCommitment","Agent","OrganizationalGoal",goal,1).stream().map(a->agent(system,a,aliases)).toList(),
                                related(system,"goalAchievement","Agent","OrganizationalGoal",goal,1).stream().map(a->agent(system,a,aliases)).toList(),source,relevant,verification));
                    }
                    result.add(new Scheme(scheme==null?"":scheme.name(),scheme==null?"Specification goals":text(scheme,state,"name"),
                            scheme==null?"":text(scheme,state,"semanticId"),scope.getKey(),scheme==null?"":text(scheme,state,"runtimeIdentity"),
                            scheme!=null && text(scheme,state,"sourceLayer").equals("RUNTIME"),scheme==null?"":text(scheme,state,"arguments"),
                            scheme==null?List.of():related(system,"responsibleFor","Group","Scheme",scheme,1).stream().map(g->g.name()+" ["+text(g,state,"semanticId")+"]").toList(),goals));
                }
            }
            var cut=coordinator.verificationSnapshot();
            return new GoalViewSnapshot(result,latest!=null && latest.freshness().equals("STALE")?"STALE":cut.metadata()==null?"MODEL_READY":cut.metadata().lifecycle().name(),
                    control==null?"UNAVAILABLE":control.state().name(),control==null?"":control.diagnostic());
        });
    }
    private static Agent agent(MSystem system,MObject agent,Map<String,List<String>> aliases) {
        var contexts=new ArrayList<String>();
        for(var link:system.state().allLinks())if(link.association().getAnnotation("RoleInGroup")!=null && link.linkedObjects().contains(agent))
            contexts.add(link.association().name()+" → "+link.linkedObjects().stream().filter(o->o!=agent).map(MObject::name).toList());
        return new Agent(agent.name(),text(agent,system.state(),"name"),aliases.getOrDefault(agent.name(),List.of()),contexts);
    }
    private static List<MObject> related(MSystem system,String kind,String first,String second,MObject object,int index) {
        var association=system.model().getAssociation(DomainProjection.relation(kind,first,second));if(association==null)return List.of();
        return system.state().allLinks().stream().filter(l->l.association()==association && l.linkedObjects().get(index)==object)
                .map(l->l.linkedObjects().get(1-index)).distinct().sorted(Comparator.comparing(MObject::name)).toList();
    }
    private static String text(MObject object,MSystemState state,String attribute) {
        if(object.cls().attribute(attribute,true)==null)return "";var value=object.state(state).attributeValue(attribute);
        return value instanceof StringValue string?string.value():"";
    }
    private static Integer integer(MObject object,MSystemState state,String attribute) {
        var value=object.state(state).attributeValue(attribute);return value instanceof IntegerValue number?number.value():null;
    }
}
