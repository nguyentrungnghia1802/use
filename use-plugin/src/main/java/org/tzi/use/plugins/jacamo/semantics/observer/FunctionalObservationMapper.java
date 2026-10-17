package org.tzi.use.plugins.jacamo.semantics.observer;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder;

/** Stateless participant of the native mutation transaction; no second state/session. */
public final class FunctionalObservationMapper {
    public Result apply(Port port,BridgeEntityId runtime,Map<String,Object> data)throws UseApiException {
        var cut=FunctionalObservation.decode(runtime,data);String id=cut.schemeIdentity();
        for(var old:List.copyOf(port.objects()))if(old.cls().name().equals(FunctionalUseBinding.SCHEME)
                && cut.name().equals(port.metadata(old,"functionalBoardName")) && runtime.scope().equals(port.metadata(old,"functionalOrganizationName"))
                && !id.equals(port.metadata(old,"semanticId")))retire(port,port.metadata(old,"semanticId"));
        var scheme=port.upsert(id,FunctionalUseBinding.SCHEME,cut.name());port.alias(cut.declarationIdentity(),scheme);
        port.metadata(scheme,"functionalBoardName",cut.name());port.metadata(scheme,"functionalOrganizationName",runtime.scope());
        port.metadata(scheme,"functionalScheme",id);port.metadata(scheme,"authority","moise");port.metadata(scheme,"incarnation",runtime.incarnation());
        port.metadata(scheme,"boardArtifactIdentity",cut.artifactUuid());port.metadata(scheme,"functionalSource",NativeUseModelBuilder.canonicalJson(data));
        text(port,scheme,"name",cut.name());text(port,scheme,"sourceId",cut.definition().metadata().semanticId());
        var goals=new TreeMap<String,MObject>();var goalIdentities=new HashSet<String>();
        for(var observed:cut.goals()) {
            var source=cut.definition().goals().stream().filter(g->g.metadata().semanticId().equals(observed.definition())).findFirst().orElseThrow();
            String gid=FunctionalObservation.goalIdentity(id,source.metadata().semanticId(),observed.arguments());goalIdentities.add(gid);
            var goal=port.upsert(gid,FunctionalUseBinding.GOAL,source.goalId());goals.put(source.metadata().semanticId(),goal);
            port.metadata(goal,"functionalScheme",id);port.metadata(goal,"authority","moise");port.metadata(goal,"argumentsDigest",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(observed.arguments()));
            text(port,goal,"sourceId",source.metadata().semanticId());text(port,goal,"name",source.goalId());text(port,goal,"state",observed.state().name());
            text(port,goal,"goalType",source.goalType());text(port,goal,"arguments",NativeUseModelBuilder.canonicalJson(observed.arguments()));
            text(port,goal,"ttf",source.ttf());text(port,goal,"location",source.location());
            integer(port,goal,"minAgents",source.minAgentsToSatisfy());port.value(goal,"allAgents",BooleanValue.get(source.minAgentsToSatisfy()==-1));
            integer(port,goal,"committedCount",observed.committed().size());integer(port,goal,"achievedCount",observed.achieved().size());
            port.connect("FunctionalGoals",scheme,goal);
            port.disconnect("FunctionalGoalCommitted",null,goal);port.disconnect("FunctionalGoalAchieved",null,goal);
            for(String agent:observed.committed())port.connect("FunctionalGoalCommitted",agent(port,agent),goal);
            for(String agent:observed.achieved())port.connect("FunctionalGoalAchieved",agent(port,agent),goal);
        }
        // Changing argument bindings retires the previous goal occurrence, preventing latch transfer.
        for(var old:List.copyOf(port.objects()))if(old.cls().name().equals(FunctionalUseBinding.GOAL) && id.equals(port.metadata(old,"functionalScheme"))
                && !goalIdentities.contains(port.metadata(old,"semanticId")))port.delete(port.metadata(old,"semanticId"));
        for(var source:cut.definition().missions()) {
            String mid=FunctionalObservation.missionIdentity(id,source.metadata().semanticId());var mission=port.upsert(mid,FunctionalUseBinding.MISSION,source.missionId());
            port.metadata(mission,"functionalScheme",id);port.metadata(mission,"authority","moise");
            port.metadata(mission,"preferredMissionSemanticIds",NativeUseModelBuilder.canonicalJson(source.preferredMissionSemanticIds()));
            text(port,mission,"sourceId",source.metadata().semanticId());text(port,mission,"name",source.missionId());
            var bounds=cut.organization().functionalSpecification().schemeMissionCardinalities().stream().filter(c->c.schemeId().equals(cut.definition().metadata().semanticId()) && c.missionId().equals(source.metadata().semanticId())).toList();
            if(bounds.size()!=1)throw new IllegalArgumentException("FUNCTIONAL_MISSION_BOUNDS_UNAVAILABLE:"+source.metadata().semanticId());
            integer(port,mission,"minimum",bounds.getFirst().min());integer(port,mission,"maximum",bounds.getFirst().max());
            var committed=cut.commitments().stream().filter(c->c.mission().equals(source.metadata().semanticId())).toList();integer(port,mission,"committedCount",committed.size());
            port.connect("FunctionalMissions",scheme,mission);port.disconnect("FunctionalCommitments",null,mission);port.disconnect("FunctionalMissionGoals",mission,null);
            for(String goal:source.goalSemanticIds())port.connect("FunctionalMissionGoals",mission,goals.get(goal));
            var accepted=committed.stream().map(FunctionalObservation.Commitment::player).collect(java.util.stream.Collectors.toSet());
            for(var previous:List.copyOf(port.objects()))if(previous.cls().name().equals(FunctionalUseBinding.COMMITMENT) && mid.equals(port.metadata(previous,"commitmentMission")) && !accepted.contains(port.metadata(previous,"commitmentPlayer")))port.delete(port.metadata(previous,"semanticId"));
            for(var commitment:committed) {
                var actor=agent(port,commitment.player());port.connect("FunctionalCommitments",actor,mission);
                var existing=port.objects().stream().filter(o->mid.equals(port.metadata(o,"commitmentMission")) && commitment.player().equals(port.metadata(o,"commitmentPlayer"))).findFirst();
                MObject occurrence;
                if(existing.isPresent())occurrence=existing.get();
                else {
                    long serial=Long.parseLong(port.metadata(mission,"commitmentSequence").isBlank()?"0":port.metadata(mission,"commitmentSequence"))+1;port.metadata(mission,"commitmentSequence",Long.toString(serial));
                    String cid="functional-commitment:"+org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(List.of(mid,commitment.player(),serial));
                    occurrence=port.upsert(cid,FunctionalUseBinding.COMMITMENT,"commitment");port.metadata(occurrence,"functionalScheme",id);port.metadata(occurrence,"commitmentMission",mid);port.metadata(occurrence,"commitmentPlayer",commitment.player());port.metadata(occurrence,"authority","moise");
                    text(port,occurrence,"sourceId",source.metadata().semanticId());text(port,occurrence,"name",source.missionId());
                }
                port.connect("FunctionalCommitmentActor",actor,occurrence);port.connect("FunctionalCommitmentMission",mission,occurrence);
            }
        }
        port.disconnect("FunctionalResponsibility",null,scheme);
        for(String contextId:cut.responsibleContexts()) {
            var context=required(port,contextId);
            if(!context.cls().isSubClassifierOf(scheme.cls().model().getClass("OrgContext")))throw new IllegalArgumentException("FUNCTIONAL_RESPONSIBLE_CONTEXT_KIND");
            if(!Objects.toString(data.get("organisationSpecSemanticId")).equals(port.metadata(context,"organisationSpecSemanticId")))throw new IllegalArgumentException("FUNCTIONAL_RESPONSIBILITY_ORGANIZATION_MISMATCH");
            port.connect("FunctionalResponsibility",context,scheme);
        }
        try {
            var descriptor=org.tzi.use.plugins.jacamo.semantics.model.ExecutionDescriptor.from(cut.definition());
            port.metadata(scheme,"executionDescriptor",NativeUseModelBuilder.canonicalJson(descriptor));port.metadata(scheme,"executionDescriptorError","");
        } catch(IllegalArgumentException unsupported) {port.metadata(scheme,"executionDescriptorError",unsupported.getMessage());}
        port.metadata(scheme,"lastFunctionalCut",cut.cutHash());
        return new Result(id,scheme,"MOISE_FUNCTIONAL_PUBLIC_OBSERVATION");
    }
    private static void retire(Port port,String scheme)throws UseApiException {
        for(var object:List.copyOf(port.objects()))if(scheme.equals(port.metadata(object,"functionalScheme")) && !scheme.equals(port.metadata(object,"semanticId")))port.delete(port.metadata(object,"semanticId"));
        port.delete(scheme);
    }
    private static MObject required(Port port,String id){var object=port.find(id);if(object==null)throw new IllegalArgumentException("FUNCTIONAL_RUNTIME_BINDING_UNAVAILABLE:"+id);return object;}
    private static MObject agent(Port port,String id){var object=required(port,id);if(!object.cls().name().equals("AtomicAgent"))throw new IllegalArgumentException("FUNCTIONAL_COMMITMENT_AGENT_KIND");return object;}
    private static void text(Port port,MObject object,String attribute,String value)throws UseApiException {port.value(object,attribute,new StringValue(value));}
    private static void integer(Port port,MObject object,String attribute,int value)throws UseApiException {port.value(object,attribute,IntegerValue.valueOf(value));}
}
