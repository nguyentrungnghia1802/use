package org.tzi.use.state;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import org.tzi.use.semantic.engine.frontend.IStarFrontend;
import org.vnu.sme.goal.dsl.istar.mm.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionIStarStateTest {
    private final Path example=Path.of("../../examples/auction/state-only").toAbsolutePath().normalize();
    private CslStateVerifier verifier()throws Exception{return new CslStateVerifier(example.resolve("attribute-mapping.xml"),example.resolve("auction.csl"),example.resolve("auction.istar"));}
    private CslStateVerifier.Snapshot snapshot(long sequence,boolean running,double bid,String winner,boolean second) {
        return snapshot(sequence,running,bid,winner,second,true);
    }
    private CslStateVerifier.Snapshot snapshot(long sequence,boolean running,double bid,String winner,boolean second,boolean focused) {
        var nodes=List.of(new CslStateVerifier.Node("org","organization","auction","aorg","/main/aorg","",Map.of()),
                new CslStateVerifier.Node("group","group","auctionGroup","agrp","/main/aorg","org",Map.of()),
                new CslStateVerifier.Node("agent1","agent","auction_capabilities","bob","","",Map.of()),
                new CslStateVerifier.Node("agent2","agent","auction_capabilities","alice","","",Map.of()));
        var roles=List.of(new CslStateVerifier.Enactment("group","agent1","auctioneer"),new CslStateVerifier.Enactment("group","agent2","participant"));
        var artifacts=new ArrayList<CslStateVerifier.Artifact>();
        artifacts.add(new CslStateVerifier.Artifact("a1-id","auction_env.AuctionArtifact","a1","/main/aorg",Map.of("running",running,"task","ticket","best_bid",bid,"winner",winner),focused?Set.of("agent1","agent2"):Set.of()));
        if(second)artifacts.add(new CslStateVerifier.Artifact("a2-id","auction_env.AuctionArtifact","a2","/main/aorg",Map.of("running",false,"task","other-ticket","best_bid",Double.MAX_VALUE,"winner","no_winner")));
        return new CslStateVerifier.Snapshot("run",sequence,true,artifacts,nodes,roles);
    }
    private boolean status(CslStateVerifier.Result r,String element,String value){return r.elements().stream().anyMatch(e->e.element().equals(element)&&e.status().equals(value));}

    @Test void actualExampleHasFourActorViewsAndPropagatesStateTasksThroughBothRolesToTheOrganization()throws Exception {
        var model=new IStarFrontend().compileScoped(Files.readString(example.resolve("auction.istar")),"auction.istar",EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
        assertEquals(4,model.getActors().size());assertInstanceOf(Agent.class,model.findActor("AuctionGroup").orElseThrow());
        assertInstanceOf(GenericActor.class,model.findActor("auction_capabilities").orElseThrow());
        assertInstanceOf(Role.class,model.findActor("Auctioneer").orElseThrow());assertInstanceOf(Role.class,model.findActor("Participant").orElseThrow());
        assertEquals(3,model.getDependencies().size());
        assertEquals(2,model.findActor("auction_capabilities").orElseThrow().associations().size());
        try(var verifier=verifier()) {
            var initial=verifier.update(snapshot(1,false,Double.MAX_VALUE,"no_winner",true));assertEquals("APPROVE",initial.decision());
            assertFalse(status(initial,"AuctionCompleted","FULFILLED"));
            var opened=verifier.update(snapshot(2,true,Double.MAX_VALUE,"no_winner",true));assertTrue(status(opened,"Auctioneer.start","FULFILLED"));
            assertFalse(status(opened,"Participant.bid","FULFILLED"));
            var bid=verifier.update(snapshot(3,true,10,"no_winner",true));assertTrue(status(bid,"Participant.bid","FULFILLED"));assertTrue(status(bid,"O_bid","FULFILLED"));
            var closed=verifier.update(snapshot(4,false,10,"alice",true));assertEquals("APPROVE",closed.decision());
            assertTrue(status(closed,"Auctioneer.decide","FULFILLED"));assertTrue(status(closed,"AuctionCompleted","FULFILLED"));
            assertEquals(1,closed.elements().stream().filter(e->e.element().equals("AuctionCompleted")&&e.status().equals("FULFILLED")).count(),"Other Auction must retain independent history");
            assertEquals(1,closed.elements().stream().filter(e->e.element().equals("AuctionCompleted")&&!e.status().equals("FULFILLED")).count());
        }
    }
    @Test void startRequiresObservedCreationFocusAndRunningAndInitIsNotACompletedStop()throws Exception {
        try(var verifier=verifier()) {
            var created=verifier.update(snapshot(1,false,Double.MAX_VALUE,"no_winner",false,false));
            assertTrue(status(created,"CreateAuctionArtifact","FULFILLED"));
            assertFalse(status(created,"FocusAuctionArtifact","FULFILLED"));
            assertFalse(status(created,"Auctioneer.start","FULFILLED"));
            assertFalse(status(created,"StopAuction","FULFILLED"));
            var focused=verifier.update(snapshot(2,false,Double.MAX_VALUE,"no_winner",false,true));
            assertTrue(status(focused,"FocusAuctionArtifact","FULFILLED"));assertFalse(status(focused,"Auctioneer.start","FULFILLED"));
            var opened=verifier.update(snapshot(3,true,Double.MAX_VALUE,"no_winner",false,true));
            assertTrue(status(opened,"StartAuction","FULFILLED"));assertTrue(status(opened,"Auctioneer.start","FULFILLED"));
            assertFalse(status(opened,"Auctioneer.decide","FULFILLED"));
        }
    }
    @Test void stopWithoutABidMayPublishNoWinnerAndDoesNotPretendThatABidHappened()throws Exception {
        try(var verifier=verifier()) {
            verifier.update(snapshot(1,true,Double.MAX_VALUE,"no_winner",false));
            var stopped=verifier.update(snapshot(2,false,Double.MAX_VALUE,"no_winner",false));
            assertEquals("APPROVE",stopped.decision());assertTrue(status(stopped,"StopAuction","FULFILLED"));
            assertTrue(status(stopped,"Auctioneer.decide","FULFILLED"));assertFalse(status(stopped,"Participant.bid","FULFILLED"));
            assertFalse(status(stopped,"AuctionCompleted","FULFILLED"));
        }
    }
    @Test void runningWithoutTheAuctioneersFocusIsAStateViolationAndUnpublishedWinnerIsValidWhileBidding()throws Exception {
        try(var verifier=verifier()) {
            var invalid=verifier.update(snapshot(1,true,Double.MAX_VALUE,"no_winner",false,false));
            assertEquals("HOLD_FAILURE",invalid.decision());
            assertTrue(invalid.checks().stream().anyMatch(c->c.id().equals("Auction::RunningAuctionIsObserved")&&c.verdict().equals("FAIL")));
        }
        try(var verifier=verifier()) {
            verifier.update(snapshot(1,true,Double.MAX_VALUE,"no_winner",false));
            var improved=verifier.update(snapshot(2,true,20,"no_winner",false));assertEquals("APPROVE",improved.decision());
            var unchanged=verifier.update(snapshot(3,true,20,"no_winner",false));assertEquals("APPROVE",unchanged.decision());
            assertTrue(status(unchanged,"Participant.bid","FULFILLED"));
            // Identical state cannot distinguish a worse successful bid from no operation.
            assertEquals(improved.elements(),unchanged.elements());
        }
    }
    @Test void aBestBidIncreaseViolatesTheActualArtifactRuleEvenAfterTheBidGoalWasFulfilled()throws Exception {
        try(var verifier=verifier()) {
            verifier.update(snapshot(1,true,Double.MAX_VALUE,"no_winner",false));
            verifier.update(snapshot(2,true,20,"no_winner",false));
            var invalid=verifier.update(snapshot(3,true,30,"no_winner",false));
            assertEquals("HOLD_FAILURE",invalid.decision());
            assertTrue(status(invalid,"BidNeverIncreases","VIOLATED"));
            assertTrue(status(invalid,"Participant.bid","FULFILLED"),"Historical achievement does not hide a new state violation");
        }
    }
}
