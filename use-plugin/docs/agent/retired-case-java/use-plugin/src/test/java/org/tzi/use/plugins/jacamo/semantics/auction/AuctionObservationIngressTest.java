package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProtocolException;
import static org.junit.jupiter.api.Assertions.*;

class AuctionObservationIngressTest {
    @Test void observerEvidenceAndItsExistingStreamBoundaryAreJournaledWithoutDomainMutation()throws Exception {
        try(var fixture=new AuctionLiveFixture();var facade=AuctionImportTest.facade(fixture.source,new Session())) {
            facade.importProject(Path.of("examples/auction/auction.jcm"),new JaCaMoFacade.ImportSelection("AUCTION_OBSERVED_V1",Path.of("examples/auction/auction_jacamo_to_csl_rules.xml")));
            var projector=AuctionImportTest.projector(facade);var coordinator=projector.coordinator();var initial=coordinator.latest();
            var frame=new VerificationObservation(VerificationObservation.Kind.CLOCK,"recorded-clock",Completeness.PARTIAL,"SOURCE_CLOCK_NOT_QUALIFIED",
                    VerificationObservation.SourceTime.unavailable("RECORDED_TEST_WITHOUT_SOURCE_TIME"),Map.of("clockIdentity","recorded-clock","sourceProof",Map.of("fixture","T01")));
            var event=frame.event("auction-import-run",1,fixture.source.modelRevision(),1,Instant.EPOCH,"","");
            assertFalse(projector.apply(event));assertEquals(1,projector.sourceWatermarks().get(VerificationObservation.SOURCE_ID));
            var identity=new BridgeEntityId("bridge","runtime","verification-boundary",event.sessionId(),"batch",event.eventId());
            var boundary=new RuntimeEvent("recorded-boundary",event.sessionId(),event.generation(),event.modelRevision(),"bridge",VerificationBoundary.SOURCE_ID,1,Instant.EPOCH,
                    RuntimeEventKind.STREAM_BOUNDARY,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,identity,null,event.eventId(),event.eventId(),Map.of(),
                    Map.of("boundarySource",event.sourceId(),"boundaryEventId",event.eventId(),"boundarySourceSequence",event.sourceSequence()),new SourceWatermark(VerificationBoundary.SOURCE_ID,1),Completeness.COMPLETE,List.of());
            assertFalse(projector.apply(boundary));var pending=coordinator.read(coordinator::lastObservation);var completed=coordinator.awaitEvaluation(pending,30000);
            assertEquals(initial.stateHash(),completed.stateHash());assertEquals(initial.stateVersion()+1,completed.stateVersion());
            assertEquals(0,coordinator.system().state().numObjects());assertNotEquals("COMPLETE_OBSERVED",completed.coverage());
            assertTrue(coordinator.history().stream().anyMatch(result->result.eventId().equals(event.eventId())));
            assertTrue(coordinator.history().stream().anyMatch(result->result.eventId().equals(boundary.eventId())));
            assertFalse(projector.resyncRequired());
        }
    }
    @Test void unknownObservationVersionCannotBecomeAcceptedEvidenceOrMutateTheSystem()throws Exception {
        try(var fixture=new AuctionLiveFixture();var facade=AuctionImportTest.facade(fixture.source,new Session())) {
            facade.importProject(Path.of("examples/auction/auction.jcm"),new JaCaMoFacade.ImportSelection("AUCTION_OBSERVED_V1",Path.of("examples/auction/auction_jacamo_to_csl_rules.xml")));
            var projector=AuctionImportTest.projector(facade);var system=projector.coordinator().system();long version=projector.coordinator().latest().stateVersion();
            var event=event(fixture.source.modelRevision(),Map.of("verificationObservation",Map.of("version","99.0","kind","CLOCK")));
            assertThrows(NativeRuntimeProtocolException.class,()->projector.apply(event));
            assertEquals(version,projector.coordinator().latest().stateVersion());assertEquals(0,system.state().numObjects());
            assertTrue(projector.resyncRequired());assertEquals("STALE",facade.runtimeVerificationResult().freshness());
        }
    }
    static RuntimeEvent event(String revision,Map<String,Object> after) {
        var identity=new BridgeEntityId("observer","runtime","verification-observation","auction-import-run","clock","auction-import-run");
        return new RuntimeEvent("observation:1","auction-import-run",1,revision,"observer","observer:verification",1,Instant.EPOCH,
                RuntimeEventKind.CHANGED,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,identity,null,"","",Map.of(),after,
                new SourceWatermark("observer:verification",1),Completeness.PARTIAL,List.of());
    }
}
