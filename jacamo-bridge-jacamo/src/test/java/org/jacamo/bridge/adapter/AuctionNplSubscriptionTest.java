package org.jacamo.bridge.adapter;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuctionNplSubscriptionTest {
    @Test void actualCreationLoggerAttachesBeforeFirstActionWithoutWaitingForAPollAndCloseRevokesIt()throws Exception {
        var env=CartagoEnvironment.getInstance();env.init();var actor=new CartagoBasicContext("earlyNorm"+UUID.randomUUID().toString().replace("-",""));var boards=new ArrayList<ora4mas.nopl.OrgArt>();
        var events=new CopyOnWriteArrayList<RuntimeEvent>();ArtifactId id=null;
        try(var source=new NplBoardSnapshotSource("creation-run",()->List.copyOf(boards));var cartago=new CartagoSnapshotSource(env,List.of(),"creation-run",null,source::artifactCreated)) {
            source.attach(events::add);cartago.attach(e->{});
            id=actor.makeArtifact(actor.getJoinedWspId("main"),"earlyBoard"+UUID.randomUUID().toString().replace("-",""),NplLiveBoardObservationTest.Board.class.getName());boards.add(NplLiveBoardObservationTest.Board.instance);
            var proof=CanonicalJson.object(((List<?>)source.captureMetadata().get("selectedSubscriptions")).getFirst());
            assertEquals(id.getId().toString(),proof.get("boardArtifactIdentity"));assertEquals("CARTAGO_CREATION_BEFORE_RETURN",proof.get("origin"));assertEquals(true,proof.get("emptyBaselineAfterListener"));
            actor.doAction(id,new Op("load","scope early { norm n: start -> obligation(alice,true,done(sch_a1,bid,alice),`now`+`10 seconds`). }"));actor.doAction(id,new Op("addFact","start"));
            NplLiveBoardObservationTest.await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED));
            assertEquals("CREATED_EVENT_OBSERVED",events.getFirst().after().get("history"));assertEquals("COMPLETE",CanonicalJson.object(events.getFirst().after().get("sourceLifecycle")).get("status"));
            source.close();int count=events.size();actor.doAction(id,new Op("addFact","done(sch_a1,bid,alice)"));Thread.sleep(60);assertEquals(count,events.size());assertTrue(source.capture().isEmpty());
        }finally {
            if(id!=null){NplLiveBoardObservationTest.Board.instance.getNormativeEngine().stop();NplLiveBoardObservationTest.Board.instance.destroy();env.getController(id.getWorkspaceId().getFullName()).removeArtifact(id.getName());}
            for(var agent:env.getController("/main").getCurrentAgents())if(agent.getAgentName().equals(actor.getName()))env.resolveWSP("/main").getWorkspace().quitAgent(agent);env.shutdown();
        }
    }
}
