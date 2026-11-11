package org.jacamo.bridge.contract;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VerificationObservationTest {
    private static VerificationObservation frame(VerificationObservation.Kind kind,Map<String,Object> data) {
        return new VerificationObservation(kind,"actual-subject",Completeness.PARTIAL,"SOURCE_EVIDENCE_INCOMPLETE",
                VerificationObservation.SourceTime.unavailable("NO_QUALIFIED_CLOCK"),data);
    }
    @Test void eachSchemaRoundTripsWithoutTurningPartialOrMissingTimeIntoTruth() {
        var bodies=Map.<VerificationObservation.Kind,Map<String,Object>>of(
                VerificationObservation.Kind.ARTIFACT_FIELD_CUT,Map.of("artifactIdentity","artifact-id","classHash","a".repeat(64),"fieldCutId","cut-1","properties",Map.of(),"fields",Map.of(),"coherence","UNAVAILABLE","probeEvidence",Map.of()),
                VerificationObservation.Kind.JASON_STEP,Map.of("agentIdentity","agent-id","goalInvocation","goal-1","stepInvocation","step-1","sourceStepId","parsed-step-id","phase","ENABLED","artifactIdentity","","schemeIdentity","","operationInvocation","","bindings",Map.of()),
                VerificationObservation.Kind.GOAL_TRANSITION,Map.of("boardIdentity","board-id","goalDefinition","goal-definition","previousState","","state","enabled","sourceCutId","cut-1","goalEvidence",Map.of()),
                VerificationObservation.Kind.NPL_LIFECYCLE,Map.of("boardIdentity","board-id","activationKey","","state","UNFULFILLED","origin","LISTENER_TERMINAL","norm",Map.of("type","literal"),"identityStatus","UNAVAILABLE","sourceObservation",Map.of()),
                VerificationObservation.Kind.CLOCK,Map.of("clockIdentity","clock-id","sourceProof",Map.of("origin","RECORDED_TEST_ONLY")));
        for(var body:bodies.entrySet()) {
            var expected=frame(body.getKey(),body.getValue());var event=expected.event("run",7,"revision",1,Instant.EPOCH,"invocation","cause");
            var decoded=ContractPayloads.event(CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(ContractPayloads.event(event)))));
            assertEquals(expected,VerificationObservation.require(decoded));assertEquals(ProjectionStatus.EVIDENCE_ONLY,decoded.projectionStatus());
            assertEquals(Completeness.PARTIAL,decoded.completeness());assertEquals("UNAVAILABLE",VerificationObservation.require(decoded).sourceTime().status());
            assertThrows(UnsupportedOperationException.class,()->expected.data().put("mutated",true));
        }
    }
    @Test void sourceTimeIsSeparateFromBridgeReceiptTimeAndNeverImplicitlyQualifiedAsMonotonic() {
        var sourceTime=new VerificationObservation.SourceTime("OBSERVED","recorded-clock","EPOCH_MILLISECONDS","123456","RECORDED_TEST_SOURCE_API","");
        var frame=new VerificationObservation(VerificationObservation.Kind.CLOCK,"recorded-clock",Completeness.COMPLETE,"",sourceTime,Map.of("clockIdentity","recorded-clock","sourceProof",Map.of()));
        var event=frame.event("run",1,"revision",1,Instant.EPOCH,"","");assertEquals("123456",VerificationObservation.require(event).sourceTime().rawValue());
        assertEquals(Instant.EPOCH,event.observedAt());
        assertThrows(ContractException.class,()->new VerificationObservation.SourceTime("OBSERVED","clock","EPOCH_MILLISECONDS","9223372036854775808","API",""));
        assertThrows(ContractException.class,()->new VerificationObservation(VerificationObservation.Kind.CLOCK,"clock",Completeness.COMPLETE,"",VerificationObservation.SourceTime.unavailable("MISSING"),Map.of("clockIdentity","clock","sourceProof",Map.of())));
    }
    @Test void unknownVersionsMalformedTypesExtraAuthorityAndMixedEpochsFailClosed() {
        var event=frame(VerificationObservation.Kind.CLOCK,Map.of("clockIdentity","clock","sourceProof",Map.of())).event("run",1,"revision",1,Instant.EPOCH,"","");
        var data=new HashMap<>(CanonicalJson.object(event.after().get(VerificationObservation.KEY)));data.put("version","99.0");
        assertThrows(ContractException.class,()->VerificationObservation.require(withPayload(event,data)));
        var valid=new HashMap<>(CanonicalJson.object(event.after().get(VerificationObservation.KEY)));var scope=new HashMap<>(CanonicalJson.object(valid.get("scope")));scope.put("generation",1.5);valid.put("scope",scope);
        assertThrows(ContractException.class,()->VerificationObservation.require(withPayload(event,valid)));
        scope.put("generation",1);scope.put("modelRevision","other-revision");assertThrows(ContractException.class,()->VerificationObservation.require(withPayload(event,valid)));
        assertThrows(ContractException.class,()->frame(VerificationObservation.Kind.CLOCK,Map.of("clockIdentity",false,"sourceProof",Map.of())));
        assertThrows(ContractException.class,()->frame(VerificationObservation.Kind.CLOCK,Map.of("clockIdentity","clock","sourceProof",Map.of(),"xmlPath","http://untrusted.invalid/input")));
        assertThrows(ContractException.class,()->frame(VerificationObservation.Kind.CLOCK,Map.of("clockIdentity","clock","sourceProof",Map.of("tooLarge","x".repeat(VerificationObservation.MAX_BYTES)))));
    }
    @Test void aSnapshotCannotAdoptAnObservationFromTheFutureOrAnotherRun() {
        var event=frame(VerificationObservation.Kind.CLOCK,Map.of("clockIdentity","clock","sourceProof",Map.of())).event("run",2,"revision",3,Instant.EPOCH,"","");
        var fact=new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),List.of());
        var marks=Map.of(event.sourceId(),new SourceWatermark(event.sourceId(),2));
        var cut=new RuntimeSnapshot("snapshot","revision",Instant.EPOCH,Instant.EPOCH,marks,marks,1,List.of(fact),Map.of(event.sourceId(),Completeness.PARTIAL),"f".repeat(64));
        assertThrows(ContractException.class,()->VerificationObservation.require(fact,cut,"run",2));
        var validMarks=Map.of(event.sourceId(),event.watermark());
        var valid=new RuntimeSnapshot("snapshot","revision",Instant.EPOCH,Instant.EPOCH,validMarks,validMarks,1,List.of(fact),Map.of(event.sourceId(),Completeness.PARTIAL),"f".repeat(64));
        assertThrows(ContractException.class,()->VerificationObservation.require(fact,valid,"foreign-run",2));
        assertThrows(ContractException.class,()->VerificationObservation.require(fact,valid,"run",3));
        assertEquals(VerificationObservation.require(event),VerificationObservation.require(fact,valid,"run",2));
    }
    private static RuntimeEvent withPayload(RuntimeEvent e,Map<String,Object> payload) {
        return new RuntimeEvent(e.eventId(),e.sessionId(),e.generation(),e.modelRevision(),e.subsystem(),e.sourceId(),e.sourceSequence(),e.observedAt(),e.kind(),e.factKind(),e.projectionStatus(),e.entityId(),e.relationId(),e.correlationId(),e.causationId(),e.before(),Map.of(VerificationObservation.KEY,payload),e.watermark(),e.completeness(),e.evidence());
    }
}
