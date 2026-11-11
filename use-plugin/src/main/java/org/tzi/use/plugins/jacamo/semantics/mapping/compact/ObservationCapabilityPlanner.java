package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Produces actual versioned provider contracts from admitted selectors.
 * No source tables, legacy XML nodes, case recipes or executable expressions. */
public final class ObservationCapabilityPlanner {
    private record PlanKey(String path,String hash,String trigger,String context,int size) { }
    private record Region(CompactMappingSpecification.Correspondence declaration,String task,int first,int after) { }
    public static List<ObservationCapabilityRequest> compile(CompactMappingSpecification specification,SourceSymbolCatalog sources,
            List<CslBinding> csl,List<IStarBinding> istar) {
        var requests=new TreeMap<String,ObservationCapabilityRequest>();
        var plans=new TreeMap<String,Map.Entry<PlanKey,List<Region>>>();
        for(var mapping:specification.istarMappings())if(SourceReference.parse(mapping.source()) instanceof SourceReference.AslPlanStepRef selected) {
            var source=sources.selectedPlan(selected);var plan=source.plan();
            var key=new PlanKey(source.path(),plan.sourceHash(),plan.trigger().replaceFirst("^\\+!",""),Objects.toString(plan.context(),""),plan.stepCount());
            String identity=SemanticJson.hash(List.of(key.path(),key.hash(),key.trigger(),key.context()));
            var binding=istar.stream().filter(b->b.sourceRef().raw().equals(mapping.source())&&b.targetRef().raw().equals(mapping.target())).findFirst().orElseThrow();
            plans.computeIfAbsent(identity,k->Map.entry(key,new ArrayList<>())).getValue().add(new Region(mapping,binding.targetSymbol().name(),selected.firstIndex(),selected.afterIndex()));
        }
        var bySource=new TreeMap<String,List<Map<String,Object>>>();var pins=new HashMap<String,PlanKey>();
        for(var entry:plans.entrySet()) {
            var key=entry.getValue().getKey();var regions=entry.getValue().getValue();regions.sort(Comparator.comparingInt(Region::first));
            var steps=new ArrayList<Map<String,Object>>();int next=0;
            for(var region:regions) {
                if(region.first()<next)throw region.declaration().location().error("OVERLAPPING_SOURCE_TASK_REGIONS");
                if(next<region.first())steps.add(trace(entry.getKey(),next,region.first()));
                steps.add(step(region));next=region.after();
            }
            if(next<key.size())steps.add(trace(entry.getKey(),next,key.size()));
            String source=SemanticJson.hash(List.of(key.path(),key.hash()));pins.put(source,key);
            bySource.computeIfAbsent(source,k->new ArrayList<>()).add(Map.of("trigger",key.trigger(),"context",key.context(),"steps",steps));
        }
        for(var source:bySource.entrySet()) {
            var pin=pins.get(source.getKey());String id="source_steps_"+source.getKey();
            add(requests,new ObservationCapabilityRequest(id,"jason.plan-boundaries.v1",true,
                    Map.of("sourceRef","asl:"+pin.path(),"sourcePath",pin.path(),"sourceHash",pin.hash(),"plans",source.getValue())));
        }
        for(var binding:csl)if(binding.sourceRef() instanceof SourceReference.ArtifactFieldRef) {
            var field=binding.sourceSymbol();String type=field.signature(),hash=field.sourceDigest();
            if(!Set.of("java.lang.String","int","long","boolean","double","java.lang.Integer","java.lang.Long","java.lang.Boolean","java.lang.Double").contains(type)
                    ||hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("FIELD_TYPED_SOURCE_PROOF_REQUIRED:"+field.qualifiedName());
            var properties=new TreeMap<String,Object>();
            for(var property:csl)if(property.sourceRef() instanceof SourceReference.ArtifactPropertyRef&&field.owner().equals(property.sourceSymbol().owner())) {
                var declaration=specification.cslMappings().stream().filter(m->m.source().equals(property.sourceRef().raw())&&m.target().equals(property.targetRef().raw())).findFirst().orElseThrow();
                String converter=declaration.attributes().getOrDefault("converter","");
                String tag=converter.equals("JASON_ATOM_TO_STRING/1.0")?"JASON_ATOM":switch(property.targetSymbol().declaredType()) {
                    case "String"->"STRING";case "Boolean"->"BOOLEAN";case "Integer"->"JAVA_INTEGER";case "Real"->"IEEE754_BINARY64";
                    default->throw declaration.location().error("FIELD_PROPERTY_CODEC_UNSUPPORTED");
                };
                if(!converter.isEmpty()&&!converter.equals("JASON_ATOM_TO_STRING/1.0"))throw declaration.location().error("PROPERTY_CONVERTER_UNSUPPORTED:"+converter);
                Object old=properties.putIfAbsent(property.sourceSymbol().simpleName(),tag);
                if(old!=null&&!old.equals(tag))throw declaration.location().error("PROPERTY_INTERFACE_TYPE_CONFLICT");
            }
            add(requests,new ObservationCapabilityRequest("source_field_"+SemanticJson.hash(List.of(field.owner(),field.simpleName())),"cartago.artifact-field.v1",true,
                    Map.of("class",field.owner(),"field",field.simpleName(),"type",type,"hash",hash,"properties",properties)));
        }
        for(var selection:specification.requirements()) {
            String provider=selection.provider();
            if(!provider.matches("^[A-Za-z0-9_.-]+\\.v[0-9]+$"))throw selection.location().error("UNVERSIONED_CAPABILITY_PROVIDER:"+provider);
            if(selection.parameters().isEmpty()) {
                if(requests.values().stream().noneMatch(r->r.provider().equals(provider)))throw selection.location().error("CAPABILITY_SELECTION_HAS_NO_ADMITTED_SOURCE:"+provider);
                continue;
            }
            if(provider.equals("cartago.operation-history.v1")) {
                String source=text(selection,"source");var ref=SourceReference.parse(source);
                if(!(ref instanceof SourceReference.ArtifactRef))throw selection.location().error("OPERATION_HISTORY_ARTIFACT_SOURCE_REQUIRED");
                var symbol=sources.resolve(ref);String hash=symbol.sourceDigest();
                if(hash==null||!hash.matches("[0-9a-f]{64}"))throw selection.location().error("OPERATION_HISTORY_BINARY_PROOF_REQUIRED");
                var operations=words(text(selection,"operations"));
                if(operations.isEmpty()||new HashSet<>(operations).size()!=operations.size())throw selection.location().error("OPERATION_HISTORY_OPERATION_SELECTION_REQUIRED");
                for(String operation:operations)sources.resolve(new SourceReference.ArtifactOpRef(symbol.qualifiedName(),operation,List.of(),"artifact:"+symbol.qualifiedName()+"/op:"+operation));
                var parameters=new TreeMap<String,Object>(sources.requestPin(ref));parameters.put("class",symbol.qualifiedName());parameters.put("hash",hash);parameters.put("operations",operations);
                add(requests,new ObservationCapabilityRequest(selection.id(),provider,selection.required(),parameters));
            } else if(provider.equals("moise.scheme-lifecycle.v1")) {
                var ref=SourceReference.parse(text(selection,"source"));
                if(!(ref instanceof SourceReference.MoiseSchemeRef scheme))throw selection.location().error("SCHEME_SOURCE_REQUIRED");
                sources.resolve(ref);
                var parameters=new TreeMap<String,Object>(sources.requestPin(ref));parameters.put("organization",scheme.organization());parameters.put("scheme",scheme.schemeName());
                add(requests,new ObservationCapabilityRequest(selection.id(),provider,selection.required(),parameters));
            } else throw selection.location().error("CAPABILITY_TYPED_SELECTION_UNSUPPORTED:"+provider);
        }
        return List.copyOf(requests.values());
    }
    private static Map<String,Object> trace(String identity,int first,int after) {
        return values("trace_"+SemanticJson.hash(List.of(identity,first,after)).substring(0,24),first,after,List.of(),"","","",false,List.of());
    }
    private static Map<String,Object> step(Region region) {
        var declaration=region.declaration();var a=declaration.attributes();var variables=words(a.getOrDefault("variables",""));
        if(new HashSet<>(variables).size()!=variables.size()||variables.stream().anyMatch(v->!v.matches("[A-Z_][A-Za-z0-9_]*")))throw declaration.location().error("STEP_CAPTURE_VARIABLE_INVALID");
        String artifact=a.getOrDefault("artifactVariable",""),type=a.getOrDefault("artifactClass",""),scheme=a.getOrDefault("schemeVariable","");
        if(artifact.isBlank()!=type.isBlank()||!artifact.isBlank()&&!variables.contains(artifact)||!scheme.isBlank()&&!variables.contains(scheme))throw declaration.location().error("STEP_CAPTURE_REFERENCE_INVALID");
        String allow=a.getOrDefault("allowMissingArtifactAtEntry","false");
        if(!Set.of("true","false").contains(allow))throw declaration.location().error("STEP_ENTRY_BOOLEAN_REQUIRED");
        var inputs=new ArrayList<Map<String,Object>>();
        for(String input:words(a.getOrDefault("inputs",""))) {
            var parts=input.split(":",-1);
            if(parts.length!=3||!parts[0].matches("[A-Za-z_][A-Za-z0-9_]*")||!variables.contains(parts[1])||!parts[2].equals("java.lang.String"))throw declaration.location().error("STEP_TYPED_INPUT_INVALID");
            inputs.add(Map.of("name",parts[0],"variable",parts[1],"type",parts[2]));
        }
        return values(region.task(),region.first(),region.after(),variables,artifact,type,scheme,Boolean.parseBoolean(allow),inputs);
    }
    private static Map<String,Object> values(String task,int first,int after,List<String> variables,String artifact,String type,String scheme,boolean allow,List<Map<String,Object>> inputs) {
        return Map.of("task",task,"first",first,"after",after,"variables",variables,"artifactVariable",artifact,"artifactClass",type,"schemeVariable",scheme,"allowMissingArtifactAtEntry",allow,"inputs",inputs);
    }
    private static List<String> words(String value){return value.isBlank()?List.of():List.of(value.trim().split("\\s+"));}
    private static String text(CompactMappingSpecification.Requirement selection,String key){Object value=selection.parameters().get(key);if(!(value instanceof String text)||text.isBlank())throw selection.location().error("CAPABILITY_SELECTOR_REQUIRED:"+key);return text;}
    private static void add(Map<String,ObservationCapabilityRequest> requests,ObservationCapabilityRequest request) {
        if(requests.putIfAbsent(request.id(),request)!=null)throw new IllegalArgumentException("CAPABILITY_REQUEST_ID_CONFLICT:"+request.id());
    }
    private ObservationCapabilityPlanner() { }
}
