package org.jacamo.bridge.adapter;

import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import java.util.*;
import java.util.concurrent.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual NPL timers, not DTO timeout simulation. This isolated normative
 * fixture is separate from the canonical external Auction acceptance. */
class AuctionClockNplTest {
    public static class InitializingBoard extends ora4mas.nopl.NormativeBoard {
        static InitializingBoard instance;
        @Override public void init(){nengine=new npl.NPLInterpreter();instance=this;}
    }
    @Test void publishedArtifactWithAnUninitializedActualEngineIsUnavailableAndRecoversWithoutFalseEmptyPopulation()throws Exception {
        var env=CartagoEnvironment.getInstance();env.init();var actor=new CartagoBasicContext("initializingClock"+UUID.randomUUID().toString().replace("-",""));
        var artifact=actor.makeArtifact(actor.getJoinedWspId("main"),"initializingBoard"+UUID.randomUUID().toString().replace("-",""),InitializingBoard.class.getName());
        try(var source=new NplBoardSnapshotSource("initializing-run",()->List.of(InitializingBoard.instance))) {
            source.attach(event->fail("There is no actual norm event"));
            assertTrue(assertDoesNotThrow(source::hasPendingTimedObligations),"Unreadable engine is an unknown scheduling hint, never no pending obligations");
            assertEquals(Completeness.UNAVAILABLE,source.completeness());assertTrue(source.capture().isEmpty());assertTrue(source.captureMetadata().toString().contains("NPL_BOARD_CAPTURE_UNAVAILABLE"));
            InitializingBoard.instance.getNormativeEngine().init();assertFalse(source.hasPendingTimedObligations());assertEquals(Completeness.PARTIAL,source.completeness());assertTrue(source.capture().isEmpty());
        } finally {
            InitializingBoard.instance.getNormativeEngine().stop();env.getController("/main").removeArtifact(artifact.getName());
            for(var id:env.getController("/main").getCurrentAgents())if(id.getAgentName().equals(actor.getName()))env.resolveWSP("/main").getWorkspace().quitAgent(id);env.shutdown();
        }
    }
    @Test void unchangedWorldStillPublishesSourceClockCutsThroughoutARealTenSecondObligation()throws Exception {
        var env=CartagoEnvironment.getInstance();env.init();var actor=new CartagoBasicContext("clockActor"+UUID.randomUUID().toString().replace("-",""));
        var board=actor.makeArtifact(actor.getJoinedWspId("main"),"clockBoard"+UUID.randomUUID().toString().replace("-",""),NplLiveBoardObservationTest.Board.class.getName());
        var events=new CopyOnWriteArrayList<RuntimeEvent>();String revision=System.getProperty("jacamo.bridge.modelRevision","unnegotiated");long generation=Long.parseLong(System.getProperty("jacamo.bridge.generation","0"));
        try(var wire=new VerificationObservationSource("idle-clock",generation,revision);
            var norms=new NplBoardSnapshotSource("idle-clock",()->List.of(NplLiveBoardObservationTest.Board.instance));
            var coordinator=new SnapshotCoordinator(List.of(wire,norms),4096,events::add);
            var clock=new AuctionClockObservation("idle-clock",generation,revision,frame->wire.publish(frame,"",""))) {
            clock.pulse(false);assertFalse(norms.hasPendingTimedObligations());
            actor.doAction(board,new Op("load","scope monitor { norm n_bid: start -> obligation(alice,true,missing,`now`+`10 seconds`). }"));actor.doAction(board,new Op("addFact","start"));
            NplLiveBoardObservationTest.await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED));assertTrue(norms.hasPendingTimedObligations());
            var activation=events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED).findFirst().orElseThrow();
            long deadline=Long.parseLong((String)activation.after().get("deadline"));long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            // No more test calls to any Artifact or normative operation.
            while(System.nanoTime()<end&&events.stream().noneMatch(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED)) {
                clock.pulse(norms.hasPendingTimedObligations());Thread.sleep(100);
            }
            assertTrue(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED));
            var times=events.stream().filter(VerificationObservation::present).map(VerificationObservation::require).filter(o->o.kind()==VerificationObservation.Kind.CLOCK).toList();
            assertTrue(times.size()>=8,"Idle source must deliver bounded advancing evidence before expiry");
            assertTrue(times.stream().mapToLong(o->Long.parseLong(o.sourceTime().rawValue())).max().orElseThrow()>=deadline-1500);
            var cut=coordinator.capture(revision,4).snapshot();assertTrue(cut.facts().stream().anyMatch(f->f.values().containsKey(VerificationObservation.KEY)));
            assertEquals(Completeness.PARTIAL,cut.sourceCompleteness().get("npl"));assertEquals(Completeness.PARTIAL,cut.sourceCompleteness().get(VerificationObservation.SOURCE_ID));
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/auction-implementation"));
            java.nio.file.Files.write(java.nio.file.Path.of("target/auction-implementation/N04-idle-npl.json"),CanonicalJson.encode(Map.of("scope","ACTUAL_NPL_TEN_SECOND_IDLE_COMPONENT_NOT_E01","events",events.stream().map(ContractPayloads::event).toList(),"snapshot",ContractPayloads.runtime(cut))));
        } finally {
            NplLiveBoardObservationTest.Board.instance.getNormativeEngine().stop();env.getController("/main").removeArtifact(board.getName());
            for(var id:env.getController("/main").getCurrentAgents())if(id.getAgentName().equals(actor.getName()))env.resolveWSP("/main").getWorkspace().quitAgent(id);env.shutdown();
        }
    }
    @Test void lateActivationReceivesItsOwnTenSecondsAndOneHourKeepsActualNplDeadline()throws Exception {
        var engine=new npl.NPLInterpreter();var program=new npl.parser.nplp(new java.io.StringReader("scope clock { norm first: early -> obligation(alice,true,missing,`now`+`10 seconds`). norm late: later -> obligation(bob,true,missing,`now`+`10 seconds`). norm decide: later -> obligation(carol,true,missing,`now`+`1 hour`). }"));
        // Use the same public parser/load path as the normative board.
        var normativeProgram=new npl.NormativeProgram();program.program(normativeProgram,engine);engine.init();engine.loadNP(normativeProgram.getRoot());
        try {
            engine.addFact(jason.asSyntax.ASSyntax.createLiteral("early"));engine.verifyNorms();
            long first=engine.getActive().stream().filter(n->n.getNorm().getId().equals("first")).findFirst().orElseThrow().getTimeDeadline();Thread.sleep(1100);
            long before=System.currentTimeMillis();engine.addFact(jason.asSyntax.ASSyntax.createLiteral("later"));engine.verifyNorms();long after=System.currentTimeMillis();
            var crosswalk=new NplActivationCrosswalk("late-clock",0);var late=engine.getActive().stream().filter(n->n.getNorm().getId().equals("late")).findFirst().orElseThrow();
            var decide=engine.getActive().stream().filter(n->n.getNorm().getId().equals("decide")).findFirst().orElseThrow();
            var observed=crosswalk.observe("board",late.copy(),"SNAPSHOT","ACTIVE");long lateDeadline=Long.parseLong((String)observed.get("deadline"));assertTrue(lateDeadline-first>=1000);assertTrue(lateDeadline>=before+10000&&lateDeadline<=after+10000);
            observed=crosswalk.observe("board",decide.copy(),"SNAPSHOT","ACTIVE");long oneHour=Long.parseLong((String)observed.get("deadline"));assertTrue(oneHour>=before+3600000&&oneHour<=after+3600000);
        } finally {engine.stop();}
    }
}
