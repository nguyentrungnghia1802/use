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
        var shorthandProviders=new HashSet<String>();
        for(var selection:specification.requirements())if(selection.parameters().isEmpty()&&!shorthandProviders.add(selection.provider()))
            throw selection.location().error("CAPABILITY_DUPLICATE_PROVIDER_SHORTHAND:"+selection.provider());
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
                if(next<region.first())steps.add(trace(entry.getKey(),next,region.first(),region));
                steps.add(step(region));next=region.after();
            }
            if(next<key.size())steps.add(trace(entry.getKey(),next,key.size(),regions.getLast()));
            String source=SemanticJson.hash(List.of(key.path(),key.hash()));pins.put(source,key);
            bySource.computeIfAbsent(source,k->new ArrayList<>()).add(Map.of("trigger",key.trigger(),"context",key.context(),"steps",steps));
        }
        String jasonReqId = specification.requirements().stream()
                .filter(r -> r.provider().equals("jason.plan-boundaries.v1"))
                .map(CompactMappingSpecification.Requirement::id)
                .findFirst().orElse(null);
        for(var source:bySource.entrySet()) {
            var pin=pins.get(source.getKey());
            String id = (jasonReqId != null && bySource.size() == 1) ? jasonReqId : "source_steps_"+source.getKey();
            add(requests,new ObservationCapabilityRequest(id,"jason.plan-boundaries.v1",true,
                    Map.of("sourceRef","asl:"+pin.path(),"sourcePath",pin.path(),"sourceHash",pin.hash(),"plans",source.getValue())));
        }
        String fieldReqId = specification.requirements().stream()
                .filter(r -> r.provider().equals("cartago.artifact-field.v1"))
                .map(CompactMappingSpecification.Requirement::id)
                .findFirst().orElse(null);
        var fieldSelections=new TreeMap<String,CslBinding>();
        for(var binding:csl)if(binding.sourceRef() instanceof SourceReference.ArtifactFieldRef)
            fieldSelections.putIfAbsent(binding.sourceSymbol().owner()+":"+binding.sourceSymbol().simpleName(),binding);
        for(var binding:fieldSelections.values()) {
            var field=binding.sourceSymbol();String type=field.signature(),hash=field.sourceDigest();
            if(!Set.of("java.lang.String","int","long","boolean","double","java.lang.Integer","java.lang.Long","java.lang.Boolean","java.lang.Double").contains(type)
                    ||hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("FIELD_TYPED_SOURCE_PROOF_REQUIRED:"+field.qualifiedName());
            var properties=new TreeMap<String,Object>();
            for(var property:csl)if(property.sourceRef() instanceof SourceReference.ArtifactPropertyRef&&field.owner().equals(property.sourceSymbol().owner())) {
                var declaration=specification.cslMappings().stream().filter(m->m.source().equals(property.sourceRef().raw())&&m.target().equals(property.targetRef().raw())).findFirst().orElseThrow();
                String converter=declaration.attributes().getOrDefault("converter","");
                String tag=converter.equals("JASON_ATOM_TO_STRING/1.0")?"JASON_ATOM":converter.equals(CompactValueConverters.NUMBER_TO_REAL)?"JAVA_SCALAR_NUMBER":switch(property.targetSymbol().declaredType()) {
                    case "String"->"STRING";case "Boolean"->"BOOLEAN";case "Integer"->"JAVA_INTEGER";case "Real"->"IEEE754_BINARY64";
                    default->throw declaration.location().error("FIELD_PROPERTY_CODEC_UNSUPPORTED");
                };
                if(!converter.isEmpty()&&!Set.of("JASON_ATOM_TO_STRING/1.0",CompactValueConverters.NUMBER_TO_REAL).contains(converter))throw declaration.location().error("PROPERTY_CONVERTER_UNSUPPORTED:"+converter);
                Object old=properties.putIfAbsent(property.sourceSymbol().simpleName(),tag);
                if(old!=null&&!old.equals(tag))throw declaration.location().error("PROPERTY_INTERFACE_TYPE_CONFLICT");
            }
            String id = (fieldReqId != null && fieldSelections.size()==1) ? fieldReqId : "source_field_"+SemanticJson.hash(List.of(field.owner(),field.simpleName()));
            add(requests,new ObservationCapabilityRequest(id,"cartago.artifact-field.v1",true,
                    Map.of("class",field.owner(),"field",field.simpleName(),"type",type,"hash",hash,"properties",properties)));
        }
        for(var selection:specification.requirements()) {
            String provider=selection.provider();
            if(!provider.matches("^[A-Za-z0-9_.-]+\\.v[0-9]+$"))throw selection.location().error("UNVERSIONED_CAPABILITY_PROVIDER:"+provider);
            if(selection.parameters().isEmpty()) {
                boolean admitted = requests.values().stream().anyMatch(r->r.provider().equals(provider));
                if(!admitted&&provider.equals("moise.scheme-lifecycle.v1")) {
                    var selected=uniqueScheme(selection,sources,csl,istar);
                    var parameters=new TreeMap<String,Object>(sources.requestPin(selected));
                    parameters.put("organization",selected.organization());parameters.put("scheme",selected.schemeName());
                    add(requests,new ObservationCapabilityRequest(selection.id(),provider,selection.required(),parameters));
                    admitted=true;
                }
                if(!admitted)throw selection.location().error("CAPABILITY_SELECTION_HAS_NO_ADMITTED_SOURCE:"+provider);
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
        for(var view:specification.observerViews()) {
            var ref=SourceReference.parse(view.source());var symbol=sources.resolve(ref);
            var operations=new TreeSet<String>();
            for(var field:view.fields()) {
                var origin=CompactObserverBindings.parse(view,field);
                if(origin.kind()==CompactObserverBindings.Kind.FIRST_SUCCESS) {
                    var artifact=(SourceReference.ArtifactRef)ref;String name=origin.arguments().getFirst();
                    sources.resolve(new SourceReference.ArtifactOpRef(artifact.className(),name,List.of(),"artifact:"+artifact.className()+"/op:"+name));operations.add(name);
                }
                if(origin.kind()==CompactObserverBindings.Kind.ADMIN_SATISFIED)operations.add("admCommand");
            }
            if(ref instanceof SourceReference.MoiseSchemeRef scheme&&!requests.values().stream().anyMatch(r->r.provider().equals("moise.scheme-lifecycle.v1")&&scheme.organization().equals(r.parameters().get("organization"))&&scheme.schemeName().equals(r.parameters().get("scheme")))) {
                var parameters=new TreeMap<String,Object>(sources.requestPin(ref));parameters.put("organization",scheme.organization());parameters.put("scheme",scheme.schemeName());
                add(requests,new ObservationCapabilityRequest("observer_scheme_"+SemanticJson.hash(ref.raw()),"moise.scheme-lifecycle.v1",true,parameters));
            }
            if(!operations.isEmpty()) {
                String type=ref instanceof SourceReference.ArtifactRef artifact?artifact.className():"ora4mas.nopl.SchemeBoard";
                String hash=ref instanceof SourceReference.ArtifactRef?symbol.sourceDigest():org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.MoisePopulationClosure.supportedClassHash(RuntimeFactKind.SCHEME_BOARD);
                var existing=requests.values().stream().filter(r->r.provider().equals("cartago.operation-history.v1")&&type.equals(r.parameters().get("class"))).toList();
                if(existing.size()>1)throw view.location().error("COMPACT_OBSERVER_OPERATION_REQUEST_AMBIGUOUS");
                if(existing.size()==1) {
                    if(!((List<?>)existing.getFirst().parameters().get("operations")).containsAll(operations))throw view.location().error("COMPACT_OBSERVER_OPERATION_SELECTION_INCOMPLETE");
                } else {
                    var parameters=new TreeMap<String,Object>(sources.requestPin(ref));parameters.put("class",type);parameters.put("hash",hash);parameters.put("operations",List.copyOf(operations));
                    add(requests,new ObservationCapabilityRequest("observer_operations_"+SemanticJson.hash(view.source()),"cartago.operation-history.v1",true,parameters));
                }
            }
        }
        boolean npl=specification.observerViews().stream().anyMatch(v->v.population().equals("NPL_GOAL_OBLIGATIONS/1.0")||v.fields().stream().anyMatch(f->CompactObserverBindings.parse(v,f).kind()==CompactObserverBindings.Kind.NOW));
        var temporalProviders=new ArrayList<String>();
        if(npl)temporalProviders.add("npl.lifecycle.v1");
        // MOISE public transition receipts carry the same logical source clock.
        // A disabled domain deadline does not remove this provenance prerequisite.
        if(npl||requests.values().stream().anyMatch(r->r.provider().equals("moise.scheme-lifecycle.v1")))temporalProviders.add("npl.source-clock.v1");
        for(String provider:temporalProviders)if(requests.values().stream().noneMatch(r->r.provider().equals(provider)))
            add(requests,new ObservationCapabilityRequest("framework_"+provider,provider,true,Map.of("hash",PinnedRuntimeClassProfiles.nplObservationContractHash())));
        return List.copyOf(requests.values());
    }
    private static Map<String,Object> trace(String identity,int first,int after,Region neighbor) {
        var attributes=neighbor.declaration().attributes();
        var variables=words(attributes.getOrDefault("variables",""));
        return values("trace_"+SemanticJson.hash(List.of(identity,first,after)).substring(0,24),first,after,variables,
                attributes.getOrDefault("artifactVariable",""),attributes.getOrDefault("artifactClass",""),attributes.getOrDefault("schemeVariable",""),true,List.of());
    }
    private static SourceReference.MoiseSchemeRef uniqueScheme(CompactMappingSpecification.Requirement selection,SourceSymbolCatalog sources,List<CslBinding> csl,List<IStarBinding> istar) {
        var refs=new ArrayList<SourceReference>();csl.forEach(b->refs.add(b.sourceRef()));istar.forEach(b->refs.add(b.sourceRef()));
        var exact=refs.stream().filter(SourceReference.MoiseSchemeRef.class::isInstance).map(SourceReference.MoiseSchemeRef.class::cast)
                .map(s->s.organization()+"/"+s.schemeName()).collect(java.util.stream.Collectors.toSet());
        var organizations=new TreeSet<String>();
        for(var ref:refs)switch(ref) {
            case SourceReference.MoiseGroupRef r->organizations.add(r.organization());
            case SourceReference.MoiseRoleRef r->organizations.add(r.organization());
            case SourceReference.MoiseGoalRef r->organizations.add(r.organization());
            case SourceReference.MoiseSchemeRef r->organizations.add(r.organization());
            default->{ }
        }
        var candidates=sources.allSymbols().stream().filter(s->s.kind()==SourceReference.Kind.MOISE_SCHEME)
                .filter(s->exact.isEmpty()?organizations.contains(s.owner()):exact.contains(s.qualifiedName())).toList();
        if(candidates.size()!=1)throw selection.location().error("CAPABILITY_EXACT_SCHEME_SELECTION_REQUIRED:candidates="+candidates.size());
        var scheme=candidates.getFirst();
        return new SourceReference.MoiseSchemeRef(scheme.owner(),scheme.simpleName(),"moise:"+scheme.owner()+"/scheme:"+scheme.simpleName());
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
