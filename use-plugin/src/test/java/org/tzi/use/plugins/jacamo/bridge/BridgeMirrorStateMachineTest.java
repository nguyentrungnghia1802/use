package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;

class BridgeMirrorStateMachineTest {
    @Test void coherentSourceCutIsNotADomainIncarnationAndCannotBypassItsScopeChecks() {
        var mark=new SourceWatermark("cartago",1);
        var artifact=new RuntimeFact(new BridgeEntityId("cartago","environment","artifact","/main","counter","physical"),RuntimeFactKind.ARTIFACT,Map.of("name","counter"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var cut=new RuntimeSnapshot("capture","revision",Instant.EPOCH,Instant.EPOCH,Map.of("cartago",mark),Map.of("cartago",mark),1,List.of(artifact),Map.of("cartago",Completeness.COMPLETE),"proof",Map.of("capturedFactKinds",List.of("ARTIFACT")));
        var after=CartagoSourceCut.payload(cut);
        var event=new RuntimeEvent("cut","session",2,"revision","cartago","cartago",1,Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,new BridgeEntityId("cartago","environment","source-cut","env","capture","session"),null,"","",Map.of(),after,mark,Completeness.COMPLETE,List.of());
        var mirror=live();var before=mirror.facts();assertTrue(mirror.apply(event));assertFalse(mirror.apply(event));
        assertTrue(mirror.facts().entrySet().containsAll(before.entrySet()));assertEquals(artifact,mirror.facts().get(artifact.id().canonical()));
        assertFalse(mirror.facts().containsKey(event.entityId().canonical()));assertEquals(BridgeClientState.LIVE,mirror.state());
        var invalid=new java.util.LinkedHashMap<>(after);invalid.put("sourceCutVersion","unknown");
        var bad=new RuntimeEvent("bad-cut","session",2,"revision","cartago","cartago",1,Instant.EPOCH,event.kind(),event.factKind(),event.projectionStatus(),event.entityId(),null,"","",Map.of(),invalid,mark,Completeness.COMPLETE,List.of());
        var rejected=live();var unchanged=rejected.facts();assertThrows(BridgeProtocolException.class,()->rejected.apply(bad));
        assertEquals(unchanged,rejected.facts());assertEquals(BridgeClientState.RESYNC_REQUIRED,rejected.state());
    }
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
        mirror.replace(new RuntimeSnapshot("snapshot-2","revision",Instant.EPOCH,Instant.EPOCH,Map.of("source",new SourceWatermark("source",4)),Map.of("source",new SourceWatermark("source",4)),1,List.of(replacement),Map.of("jason",Completeness.COMPLETE),"f".repeat(64)));
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

    @Test void staleAndMissingSourceSnapshotsCannotReplaceAcceptedFacts() {
        var mirror=live();mirror.apply(event("new","session",2,"revision",1,RuntimeEventKind.CHANGED,id("inc-1"),Map.of("active",false)));
        var facts=mirror.facts();assertThrows(BridgeProtocolException.class,()->mirror.replace(new RuntimeSnapshot("old","revision",Instant.EPOCH,Instant.EPOCH,Map.of("source",new SourceWatermark("source",0)),Map.of("source",new SourceWatermark("source",0)),1,List.of(),Map.of("jason",Completeness.COMPLETE),"old")));assertEquals(facts,mirror.facts());assertEquals(BridgeClientState.RESYNC_REQUIRED,mirror.state());
        assertThrows(BridgeProtocolException.class,()->mirror.replace(new RuntimeSnapshot("missing","revision",Instant.EPOCH,Instant.EPOCH,Map.of(),Map.of(),1,List.of(),Map.of(),"missing")));assertEquals(facts,mirror.facts());
    }
    @Test void partialReplacementPreservesLastKnownFactsAndNewEpochClearsTheLedger() {
        var mirror=live();var fact=mirror.facts().get(id("inc-1").canonical());
        mirror.replace(new RuntimeSnapshot("partial","revision",Instant.EPOCH,Instant.EPOCH,Map.of("source",new SourceWatermark("source",0)),Map.of("source",new SourceWatermark("source",0)),1,List.of(),Map.of("jason",Completeness.PARTIAL),"partial"));assertEquals(fact,mirror.facts().get(id("inc-1").canonical()));
        var e=event("id-reused","session",2,"revision",1,RuntimeEventKind.CREATED,id("inc-2"),Map.of());mirror.apply(e);
        mirror.acceptIdentity("session",3,"revision");mirror.replace(new RuntimeSnapshot("new-run","revision",Instant.EPOCH,Instant.EPOCH,Map.of("source",new SourceWatermark("source",0)),Map.of("source",new SourceWatermark("source",0)),1,List.of(),Map.of("jason",Completeness.COMPLETE),"new"));
        assertTrue(mirror.apply(event("id-reused","session",3,"revision",1,RuntimeEventKind.CREATED,id("inc-2"),Map.of())));
        assertThrows(BridgeProtocolException.class,()->mirror.acceptIdentity("session",2,"revision"));
    }
    @Test void missingAuthorityCoverageCannotRetireItsLastKnownFacts() {
        var mirror=live();
        var known=mirror.facts();
        var waters=Map.of("source",new SourceWatermark("source",0));
        mirror.replace(new RuntimeSnapshot("missing-jason","revision",Instant.EPOCH,Instant.EPOCH,
                waters,waters,1,List.of(),Map.of("cartago",Completeness.COMPLETE),"missing-jason"));
        assertEquals(known,mirror.facts(),"Cartago completeness says nothing about an absent Jason player");
        mirror.replace(new RuntimeSnapshot("complete-jason","revision",Instant.EPOCH,Instant.EPOCH,
                waters,waters,1,List.of(),Map.of("jason",Completeness.COMPLETE),"complete-jason"));
        assertTrue(mirror.facts().isEmpty(),"Only the matching complete authority may certify absence");
    }

    private RuntimeEvent norm(long seq,RuntimeEventKind kind,BridgeEntityId norm) {
        return new RuntimeEvent("norm:"+seq,"session",2,"revision","npl","npl",seq,Instant.EPOCH,kind,RuntimeFactKind.NORM_INSTANCE,ProjectionStatus.EVIDENCE_ONLY,norm,null,"","",Map.of(),Map.of("state",kind==RuntimeEventKind.NORM_ACTIVATED?"ACTIVE":kind==RuntimeEventKind.NORM_FULFILLED?"FULFILLED":"INACTIVE"),new SourceWatermark("npl",seq),Completeness.COMPLETE,List.of());
    }
    @Test void authoritativeNewNormActivationAndTerminalUpdatesStayInTheLiveStream() {
        var mirror=live();var id=new BridgeEntityId("npl","organisation","norm-instance","board","norm","activation");var activated=norm(1,RuntimeEventKind.NORM_ACTIVATED,id);
        assertTrue(mirror.apply(activated));assertFalse(mirror.apply(activated));assertTrue(mirror.apply(norm(2,RuntimeEventKind.NORM_FULFILLED,id)));assertEquals("FULFILLED",mirror.facts().get(id.canonical()).values().get("state"));assertTrue(mirror.apply(norm(3,RuntimeEventKind.NORM_INACTIVATED,id)));assertEquals(BridgeClientState.LIVE,mirror.state());
        var unknown=live();assertThrows(BridgeProtocolException.class,()->unknown.apply(norm(1,RuntimeEventKind.NORM_FULFILLED,id)));
        var wrong=live();assertThrows(BridgeProtocolException.class,()->wrong.apply(norm(1,RuntimeEventKind.NORM_ACTIVATED,id("wrong-kind"))));
    }
    @Test void lateDiscoveredNormAcceptsTerminalObservationWithBoundPublicCreationEvidence() {
        var mirror=live();
        var id=new BridgeEntityId("npl","organisation","norm-instance","board","activation-key","session:board-uuid");
        var terminal=new RuntimeEvent("late-terminal","session",2,"revision","npl","npl",1,Instant.EPOCH,
                RuntimeEventKind.NORM_FULFILLED,RuntimeFactKind.NORM_INSTANCE,ProjectionStatus.EVIDENCE_ONLY,
                id,null,"","",Map.of(),Map.of("state","FULFILLED","observationVersion","NPL_OBSERVATION/1.0",
                    "activationIdentityStatus","BOUND_PUBLIC_CREATED_ANNOTATION","activationKey","activation-key"),
                new SourceWatermark("npl",1),Completeness.COMPLETE,List.of());
        assertTrue(mirror.apply(terminal));
        assertEquals("FULFILLED",mirror.facts().get(id.canonical()).values().get("state"));
        assertEquals(BridgeClientState.LIVE,mirror.state());
    }
    private RuntimeEvent focusEvent(String eventId,long sequence,RuntimeEventKind kind,BridgeEntityId id,boolean focused) {
        return new RuntimeEvent(eventId,"session",2,"revision","cartago","source",sequence,Instant.EPOCH,kind,
                RuntimeFactKind.RELATION_STATE,ProjectionStatus.MATERIALIZED_FAITHFULLY,id,null,"corr","",Map.of(),
                Map.of("normalizedEventKind","SET_CARTAGO_FOCUS","artifactSemanticId",id.scope(),
                        "agentSemanticId",id.localId(),"focused",focused),
                new SourceWatermark("source",sequence),Completeness.COMPLETE,List.of());
    }

    private BridgeMirrorStateMachine live(){var mirror=new BridgeMirrorStateMachine(16);mirror.acceptIdentity("session",2,"revision");mirror.replace(new RuntimeSnapshot("snapshot","revision",Instant.EPOCH,Instant.EPOCH,Map.of("source",new SourceWatermark("source",0)),Map.of("source",new SourceWatermark("source",0)),1,List.of(fact(id("inc-1"),true)),Map.of("jason",Completeness.COMPLETE),"f".repeat(64)));return mirror;}
    private RuntimeFact fact(BridgeEntityId id,boolean active){return new RuntimeFact(id,RuntimeFactKind.AGENT,Map.of("active",active),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());}
    private BridgeEntityId id(String incarnation){return new BridgeEntityId("jason","agent","runtime-agent","project","alice",incarnation);}
    private RuntimeEvent event(String eventId,String session,long generation,String revision,long sequence,RuntimeEventKind kind,BridgeEntityId id,Map<String,Object> after){return new RuntimeEvent(eventId,session,generation,revision,"jason","source",sequence,Instant.EPOCH,kind,kind==RuntimeEventKind.GAP?null:RuntimeFactKind.AGENT,kind==RuntimeEventKind.GAP?null:ProjectionStatus.MATERIALIZED_FAITHFULLY,id,null,"corr","",Map.of(),after,new SourceWatermark("source",sequence),Completeness.COMPLETE,List.of());}
}
