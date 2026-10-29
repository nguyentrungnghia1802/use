package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.runtime.*;
import org.tzi.use.uml.mm.MModel;

/** Per-program compiled mapping: no static parse/rebuild and no second MSystem per event. */
public final class JaCaMoRuntimeStateMapping implements RuntimeStateMapping {
    private final SemanticProgram program;private final CanonicalObservationMappingBindings bindings;private final SemanticObservationMapper mapper;
    public JaCaMoRuntimeStateMapping(SemanticProgram program,MModel model){this.program=program;bindings=new CanonicalObservationMappingBindings(program,model);mapper=new SemanticObservationMapper(bindings);}
    public SemanticMutationPlan plan(SemanticObservationMapper.Port reads,BridgeEntityId runtime,String kind,Completeness completeness,Map<String,Object> payload) {
        // An XML case requires its own admitted crosswalk. The JSON mapper cannot
        // manufacture organization scope or scheme/player identity from a class name.
        if(caseSpecific()&&!caseHelper(kind))throw new SemanticObservationMapper.UnsupportedObservation("XML_RUNTIME_RULE_BATCH_REQUIRED");
        program.assertUnchanged();var properties=new ArrayList<SemanticMutationPlan.PropertyAssignment>();var enactments=new ArrayList<SemanticMutationPlan.EnactmentCandidate>();
        if(kind.equals("UPSERT_CARTAGO_PROPERTY_SNAPSHOT"))property(reads,payload,properties);
        if(kind.equals("APPLY_CARTAGO_PROPERTY_DELTA"))for(Object value:requiredList(payload,"properties"))property(reads,CanonicalJson.object(value),properties);
        if(kind.equals("UPSERT_MOISE_GROUP")) {
            String source=text(payload,"groupSpecSemanticId");var definition=program.source().require(source);
            if(!definition.sourceKind().equals("MOISE_GROUP"))throw new IllegalArgumentException("GROUP_DEFINITION_SOURCE_REQUIRED");
            var roleFeature=definition.features().get("roleSemanticIds");
            if(roleFeature==null||!(roleFeature.value() instanceof List<?> declared))throw new IllegalArgumentException("GROUP_ROLE_DEFINITION_UNAVAILABLE");
            for(Object raw:requiredList(payload,"players")) {
                var player=CanonicalJson.object(raw);String role=text(player,"roleSemanticId");
                if(!((List<?>)roleFeature.value()).contains(role))throw new IllegalArgumentException("RUNTIME_ROLE_NOT_IN_FROZEN_GROUP_DEFINITION");
                enactments.add(new SemanticMutationPlan.EnactmentCandidate(role,player.containsKey("agentSemanticId")?text(player,"agentSemanticId"):text(player,"agentDeclarationSemanticId")));
            }
        }
        return new SemanticMutationPlan(program.hash(),runtime,kind,completeness,payload,properties,enactments);
    }
    public SemanticObservationMapper.Result apply(SemanticMutationPlan plan,SemanticObservationMapper.Port writer) throws UseApiException {
        program.assertUnchanged();if(!program.hash().equals(plan.programHash()))throw new IllegalArgumentException("MUTATION_PLAN_PROGRAM_MISMATCH");
        return mapper.apply(writer,plan.runtime(),plan.normalizedKind(),plan.payload());
    }
    public void removeProperty(SemanticObservationMapper.Port writer,org.tzi.use.uml.sys.MObject object,String property) throws UseApiException {mapper.removeProperty(writer,object,property);}
    public String programHash(){return program.hash();}
    public boolean caseSpecific(){return program.xmlPlan()!=null;}
    /** Selection belongs to mapping infrastructure, never to the native writer. */
    public CaseRuntimeMapping caseMapping(MModel model) {
        if(program.xmlPlan()!=null)return new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.XmlRuntimeStateMapping(program,model);
        return null;
    }
    public static boolean caseHelper(String kind){return Set.of("UPSERT_CARTAGO_WORKSPACE","UPSERT_CARTAGO_AGENT_IDENTITY","UPSERT_JASON_AGENT_STATE","DELETE_CARTAGO_AGENT_IDENTITY").contains(kind);}
    private void property(SemanticObservationMapper.Port reads,Map<String,Object> data,List<SemanticMutationPlan.PropertyAssignment> assignments) {
        String owner=text(data,"artifactSemanticId");var object=reads.find(owner);
        if(object==null)throw new SemanticObservationMapper.UnsupportedObservation("PROPERTY_OWNER_NOT_IN_DOMAIN_PROFILE:"+owner);
        for(var attribute:bindings.properties(object,text(data,"name")))assignments.add(new SemanticMutationPlan.PropertyAssignment(owner,attribute.name(),bindings.decode(object,attribute,requiredList(data,"values"),requiredList(data,"valueTypes"))));
    }
    private static String text(Map<String,Object> data,String key){if(!(data.get(key) instanceof String text)||text.isBlank())throw new IllegalArgumentException("RUNTIME_MAPPING_TEXT_REQUIRED:"+key);return text;}
    private static List<?> requiredList(Map<String,Object> data,String key){if(!(data.get(key) instanceof List<?> list))throw new IllegalArgumentException("RUNTIME_MAPPING_LIST_REQUIRED:"+key);return list;}
}
