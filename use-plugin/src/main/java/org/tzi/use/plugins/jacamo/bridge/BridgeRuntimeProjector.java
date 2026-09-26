package org.tzi.use.plugins.jacamo.bridge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.BridgeRelationId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeSnapshot;
import org.tzi.use.plugins.jacamo.runtime.MutationStatus;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.semantic.Dimension;
import org.tzi.use.plugins.jacamo.semantic.SemanticId;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.plugins.jacamo.trace.TraceRecord;

/** Exact-ID adapter from neutral Bridge state/events into the frozen Runtime Mapping V2 engine. */
public final class BridgeRuntimeProjector {
    public record ProjectionResult(int materialized, List<String> evidenceOnly, List<String> unavailable) {
        public ProjectionResult { evidenceOnly=List.copyOf(evidenceOnly);unavailable=List.copyOf(unavailable); }
    }
    private final Set<String> sourceIds;private final NativeSemanticAdapter.Result model;private final TraceIndex trace;private final RuntimeMutationEngine mutations;
    private final Map<String,Long> acceptedSourceWatermarks=new LinkedHashMap<>();private long internalSequence;
    public BridgeRuntimeProjector(String sourceId,NativeSemanticAdapter.Result model,TraceIndex trace,RuntimeMutationEngine mutations){this(Set.of(required(sourceId)),model,trace,mutations);}
    public BridgeRuntimeProjector(Set<String> sourceIds,NativeSemanticAdapter.Result model,TraceIndex trace,RuntimeMutationEngine mutations){
        if(sourceIds==null||sourceIds.isEmpty()||sourceIds.stream().anyMatch(id->id==null||id.isBlank()))throw new IllegalArgumentException("sourceIds");
        this.sourceIds=Set.copyOf(sourceIds);this.model=model;this.trace=trace;this.mutations=mutations;
    }

    public synchronized ProjectionResult applySnapshot(RuntimeSnapshot snapshot){
        var evidence=new ArrayList<String>();var unavailable=new ArrayList<String>();var normalized=new ArrayList<org.tzi.use.plugins.jacamo.runtime.RuntimeEvent>();
        for(RuntimeFact fact:snapshot.facts().stream().sorted(Comparator.comparing(f->f.id().canonical())).toList()){
            if(fact.projectionStatus()==ProjectionStatus.UNAVAILABLE||fact.completeness()==Completeness.UNAVAILABLE){unavailable.add(fact.id().canonical());continue;}
            if(fact.projectionStatus()!=ProjectionStatus.MATERIALIZED_FAITHFULLY||fact.completeness()!=Completeness.COMPLETE){evidence.add(fact.id().canonical());continue;}
            String kind=(String)fact.values().get("normalizedEventKind");if(kind==null){evidence.add(fact.id().canonical());continue;}
            BridgeRelationId binding=fact.relations().stream().filter(r->r.relationKind().equals("runtime-model-binding")).findFirst().orElseThrow(()->new BridgeProtocolException("BRIDGE_RUNTIME_BINDING_REQUIRED:"+fact.id().canonical()));
            normalized.add(normalize(fact.id(),binding,kind,fact.values(),"",++internalSequence,snapshot.captureEndedAt()));
        }
        mutations.applySnapshot(new org.tzi.use.plugins.jacamo.runtime.RuntimeSnapshot(snapshot.snapshotId(),snapshot.captureEndedAt(),internalSequence,normalized,snapshot.stateFingerprint()));
        acceptedSourceWatermarks.clear();
        for(String sourceId:sourceIds)acceptedSourceWatermarks.put(sourceId,snapshot.endWatermarks().containsKey(sourceId)?snapshot.endWatermarks().get(sourceId).sequence():0L);
        return new ProjectionResult(normalized.size(),evidence,unavailable);
    }

