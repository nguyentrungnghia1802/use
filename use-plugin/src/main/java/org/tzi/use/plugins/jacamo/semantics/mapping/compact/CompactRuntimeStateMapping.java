package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MObject;

/** Applies compact value correspondences through the existing native writer.
 * Recurrence, checkpoint approval and history remain owned by the native session.
 * No independent evaluator, case-specific observer or shadow MSystem is created. */
public final class CompactRuntimeStateMapping implements CaseRuntimeMapping {
    private final SemanticProgram program;
    private final MModel model;
    private final String revision;
    private record FieldDecoder(ObservationCapabilityRequest request,ArtifactFieldCutDecoder decoder) { }
    private final Map<String,FieldDecoder> fields;
    public CompactRuntimeStateMapping(SemanticProgram program,MModel model) {
        this.program=Objects.requireNonNull(program);this.model=Objects.requireNonNull(model);
        if(!program.isCompact())throw new IllegalArgumentException("COMPACT_PROGRAM_REQUIRED");
        var asset=program.complement().assets().stream().filter(a->a.id().equals("official-model")).findFirst().orElseThrow();
        var source=ContractPayloads.model(CanonicalJson.object(CanonicalJson.decode(asset.bytes())));
        program.requireSourceHash(new SourceModelNormalizer().normalize(source).sourceHash());revision=source.modelRevision();
        var decoders=new TreeMap<String,FieldDecoder>();
        for(var request:program.compactPlan().capabilityRequests())if(request.provider().equals("cartago.artifact-field.v1")) {
            String key=text(request.parameters(),"class")+":"+text(request.parameters(),"field");
            if(decoders.putIfAbsent(key,new FieldDecoder(request,new ArtifactFieldCutDecoder(source,request)))!=null)
                throw new IllegalArgumentException("COMPACT_DUPLICATE_FIELD_CAPABILITY:"+key);
        }
        fields=Map.copyOf(decoders);
    }
    @Override public void validateObservation(VerificationObservation observation,String run,long generation,String revision) {
        program.assertUnchanged();
        if(run==null||run.isBlank()||generation<0||!this.revision.equals(revision))throw new IllegalArgumentException("COMPACT_OBSERVATION_EPOCH_REQUIRED");
    }
    @Override public List<Outcome> applyCut(Port port,String run,long generation,RuntimeSnapshot cut,List<RuntimeEvent> prefix,RuntimeEvent event) throws UseApiException {
        program.assertUnchanged();Objects.requireNonNull(cut);
        if(run==null||run.isBlank()||generation<0)throw new IllegalArgumentException("COMPACT_CUT_EPOCH_REQUIRED");
        if(!revision.equals(cut.modelRevision()))throw new IllegalArgumentException("COMPACT_CUT_REVISION_MISMATCH");
        if(event!=null&&(!run.equals(event.sessionId())||generation!=event.generation()||!cut.modelRevision().equals(event.modelRevision())))
            throw new IllegalArgumentException("COMPACT_EVENT_CUT_SCOPE_MISMATCH");
        var outcome=new ArrayList<Outcome>();
        for(var object:port.objects())if(ownsRemoval(port,object)) {
            port.metadata(object,"compact:inventory","UNPROVEN");
            port.metadata(object,"compact:quality","UNAVAILABLE");
        }
        var ids=new HashSet<String>();
        for(var fact:cut.facts())if(!ids.add(fact.id().canonical()))throw new IllegalArgumentException("COMPACT_CUT_DUPLICATE_IDENTITY:"+fact.id().canonical());
        var workspaces=new HashMap<String,RuntimeFact>();
        for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.WORKSPACE&&fact.id().authority().equals("cartago")) {
            if(workspaces.putIfAbsent(text(fact.values(),"semanticId"),fact)!=null)throw new IllegalArgumentException("COMPACT_WORKSPACE_ALIAS_AMBIGUOUS");
        }
        var props=new HashMap<String,Map<String,List<RuntimeFact>>>();
        for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.PROPERTY&&fact.id().authority().equals("cartago")) {
            var owner=text(fact.values(),"artifactSemanticId");var name=text(fact.values(),"name");
            if(!owner.isBlank()&&!name.isBlank()) {
                props.computeIfAbsent(owner,k->new HashMap<>()).computeIfAbsent(name,k->new ArrayList<>()).add(fact);
            }
        }
        for(var binding:program.compactPlan().cslBindings())if(binding.sourceRef() instanceof SourceReference.WorkspaceRef selected) {
            String target=binding.targetRef().qualifier();var cls=model.getClass(target);
            if(cls==null)throw new IllegalArgumentException("COMPACT_NATIVE_CLASS_MISSING:"+target);
            for(var workspace:workspaces.values())if(selected.name().equals("*")||selected.name().equals(text(workspace.values(),"name"))) {
                String physical=workspace.id().canonical();
                String identity="compact:"+program.hash()+":"+target+":"+run+":"+generation+":"+physical;
                var object=port.upsert(identity,target,text(workspace.values(),"name"));
                port.metadata(object,"compact:program",program.hash());port.metadata(object,"compact:epoch",run+":"+generation);
                port.metadata(object,"semanticId",identity);port.metadata(object,"compact:physical",physical);
                port.metadata(object,"compact:workspace",physical);port.metadata(object,"compact:sourceRef",selected.raw());
                port.metadata(object,"compact:kind","WORKSPACE_CONTEXT");port.metadata(object,"compact:cut",cut.snapshotId());
                port.metadata(object,"compact:quality",workspace.completeness()==Completeness.COMPLETE&&workspace.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY?"COMPLETE":"UNAVAILABLE");
                // A transported COMPLETE flag does not prove inventory closure.
                port.metadata(object,"compact:inventory",completeInventory(cut,event)?"COMPLETE":"UNPROVEN");
                for(var member:program.compactPlan().cslBindings())if(member.targetRef().qualifier().equals(target)&&member.sourceRef() instanceof SourceReference.WorkspaceIdentityRef ref) {
                    var attr=cls.attribute(member.targetRef().member(),true);String field=member.targetRef().member();
                    String key=ref.field().equals("path")?"fullName":ref.field();String raw=text(workspace.values(),key);
                    Value value=workspace.completeness()==Completeness.COMPLETE&&workspace.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY&&!raw.isBlank()?new StringValue(raw):UndefinedValue.instance;
                    port.value(object,field,value);
                    if(!value.isUndefined()) {
                        org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.recordPropertyWritten(port,object,attr,value);
                        port.metadata(object,"propertySlot:"+field,"identity:"+physical+":"+ref.field());
                    } else {port.metadata(object,"propertyAbsent:"+field,"true");port.metadata(object,"propertySlot:"+field,"");port.metadata(object,"observedValueHash:"+field,"");}
                }
                outcome.add(new Outcome(true,"COMPACT-WORKSPACE-PROJECTION",identity,object,"SOURCE_QUALIFIED_WORKSPACE"));
            }
        }
        for(var binding:program.compactPlan().cslBindings())if(binding.sourceRef() instanceof SourceReference.ArtifactRef type) {
            String target=binding.targetRef().qualifier();var cls=model.getClass(target);
            if(cls==null)throw new IllegalArgumentException("COMPACT_NATIVE_CLASS_MISSING:"+target);
            for(var fact:cut.facts())if(fact.kind()==RuntimeFactKind.ARTIFACT&&fact.id().authority().equals("cartago")
                    &&binding.sourceSymbol().qualifiedName().equals(text(fact.values(),"artifactTypeJavaClassName"))) {
                String physical=fact.id().canonical();
                var workspace=workspaces.get(text(fact.values(),"workspaceSemanticId"));
                var aliases=CartagoPhysicalReference.aliases(fact,workspace);
                String sourceAlias=text(fact.values(),"semanticId");
                String identity="compact:"+program.hash()+":"+target+":"+run+":"+generation+":"+physical;
                MObject object=port.upsert(identity,target,text(fact.values(),"name"));
                port.metadata(object,"compact:program",program.hash());port.metadata(object,"compact:epoch",run+":"+generation);
                port.metadata(object,"compact:physical",physical);port.metadata(object,"semanticId",identity);
                port.metadata(object,"compact:kind","ARTIFACT");
                port.metadata(object,"compact:workspace",workspace.id().canonical());
                port.metadata(object,"compact:cut",cut.snapshotId());
                port.metadata(object,"compact:quality",fact.completeness()==Completeness.COMPLETE&&fact.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY?"COMPLETE":"UNAVAILABLE");
                port.metadata(object,"compact:inventory",completeInventory(cut,event)?"COMPLETE":"UNPROVEN");
                var decoded=new TreeMap<String,ArtifactFieldCutDecoder.Result>();
                Object receipts=fact.values().get(CapabilityObservation.KEY);
                var byRequest=receipts instanceof Map<?,?>?CanonicalJson.object(receipts):Map.<String,Object>of();
                for(var decoder:fields.entrySet())if(type.className().equals(decoder.getValue().request().parameters().get("class")))
                    decoded.put(decoder.getKey(),decoder.getValue().decoder().decode(byRequest.get(decoder.getValue().request().id()),fact,workspace,cut,run,generation));
                for(String alias:aliases)port.alias(alias,object);
                for(var member:program.compactPlan().cslBindings())if(member.isAttribute()&&member.targetRef().qualifier().equals(target)) {
                    String field=member.targetRef().member();var attr=cls.attribute(field,true);
                    if(attr==null)throw new IllegalArgumentException("COMPACT_NATIVE_ATTRIBUTE_MISSING:"+target+"."+field);
                    Value value=UndefinedValue.instance;String quality="COMPACT_ATTRIBUTE_UNAVAILABLE";
                    String slot="";
                    if(member.sourceRef() instanceof SourceReference.ArtifactPropertyRef ref) {
                        var ownerProps=props.getOrDefault(sourceAlias,props.getOrDefault(physical,Map.of()));
                        var matches=ownerProps.getOrDefault(ref.propertyName(),List.of());var property=matches.size()==1?matches.getFirst():null;
                        if(matches.size()>1)quality="COMPACT_PROPERTY_AMBIGUOUS";
                        if(cut.sourceCompleteness().get("cartago")==Completeness.COMPLETE&&property!=null&&property.completeness()==Completeness.COMPLETE&&property.projectionStatus()==ProjectionStatus.MATERIALIZED_FAITHFULLY) {
                            Object values=property.values().get("values"),types=property.values().get("valueTypes");
                            String converter=declaration(member).attributes().getOrDefault("converter","");
                            if(converter.isEmpty()&&values instanceof List<?> vs&&types instanceof List<?> ts) {
                                value=org.tzi.use.plugins.jacamo.semantics.csl.ScalarValueCodec.decode(attr.type().toString(),vs,ts);
                                quality=value.isUndefined()?"COMPACT_SOURCE_UNDEFINED":"";
                                slot=property.id().canonical();
                            } else if(converter.equals("JASON_ATOM_TO_STRING/1.0")) {
                                var candidates=decoded.values().stream().filter(d->d.quality()==Completeness.COMPLETE)
                                        .map(d->CanonicalJson.object(d.values().get("rawProperties")).get(ref.propertyName())).filter(Objects::nonNull).toList();
                                if(!candidates.isEmpty()) {
                                    var canonical=candidates.stream().map(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson::hash).distinct().toList();
                                    if(canonical.size()!=1)throw new IllegalArgumentException("COMPACT_PROPERTY_RECEIPTS_DISAGREE:"+ref.propertyName());
                                    value=new StringValue(RuntimeValueConverters.atomName(candidates.getFirst()));slot=property.id().canonical();quality="";
                                }else quality="COMPACT_TYPED_PROPERTY_RECEIPT_UNAVAILABLE";
                            }
                        }
                    } else if(member.sourceRef() instanceof SourceReference.ArtifactFieldRef ref) {
                        var receipt=decoded.get(ref.className()+":"+ref.fieldName());
                        if(receipt!=null&&receipt.quality()==Completeness.COMPLETE) {
                            Object raw=CanonicalJson.object(receipt.values().get("fields")).get(ref.fieldName());
                            value=RuntimeInstanceLinker.convertValue(raw,member.conversion());
                            slot="field:"+physical+":"+ref.fieldName()+":"+receipt.cutId();
                            quality=value.isUndefined()?"COMPACT_FIELD_VALUE_UNSUPPORTED":"";
                        } else quality=receipt==null?"COMPACT_FIELD_CAPABILITY_MISSING":receipt.diagnostic();
                    } else if(member.sourceRef() instanceof SourceReference.ArtifactIdentityRef ref
                            &&fact.completeness()==Completeness.COMPLETE&&workspace!=null&&workspace.completeness()==Completeness.COMPLETE) {
                        String source=switch(ref.field()) {
                            case "workspace"->text(workspace.values(),"fullName");
                            case "workspaceName"->text(workspace.values(),"name");
                            default->text(fact.values(),ref.field());
                        };
                        if(!source.isBlank()){value=new StringValue(source);slot="identity:"+physical+":"+ref.field();quality="";}
                    }
                    port.value(object,field,value);port.metadata(object,"compact:quality:"+field,quality);
                    if(!value.isUndefined()) {
                        org.tzi.use.plugins.jacamo.semantics.runtime.CanonicalObservationMappingBindings.recordPropertyWritten(port,object,attr,value);
                        port.metadata(object,"propertySlot:"+field,slot);
                    }
                    if(value.isUndefined()) {
                        port.metadata(object,"propertyAbsent:"+field,"true");
                        port.metadata(object,"propertySlot:"+field,"");
                        port.metadata(object,"observedValueHash:"+field,"");
                    }
                }
                outcome.add(new Outcome(true,"COMPACT-ATTRIBUTE-PROJECTION",identity,object,"SOURCE_QUALIFIED_ARTIFACT"));
            }
        }
        // Transport completeness alone is not a closed artifact inventory.
        // Removal requires the producer's declared source cut or exact disposal.
        boolean closed=false;Set<String> retained=Set.of();String disposed="";
        if(event!=null&&CartagoSourceCut.KIND.equals(event.after().get("normalizedEventKind"))) {
            var sourceCut=CartagoSourceCut.require(event);
            closed=CartagoSourceCut.kinds(sourceCut).contains(RuntimeFactKind.ARTIFACT);
            retained=sourceCut.facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT).map(f->f.id().canonical()).collect(java.util.stream.Collectors.toSet());
        } else if(event!=null&&event.sourceId().equals("cartago")&&event.factKind()==RuntimeFactKind.ARTIFACT
                &&Set.of(RuntimeEventKind.REMOVED,RuntimeEventKind.DISPOSED).contains(event.kind())
                &&event.completeness()==Completeness.COMPLETE&&"DELETE_CARTAGO_ARTIFACT".equals(event.after().get("normalizedEventKind"))) {
            if(event.entityId()==null||!event.entityId().authority().equals("cartago")||!event.entityId().kind().equals("artifact"))
                throw new IllegalArgumentException("COMPACT_DISPOSAL_IDENTITY_REQUIRED");
            disposed=event.entityId().canonical();
        }
        if(closed||!disposed.isBlank()) {
            for(var object:List.copyOf(port.objects()))if(ownsRemoval(port,object)&&port.metadata(object,"compact:kind").equals("ARTIFACT")&&port.metadata(object,"compact:epoch").equals(run+":"+generation)
                    &&(closed&&!retained.contains(port.metadata(object,"compact:physical"))||!disposed.isBlank()&&disposed.equals(port.metadata(object,"compact:physical")))) {
                String id=port.metadata(object,"semanticId");port.delete(id);
                outcome.add(new Outcome(false,"COMPACT-DISPOSAL",id,null,"SOURCE_INVENTORY_REMOVAL"));
            }
        }
        if(completeInventory(cut,event)) {
            var current=cut.facts().stream().filter(f->f.kind()==RuntimeFactKind.WORKSPACE).map(f->f.id().canonical()).collect(java.util.stream.Collectors.toSet());
            for(var object:List.copyOf(port.objects()))if(ownsRemoval(port,object)&&port.metadata(object,"compact:kind").equals("WORKSPACE_CONTEXT")
                    &&port.metadata(object,"compact:epoch").equals(run+":"+generation)&&!current.contains(port.metadata(object,"compact:physical"))) {
                String id=port.metadata(object,"semanticId");port.delete(id);outcome.add(new Outcome(false,"COMPACT-WORKSPACE-DISPOSAL",id,null,"SOURCE_INVENTORY_REMOVAL"));
            }
        }
        return List.copyOf(outcome);
    }
    /** Occurrences require source-qualified actor/scope/commitment evidence.
     * Returning none cannot fabricate a complete cast or an execution certificate. */
    public List<OccurrenceProjection.Scope> occurrences(Port port,String run,long generation,String revision){
        CompactNativePreparation.requireSupportedRuntime(program);if(!this.revision.equals(revision))throw new IllegalArgumentException("COMPACT_OCCURRENCE_REVISION_MISMATCH");
        var scopes=new ArrayList<OccurrenceProjection.Scope>();
        for(var context:port.objects())if(ownsRemoval(port,context)&&port.metadata(context,"compact:kind").equals("WORKSPACE_CONTEXT")&&port.metadata(context,"compact:epoch").equals(run+":"+generation)) {
            String source=port.metadata(context,"compact:sourceRef"),id=port.metadata(context,"semanticId");
            var members=new ArrayList<OccurrenceProjection.Member>();
            for(var binding:program.compactPlan().istarBindings())if(binding.kind()==IStarBinding.BindingKind.ACTOR_MAPPING&&binding.sourceRef().raw().equals(source)) {
                String actor=binding.targetSymbol().name();var selves=new TreeMap<String,String>();
                for(var element:program.goalBindings().elements().values())if(element.actorId().equals(actor)&&!element.derived()) {
                    if(element.selfDeclaration().isBlank())continue; // An unconditioned leaf has no invented predicate receiver.
                    if(!context.cls().name().equals(element.selfDeclaration()))throw new IllegalStateException("COMPACT_WORKSPACE_SELF_TYPE_MISMATCH:"+element.elementId());
                    selves.put(element.elementId(),id);
                }
                String instance="workspace_"+org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(List.of(actor,id));
                members.add(new OccurrenceProjection.Member(actor,actor,instance,id,"",id,id,selves));
            }
            if(members.isEmpty())continue;
            var issues="COMPLETE".equals(port.metadata(context,"compact:inventory"))?List.<String>of():List.of("COMPACT_WORKSPACE_INVENTORY_UNPROVEN");
            String topology=org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(List.of(program.hash(),id,org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(members)));
            scopes.add(new OccurrenceProjection.Scope(id,source,id,id,topology,port.metadata(context,"compact:cut"),members,issues,
                    Map.of("workspace",port.metadata(context,"compact:workspace")),Map.of()));
        }
        return List.copyOf(scopes);
    }
    public List<TaskDemandProjection.Demand> taskDemands(Port port,String run,long generation,String revision){
        CompactNativePreparation.requireSupportedRuntime(program);return List.of();
    }
    @Override public boolean ownsRemoval(Port port,MObject object){return program.hash().equals(port.metadata(object,"compact:program"));}
    @Override public String rawInputRuleId(){return "COMPACT-SOURCE-CUT";}
    @Override public String observationRejectionCode(){return "COMPACT_OBSERVATION_REJECTED";}
    @Override public String mappingRejectionCode(){return "COMPACT_PROJECTION_REJECTED";}
    private CompactMappingSpecification.Correspondence declaration(CslBinding binding) {
        return program.compactPlan().specification().cslMappings().stream()
                .filter(m->m.source().equals(binding.sourceRef().raw())&&m.target().equals(binding.targetRef().raw())).findFirst().orElseThrow();
    }
    private boolean completeInventory(RuntimeSnapshot cut,RuntimeEvent event) {
        if(cut.sourceCompleteness().get("cartago")!=Completeness.COMPLETE)return false;
        if(event!=null&&CartagoSourceCut.KIND.equals(event.after().get("normalizedEventKind"))) {
            var source=CartagoSourceCut.require(event);var metadata=source.captureMetadata();
            return CartagoSourceCut.kinds(source).containsAll(program.compactPlan().cartagoKinds())
                    &&"LOCAL_SOURCE_STABLE_INTERVAL".equals(metadata.get("cutPolicy"))
                    &&matchingCapture(metadata);
        }
        var metadata=cut.captureMetadata();
        if(!"PER_SOURCE_WATERMARK_AND_TOPOLOGY_VALIDATED".equals(metadata.get("cutPolicy"))
                ||!Objects.equals(cut.startWatermarks().get("cartago"),cut.endWatermarks().get("cartago")))return false;
        Object sources=metadata.get("sourceEvidence");
        if(!(sources instanceof Map<?,?> evidence)||!(evidence.get("cartago") instanceof Map<?,?> cartago))return false;
        return matchingCapture(CanonicalJson.object(cartago))
                &&"OFFICIAL_CONTROLLER_ENUMERATION_THEN_SELECTED_ARTIFACT_INFO_AND_VALUES".equals(cartago.get("inventoryAcquisition"));
    }
    private boolean matchingCapture(Map<String,Object> metadata) {
        var scope=program.compactPlan().captureScope();
        return scope.hash().equals(metadata.get("captureScopeHash"))&&scope.evidence().equals(metadata.get("captureScope"))
                &&metadata.get("capturedFactKinds") instanceof List<?> kinds&&new HashSet<>(kinds).equals(program.compactPlan().cartagoKinds().stream().map(Enum::name).collect(java.util.stream.Collectors.toSet()));
    }
    private static String text(Map<String,Object> values,String key){return values.get(key) instanceof String s?s:"";}
}
