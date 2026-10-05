package org.tzi.use.plugins.jacamo.bridge;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.ContractException;
import org.jacamo.bridge.contract.ContractPayloads;
import org.jacamo.bridge.contract.EventLedger;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.RuntimeSnapshot;
import org.jacamo.bridge.contract.VerificationBoundary;

/** Transactional, incarnation-aware neutral mirror ahead of frozen runtime mapping. */
public final class BridgeMirrorStateMachine {
    private final EventLedger ledger;
    private final Map<String, RuntimeFact> facts = new TreeMap<>();
    private final Map<String, Long> sequences = new TreeMap<>();
    private String sessionId;
    private long generation;
    private String modelRevision;
    private String snapshotId;
    private BridgeClientState state = BridgeClientState.DISCONNECTED;

    public BridgeMirrorStateMachine(int idempotencyCapacity) { ledger = new EventLedger(idempotencyCapacity); }
    public synchronized BridgeClientState state() { return state; }
    synchronized void transition(BridgeClientState next) { state = next; }

    public synchronized void acceptIdentity(String session, long nextGeneration, String revision) {
        if (session == null || session.isBlank() || revision == null || revision.isBlank() || nextGeneration < 0)
            throw new BridgeProtocolException("BRIDGE_IDENTITY_INVALID");
        sessionId=session; generation=nextGeneration; modelRevision=revision;
    }

    public synchronized void replace(RuntimeSnapshot snapshot) {
        requireRevision(snapshot.modelRevision());
        Map<String, RuntimeFact> replacement = new TreeMap<>();
        for (RuntimeFact fact : snapshot.facts()) {
            if (replacement.put(fact.id().canonical(), fact) != null)
                throw new BridgeProtocolException("BRIDGE_SNAPSHOT_DUPLICATE_ID:" + fact.id().canonical());
        }
        var replacementSequences = new TreeMap<String,Long>();
        snapshot.endWatermarks().forEach((key,value)->replacementSequences.put(key,value.sequence()));
        facts.clear(); facts.putAll(replacement); sequences.clear(); sequences.putAll(replacementSequences);
        snapshotId=snapshot.snapshotId(); state=BridgeClientState.LIVE;
    }

