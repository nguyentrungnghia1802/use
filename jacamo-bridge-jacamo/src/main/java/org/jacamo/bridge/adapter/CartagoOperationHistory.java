package org.jacamo.bridge.adapter;

import java.util.*;
import cartago.*;
import org.jacamo.bridge.contract.*;

/** Bounded actual logger receipts for an explicitly pinned Artifact class. No operation/goal credit policy. */
public final class CartagoOperationHistory implements ObservationCapabilityRegistry.Handle {
    public static final String PROVIDER="cartago.operation-history.v1",VERSION="CARTAGO_OPERATION_HISTORY/1.0";
    private static final String FRAMEWORK="67830177f2cb450876ac3b43aa90741cbc0b72079f5fcdc8320ff2f11b17c012";
    private final ObservationCapabilityRequest request;
    private final FrameworkObservationProviders.Context context;
    private final String type,hash,resource,provider;
    private final Map<String,History> histories=new LinkedHashMap<>();
    private long lastSequence;private String lastEventHash="";private boolean closed;
    private static final class History {
        boolean fromCreation,broken;
        final List<RuntimeEvent> events=new ArrayList<>();
        final Map<String,RuntimeEvent> pending=new HashMap<>();
        final Set<String> terminals=new HashSet<>();
    }
    public CartagoOperationHistory(ObservationCapabilityRequest request,FrameworkObservationProviders.Context context)throws Exception {
        this.request=request;this.context=context;
        if(!request.provider().equals(PROVIDER)||!Set.of("class","hash","sourceRef","sourceHash","sourcePath").containsAll(request.parameters().keySet()))throw new IllegalArgumentException("OPERATION_HISTORY_REQUEST_UNSUPPORTED");
        type=text(request.parameters(),"class");hash=text(request.parameters(),"hash");
        if(!type.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")||!hash.matches("[0-9a-f]{64}")||context.run()==null||context.run().isBlank()||context.generation()<0)throw new IllegalArgumentException("OPERATION_HISTORY_CLASS_PIN_OR_SCOPE_REQUIRED");
        ProviderSourcePin.require(request,context.root());
        Class<?> selected=Class.forName(type,false,Thread.currentThread().getContextClassLoader());
        var binary=DeclaredArtifactTypeProof.readActualBinary(selected);var framework=DeclaredArtifactTypeProof.readActualBinary(Artifact.class);
        if(!Artifact.class.isAssignableFrom(selected)||binary==null||framework==null||!hash.equals(AdapterEvidence.digest(binary.bytes()))||!FRAMEWORK.equals(AdapterEvidence.digest(framework.bytes())))throw new IllegalArgumentException("OPERATION_HISTORY_BINARY_MISMATCH");
        resource=binary.resource().toExternalForm();provider=selected.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
    }
    /** Called on the existing CArtAgO source publication lane, before transport. */
    public synchronized void observe(RuntimeEvent event) {
        if(closed)return;
        if(!event.sourceId().equals("cartago"))return;
        if(!context.run().equals(event.sessionId())||context.generation()!=event.generation()||!context.source().modelRevision().equals(event.modelRevision()))throw new IllegalArgumentException("OPERATION_HISTORY_SOURCE_EPOCH_MISMATCH");
        String eventHash=AdapterEvidence.digest(CanonicalJson.encode(ContractPayloads.event(event)));
        if(event.sourceSequence()==lastSequence&&eventHash.equals(lastEventHash))return;
        if(lastSequence!=0&&event.sourceSequence()!=lastSequence+1)histories.values().forEach(h->h.broken=true);
        if(event.sourceSequence()<=lastSequence)return;
        lastSequence=event.sourceSequence();lastEventHash=eventHash;
        if(event.kind()==RuntimeEventKind.GAP){histories.values().forEach(h->h.broken=true);return;}
        if(event.entityId()==null||event.factKind()!=RuntimeFactKind.ARTIFACT||!event.entityId().authority().equals("cartago"))return;
        String uuid=event.entityId().incarnation();var after=event.after();
        if(event.kind()==RuntimeEventKind.DISPOSED){histories.remove(uuid);return;}
        boolean birth=event.kind()==RuntimeEventKind.CREATED&&type.equals(after.get("artifactTypeJavaClassName"));
        Object signature=after.get("operationSignature");
        boolean operation=Set.of(RuntimeEventKind.STARTED,RuntimeEventKind.SUCCEEDED,RuntimeEventKind.FAILED).contains(event.kind())&&!event.correlationId().isBlank()
                &&signature instanceof List<?> parts&&parts.size()==4&&(histories.containsKey(uuid)||type.equals(parts.getFirst()));
        if(!birth&&!operation)return;
        if(!histories.containsKey(uuid)&&histories.size()>=256){histories.values().forEach(h->h.broken=true);return;}
        var history=histories.computeIfAbsent(uuid,k->new History());
        if(birth) {
            var actual=CanonicalJson.object(after.get("artifactProviderObservation"));
            if(history.fromCreation||!"COMPLETE".equals(actual.get("status"))||!hash.equals(actual.get("classHash"))||!uuid.equals(actual.get("artifactUuid"))||!resource.equals(actual.get("resource"))||!provider.equals(actual.get("provider")))history.broken=true;
            else history.fromCreation=true;
        }
        if(operation) {
            if(event.completeness()!=Completeness.COMPLETE||!event.correlationId().equals(after.get("opId"))||!"CARTAGO_OPID_AGENT_BODY_ID".equals(after.get("performerAuthority"))||!(after.get("performerSemanticId") instanceof String performer)||performer.isBlank())history.broken=true;
            String invocation=event.correlationId();
            if(event.kind()==RuntimeEventKind.STARTED) {
                if(!"started".equals(after.get("phase"))||history.terminals.contains(invocation)||history.pending.putIfAbsent(invocation,event)!=null)history.broken=true;
            }else {
                var start=history.pending.remove(invocation);
                if(start==null||!history.terminals.add(invocation)||!Objects.equals(start.after().get("performerSemanticId"),after.get("performerSemanticId"))||!Objects.equals(start.after().get("operationSignature"),signature)
                        ||!Objects.equals(after.get("phase"),event.kind()==RuntimeEventKind.SUCCEEDED?"completed":"failed"))history.broken=true;
            }
            if(history.events.size()<512)history.events.add(event);else history.broken=true;
        }
    }
    public Map<String,Object> capture(ArtifactId identity) {
        if(!identity.getArtifactType().equals(type))return Map.of();
        synchronized(this){if(closed)return unavailable("PROVIDER_CLOSED");}
        var actual=CartagoArtifactProviderObservation.capture(context.environment(),identity);
        if(!"COMPLETE".equals(actual.get("status"))||!hash.equals(actual.get("classHash"))||!resource.equals(actual.get("resource"))||!provider.equals(actual.get("provider")))return unavailable("ACTUAL_PROVIDER_OR_INCARNATION_UNAVAILABLE");
        synchronized(this) {
            if(closed)return unavailable("PROVIDER_CLOSED");String uuid=identity.getId().toString();
            if(!histories.containsKey(uuid)&&histories.size()>=256)return unavailable("HISTORY_CAPACITY");
            var history=histories.computeIfAbsent(uuid,k->new History());
            var result=new LinkedHashMap<String,Object>();result.put("version",VERSION);result.put("status","COMPLETE");result.put("run",context.run());result.put("generation",context.generation());result.put("revision",context.source().modelRevision());
            result.put("javaClass",type);result.put("classHash",hash);result.put("resource",resource);result.put("provider",provider);result.put("frameworkArtifactHash",FRAMEWORK);
            result.put("artifact",new BridgeEntityId("cartago","environment","artifact",identity.getWorkspaceId().getFullName(),identity.getName(),uuid).canonical());result.put("artifactUuid",uuid);result.put("workspaceUuid",identity.getWorkspaceId().getUUID().toString());
            result.put("lastSourceSequence",lastSequence);result.put("fromCreation",history.fromCreation);result.put("historyQuality",history.fromCreation&&!history.broken?"COMPLETE":"INCOMPLETE");
            result.put("scopeMeaning","ICARTAGOLOGGER_OBSERVED_START_TERMINAL_PREFIX_SUSPEND_RESUME_UNSUPPORTED");result.put("events",history.events.stream().map(ContractPayloads::event).toList());
            try {result.put("hash",AdapterEvidence.digest(CanonicalJson.encode(result,128*1024)));CanonicalJson.encode(result,128*1024);return JasonStructuredTerms.immutable(result);}
            catch(ContractException overflow){history.broken=true;return unavailable("HISTORY_RECEIPT_CAPACITY");}
        }
    }
    private static Map<String,Object> unavailable(String diagnostic){return Map.of("version",VERSION,"status","INCOMPLETE","diagnostic",diagnostic);}
    public synchronized Capability capability(){return new Capability(request.id(),closed?CapabilityStatus.UNAVAILABLE:CapabilityStatus.COMPLETE,List.of(VERSION),closed?"PROVIDER_CLOSED":"Pinned actual Artifact logger receipts; per-cut history completeness remains authoritative");}
    @Override public synchronized void close(){if(closed)return;closed=true;histories.clear();}
    private static String text(Map<String,Object> values,String key){if(!(values.get(key) instanceof String value)||value.isBlank())throw new IllegalArgumentException("OPERATION_HISTORY_REQUEST_FIELD:"+key);return value;}
}
