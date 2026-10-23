package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;

public class NplLiveBoardObservationTest {
    private String createdActor;
    @org.junit.jupiter.api.AfterEach void releaseEnvironment()throws Exception{var env=CartagoEnvironment.getInstance();if(createdActor!=null)for(var agent:env.getController("/main").getCurrentAgents())if(createdActor.equals(agent.getAgentName()))env.resolveWSP("/main").getWorkspace().quitAgent(agent);env.shutdown();}
    public static class Board extends ora4mas.nopl.NormativeBoard {
        static volatile Board instance;
        @Override public void init(){var config=jason.util.Config.get();synchronized(config){String previous=config.getProperty(jason.util.Config.START_WEB_OI);config.setProperty(jason.util.Config.START_WEB_OI,"false");try{super.init();}finally{if(previous==null)config.remove(jason.util.Config.START_WEB_OI);else config.setProperty(jason.util.Config.START_WEB_OI,previous);}}instance=this;getNormativeEngine().setUpdateInterval(10);}
    }
    @Test void realNplListenerAndPublicGettersKeepActivationFulfillmentDeadlineMissAndUnusedPermissionDistinct()throws Exception {
        var environment=CartagoEnvironment.getInstance();environment.init();var context=new CartagoBasicContext("normActor"+UUID.randomUUID().toString().replace("-",""));createdActor=context.getName();String name="norms"+UUID.randomUUID().toString().replace("-","");var board=context.makeArtifact(context.getJoinedWspId("main"),name,Board.class.getName());var events=new CopyOnWriteArrayList<RuntimeEvent>();
        var extended=new CopyOnWriteArrayList<RuntimeEvent>();
        try(var wire=new VerificationObservationSource("norm-run",Long.parseLong(System.getProperty("jacamo.bridge.generation","0")),System.getProperty("jacamo.bridge.modelRevision","unnegotiated"));
                var source=new NplBoardSnapshotSource("norm-run",()->List.of(Board.instance))) {
            wire.attach(extended::add);
            source.attach(events::add);String program="scope monitor { norm n_ok: start -> obligation(alice,true,done,`now`+`10 seconds`). norm n_deadline: start -> obligation(bob,true,missing,`now`+`1 seconds`). norm n_optional: start -> permission(carol,true,unused,`now`+`10 seconds`). }";
            context.doAction(board,new Op("load",program));context.doAction(board,new Op("addFact","start"));await(()->events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED).count()==3);
            var activated=events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED).toList();assertTrue(activated.stream().allMatch(e->e.projectionStatus()==ProjectionStatus.EVIDENCE_ONLY && e.completeness()==Completeness.COMPLETE));var first=activated.stream().filter(e->e.after().get("normDefinition").equals("n_ok")).findFirst().orElseThrow();assertEquals(board.getId().toString(),first.after().get("boardArtifactIdentity"));
            context.doAction(board,new Op("addFact","done"));await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_FULFILLED));var fulfilled=events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_FULFILLED).findFirst().orElseThrow();assertEquals(first.entityId(),fulfilled.entityId());assertEquals("CREATED_EVENT_OBSERVED",fulfilled.after().get("history"));
            await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED));var miss=events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED).findFirst().orElseThrow();assertEquals("n_deadline",miss.after().get("normDefinition"));var facts=source.capture();assertTrue(facts.stream().anyMatch(f->f.values().get("normDefinition").equals("n_optional") && f.values().get("state").equals("ACTIVE") && f.values().get("modality").equals("PERMISSION")));assertEquals(Completeness.PARTIAL,source.completeness());
            await(()->extended.stream().filter(e->e.kind()!=RuntimeEventKind.GAP).map(VerificationObservation::require).anyMatch(o->o.data().get("state").equals("UNFULFILLED")));
            var observed=extended.stream().map(VerificationObservation::require).filter(o->o.data().get("state").equals("UNFULFILLED")).findFirst().orElseThrow();
            assertEquals(miss.entityId().canonical(),observed.subject());assertEquals(miss.after(),observed.data().get("sourceObservation"));
            assertFalse(CanonicalJson.object(observed.data().get("norm")).isEmpty());assertEquals("OBSERVED",observed.sourceTime().status());assertEquals(Completeness.COMPLETE,observed.quality());
            var timeProof=CanonicalJson.object(miss.after().get("sourceLifecycle"));assertEquals(timeProof.get("terminalEpochMillis"),observed.sourceTime().rawValue());
            assertEquals(NplLifecycleEvidence.AUTHORITY,observed.sourceTime().authority());assertEquals(Completeness.PARTIAL,source.completeness(),"One qualified activation never proves a complete global normative population");
        } finally {if(Board.instance!=null){Board.instance.getNormativeEngine().stop();Board.instance.destroy();}environment.getController("/main").removeArtifact(name);}
    }
    @Test void stateDeadlineMaintenanceInactivityAndSameNameBoardReplacementHaveDistinctSourceLifetimes()throws Exception {
        var env=CartagoEnvironment.getInstance();env.init();String name="stateNorms"+UUID.randomUUID().toString().replace("-","");var ctx=new CartagoBasicContext(name);createdActor=ctx.getName();var first=ctx.makeArtifact(ctx.getJoinedWspId("main"),name,Board.class.getName());var events=new CopyOnWriteArrayList<RuntimeEvent>();var firstInstance=Board.instance;
        try(var source=new NplBoardSnapshotSource("state-run",()->List.of(Board.instance))) {
            source.attach(events::add);String program="scope monitor { norm n_state: start -> obligation(alice,keep,missing,finished). norm n_inactive: start -> obligation(bob,keep,unused,`now`+`10 seconds`). }";
            ctx.doAction(first,new Op("load",program));ctx.doAction(first,new Op("addFact","keep"));ctx.doAction(first,new Op("addFact","start"));await(()->events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED).count()==2);
            var activation=events.stream().filter(e->e.after().get("normDefinition").equals("n_state")).findFirst().orElseThrow();assertEquals("STATE",activation.after().get("deadlineKind"));assertEquals("finished",activation.after().get("deadlineRaw"));
            ctx.doAction(first,new Op("addFact","finished"));await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED && e.after().get("normDefinition").equals("n_state")));
            assertTrue(firstInstance.getNormativeEngine().removeFact(jason.asSyntax.ASSyntax.parseLiteral("keep[source(nboard)]")));firstInstance.getNormativeEngine().verifyNorms();await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_INACTIVATED && e.after().get("normDefinition").equals("n_inactive")));
            firstInstance.getNormativeEngine().stop();ctx.doAction(new Op("disposeArtifact",first),ctx.getJoinedWspId("main"));var replacement=ctx.makeArtifact(ctx.getJoinedWspId("main"),name,Board.class.getName());source.capture();ctx.doAction(replacement,new Op("load",program));ctx.doAction(replacement,new Op("addFact","keep"));ctx.doAction(replacement,new Op("addFact","start"));await(()->events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED && e.after().get("normDefinition").equals("n_state") && replacement.getId().toString().equals(e.after().get("boardArtifactIdentity"))));
            var next=events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_ACTIVATED && e.after().get("normDefinition").equals("n_state") && replacement.getId().toString().equals(e.after().get("boardArtifactIdentity"))).findFirst().orElseThrow();assertNotEquals(activation.entityId(),next.entityId());assertNotEquals(activation.after().get("activationKey"),next.after().get("activationKey"));assertTrue(source.capture().stream().filter(f->f.kind()==RuntimeFactKind.NORM_INSTANCE).allMatch(f->replacement.getId().toString().equals(f.values().get("boardArtifactIdentity"))));
        }finally{if(Board.instance!=null){Board.instance.getNormativeEngine().stop();Board.instance.destroy();}env.getController("/main").removeArtifact(name);}
    }
    @Test void noSelectedBoardIsAnAvailableEmptySelectionWithUnprovenGlobalNormPopulation()throws Exception {
        try(var source=new NplBoardSnapshotSource("empty-npl",List::of)){source.attach(e->{});assertTrue(source.capture().isEmpty());assertEquals(Completeness.PARTIAL,source.completeness());assertTrue(source.captureMetadata().toString().contains("NPL_NO_SELECTED_BOARDS_GLOBAL_POPULATION_UNPROVEN"));}
    }
    static void await(java.util.function.BooleanSupplier condition)throws Exception{long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);while(!condition.getAsBoolean() && System.nanoTime()<end)Thread.sleep(10);assertTrue(condition.getAsBoolean(),"NPL source event timed out");}
}