    public synchronized boolean apply(org.jacamo.bridge.contract.RuntimeEvent event){
        long acceptedSourceWatermark=acceptedSourceWatermarks.getOrDefault(event.sourceId(),-1L);
        if(event.sourceSequence()<=acceptedSourceWatermark)throw new BridgeProtocolException("BRIDGE_RUNTIME_EVENT_REWIND");
        if(event.projectionStatus()!=ProjectionStatus.MATERIALIZED_FAITHFULLY||event.completeness()!=Completeness.COMPLETE){acceptedSourceWatermarks.put(event.sourceId(),event.sourceSequence());return false;}
        Object raw=event.after().get("normalizedEventKind");if(!(raw instanceof String kind))return false;
        if(event.entityId()==null||event.relationId()==null||!event.relationId().relationKind().equals("runtime-model-binding"))throw new BridgeProtocolException("BRIDGE_RUNTIME_BINDING_REQUIRED:"+event.eventId());
        var result=mutations.apply(normalize(event.entityId(),event.relationId(),kind,event.after(),event.correlationId(),++internalSequence,event.observedAt()));
        if(result.status()!=MutationStatus.APPLIED)throw new BridgeProtocolException("BRIDGE_RUNTIME_MUTATION_REJECTED:"+result.diagnostic());
        acceptedSourceWatermarks.put(event.sourceId(),event.sourceSequence());return true;
    }

    private org.tzi.use.plugins.jacamo.runtime.RuntimeEvent normalize(BridgeEntityId runtime,BridgeRelationId binding,String kind,Map<String,Object> values,String correlation,long sequence,java.time.Instant time){
        SemanticId semantic=exactSemantic(runtime,binding);TraceRecord target=trace.bySemanticId(semantic.value()).stream().filter(r->r.targetKind().equals("OBJECT")).findFirst().orElseThrow(()->new BridgeProtocolException("BRIDGE_RUNTIME_TRACE_TARGET_REQUIRED:"+semantic.value()));
        var current=trace.byRuntimeKey(runtime.canonical());if(current.isEmpty())trace.registerRuntimeKey(target.traceId(),runtime.canonical());else if(!current.get().traceId().equals(target.traceId()))throw new BridgeProtocolException("BRIDGE_RUNTIME_ALIAS_CONFLICT:"+runtime.canonical());
        org.tzi.use.plugins.jacamo.runtime.RuntimeEventKind eventKind;try{eventKind=org.tzi.use.plugins.jacamo.runtime.RuntimeEventKind.valueOf(kind);}catch(Exception error){throw new BridgeProtocolException("BRIDGE_RUNTIME_EVENT_KIND_UNSUPPORTED:"+kind,error);}
        var payload=new LinkedHashMap<String,Object>(values);payload.remove("normalizedEventKind");
        return org.tzi.use.plugins.jacamo.runtime.RuntimeEvent.create("bridge:"+runtime.canonical()+":"+sequence,time,sequence,dimension(runtime),eventKind,runtime.canonical(),semantic.value(),payload,correlation==null||correlation.isBlank()?null:correlation);
    }
    private SemanticId exactSemantic(BridgeEntityId runtime,BridgeRelationId binding){
        if(binding.endpoints().stream().noneMatch(runtime::equals))throw new BridgeProtocolException("BRIDGE_RUNTIME_BINDING_ENDPOINT_MISSING:"+runtime.canonical());
        var matches=binding.endpoints().stream().filter(id->!id.equals(runtime)).map(id->model.bridgeIdentityMap().get(id.canonical())).filter(java.util.Objects::nonNull).distinct().toList();
        if(matches.size()!=1)throw new BridgeProtocolException("BRIDGE_RUNTIME_BINDING_AMBIGUOUS:"+binding.canonical());return matches.getFirst();
    }
    private Dimension dimension(BridgeEntityId id){return switch(id.dimension().toLowerCase(java.util.Locale.ROOT)){case "agent"->Dimension.AGENT;case "environment"->Dimension.ENVIRONMENT;case "organisation","organization"->Dimension.ORGANISATION;default->throw new BridgeProtocolException("BRIDGE_RUNTIME_DIMENSION_UNSUPPORTED:"+id.dimension());};}
    private static String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("sourceId");return value;}
}
