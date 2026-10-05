package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;

class BridgeMirrorStateMachineTest {
    @Test void callbackBoundaryAdvancesTheStreamWithoutInventingADomainObject() {
        var mirror=live();
        mirror.apply(event("cause","session",2,"revision",1,RuntimeEventKind.CHANGED,id("inc-1"),Map.of("active",false)));
        var facts=mirror.facts();String fingerprint=mirror.fingerprint();
        var checkpoint=boundary("boundary-1",1,2,"corr",1,"source","cause");
        assertTrue(mirror.apply(checkpoint));
        assertFalse(mirror.apply(checkpoint));
        assertEquals(facts,mirror.facts());assertEquals(fingerprint,mirror.fingerprint());
        assertTrue(mirror.apply(boundary("boundary-2",2,2,"corr",1,"source","cause")));
        assertEquals(BridgeClientState.LIVE,mirror.state());
        assertEquals(facts,mirror.facts());
    }

    @Test void boundaryStillRequiresExactOwnerObservedCausationCorrelationAndOrdering() {
        var invalid=List.of(
                boundary("stale",1,1,"corr",1,"source","cause"),
                boundary("uncorrelated",1,2,"",1,"source","cause"),
                boundary("unobserved",1,2,"corr",2,"source","cause"),
                boundary("unknown-source",1,2,"corr",1,"other","cause"),
                boundary("wrong-cause",1,2,"corr",1,"source","different"));
        for(var checkpoint:invalid) {
            var mirror=live();mirror.apply(event("cause","session",2,"revision",1,RuntimeEventKind.CHANGED,id("inc-1"),Map.of("active",false)));
            var facts=mirror.facts();
            assertThrows(BridgeProtocolException.class,()->mirror.apply(checkpoint),checkpoint.eventId());
            assertEquals(BridgeClientState.RESYNC_REQUIRED,mirror.state());assertEquals(facts,mirror.facts());
        }
        var gap=live();gap.apply(event("cause","session",2,"revision",1,RuntimeEventKind.CHANGED,id("inc-1"),Map.of()));
        gap.apply(boundary("first",1,2,"corr",1,"source","cause"));
        assertThrows(BridgeProtocolException.class,()->gap.apply(boundary("gap",3,2,"corr",1,"source","cause")));
        assertEquals(BridgeClientState.RESYNC_REQUIRED,gap.state());
    }

    private RuntimeEvent boundary(String eventId,long sequence,long generation,String correlation,long causeSequence,String source,String cause) {
        return new RuntimeEvent(eventId,"session",generation,"revision","bridge",VerificationBoundary.SOURCE_ID,sequence,Instant.EPOCH,
                RuntimeEventKind.STREAM_BOUNDARY,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,
                new BridgeEntityId("bridge","runtime","verification-boundary","session","batch","cause"),null,
                correlation,"cause",Map.of(),Map.of("boundarySource",source,"boundarySourceSequence",causeSequence,"boundaryEventId",cause),
                new SourceWatermark(VerificationBoundary.SOURCE_ID,sequence),Completeness.COMPLETE,List.of());
    }

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

    @Test void exactCartagoFocusHasItsOwnCreationRemovalAndReappearanceLifecycle() {
        var mirror=live();
        var focusId=new BridgeEntityId("cartago","environment","focus","cartago:artifact:env:/main:uuid",
                "cartago:agent:env:/main:alice:1","relation");
        var focused=focusEvent("focus-1",1,RuntimeEventKind.FOCUSED,focusId,true);
        assertTrue(mirror.apply(focused));
        assertTrue(mirror.facts().containsKey(focusId.canonical()));
        assertFalse(mirror.apply(focused));
        assertTrue(mirror.apply(focusEvent("unfocus-2",2,RuntimeEventKind.UNFOCUSED,focusId,false)));
        assertFalse(mirror.facts().containsKey(focusId.canonical()));
        assertTrue(mirror.apply(focusEvent("focus-3",3,RuntimeEventKind.FOCUSED,focusId,true)));
        assertTrue(mirror.facts().containsKey(focusId.canonical()));
        assertEquals(BridgeClientState.LIVE,mirror.state());

        var invalid=live();
        assertThrows(BridgeProtocolException.class,()->invalid.apply(event("wrong-kind","session",2,"revision",1,
                RuntimeEventKind.FOCUSED,id("unknown"),Map.of())));
        assertEquals(BridgeClientState.RESYNC_REQUIRED,invalid.state());
    }

    private RuntimeEvent focusEvent(String eventId,long sequence,RuntimeEventKind kind,BridgeEntityId id,boolean focused) {
        return new RuntimeEvent(eventId,"session",2,"revision","cartago","source",sequence,Instant.EPOCH,kind,
                RuntimeFactKind.RELATION_STATE,ProjectionStatus.MATERIALIZED_FAITHFULLY,id,null,"corr","",Map.of(),
                Map.of("normalizedEventKind","SET_CARTAGO_FOCUS","artifactSemanticId",id.scope(),
                        "agentSemanticId",id.localId(),"focused",focused),
                new SourceWatermark("source",sequence),Completeness.COMPLETE,List.of());
    }

    private BridgeMirrorStateMachine live(){var mirror=new BridgeMirrorStateMachine(16);mirror.acceptIdentity("session",2,"revision");mirror.replace(new RuntimeSnapshot("snapshot","revision",Instant.EPOCH,Instant.EPOCH,Map.of("source",new SourceWatermark("source",0)),Map.of("source",new SourceWatermark("source",0)),1,List.of(fact(id("inc-1"),true)),Map.of("source",Completeness.COMPLETE),"f".repeat(64)));return mirror;}
    private RuntimeFact fact(BridgeEntityId id,boolean active){return new RuntimeFact(id,RuntimeFactKind.AGENT,Map.of("active",active),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());}
    private BridgeEntityId id(String incarnation){return new BridgeEntityId("jason","agent","runtime-agent","project","alice",incarnation);}
    private RuntimeEvent event(String eventId,String session,long generation,String revision,long sequence,RuntimeEventKind kind,BridgeEntityId id,Map<String,Object> after){return new RuntimeEvent(eventId,session,generation,revision,"jason","source",sequence,Instant.EPOCH,kind,kind==RuntimeEventKind.GAP?null:RuntimeFactKind.AGENT,kind==RuntimeEventKind.GAP?null:ProjectionStatus.MATERIALIZED_FAITHFULLY,id,null,"corr","",Map.of(),after,new SourceWatermark("source",sequence),Completeness.COMPLETE,List.of());}
}
