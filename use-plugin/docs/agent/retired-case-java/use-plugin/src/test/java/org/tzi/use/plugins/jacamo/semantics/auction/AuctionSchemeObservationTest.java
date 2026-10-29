package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import cartago.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real SchemeBoard calls; no source-model edits or simulated transition DTOs. */
class AuctionSchemeObservationTest {
    @Test void actualDoneAndAdministrativeSatisfactionHaveDifferentEvidenceAndAStoppedSuccessHasNoEffect()throws Exception {
        try(var fixture=new AuctionOrgFixture();var observer=new AuctionSchemeObservation(fixture.live.environment,fixture.live.source,fixture.live.run.id(),fixture.live.run.generation())) {
            var live=fixture.live;fixture.organizationBoard();var group=fixture.group();var id=fixture.scheme("monitor","service");
            var baseline=observer.capture(id,"ARTIFACT_CREATED",System.currentTimeMillis(),"",null,"");assertEquals("COMPLETE",baseline.get("status"));assertEquals(true,baseline.get("fromCreation"));
            action(live,group,new Op("addScheme",id.getName()));
            var actual=(ora4mas.nopl.SchemeBoard)live.environment.resolveWSP(id.getWorkspaceId().getFullName()).getWorkspace().getArtifactDescriptor(id.getName()).getArtifact();
            long limit=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<limit&&actual.getSchState().getGroupsResponsibleFor().stream().noneMatch(g->g.hasPlayer(live.actor.getName(),"auctioneer")))Thread.sleep(10);
            assertTrue(actual.getSchState().getGroupsResponsibleFor().stream().anyMatch(g->g.hasPlayer(live.actor.getName(),"auctioneer")),"Group addScheme sends asynchronous linked role-player updates");
            action(live,id,new Op("commitMission","mAuctioneer"));
            var before=observer.capture(id,"PROPERTY_PUBLICATION",System.currentTimeMillis(),"",null,"");assertFalse(goals(before,"start").get("state").equals("SATISFIED"));
            live.actor.doAction(id,new Op("goalAchieved","start"),5000);var after=observer.capture(id,"PROPERTY_PUBLICATION",System.currentTimeMillis(),"",null,"");
            assertEquals("SATISFIED",goals(after,"start").get("state"));assertTrue(((List<?>)after.get("done")).size()>0);assertEquals(false,after.get("adminBidOverride"));
            var running=ora4mas.nopl.OrgArt.class.getDeclaredField("running");assertTrue(running.trySetAccessible());running.setBoolean(actual,false);
            live.actor.doAction(id,new Op("admCommand","goalSatisfied(bid)"),5000);var stopped=observer.capture(id,"OP_SUCCEEDED",System.currentTimeMillis(),"stopped-command",null,"goalSatisfied(bid)");
            assertFalse(goals(stopped,"bid").get("state").equals("SATISFIED"));assertEquals(false,stopped.get("adminBidOverride"));
            running.setBoolean(actual,true);live.actor.doAction(id,new Op("admCommand","goalSatisfied(bid)"),5000);
            var recovered=observer.capture(id,"PROPERTY_PUBLICATION",System.currentTimeMillis(),"",null,"");assertEquals("SATISFIED",goals(recovered,"bid").get("state"));
            assertEquals(stopped.get("done"),recovered.get("done"),"Administrative satisfaction must not fabricate a done fact");
            assertEquals(false,recovered.get("adminBidOverride"),"A missing correlated STARTED callback is not permission to credit an override");
        }
    }
    @Test void lateAttachAndDifferentSchemeIncarnationsCannotReconstructAnEarlierHistory()throws Exception {
        try(var fixture=new AuctionOrgFixture();var observer=new AuctionSchemeObservation(fixture.live.environment,fixture.live.source,fixture.live.run.id(),fixture.live.run.generation())) {
            var first=fixture.scheme("one","service");var second=fixture.scheme("two","service");
            fixture.live.actor.doAction(first,new Op("admCommand","goalSatisfied(bid)"),5000);
            var late=observer.capture(first,"SNAPSHOT",0,"",null,"");assertEquals(false,late.get("fromCreation"));assertEquals("INCOMPLETE",late.get("historyQuality"));
            assertEquals("SATISFIED",goals(late,"bid").get("state"));assertFalse(late.containsKey("bidCommittedAtSatisfaction"));
            var other=observer.capture(second,"ARTIFACT_CREATED",System.currentTimeMillis(),"",null,"");assertNotEquals(late.get("schemeIdentity"),other.get("schemeIdentity"));
            assertEquals(false,other.get("adminBidOverride"));observer.close();assertEquals("INCOMPLETE",observer.capture(second,"SNAPSHOT",0,"",null,"").get("status"));
        }
    }
    static Map<String,Object> goals(Map<String,Object> cut,String goal){return ((List<?>)cut.get("goals")).stream().map(CanonicalJson::object).filter(g->g.get("name").equals(goal)).findFirst().orElseThrow();}
    static void action(AuctionLiveFixture fixture,ArtifactId id,Op op)throws Exception {
        try{fixture.actor.doAction(id,op,5000);}catch(cartago.util.agent.ActionFailedException error){throw new AssertionError(op+":"+error.getFailureMsg()+":"+error.getFailureDescr(),error);}
    }
}
