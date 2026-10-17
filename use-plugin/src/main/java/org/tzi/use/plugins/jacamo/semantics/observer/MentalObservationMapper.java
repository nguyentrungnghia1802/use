package org.tzi.use.plugins.jacamo.semantics.observer;

import java.util.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder;

public final class MentalObservationMapper {
    public static String identity(String agent){return "mental-state:"+SemanticJson.hash(List.of(agent));}
    public void apply(Port port,MObject agent,String owner,Map<String,Object> data)throws UseApiException {
        if(!data.containsKey("mentalObservation")) {port.metadata(agent,"mentalDecision","UNSUPPORTED:STRUCTURED_MENTAL_OBSERVATION_UNAVAILABLE");return;}
        var cut=MentalStateObservation.decode(owner,data.get("mentalObservation"));String id=identity(owner);var state=port.upsert(id,MentalUseBinding.STATE,"mental-state");port.metadata(state,"mentalAgent",owner);port.metadata(state,"authority","jason");
        String previousCycle=port.metadata(state,"mentalCycle");if(!previousCycle.isBlank() && cut.cycle()<Long.parseLong(previousCycle))throw new IllegalArgumentException("MENTAL_AGENT_CYCLE_REGRESSION");port.metadata(state,"mentalCycle",Long.toString(cut.cycle()));
        text(port,state,"cycle",Long.toString(cut.cycle()));text(port,state,"beliefBaseClass",cut.beliefBaseClass());flag(port,state,"complete",cut.complete());flag(port,state,"hasRules",cut.beliefs().stream().anyMatch(b->Boolean.TRUE.equals(b.get("rule"))));flag(port,state,"groundFactsOnly",cut.beliefs().stream().allMatch(b->Boolean.TRUE.equals(b.get("supported")) && !Boolean.TRUE.equals(b.get("rule"))));port.connect("AgentMentalState",agent,state);port.metadata(state,"mentalSource",NativeUseModelBuilder.canonicalJson(cut.raw()));
        var accepted=new HashSet<String>();for(var belief:cut.beliefs()) {
            String raw=(String)belief.get("raw");var previous=port.objects().stream().filter(o->o.cls().name().equals(MentalUseBinding.BELIEF) && owner.equals(port.metadata(o,"mentalAgent")) && raw.equals(port.metadata(o,"storedRaw"))).findFirst();
            String bid;
            if(previous.isPresent())bid=port.metadata(previous.get(),"semanticId");
            else {long serial=Long.parseLong(port.metadata(state,"beliefSequence").isBlank()?"0":port.metadata(state,"beliefSequence"))+1;port.metadata(state,"beliefSequence",Long.toString(serial));bid="stored-belief:"+SemanticJson.hash(List.of(owner,raw,serial));}
            accepted.add(bid);var object=port.upsert(bid,MentalUseBinding.BELIEF,"stored-belief");port.metadata(object,"storedRaw",raw);port.metadata(object,"mentalAgent",owner);port.metadata(object,"authority","jason");port.metadata(object,"mentalSource",NativeUseModelBuilder.canonicalJson(belief));
            for(String field:List.of("raw","namespace","functor","ruleBody"))text(port,object,field,(String)belief.get(field));text(port,object,"literalKey",(String)belief.get("key"));text(port,object,"arguments",NativeUseModelBuilder.canonicalJson(belief.get("terms")));text(port,object,"annotations",NativeUseModelBuilder.canonicalJson(Map.of("values",belief.get("annotations"),"tail",belief.get("annotationTail"))));for(String field:List.of("negative","rule","ground","supported"))flag(port,object,field,(Boolean)belief.get(field));port.connect("StoredBeliefs",state,object);
        }
        // Absence only deletes from a COMPLETE BB cut, and only this observer family.
        if(cut.complete())for(var old:List.copyOf(port.objects()))if(old.cls().name().equals(MentalUseBinding.BELIEF) && owner.equals(port.metadata(old,"mentalAgent")) && !accepted.contains(port.metadata(old,"semanticId")))port.delete(port.metadata(old,"semanticId"));
        port.metadata(agent,"mentalDecision",cut.complete()?"MAPPED:STORED_BB_CUT":"PARTIAL:STORED_BB_CUT");
        if(data.containsKey("executionObservation")) {
            var execution=ExecutionObservation.decode(owner,data.get("executionObservation"));var object=port.upsert("execution-state:"+SemanticJson.hash(List.of(owner)),MentalUseBinding.EXECUTION,"execution-state");port.metadata(object,"mentalAgent",owner);port.metadata(object,"authority","jason");text(port,object,"cycle",Long.toString(execution.cycle()));text(port,object,"payload",NativeUseModelBuilder.canonicalJson(execution.raw()));flag(port,object,"complete",execution.complete());port.connect("AgentExecution",agent,object);
        }
        port.metadata(agent,"lastAgentCut",SemanticJson.hash(data));
    }
    private static void text(Port port,MObject object,String field,String value)throws UseApiException{port.value(object,field,new StringValue(value));}
    private static void flag(Port port,MObject object,String field,boolean value)throws UseApiException{port.value(object,field,BooleanValue.get(value));}
}
