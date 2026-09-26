package org.jacamo.bridge.adapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import moise.os.OS;
import moise.os.ss.Group;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.BridgeRelationId;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.ModelFact;
import org.jacamo.bridge.contract.RelationCardinality;

/** Official Moise object-graph adapter; normative facts remain facts, never generated OCL. */
public final class OfficialMoiseAdapter {
    public record Result(List<ModelFact> facts, List<RelationCardinality> groupRoleCardinalities,
                         List<RelationCardinality> parentSubGroupCardinalities) { }

    public Result load(Path projectRoot, Path osFile, String projectKey) throws Exception {
        Path exact=osFile.toAbsolutePath().normalize(); OS os=OS.loadOSFromURI(exact.toUri().toString());
        var evidence=AdapterEvidence.file("moise-os",projectRoot,exact,"OS.loadOSFromURI official graph");
        var facts=new ArrayList<ModelFact>(); var roleCards=new ArrayList<RelationCardinality>(); var subgroupCards=new ArrayList<RelationCardinality>();
        os.getSS().getRolesDef().stream().sorted(Comparator.comparing(role->role.getId())).forEach(role -> facts.add(new ModelFact("role",
                id(projectKey,"role",role.getId()),Map.of("abstract",Boolean.toString(role.isAbstract())),
                Map.of("superRoles",role.getSuperRoles().stream().sorted(Comparator.comparing(r->r.getId())).map(r->id(projectKey,"role",r.getId())).toList()),CapabilityStatus.COMPLETE,List.of(evidence))));
        if(os.getSS().getRootGrSpec()!=null) group(projectKey,os.getSS().getRootGrSpec(),null,evidence,facts,roleCards,subgroupCards);
        os.getFS().getSchemes().stream().sorted(Comparator.comparing(s->s.getId())).forEach(scheme->{
            var schemeId=id(projectKey,"scheme",scheme.getId());
            var schemeRefs=new java.util.LinkedHashMap<String,List<BridgeEntityId>>();
            if(scheme.getRoot()!=null)schemeRefs.put("rootGoal",List.of(id(projectKey,"organisational-goal",scheme.getRoot().getId())));
            schemeRefs.put("missions",scheme.getMissions().stream().sorted(Comparator.comparing(m->m.getId())).map(m->id(projectKey,"mission",m.getId())).toList());
            facts.add(new ModelFact("scheme",schemeId,Map.of(),schemeRefs,CapabilityStatus.COMPLETE,List.of(evidence)));
            scheme.getMissions().stream().sorted(Comparator.comparing(m->m.getId())).forEach(mission->facts.add(new ModelFact("mission",id(projectKey,"mission",mission.getId()),Map.of(),Map.of("scheme",List.of(schemeId),"goals",mission.getGoals().stream().sorted(Comparator.comparing(g->g.getId())).map(g->id(projectKey,"organisational-goal",g.getId())).toList()),CapabilityStatus.COMPLETE,List.of(evidence))));
            scheme.getGoals().stream().sorted(Comparator.comparing(g->g.getId())).forEach(goal->{var refs=new java.util.LinkedHashMap<String,List<BridgeEntityId>>();refs.put("scheme",List.of(schemeId));if(goal.getPlan()!=null)refs.put("plan",List.of(planId(projectKey,goal.getPlan())));facts.add(new ModelFact("organisational-goal",id(projectKey,"organisational-goal",goal.getId()),Map.of("description",String.valueOf(goal.getDescription()==null?"":goal.getDescription()),"type",String.valueOf(goal.getType()),"minAgents",Integer.toString(goal.getMinAgToSatisfy()),"ttf",String.valueOf(goal.getTTF())),refs,CapabilityStatus.COMPLETE,List.of(evidence)));});
            scheme.getPlans().stream().sorted(Comparator.comparing(Object::toString)).forEach(plan->facts.add(new ModelFact("organisational-plan",planId(projectKey,plan),Map.of("operator",String.valueOf(plan.getOp()),"ast",plan.toString()),Map.of("scheme",List.of(schemeId),"targetGoal",List.of(id(projectKey,"organisational-goal",plan.getTargetGoal().getId())),"subGoals",plan.getSubGoals().stream().map(g->id(projectKey,"organisational-goal",g.getId())).toList()),CapabilityStatus.COMPLETE,List.of(evidence))));
        });
        os.getNS().getNorms().stream().sorted(Comparator.comparing(n->n.getId())).forEach(norm->facts.add(new ModelFact("norm",id(projectKey,"norm",norm.getId()),Map.of("type",String.valueOf(norm.getType()),"condition",String.valueOf(norm.getCondition()),"timeConstraint",String.valueOf(norm.getTimeConstraint())),Map.of("role",List.of(id(projectKey,"role",norm.getRole().getId())),"mission",List.of(id(projectKey,"mission",norm.getMission().getId()))),CapabilityStatus.COMPLETE,List.of(evidence))));
        facts.sort(Comparator.comparing(f->f.id().canonical())); roleCards.sort(Comparator.comparing(c->c.id().canonical())); subgroupCards.sort(Comparator.comparing(c->c.id().canonical()));
        return new Result(List.copyOf(facts),List.copyOf(roleCards),List.copyOf(subgroupCards));
    }

    private void group(String projectKey,Group group,Group parent,org.jacamo.bridge.contract.Evidence evidence,List<ModelFact> facts,List<RelationCardinality> roleCards,List<RelationCardinality> subgroupCards) {
        var groupId=id(projectKey,"group",group.getId()); facts.add(new ModelFact("group",groupId,Map.of(),parent==null?Map.of():Map.of("parent",List.of(id(projectKey,"group",parent.getId()))),CapabilityStatus.COMPLETE,List.of(evidence)));
        group.getRoles().getAll().stream().sorted(Comparator.comparing(r->r.getId())).forEach(role->{var roleId=id(projectKey,"role",role.getId());var card=group.getRoleCardinality(role);var relation=new BridgeRelationId("group-role-cardinality",List.of(groupId,roleId),evidence.evidenceId(),"model");roleCards.add(new RelationCardinality(relation,groupId,roleId,card.getMin(),card.getMax(),List.of(evidence)));});
        group.getSubGroups().getAll().stream().sorted(Comparator.comparing(g->g.getId())).forEach(child->{var childId=id(projectKey,"group",child.getId());var card=group.getSubGroupCardinality(child);var relation=new BridgeRelationId("parent-subgroup-cardinality",List.of(groupId,childId),evidence.evidenceId(),"model");subgroupCards.add(new RelationCardinality(relation,groupId,childId,card.getMin(),card.getMax(),List.of(evidence)));group(projectKey,child,group,evidence,facts,roleCards,subgroupCards);});
    }
    private BridgeEntityId id(String projectKey,String kind,String local){return new BridgeEntityId("moise","organisation",kind,projectKey,local,"model");}
    private BridgeEntityId planId(String projectKey,moise.os.fs.Plan plan){return id(projectKey,"organisational-plan",AdapterEvidence.digest(plan.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0,16));}
}