    public synchronized boolean apply(RuntimeEvent event) {
        try { requireIdentity(event.sessionId(),event.generation(),event.modelRevision()); }
        catch (BridgeProtocolException stale) { state=BridgeClientState.RESYNC_REQUIRED; throw stale; }
        if(state!=BridgeClientState.LIVE) throw new BridgeProtocolException("BRIDGE_NOT_LIVE:"+state);
        if(event.kind()==RuntimeEventKind.GAP || event.kind()==RuntimeEventKind.MODEL_REVISION_CHANGED){state=BridgeClientState.RESYNC_REQUIRED;return false;}
        long previous=sequences.getOrDefault(event.sourceId(),-1L);
        if(event.sourceSequence()<=previous){
            EventLedger.Result duplicate;
            try { duplicate=ledger.inspect(event,ContractPayloads.event(event)); }
            catch(ContractException conflict){state=BridgeClientState.RESYNC_REQUIRED;throw new BridgeProtocolException("BRIDGE_EVENT_CONFLICT",conflict);}
            if(duplicate==EventLedger.Result.DUPLICATE)return false;
            state=BridgeClientState.RESYNC_REQUIRED;throw new BridgeProtocolException("BRIDGE_EVENT_SEQUENCE_REWIND");
        }
        if(previous>=0 && event.sourceSequence()!=previous+1){state=BridgeClientState.RESYNC_REQUIRED;throw new BridgeProtocolException("BRIDGE_EVENT_GAP:"+event.sourceId()+":previous="+previous+":current="+event.sourceSequence());}
        EventLedger.Result accepted;
        try { accepted=ledger.inspect(event,ContractPayloads.event(event)); }
        catch(ContractException conflict){state=BridgeClientState.RESYNC_REQUIRED;throw new BridgeProtocolException("BRIDGE_EVENT_CONFLICT",conflict);}
        if(accepted==EventLedger.Result.DUPLICATE)return false;
        if(event.kind()==RuntimeEventKind.STREAM_BOUNDARY) {
            try { VerificationBoundary.require(event,sequences); }
            catch(ContractException invalid) {
                state=BridgeClientState.RESYNC_REQUIRED;
                throw new BridgeProtocolException("BRIDGE_BOUNDARY_REJECTED",invalid);
            }
            sequences.put(event.sourceId(),event.sourceSequence());
            ledger.accept(event,ContractPayloads.event(event));
            return true;
        }
        if(event.entityId()==null){state=BridgeClientState.RESYNC_REQUIRED;throw new BridgeProtocolException("BRIDGE_EVENT_ENTITY_REQUIRED");}
        String id=event.entityId().canonical();
        boolean cartagoFocus=event.factKind()==RuntimeFactKind.RELATION_STATE
                && event.entityId().authority().equals("cartago")
                && event.entityId().dimension().equals("environment")
                && event.entityId().kind().equals("focus");
        boolean creation=event.kind()==RuntimeEventKind.CREATED || event.kind()==RuntimeEventKind.ADDED
                || event.kind()==RuntimeEventKind.JOINED || event.kind()==RuntimeEventKind.COMMITTED
                || cartagoFocus && event.kind()==RuntimeEventKind.FOCUSED;
        boolean removal=event.kind()==RuntimeEventKind.DISPOSED || event.kind()==RuntimeEventKind.REMOVED
                || event.kind()==RuntimeEventKind.QUIT || event.kind()==RuntimeEventKind.DECOMMITTED
                || cartagoFocus && event.kind()==RuntimeEventKind.UNFOCUSED;
        RuntimeFact current=facts.get(id);
        if(current==null&&!creation){state=BridgeClientState.RESYNC_REQUIRED;throw new BridgeProtocolException("BRIDGE_UNKNOWN_INCARNATION:"+id);}
        if(removal)facts.remove(id);
        else if(current!=null)facts.put(id,new RuntimeFact(current.id(),event.factKind(),event.after(),current.relations(),event.projectionStatus(),event.completeness(),event.evidence()));
        else facts.put(id,new RuntimeFact(event.entityId(),event.factKind(),event.after(),
                    event.relationId()==null?java.util.List.of():java.util.List.of(event.relationId()),
                    event.projectionStatus(),event.completeness(),event.evidence()));
        sequences.put(event.sourceId(),event.sourceSequence());
        ledger.accept(event,ContractPayloads.event(event)); return true;
    }

    private void requireRevision(String revision){if(!revision.equals(modelRevision))throw new BridgeProtocolException("BRIDGE_MODEL_REVISION_STALE");}
    private void requireIdentity(String session,long candidateGeneration,String revision){
        if(!session.equals(sessionId))throw new BridgeProtocolException("BRIDGE_SESSION_STALE");
        if(candidateGeneration!=generation)throw new BridgeProtocolException("BRIDGE_GENERATION_STALE");
        requireRevision(revision);
    }
    public synchronized Map<String,RuntimeFact> facts(){return Map.copyOf(facts);}
    public synchronized String sessionId(){return sessionId;}
    public synchronized long generation(){return generation;}
    public synchronized String modelRevision(){return modelRevision;}
    public synchronized String snapshotId(){return snapshotId;}
    public synchronized String fingerprint(){
        var tree=new TreeMap<String,Object>(); facts.forEach((id,fact)->tree.put(id,Map.of(
                "kind",fact.kind().name(),"values",fact.values(),"relations",fact.relations().stream().map(r->r.canonical()).toList(),
                "projectionStatus",fact.projectionStatus().name(),"completeness",fact.completeness().name())));
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CanonicalJson.encode(tree)));}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
}
