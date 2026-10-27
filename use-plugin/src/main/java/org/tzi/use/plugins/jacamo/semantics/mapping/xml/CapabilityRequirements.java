package org.tzi.use.plugins.jacamo.semantics.mapping.xml;
import java.util.*;
import org.jacamo.bridge.contract.ObservationCapabilityRequest;

/** XML requirement data only. Availability is a producer registry receipt, never this compilation. */
public final class CapabilityRequirements {
    private CapabilityRequirements(){ }
    public static List<ObservationCapabilityRequest> compile(MappingInputs inputs) {
        return compile(inputs,null);
    }
    public static List<ObservationCapabilityRequest> compile(MappingInputs inputs,org.jacamo.bridge.contract.ModelSnapshot source) {
        var result=new ArrayList<ObservationCapabilityRequest>();
        for(var node:inputs.specification().root().child("requirements").children("capability")) {
            var parameters=new TreeMap<String,Object>();
            if(!node.optional("operations").isBlank()) {
                if(!node.attr("provider").equals("cartago.operation-history.v1"))throw node.location().error("CAPABILITY_OPERATION_SELECTION_PROVIDER_UNSUPPORTED");
                var names=List.of(node.attr("operations").trim().split("\\s+"));
                if(names.size()>32||new HashSet<>(names).size()!=names.size()||names.stream().anyMatch(n->!n.matches("[A-Za-z_$][A-Za-z0-9_$]*")))throw node.location().error("CAPABILITY_OPERATION_SELECTION_INVALID");
                parameters.put("operations",names);
            }
            for(String name:List.of("class","field","type","hash","organization","scheme"))if(!node.optional(name).isBlank())parameters.put(name,node.attr(name));
            if(!node.optional("hashOrigin").isBlank()) {
                if(!node.attr("hashOrigin").equals("OFFICIAL_CLASS_PROOF")||!node.optional("hash").isBlank()
                        ||!Set.of("cartago.artifact-field.v1","cartago.operation-history.v1").contains(node.attr("provider"))
                        ||node.optional("class").isBlank()||source==null)throw node.location().error("CAPABILITY_BINARY_HASH_ORIGIN_UNSUPPORTED_OR_SOURCE_REQUIRED");
                var proofs=org.tzi.use.plugins.jacamo.semantics.mapping.SourceArtifactTypeReferences.classProofs(source,node.attr("class"));
                var identities=proofs.stream().map(p->List.of(p.sourceDigest(),p.sourceUri(),p.sourceJavaFqcn())).distinct().toList();
                if(identities.size()!=1)throw node.location().error("CAPABILITY_EXACT_OFFICIAL_CLASS_PROOF_REQUIRED");
                parameters.put("hash",identities.getFirst().getFirst());
            }
            if(!node.optional("sourceRef").isBlank()) {
                parameters.put("sourceRef",node.attr("sourceRef"));parameters.put("sourceHash",inputs.require(node.attr("sourceRef")).sha256());
                parameters.put("sourcePath",inputs.specification().requireId(node.attr("sourceRef")).attr("path"));
            }
            var properties=new TreeMap<String,Object>();
            for(var property:node.children("property"))if(properties.putIfAbsent(property.attr("name"),property.attr("type"))!=null)throw property.location().error("CAPABILITY_PROPERTY_DUPLICATE");
            if(!properties.isEmpty())parameters.put("properties",properties);
            if(!node.children("plan").isEmpty()) {
                var plans=new ArrayList<Map<String,Object>>();
                for(var plan:node.children("plan")) {
                    var steps=new ArrayList<Map<String,Object>>();
                    for(var step:plan.children("step")) {
                        var values=new TreeMap<String,Object>();values.putAll(step.attributes());
                        try{values.put("first",Integer.parseInt(step.attr("first")));values.put("after",Integer.parseInt(step.attr("after")));}
                        catch(NumberFormatException bad){throw step.location().error("STEP_ORDINAL_INTEGER_REQUIRED");}
                        values.put("variables",step.attr("variables").isBlank()?List.of():List.of(step.attr("variables").trim().split("\\s+")));
                        for(String key:List.of("artifactVariable","artifactClass","schemeVariable"))values.putIfAbsent(key,"");
                        String allow=step.optional("allowMissingArtifactAtEntry");
                        if(!allow.isEmpty()&&!Set.of("true","false").contains(allow))throw step.location().error("STEP_ENTRY_BOOLEAN_REQUIRED");
                        values.put("allowMissingArtifactAtEntry",Boolean.parseBoolean(allow));
                        values.put("inputs",step.children("input").stream().map(input->Map.<String,Object>of("name",input.attr("name"),"variable",input.attr("variable"),"type",input.attr("type"))).toList());
                        steps.add(values);
                    }
                    plans.add(Map.of("trigger",plan.attr("trigger"),"context",plan.optional("context"),"steps",steps));
                }
                parameters.put("plans",plans);
            }
            result.add(new ObservationCapabilityRequest(node.attr("id"),node.attr("provider"),Boolean.parseBoolean(node.attr("required")),parameters));
        }
        return List.copyOf(result);
    }
}
