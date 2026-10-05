package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.semantic.SemanticMetadata;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.*;
import org.tzi.use.plugins.jacamo.codegrounded.model.JacamoSpecificationModel;
import org.tzi.use.plugins.jacamo.codegrounded.trace.*;
import org.tzi.use.uml.mm.MModel;
import static org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.RelationDisposition.*;

/** Two-pass semantic projection into the single native USE model. DTO classes are never classifiers. */
public final class NativeUseModelBuilder {
    // Source enums remain usable by semantic validators; they are not USE enumerations.
    public static final List<String> BODY_TYPES=List.of("none","action","internalAction","achieve","test","addBel","addBelNewFocus","addBelBegin","addBelEnd","delBel","delBelNewFocus","delAddBel","achieveNF","constraint");
    public static final List<String> TRIGGER_OPERATORS=List.of("add","del","goalState");
    public static final List<String> TRIGGER_TYPES=List.of("belief","achieve","test","signal");
    public static final List<String> ACTION_KINDS=List.of("EXTERNAL","INTERNAL");
    public static final List<String> MOISE_PLAN_OPERATORS=List.of("sequence","choice","parallel");
    public static final List<String> MOISE_GOAL_TYPES=List.of("performance","achievement","maintenance");
    public static final List<String> MOISE_NORM_TYPES=List.of("obligation","permission");
    public Result build(JacamoSpecificationModel source) { return build(source,new CodeGroundedTraceCollector(),NativeProjectionMode.AUTO); }
    public Result build(JacamoSpecificationModel source,CodeGroundedTraceCollector trace) { return build(source,trace,NativeProjectionMode.AUTO); }
    public Result build(JacamoSpecificationModel source,NativeProjectionMode mode) { return build(source,new CodeGroundedTraceCollector(),mode); }
    public Result build(JacamoSpecificationModel source,CodeGroundedTraceCollector trace,NativeProjectionMode mode) {
        var profile=NativeProjectionProfile.forMode(mode); var snapshot=source.snapshot();
        var api=new UseModelApi(modelName(source.project().name()));
        Set<String> operationIds=new TreeSet<>();
        List<MoiseUseSymbols.Candidate> candidates=new ArrayList<>(); Map<String,SemanticMetadata> metadata=new LinkedHashMap<>();
        Map<String,String> kinds=new LinkedHashMap<>(), javaTypes=new LinkedHashMap<>(), artifactTypes=new LinkedHashMap<>();
        // One program type per exact source identity, shared by all declarations that use it.
        for(var program:source.programs()) {
            String id=DomainProjection.programId(program.sourceUri(),program.sourceDigest());
            if(metadata.putIfAbsent(id,program.metadata())==null) {
                candidates.add(new MoiseUseSymbols.Candidate(id,"agent_program",DomainProjection.basename(program.sourceUri())+"_Agent")); kinds.put(id,"agent-program");
            }
        }
        for(var environment:snapshot.cartagoEnvironments()) for(var type:environment.artifactTypes()) {
            if(type.metadata().sourceKind().equals("CARTAGO_PLATFORM_ARTIFACT_TYPE")
                    || !DomainProjection.domainArtifact(type.javaClassName(),type.metadata().capabilityStatus()==org.jacamo.bridge.contract.CapabilityStatus.COMPLETE
                            ? "APPLICATION" : "UNAVAILABLE")) {
                DomainProjection.trace(trace,"C03",type.metadata(),"SemanticEvidence",type.metadata().semanticId(),IGNORE_NOT_NEEDED,"FRAMEWORK_BOOKKEEPING_TYPE");
                continue;
            }
            String id=DomainProjection.artifactId(type.javaClassName());
            if(metadata.putIfAbsent(id,type.metadata())==null) {
                candidates.add(new MoiseUseSymbols.Candidate(id,"artifact",DomainProjection.simpleName(type.javaClassName())));
                kinds.put(id,"artifact"); javaTypes.put(id,type.javaClassName());
            }
            artifactTypes.put(type.metadata().semanticId(),id);
        }
        // A declaration is a type candidate only when the available class proves Artifact inheritance.
        for(var artifact:snapshot.artifactDeclarations()) {
            String id=DomainProjection.artifactId(artifact.javaClass());
            if(metadata.containsKey(id)) continue;
            try {
                Class<?> type=Class.forName(artifact.javaClass(),false,Thread.currentThread().getContextClassLoader());
                if(!cartago.Artifact.class.isAssignableFrom(type)) throw new IllegalArgumentException("NOT_AN_ARTIFACT_SUBCLASS:"+artifact.javaClass());
                if(!DomainProjection.domainArtifact(artifact.javaClass())) {
                    DomainProjection.trace(trace,"J04",artifact.metadata(),"SemanticEvidence",id,IGNORE_NOT_NEEDED,"PLATFORM_ARTIFACT_NOT_VERIFICATION_DOMAIN");
                    continue;
                }
                candidates.add(new MoiseUseSymbols.Candidate(id,"artifact",type.getSimpleName()));
                metadata.put(id,artifact.metadata()); kinds.put(id,"artifact"); javaTypes.put(id,artifact.javaClass());
            } catch(ClassNotFoundException|LinkageError unavailable) {
                DomainProjection.trace(trace,"J04",artifact.metadata(),"Diagnostic",id,IGNORE_NOT_NEEDED,"ARTIFACT_TYPE_UNRESOLVED:"+artifact.javaClass());
            }
        }
        MoiseDomainProjection.candidates(snapshot.moiseOrganizations(),candidates);
        Map<String,String> symbols=MoiseUseSymbols.allocate(candidates,Set.copyOf(NativeProjectionPolicy.BASE_CLASSES));
        try {
            // Pass 1: only semantic type candidates, with stable source trace and kind metadata.
            DomainProjection.installBases(api,trace,source.project().metadata());
            // Keep exact declaration proof even when its Java type is unavailable
            // in USE. An actual producer observation can then bind it without
            // inventing a cold classifier or guessing by display name.
            for(var declaration:snapshot.artifactDeclarations()) {
                var workspaces=snapshot.workspaceDeclarations().stream().filter(w->w.name().equals(declaration.workspace())).toList();
                if(workspaces.size()!=1) throw new IllegalArgumentException("ARTIFACT_DECLARATION_WORKSPACE_UNRESOLVED:"+declaration.metadata().semanticId());
                api.getModel().getClass("Artifact").addAnnotation(new org.tzi.use.uml.mm.MElementAnnotation(
                    "ArtifactDeclaration_"+DomainProjection.hash(declaration.metadata().semanticId()),new TreeMap<>(Map.of(
                        "id64",DomainProjection.encode(declaration.metadata().semanticId()),"name64",DomainProjection.encode(declaration.name()),
                        "workspace64",DomainProjection.encode(workspaces.getFirst().metadata().semanticId()),"javaClass64",DomainProjection.encode(declaration.javaClass())))));
            }
            for(String id:metadata.keySet()) {
                var cls=api.createClass(symbols.get(id),false); String kind=kinds.get(id);
                DomainProjection.annotate(cls,kind,id,javaTypes.getOrDefault(id,""));
                if(kind.equals("agent-program")) {
                    api.createGeneralization(cls.name(),"Agent");
                } else {
                    api.createGeneralization(cls.name(),"Artifact");
                    cls.addAnnotation(new org.tzi.use.uml.mm.MElementAnnotation("ArtifactProvider",Map.of("origin","APPLICATION")));
                }
                DomainProjection.trace(trace,kind.equals("agent-program") ? "A01" : "C03",metadata.get(id),"MClass","class:"+cls.name(),
                    PRESERVE_AS_ASSOCIATION,"SEMANTIC_KIND="+kind);
            }
            for(var declaration:snapshot.agentDeclarations()) {
                var program=source.programs().stream().filter(p->p.declarationId().equals(declaration.name())).findFirst().orElseThrow();
                String cls=DomainProjection.classFor(api.getModel(),"agent-program",DomainProjection.programId(program.sourceUri(),program.sourceDigest()));
                api.getModel().getClass(cls).addAnnotation(new org.tzi.use.uml.mm.MElementAnnotation(
                    "AgentDeclaration_"+DomainProjection.hash(declaration.metadata().semanticId()),new TreeMap<>(Map.of(
                        "id64",DomainProjection.encode(declaration.metadata().semanticId()),"name64",DomainProjection.encode(declaration.name()),
                        "sourceUri64",DomainProjection.encode(program.sourceUri()),"host64",DomainProjection.encode(declaration.host()),
                        "project64",DomainProjection.encode(source.project().name()),"instances",Integer.toString(declaration.instances())))));
                for(var belief:program.beliefs()) if(belief.metadata().sourceKind().equals("JASON_DOMAIN_BELIEF")) {
                    String id=DomainProjection.occurrenceId("initial-belief",declaration.metadata().semanticId(),belief.metadata().semanticId());
                    api.getModel().getClass("Belief").addAnnotation(new org.tzi.use.uml.mm.MElementAnnotation(
                        "InitialBelief_"+DomainProjection.hash(id),new TreeMap<>(Map.of(
                            "id64",DomainProjection.encode(id),"agent64",DomainProjection.encode(declaration.metadata().semanticId()),
                            "literal64",DomainProjection.encode(belief.literal()),"sourceId64",DomainProjection.encode(belief.metadata().semanticId())))));
                }
            }
            var moise=MoiseDomainProjection.install(api,snapshot.moiseOrganizations(),trace,symbols);
            // Generic environment/ownership relations are installed once on the approved bases.
            for(var environment:snapshot.cartagoEnvironments()) {
                Map<String,String> ownerTypes=new LinkedHashMap<>(); environment.artifacts().forEach(a->ownerTypes.put(a.metadata().semanticId(),a.artifactTypeSemanticId()));
                for(var property:environment.propertySnapshots()) {
                    String typeId=artifactTypes.get(ownerTypes.get(property.artifactSemanticId()));
                    if(typeId==null) {
                        if(!ownerTypes.containsKey(property.artifactSemanticId())) throw new IllegalArgumentException("OBS_PROPERTY_OWNER_UNRESOLVED:"+property.artifactSemanticId());
                        DomainProjection.trace(trace,"C09",property.metadata(),"SemanticEvidence",property.metadata().semanticId(),IGNORE_NOT_NEEDED,"FRAMEWORK_BOOKKEEPING_STATE");
                        continue;
                    }
                    var cls=api.getModel().getClass(symbols.get(typeId));
                    try {
                        String type=DomainProjection.propertyType(property.valueTypes());
                        String name=DomainProjection.propertyName(property.name());
                        if(cls.attribute(name,true)!=null && !cls.attribute(name,true).type().toString().equals(type))
                            throw new IllegalArgumentException("OBS_PROPERTY_SCHEMA_COLLISION:"+property.name());
                        if(cls.attribute(name,true)==null) api.createAttribute(cls.name(),name,type);
                        DomainProjection.propertyTrace(cls.attribute(name,true),property.metadata().semanticId());
                        DomainProjection.trace(trace,"C09",property.metadata(),"MAttribute","attribute:"+cls.name()+"."+name,CONVERT_TO_ATTRIBUTE);
                    } catch(IllegalArgumentException unsupported) {
                        DomainProjection.trace(trace,"C09",property.metadata(),"Diagnostic",property.metadata().semanticId(),CONVERT_TO_ATTRIBUTE,unsupported.getMessage());
                    }
                }
                for(var operation:environment.operations()) {
                    var artifact=environment.artifacts().stream().filter(a->a.metadata().semanticId().equals(operation.artifactSemanticId())).findFirst().orElseThrow();
                    String typeId=artifactTypes.get(artifact.artifactTypeSemanticId()); if(typeId==null) continue;
                    var backings=environment.backingOperations().stream().filter(b->b.operationDescriptorId().equals(operation.metadata().semanticId())).toList();
                    try {
                        var nativeOperation=DomainProjection.operation(api,symbols.get(typeId),operation,backings.size()==1 ? backings.getFirst() : null);
                        operationIds.add(operation.metadata().semanticId());
                        DomainProjection.trace(trace,"C05",operation.metadata(),"MOperation","operation:"+symbols.get(typeId)+"."+nativeOperation.name(),CONVERT_TO_OPERATION);
                    } catch(IllegalArgumentException unsupported) {
                        DomainProjection.trace(trace,"C05",operation.metadata(),"Diagnostic",operation.metadata().semanticId(),UNSUPPORTED,unsupported.getMessage());
                    }
                }
            }
            DomainProjection.trace(trace,"J01",source.project().metadata(),"MModel","model:"+api.getModel().name(),FLATTEN);
            snapshot.workspaceDeclarations().forEach(d -> DomainProjection.trace(trace,"J03",d.metadata(),"SemanticEvidence",d.metadata().semanticId(),IGNORE_NOT_NEEDED,"DECLARATION_NOT_RUNTIME_OBSERVATION"));
            snapshot.artifactDeclarations().forEach(d -> DomainProjection.trace(trace,"J04",d.metadata(),"SemanticEvidence",d.metadata().semanticId(),IGNORE_NOT_NEEDED,"DECLARATION_NOT_RUNTIME_OBSERVATION"));
            snapshot.schemeDeployments().forEach(d -> DomainProjection.trace(trace,"J07",d.metadata(),"MClass","class:Scheme",PRESERVE_AS_CLASS));
            snapshot.institutionDeployments().forEach(d -> DomainProjection.trace(trace,"J08",d.metadata(),"SemanticEvidence",d.metadata().semanticId(),IGNORE_NOT_NEEDED,"OPAQUE_RULE_ENGINE_NOT_PROJECTED"));
            snapshot.rawRoleTuples().forEach(d -> DomainProjection.trace(trace,"J09",d.metadata(),"SemanticEvidence",d.metadata().semanticId(),PRESERVE_AS_LINK,"EXACT_JCM_CONTEXT_REQUIRED"));
            snapshot.rawFocusTuples().forEach(d -> DomainProjection.trace(trace,"J10",d.metadata(),"SemanticEvidence",d.metadata().semanticId(),PRESERVE_AS_LINK,"EXPLICIT_RUNTIME_BINDING_REQUIRED"));
            snapshot.importProvenance().forEach(d -> DomainProjection.trace(trace,"J11",d.metadata(),"SemanticEvidence",d.metadata().semanticId(),FLATTEN));
            for(var environment:snapshot.cartagoEnvironments()) {
                environment.liveProperties().forEach(p -> DomainProjection.trace(trace,"C08",p.metadata(),"Diagnostic",p.metadata().semanticId(),CONVERT_TO_ATTRIBUTE,"LIVE_PROPERTY_UNSUPPORTED; USE_OBSERVED_SNAPSHOT_VALUES"));
                environment.focuses().forEach(f -> DomainProjection.trace(trace,"C20",f.metadata(),"SemanticEvidence",f.metadata().semanticId(),PRESERVE_AS_LINK,"EXPLICIT_AGENT_BINDING_REQUIRED"));
            }
            // Jason parses and executes these structures. The contract retains them without USE classes.
            for(var program:source.programs()) {
                DomainProjection.trace(trace,"A01",program.metadata(),"MClass","class:"+symbols.get(DomainProjection.programId(program.sourceUri(),program.sourceDigest())),FLATTEN);
                DomainProjection.trace(trace,"A02",program.planLibrary().metadata(),"SemanticEvidence",program.planLibrary().metadata().semanticId(),IGNORE_NOT_NEEDED);
                DomainProjection.trace(trace,"A17",program.planLibrary().metadata(),"SemanticEvidence",program.planLibrary().metadata().semanticId(),IGNORE_NOT_NEEDED,"ORDER_RETAINED_IN_SOURCE");
                for(var plan:program.planLibrary().plans()) {
                    DomainProjection.trace(trace,"A03",plan.metadata(),"SemanticEvidence",plan.metadata().semanticId(),IGNORE_NOT_NEEDED);
                    if(plan.trigger()!=null) DomainProjection.trace(trace,"A04",plan.trigger().metadata(),"SemanticEvidence",plan.trigger().metadata().semanticId(),IGNORE_NOT_NEEDED);
                    DomainProjection.trace(trace,"A19",plan.metadata(),"SemanticEvidence",plan.metadata().semanticId(),IGNORE_NOT_NEEDED,"ORDER_RETAINED_IN_SOURCE");
                    plan.body().forEach(b->DomainProjection.trace(trace,"A05",b.metadata(),"SemanticEvidence",b.metadata().semanticId(),IGNORE_NOT_NEEDED,
                        BODY_TYPES.contains(b.bodyType()) ? "EXECUTED_BY_JASON" : "JASON_BODY_TYPE_UNSUPPORTED_EVIDENCE_ONLY:"+b.bodyType()));
                    plan.body().stream().filter(b -> !b.nextSemanticId().isEmpty()).forEach(b -> DomainProjection.trace(trace,"A20",b.metadata(),"SemanticEvidence",b.nextSemanticId(),IGNORE_NOT_NEEDED));
                }
                program.actions().forEach(a->DomainProjection.trace(trace,a.kind().equals("INTERNAL") ? "A07" : "A06",a.metadata(),"SemanticEvidence",a.metadata().semanticId(),IGNORE_NOT_NEEDED));
                program.beliefs().forEach(b->DomainProjection.trace(trace,"A08",b.metadata(),"MClass","class:Belief",PRESERVE_AS_CLASS));
                program.beliefRules().forEach(b->DomainProjection.trace(trace,"A10",b.metadata(),"SemanticEvidence",b.metadata().semanticId(),IGNORE_NOT_NEEDED));
                program.goals().forEach(g->DomainProjection.trace(trace,"A09",g.metadata(),"MClass","class:AgentGoal",PRESERVE_AS_CLASS));
            }
            for(var cls:api.getModel().classes()) {
                String sourceId=DomainProjection.decode(cls.getAnnotationValue(DomainProjection.ANNOTATION,"sourceId64"));
                SemanticMetadata owner=metadata.getOrDefault(sourceId,source.project().metadata());
                for(var org:snapshot.moiseOrganizations()) {
                    if(org.metadata().semanticId().equals(sourceId)) owner=org.metadata();
                    for(var group:org.structuralSpecification().groups()) if(group.metadata().semanticId().equals(sourceId)) owner=group.metadata();
                    for(var scheme:org.functionalSpecification().schemes()) if(scheme.metadata().semanticId().equals(sourceId)) owner=scheme.metadata();
                    for(var card:org.structuralSpecification().groupRoleCardinalities()) if(MoiseDomainProjection.roleRelationId(org.metadata().semanticId(),card.groupId(),card.roleId()).equals(sourceId)) owner=card.metadata();
                }
                String rule=switch(DomainProjection.kind(cls)) { case "artifact" -> "C03"; case "organisation" -> "M01"; case "group" -> "M05"; case "workspace" -> "C02"; default -> "A01"; };
                for(var attribute:cls.attributes()) DomainProjection.trace(trace,rule,owner,"MAttribute","attribute:"+cls.name()+"."+attribute.name(),CONVERT_TO_ATTRIBUTE);
            }
            var constraints=new ArrayList<>(moise.constraints());
            constraints.addAll(new org.tzi.use.plugins.jacamo.codegrounded.constraint.NativeGoalConstraintPlanner().plan());
            new NativeConstraintInstaller().install(api,constraints);
            Map<String,String> runtimeArtifactClasses=new LinkedHashMap<>(); artifactTypes.forEach((id,type)->runtimeArtifactClasses.put(id,symbols.get(type)));
            return new Result(api.getModel(),trace.index(),constraints,List.of(),runtimeArtifactClasses,operationIds,
                NativeUseStructure.sha256(api.getModel()),profile,moise);
        } catch(UseApiException error) { throw new IllegalStateException("NATIVE_USE_MODEL_BUILD_FAILED:"+error.getMessage(),error); }
    }
    public static String modelName(String source) { return DomainProjection.symbol(source); }
    public static String canonicalJson(Object value) { return new String(CanonicalJson.encode(jsonTree(value)),java.nio.charset.StandardCharsets.UTF_8); }
    private static Object jsonTree(Object value) {
        if(value instanceof Map<?,?> values) { Map<String,Object> tree=new LinkedHashMap<>(); values.forEach((k,v)->tree.put(k.toString(),jsonTree(v))); return tree; }
        if(value instanceof Iterable<?> values) { List<Object> tree=new ArrayList<>(); values.forEach(v->tree.add(jsonTree(v))); return tree; }
        if(value!=null && value.getClass().isRecord()) {
            Map<String,Object> tree=new LinkedHashMap<>();
            try { for(var component:value.getClass().getRecordComponents()) tree.put(component.getName(),jsonTree(component.getAccessor().invoke(value))); }
            catch(ReflectiveOperationException error) { throw new IllegalArgumentException("SEMANTIC_DTO_SERIALIZATION_FAILED",error); }
            return tree;
        }
        return value;
    }
    public record Result(MModel model,CodeGroundedTraceIndex trace,List<NativeConstraintSpec> constraints,
        List<NativeConstraintSpec> skippedConstraints,Map<String,String> nativeArtifactTypeClassNames,
        Set<String> nativeOperationDescriptorIds,String structuralHash,NativeProjectionProfile profile,MoiseDomainProjection.Result moiseProjection) {
        public Result { constraints=List.copyOf(constraints); skippedConstraints=List.copyOf(skippedConstraints);
            nativeArtifactTypeClassNames=Map.copyOf(nativeArtifactTypeClassNames); nativeOperationDescriptorIds=Set.copyOf(nativeOperationDescriptorIds); }
    }
}
