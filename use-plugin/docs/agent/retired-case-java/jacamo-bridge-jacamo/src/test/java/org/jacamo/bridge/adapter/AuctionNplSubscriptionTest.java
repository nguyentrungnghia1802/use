package org.jacamo.bridge.adapter;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuctionNplSubscriptionTest {
    @Test void discoveryBeforeCreationCallbackMustNotLoseTheVerifiedEmptyCreationBaseline()throws Exception {
        var env=CartagoEnvironment.getInstance();env.init();var actor=new CartagoBasicContext("discoveryRace"+UUID.randomUUID().toString().replace("-",""));
        var boards=new CopyOnWriteArrayList<ora4mas.nopl.OrgArt>();var events=new CopyOnWriteArrayList<RuntimeEvent>();ArtifactId id=null;
        try(var source=new NplBoardSnapshotSource("discovery-run",()->List.copyOf(boards));
            var cartago=new CartagoSnapshotSource(env,List.of(),"discovery-run",null,created->{
                try {
                var descriptor=env.resolveWSP(created.getWorkspaceId().getFullName()).getWorkspace().getArtifactDescriptor(created.getName());
                if(descriptor==null||!(descriptor.getArtifact() instanceof ora4mas.nopl.OrgArt board))return;
                boards.add(board);source.capture(); // Force discovery to win, while actual makeArtifact has not returned.
                var before=CanonicalJson.object(((List<?>)source.captureMetadata().get("selectedSubscriptions")).getFirst());
                assertEquals("PUBLIC_ENGINE_DISCOVERY",before.get("origin"));assertEquals(false,before.get("emptyBaselineAfterListener"));
                source.artifactCreated(created);
                var after=CanonicalJson.object(((List<?>)source.captureMetadata().get("selectedSubscriptions")).getFirst());
                assertEquals(before.get("sourceSubscriptionNumber"),after.get("sourceSubscriptionNumber"),"Do not replace the existing listener/crosswalk");
                }catch(Exception unavailable){throw new AssertionError(unavailable);}
            })) {
            source.attach(events::add);cartago.attach(e->{});
            id=actor.makeArtifact(actor.getJoinedWspId("main"),"raceBoard"+UUID.randomUUID().toString().replace("-",""),NplLiveBoardObservationTest.Board.class.getName());
            var proof=CanonicalJson.object(((List<?>)source.captureMetadata().get("selectedSubscriptions")).getFirst());
            assertEquals("CARTAGO_CREATION_BEFORE_RETURN",proof.get("origin"));assertEquals(true,proof.get("emptyBaselineAfterListener"));
            actor.doAction(id,new Op("load","scope race { norm n: start -> obligation(alice,true,done,`now`+`10 seconds`). }"));actor.doAction(id,new Op("addFact","start"));
            NplLiveBoardObservationTest.await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED));
            assertEquals(1,events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED).count());
            assertArrayEquals(CanonicalJson.encode(proof),CanonicalJson.encode(events.getFirst().after().get("boardObservationProof")));
            assertEquals("CREATED_EVENT_OBSERVED",events.getFirst().after().get("history"));
        }finally {
            if(id!=null)NplLiveBoardObservationTest.destroy(actor,id);
            for(var agent:env.getController("/main").getCurrentAgents())if(agent.getAgentName().equals(actor.getName()))env.resolveWSP("/main").getWorkspace().quitAgent(agent);env.shutdown();
        }
    }
    @Test void lateCreationNotificationCannotPromoteAnAlreadyObservedActivation()throws Exception {
        var env=CartagoEnvironment.getInstance();env.init();var actor=new CartagoBasicContext("lateProof"+UUID.randomUUID().toString().replace("-",""));ArtifactId id=null;
        var boards=new CopyOnWriteArrayList<ora4mas.nopl.OrgArt>();var events=new CopyOnWriteArrayList<RuntimeEvent>();
        try(var source=new NplBoardSnapshotSource("late-run",()->List.copyOf(boards))) {
            source.attach(events::add);id=actor.makeArtifact(actor.getJoinedWspId("main"),"lateBoard"+UUID.randomUUID().toString().replace("-",""),NplLiveBoardObservationTest.Board.class.getName());
            boards.add(NplLiveBoardObservationTest.Board.instance);source.capture();
            actor.doAction(id,new Op("load","scope late { norm n: start -> obligation(alice,true,done,`now`+`10 seconds`). }"));actor.doAction(id,new Op("addFact","start"));
            NplLiveBoardObservationTest.await(()->!events.isEmpty());var original=events.getFirst().after().get("boardObservationProof");
            source.artifactCreated(id);
            assertArrayEquals(CanonicalJson.encode(original),CanonicalJson.encode(((List<?>)source.captureMetadata().get("selectedSubscriptions")).getFirst()));
            assertEquals(false,CanonicalJson.object(original).get("emptyBaselineAfterListener"));
            actor.doAction(id,new Op("addFact","done"));NplLiveBoardObservationTest.await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_FULFILLED));
            assertTrue(events.stream().allMatch(e->original.equals(e.after().get("boardObservationProof"))));
        }finally {
            if(id!=null)NplLiveBoardObservationTest.destroy(actor,id);
            for(var agent:env.getController("/main").getCurrentAgents())if(agent.getAgentName().equals(actor.getName()))env.resolveWSP("/main").getWorkspace().quitAgent(agent);env.shutdown();
        }
    }
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
            if(id!=null){NplLiveBoardObservationTest.destroy(actor,id);}
            for(var agent:env.getController("/main").getCurrentAgents())if(agent.getAgentName().equals(actor.getName()))env.resolveWSP("/main").getWorkspace().quitAgent(agent);env.shutdown();
        }
    }
}
