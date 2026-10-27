package org.jacamo.bridge.adapter;

import java.util.*;
import cartago.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.JasonSemanticContract.AgentProgramSemantic;

/** Run-owned neutral providers, attached to existing source boundaries. Contains no mapping/credit rules. */
public final class ProductionObservationSession implements AutoCloseable {
    private final FrameworkObservationProviders.Context context;
    private final List<ObservationCapabilityRequest> requests;
    private final ObservationCapabilityRegistry.Session runtime;
    private final List<ObservationCapabilityRegistry.Session> agents=new ArrayList<>();
    private boolean closed;
    public ProductionObservationSession(FrameworkObservationProviders.Context context,List<ObservationCapabilityRequest> requests) {
        this.context=context;this.requests=List.copyOf(requests);
        // The registry validates the complete identifier inventory, including agent-owned requests.
        if(requests.size()>64||requests.stream().map(ObservationCapabilityRequest::id).distinct().count()!=requests.size())throw new IllegalArgumentException("OBSERVATION_REQUEST_CAPACITY_OR_DUPLICATE");
        runtime=FrameworkObservationProviders.runtime(context).open(requests.stream().filter(r->!r.provider().equals(JasonStepCapability.PROVIDER)).toList());
    }
    public synchronized ObservationCapabilityRegistry.Session agent(jason.asSemantics.TransitionSystem ts,BridgeEntityId identity,AgentProgramSemantic source) {
        requireOpen();
        var selected=requests.stream().filter(r->r.provider().equals(JasonStepCapability.PROVIDER))
                .filter(r->source.sourceDigest().equals(r.parameters().get("sourceHash"))&&source.sourceUri().equals("project:/"+r.parameters().get("sourcePath"))).toList();
        var session=FrameworkObservationProviders.agent(context,ts,identity,source).open(selected);agents.add(session);return session;
    }
    public synchronized List<Capability> capabilities() {
        requireOpen();var values=new TreeMap<String,List<Capability>>();
        for(var c:runtime.capabilities())values.computeIfAbsent(c.name(),k->new ArrayList<>()).add(c);
        for(var a:agents)for(var c:a.status())values.computeIfAbsent(c.name(),k->new ArrayList<>()).add(c);
        return requests.stream().map(r->{var selected=values.getOrDefault(r.id(),List.of());
            if(selected.isEmpty())return new Capability(r.id(),CapabilityStatus.UNAVAILABLE,List.of(),"INCOMPLETE:NO_MATCHING_RUNTIME_OWNER");
            var unavailable=selected.stream().filter(c->c.status()!=CapabilityStatus.COMPLETE).findFirst();
            return unavailable.orElseGet(()->new Capability(r.id(),CapabilityStatus.COMPLETE,selected.stream().flatMap(c->c.evidenceIds().stream()).distinct().toList(),"Run-owned verified framework provider; per-cut quality remains authoritative"));}).toList();
    }
    public synchronized List<Capability> requiredUnavailable(){var required=requests.stream().filter(ObservationCapabilityRequest::required).map(ObservationCapabilityRequest::id).collect(java.util.stream.Collectors.toSet());return capabilities().stream().filter(c->required.contains(c.name())&&c.status()!=CapabilityStatus.COMPLETE).toList();}
    public void pulse(boolean pending) {for(var r:requests)runtime.handle(r.id(),NplClockCapability.class).ifPresent(clock->clock.pulse(pending));}
    /** Positive public descriptor closure of explicitly requested histories; no case rules. */
    boolean observesOperation(ArtifactId artifact,IArtifactOp operation) {
        if(!(operation instanceof ArtifactOpMethod method))return false;
        for(var request:requests)if(request.provider().equals(CartagoOperationHistory.PROVIDER)
                &&artifact.getArtifactType().equals(request.parameters().get("class"))) {
            if(!(request.parameters().get("operations") instanceof List<?> names)||names.contains(method.getMethod().getName()))return true;
        }
        return false;
    }
    /** An invocation can matter without any observable value change. Request
     * allowlists and framework observers decide this before payload acquisition. */
    boolean observesOperationBoundary(ArtifactId artifact,Op operation) {
        for(var request:requests) {
            if(request.provider().equals(ArtifactFieldObservation.PROVIDER)&&artifact.getArtifactType().equals(request.parameters().get("class")))return true;
            if(request.provider().equals(MoiseSchemeCapability.PROVIDER)&&artifact.getArtifactType().equals(ora4mas.nopl.SchemeBoard.class.getName()))return true;
            if(request.provider().equals(CartagoOperationHistory.PROVIDER)&&artifact.getArtifactType().equals(request.parameters().get("class"))
                    &&(!(request.parameters().get("operations") instanceof List<?> names)||names.contains(operation.getName())))return true;
        }
        return false;
    }
    boolean observesPublicationBoundary(ArtifactId artifact) {
        return requests.stream().anyMatch(request->request.provider().equals(ArtifactFieldObservation.PROVIDER)
                &&artifact.getArtifactType().equals(request.parameters().get("class"))
                ||request.provider().equals(MoiseSchemeCapability.PROVIDER)&&artifact.getArtifactType().equals(ora4mas.nopl.SchemeBoard.class.getName()));
    }
    /** Every raw CArtAgO event, in source order, before it is enriched or published. */
    public void observe(RuntimeEvent event) {
        var after=new LinkedHashMap<>(event.after());after.remove(CapabilityObservation.KEY);
        var raw=new RuntimeEvent(event.eventId(),event.sessionId(),event.generation(),event.modelRevision(),event.subsystem(),event.sourceId(),event.sourceSequence(),event.observedAt(),event.kind(),event.factKind(),event.projectionStatus(),event.entityId(),event.relationId(),event.correlationId(),event.causationId(),event.before(),after,event.watermark(),event.completeness(),event.evidence());
        for(var r:requests)runtime.handle(r.id(),CartagoOperationHistory.class).ifPresent(history->history.observe(raw));
    }
    public void capture(Map<String,Object> target,ArtifactId artifact,String origin,long time,OpId invocation,Op operation,long sequence) {
        var receipts=new LinkedHashMap<String,Object>();
        for(var r:requests) {
            Map<String,Object> data=Map.of();
            var fields=runtime.handle(r.id(),ArtifactFieldObservation.class);if(fields.isPresent())data=fields.get().capture(artifact,origin,invocation==null?"":invocation.toString());
            var scheme=runtime.handle(r.id(),MoiseSchemeCapability.class);
            if(scheme.isPresent()) {
                String command=operation!=null&&operation.getName().equals("admCommand")&&operation.getParamValues().length==1&&operation.getParamValues()[0] instanceof String text?text:"";
                data=scheme.get().capture(artifact,origin,time,invocation==null?"":invocation.toString(),invocation==null?null:invocation.getAgentBodyId(),command);
            }
            if(!data.isEmpty())receipts.put(r.id(),receipt(r,data,sequence));
        }
        if(!receipts.isEmpty()) {
            target.put("semanticId",OfficialCartagoAdapter.artifactId(context.environment().getId().toString(),artifact));
            target.put(CapabilityObservation.KEY,receipts);
        }
    }
    /** Restamp callback data at the allocated cut, adding history after the raw event was observed. */
    public void history(Map<String,Object> target,ArtifactId artifact,long sequence) {
        var receipts=new LinkedHashMap<String,Object>();
        if(target.get(CapabilityObservation.KEY) instanceof Map<?,?> previous)for(var entry:previous.entrySet()) {
            var request=requests.stream().filter(r->r.id().equals(entry.getKey())).findFirst().orElseThrow();
            var old=CapabilityObservation.require(entry.getValue(),request);receipts.put(request.id(),receipt(request,old.data(),sequence));
        }
        if(artifact!=null)for(var r:requests)runtime.handle(r.id(),CartagoOperationHistory.class).ifPresent(history->{
            boolean currentCut="UPSERT_CARTAGO_ARTIFACT".equals(target.get("normalizedEventKind"))||target.containsKey(CapabilityObservation.KEY);
            Object signature=target.get("operationSignature");
            boolean selected=signature instanceof List<?> parts&&parts.size()==4&&parts.get(1) instanceof String method
                    &&(!(r.parameters().get("operations") instanceof List<?> names)||names.contains(method));
            // Raw callbacks still all reach observe(). Publish a growing receipt only at an actual
            // selected boundary or a coherent state capture, not every unrelated workspace operation.
            if(currentCut||selected){var data=history.capture(artifact);if(!data.isEmpty())receipts.put(r.id(),receipt(r,data,sequence));}
        });
        if(!receipts.isEmpty()) {
            if(artifact!=null)target.put("semanticId",OfficialCartagoAdapter.artifactId(context.environment().getId().toString(),artifact));
            target.put(CapabilityObservation.KEY,receipts);
        }
    }
    private Map<String,Object> receipt(ObservationCapabilityRequest request,Map<String,Object> data,long sequence) {
        boolean complete="COMPLETE".equals(data.get("status"));
        var scope=new CapabilityObservation.Scope(context.run(),context.generation(),context.source().modelRevision(),"cartago",sequence);
        try{return CapabilityObservation.of(request,scope,complete?Completeness.COMPLETE:Completeness.PARTIAL,complete?"":Objects.toString(data.get("diagnostic"),"PROVIDER_INCOMPLETE"),data).payload();}
        catch(ContractException capacity){return CapabilityObservation.of(request,scope,Completeness.PARTIAL,"PROVIDER_RECEIPT_CAPACITY_OR_SHAPE",Map.of("status","INCOMPLETE","diagnostic","PROVIDER_RECEIPT_CAPACITY_OR_SHAPE")).payload();}
    }
    private void requireOpen(){if(closed)throw new IllegalStateException("OBSERVATION_SESSION_CLOSED");}
    /** Only the source's validated mutation-free capture interval may advance its capture record. */
    List<RuntimeFact> commitStableCapture(List<RuntimeFact> facts,long captured,long committed) {
        if(committed!=captured+1)throw new IllegalArgumentException("CAPABILITY_CAPTURE_SEQUENCE_INVALID");
        var result=new ArrayList<RuntimeFact>();
        for(var fact:facts) {
            if(!(fact.values().get(CapabilityObservation.KEY) instanceof Map<?,?> previous)){result.add(fact);continue;}
            var receipts=new LinkedHashMap<String,Object>();
            for(var entry:previous.entrySet()) {
                var request=requests.stream().filter(r->r.id().equals(entry.getKey())).findFirst().orElseThrow();
                var old=CapabilityObservation.require(entry.getValue(),request);
                if(old.scope().sourceSequence()!=captured||!old.scope().run().equals(context.run())||old.scope().generation()!=context.generation()||!old.scope().revision().equals(context.source().modelRevision()))throw new IllegalArgumentException("CAPABILITY_CAPTURE_NOT_CURRENT");
                var data=new LinkedHashMap<>(old.data());
                if(request.provider().equals(CartagoOperationHistory.PROVIDER)&&old.quality()==Completeness.COMPLETE) {
                    if(!(data.get("lastSourceSequence") instanceof Number number)||number.longValue()!=captured)throw new IllegalArgumentException("OPERATION_CAPTURE_NOT_CURRENT");
                    data.remove("hash");data.put("lastSourceSequence",committed);data.put("hash",AdapterEvidence.digest(CanonicalJson.encode(data)));
                }
                receipts.put(request.id(),receipt(request,data,committed));
            }
            var values=new LinkedHashMap<>(fact.values());values.put(CapabilityObservation.KEY,receipts);
            result.add(new RuntimeFact(fact.id(),fact.kind(),values,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence()));
        }
        return List.copyOf(result);
    }
    @Override public synchronized void close(){if(closed)return;closed=true;RuntimeException failure=null;for(var a:agents)try{a.close();}catch(RuntimeException e){if(failure==null)failure=e;else failure.addSuppressed(e);}try{runtime.close();}catch(RuntimeException e){if(failure==null)failure=e;else failure.addSuppressed(e);}agents.clear();if(failure!=null)throw failure;}
}
