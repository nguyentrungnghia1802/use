package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import cartago.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericMappingEngine;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingInputs;
import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.semantics.auction.AuctionDeclarativeObserverTest.*;

/** Actual source callbacks against controlled framework fixtures; full MAS/TCP is a separate final gate. */
class AuctionDeclarativeTasksTest {
    @ParameterizedTest(name="authored={0}") @ValueSource(booleans={false,true}) @Timeout(90)
    void xmlBindsDeclaredOriginalTasksFromActualSourceCallbacksAndDoesNotRebindFrozenPlayers(boolean authored)throws Exception {
        try(var f=new AuctionOrgFixture(true)) {
            var root=AuctionDeclarativeSpecificationTest.CASE;var selected=root.resolve("authored-v1");
            var inputs=authored?MappingInputs.read(root.resolve("auction.jcm"),selected.resolve("auction-mapping.xml"),selected.resolve("auction_constraints_final_fixed.ocl"),selected.resolve("auction_csl.csl"),selected.resolve("auction_istar_final.istar")):AuctionDeclarativeSpecificationTest.inputs(root);
            // The authored oracle has five Tasks. Its decide prefix is source trace only;
            // the historical generated profile deliberately retains its sixth Task.
            var expectedTasks=new ArrayList<>(List.of("A_start_t1","A_start_t2","P_bid_t1","P_bid_t2","A_decide_t2"));
            if(!authored)expectedTasks.add("A_decide_t1");
            var live=f.live;var program=new GenericMappingEngine().map(live.source,inputs);var requests=program.xmlPlan().requirements();var frames=new ArrayList<VerificationObservation>();
            var context=new FrameworkObservationProviders.Context(live.environment,live.source,AuctionDeclarativeSpecificationTest.CASE.toRealPath(),live.run.id(),live.run.generation(),frames::add,ignored->{});
            var workspace=request(requests,"workspace-operations");var field=request(requests,"candidate-field");
            try(var history=new CartagoOperationHistory(workspace,context);var fields=new ArtifactFieldObservation(live.environment,live.source,field,context.root());var driver=new AuctionDeclarativeArtifactTest.SourceDriver(f,request(requests,"execution-steps"),frames);var capture=new Capture(f,context,requests,history,frames)) {
                f.organizationBoard();var group=f.group();var participant=AuctionMonitorProjectionTest.participant(f,group);
                try(var bidder=new AuctionDeclarativeArtifactTest.SourceDriver(f,request(requests,"execution-steps"),frames,participant,"alice")) {
                    var scheme=f.scheme("nativeSteps","delivery");live.actor.doAction(group,new Op("addScheme",scheme.getName()),5000);live.actor.doAction(scheme,new Op("commitMission","mAuctioneer"),5000);participant.doAction(scheme,new Op("commitMission","mParticipant"),5000);
                    var built=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
                    try(var projector=new NativeRuntimeProjector(built,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                        projector.applySnapshot(capture.cut(frames,driver.identity,workspace,history,field,fields),live.run.id(),live.run.generation());await(projector);
                        var scopes=projector.mutations().xmlOccurrences(live.run.id(),live.run.generation(),live.source.modelRevision());assertEquals(1,scopes.size());assertTrue(scopes.getFirst().complete(),scopes.getFirst().unavailable().toString());assertEquals(3,scopes.getFirst().members().size());assertTrue(scopes.getFirst().resources().isEmpty());
                        assertTrue(projector.system().state().allObjects().stream().noneMatch(o->o.cls().name().equals("Auction")));var system=projector.system();
                        var boundaries=new ArrayList<org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult>();
                        driver.emission=frame->{try {
                            projector.applySnapshot(withPlayers(capture.cut(frames,driver.identity,workspace,history,field,fields),bidder.identity),live.run.id(),live.run.generation());await(projector);
                            frames.add(frame);var event=frame.event(live.run.id(),live.run.generation(),live.source.modelRevision(),frames.size(),java.time.Instant.now(),"","",true);
                            projector.apply(event);assertEquals(frames.size(),projector.sourceWatermarks().get(VerificationObservation.SOURCE_ID).longValue());boundaries.add(await(projector));
                        }catch(Exception error){throw new AssertionError("Causal source callback admission",error);}};
                        driver.start(scheme,"nativeSteps","delivery");assertEquals(4,boundaries.size());
                        live.actor.doAction(scheme,new Op("goalAchieved","start"),5000);
                        bidder.emission=driver.emission;bidder.arguments(scheme,"nativeSteps");long failingSeed=0;while(new Random(failingSeed).nextDouble()<0.9)failingSeed++;jason.stdlib.set_random_seed.getAgRandomIA(bidder.ts).setSeed(failingSeed);
                        bidder.goal("bid",scheme);bidder.until(()->boundaries.size()==8);
                        assertTrue(boundaries.getLast().semantic().constraints().stream().anyMatch(c->c.kind().equals("TASK_POST")&&c.witness().get("targetTaskId").equals("P_bid_t2")&&c.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL),"Explicit .fail is a known unsuccessful callback, not missing evidence");
                        var failedContext=CanonicalJson.object(((List<?>)CanonicalJson.object(boundaries.getLast().semantic().observation().get("originalEngine")).get("contexts")).getFirst());
                        var pending=CanonicalJson.object(failedContext.get("occurrences")).values().stream().map(CanonicalJson::object).filter(o->o.get("sourceElement").equals("P_bid_t2")).findFirst().orElseThrow();assertEquals("PENDING",pending.get("leafStatus"),"Original TaskMarking retains preSeen; a Task cannot be VIOLATED");assertEquals("KNOWN",pending.get("quality"));
                        jason.stdlib.set_random_seed.getAgRandomIA(bidder.ts).setSeed(0);bidder.goal("bid",scheme);bidder.until(()->boundaries.size()==12);
                        assertTrue(boundaries.stream().flatMap(b->b.semantic().constraints().stream()).filter(c->"P_bid_t2".equals(c.witness().get("targetTaskId"))&&Set.of("TASK_PRE","TASK_POST").contains(c.kind())).allMatch(c->bidder.identity.canonical().equals(c.witness().get("player"))),"A participant operation cannot credit the auctioneer");
                        participant.doAction(scheme,new Op("goalAchieved","bid"),5000);driver.goal("decide",scheme);assertEquals(16,boundaries.size());
                        var result=await(projector);var contextEvidence=CanonicalJson.object(((List<?>)CanonicalJson.object(result.semantic().observation().get("originalEngine")).get("contexts")).getFirst());
                        var occurrences=CanonicalJson.object(contextEvidence.get("occurrences")).values().stream().map(CanonicalJson::object).toList();
                        if(authored)assertTrue(occurrences.stream().noneMatch(o->"A_decide_t1".equals(o.get("sourceElement"))),"A source-only prefix cannot acquire original Task credit");
                        for(String task:expectedTasks) {
                            var row=occurrences.stream().filter(o->task.equals(o.get("sourceElement"))).findFirst().orElseThrow();assertEquals("FULFILLED",row.get("leafStatus"),()->row+" boundaries="+boundaries.stream().map(b->b.semantic().constraints().stream().filter(c->c.kind().startsWith("TASK_")).toList()).toList());assertEquals("KNOWN",row.get("quality"),row.toString());
                        }
                        assertTrue(((List<?>)contextEvidence.get("originalApiCalls")).contains("TaskMarking.update"));assertSame(system,projector.system());
                        var recording=java.nio.file.Files.createTempDirectory(java.nio.file.Files.createDirectories(java.nio.file.Path.of("target/generic-mapping-audit")),"declared-task-replay-");
                        var replay=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay();replay.exportBundle(projector,built.nativeResult().export().useText(),recording);
                        var replayed=replay.replay(recording);assertTrue(replayed.complete(),replayed.diagnostics().toString());assertEquals(result.resultHash(),replayed.finalResultHash());assertEquals(result.stateHash(),replayed.finalStateHash());
                        var other=f.scheme("secondExecution","another_service");live.actor.doAction(group,new Op("addScheme",other.getName()),5000);live.actor.doAction(other,new Op("commitMission","mAuctioneer"),5000);participant.doAction(other,new Op("commitMission","mParticipant"),5000);
                        projector.applySnapshot(withPlayers(capture.cut(frames,driver.identity,workspace,history,field,fields),bidder.identity),live.run.id(),live.run.generation());await(projector);
                        assertEquals(2,projector.mutations().xmlOccurrences(live.run.id(),live.run.generation(),live.source.modelRevision()).stream().filter(s->s.complete()).count());
                        var demands=projector.mutations().xmlTaskDemands(live.run.id(),live.run.generation(),live.source.modelRevision());assertEquals(2*expectedTasks.size(),demands.size());assertTrue(demands.stream().allMatch(d->d.quality()==Completeness.COMPLETE),demands.toString());
                        assertEquals(2,demands.stream().filter(d->d.target().equals("A_start_t1")).map(d->d.episode()).distinct().count(),"Separate source executions retain independent demand episodes");
                        var replacement=new BridgeEntityId("jason","agent","runtime-agent",live.source.semanticContract().project().name(),participant.getName(),"new-player-incarnation");
                        try(var rebound=CslRuntimeTestRegistry.bind(live.source.semanticContract(),participant.getName(),"alice",replacement)) {
                            projector.applySnapshot(withPlayers(capture.cut(frames,driver.identity,workspace,history,field,fields),replacement),live.run.id(),live.run.generation());await(projector);
                            var afterRestart=projector.mutations().xmlOccurrences(live.run.id(),live.run.generation(),live.source.modelRevision());
                            var frozen=afterRestart.stream().filter(s->s.id().equals(scopes.getFirst().id())).findFirst().orElseThrow();assertFalse(frozen.complete(),frozen.toString());
                            assertTrue(frozen.members().stream().noneMatch(m->m.player().equals(replacement.canonical())),"A frozen commitment cannot be rebound through a reused current agent name");
                            assertTrue(afterRestart.stream().anyMatch(s->!s.id().equals(frozen.id())&&s.complete()&&s.members().stream().anyMatch(m->m.player().equals(replacement.canonical()))),"A different execution that has not frozen its roster may admit the new current player");
                        }
                    }
                }finally{CslRuntimeTestRegistry.closeCartagoContext(participant);}
            }
        }
    }
    static RuntimeSnapshot withPlayers(RuntimeSnapshot cut,BridgeEntityId... players) {
        var facts=new ArrayList<>(cut.facts());
        for(var player:players)facts.add(new RuntimeFact(player,RuntimeFactKind.AGENT,Map.of("name",player.localId(),"semanticId",player.canonical(),"normalizedEventKind","UPSERT_JASON_AGENT_STATE"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
        return AuctionDeclarativeArtifactTest.copy(cut,facts);
    }
    static org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult await(NativeRuntimeProjector projector)throws Exception {
        return projector.coordinator().awaitEvaluation(projector.coordinator().latest(),15000);
    }
}
