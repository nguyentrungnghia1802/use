package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;

class BridgeMirrorStateMachineTest {
    @Test void duplicateIsIdempotentButConflictGapStaleAndUnknownIncarnationQuarantine() {
        var duplicate=live();RuntimeEvent first=event("e1","session",2,"revision",1,RuntimeEventKind.CHANGED,id("inc-1"),Map.of("active",false));
        assertTrue(duplicate.apply(first));String fingerprint=duplicate.fingerprint();assertFalse(duplicate.apply(first));assertEquals(fingerprint,duplicate.fingerprint());

        var conflict=live();conflict.apply(first);RuntimeEvent changed=event("e1","session",2,"revision",1,RuntimeEventKind.CHANGED,id("inc-1"),Map.of("active","different"));
        assertThrows(BridgeProtocolException.class,()->conflict.apply(changed));assertEquals(BridgeClientState.RESYNC_REQUIRED,conflict.state());

        var gap=live();assertFalse(gap.apply(event("gap","session",2,"revision",1,RuntimeEventKind.GAP,null,Map.of())));assertEquals(BridgeClientState.RESYNC_REQUIRED,gap.state());
        var stale=live();assertThrows(BridgeProtocolException.class,()->stale.apply(event("stale","old",2,"revision",1,RuntimeEventKind.CHANGED,id("inc-1"),Map.of())));assertEquals(BridgeClientState.RESYNC_REQUIRED,stale.state());
        var unknown=live();assertThrows(BridgeProtocolException.class,()->unknown.apply(event("unknown","session",2,"revision",1,RuntimeEventKind.CHANGED,id("inc-2"),Map.of())));assertEquals(BridgeClientState.RESYNC_REQUIRED,unknown.state());
    }

    @Test void transactionalReplacementDropsOldIncarnationAndChangesFingerprint() {
        var mirror=live();String before=mirror.fingerprint();var replacement=fact(id("inc-2"),true);
        mirror.replace(new RuntimeSnapshot("snapshot-2","revision",Instant.EPOCH,Instant.EPOCH,Map.of("source",new SourceWatermark("source",4)),Map.of("source",new SourceWatermark("source",4)),1,List.of(replacement),Map.of("source",Completeness.COMPLETE),"f".repeat(64)));
        assertFalse(mirror.facts().containsKey(id("inc-1").canonical()));assertTrue(mirror.facts().containsKey(id("inc-2").canonical()));assertNotEquals(before,mirror.fingerprint());
    }

    private BridgeMirrorStateMachine live(){var mirror=new BridgeMirrorStateMachine(16);mirror.acceptIdentity("session",2,"revision");mirror.replace(new RuntimeSnapshot("snapshot","revision",Instant.EPOCH,Instant.EPOCH,Map.of("source",new SourceWatermark("source",0)),Map.of("source",new SourceWatermark("source",0)),1,List.of(fact(id("inc-1"),true)),Map.of("source",Completeness.COMPLETE),"f".repeat(64)));return mirror;}
    private RuntimeFact fact(BridgeEntityId id,boolean active){return new RuntimeFact(id,RuntimeFactKind.AGENT,Map.of("active",active),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());}
    private BridgeEntityId id(String incarnation){return new BridgeEntityId("jason","agent","runtime-agent","project","alice",incarnation);}
    private RuntimeEvent event(String eventId,String session,long generation,String revision,long sequence,RuntimeEventKind kind,BridgeEntityId id,Map<String,Object> after){return new RuntimeEvent(eventId,session,generation,revision,"jason","source",sequence,Instant.EPOCH,kind,kind==RuntimeEventKind.GAP?null:RuntimeFactKind.AGENT,kind==RuntimeEventKind.GAP?null:ProjectionStatus.MATERIALIZED_FAITHFULLY,id,null,"corr","",Map.of(),after,new SourceWatermark("source",sequence),Completeness.COMPLETE,List.of());}
}
