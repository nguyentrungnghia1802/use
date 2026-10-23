package org.tzi.use.plugins.jacamo.semantics.auction;

import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.runtime.JaCaMoOccurrenceBindingAdapter;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.semantic.engine.runtime.Use4GoalRuntimeSessionAdapter;
import org.tzi.use.semantic.engine.runtime.EngineInputs;
import org.jacamo.bridge.contract.CanonicalJson;
import org.vnu.sme.goal.dsl.istar.mm.Task;
import org.vnu.sme.goal.verify.conformance.semantics.GoalTaskStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionOccurrenceExpansionTest {
    @Test void actualFourCommittedParticipantsNeedTwoIndependentSchemeCastsRatherThanTheGenericSingleContextCast()throws Exception {
        try(var fixture=new AuctionCastFixture(4)) {
            var old=new JaCaMoOccurrenceBindingAdapter().resolve(fixture.program,fixture.projector.mutations());
            assertTrue(old.stream().anyMatch(c->!c.complete()),"Generic admission keeps rejecting ambiguous Auction casts");
            var casts=new AuctionOccurrenceBindingAdapter(fixture.program).resolve(fixture.projector.mutations(),fixture.org.live.run);
            assertEquals(2,casts.size(),()->"Each actual scheme needs its own four-player cast: "+fixture.projector.trace().stream().flatMap(t->t.diagnostics().stream()).filter(d->d.contains("INCOMPLETE")||d.contains("UNSUPPORTED")).toList());
            assertTrue(casts.stream().allMatch(AuctionOccurrenceBindingAdapter.Scope::complete),casts.toString());
            String original=fixture.program.goalHash();
            for(var cast:casts) {
                assertEquals(6,cast.actors().size());assertEquals(4,cast.actors().stream().filter(a->a.sourceActor().equals("Participant")).count());
                var expanded=new AuctionGoalInstanceExpander(fixture.program).expand(cast);
                assertEquals(expanded.hash(),new AuctionGoalInstanceExpander(fixture.program).expand(cast).hash());
                try(var engine=session(fixture,expanded)) {
                    assertTrue(engine.topology().occurrences().values().stream().anyMatch(o->expanded.trace().get(o.sourceElementId()).source().equals("P_bid_t2")));
                    // Deliberate engine-only tuples: no assertion that these
                    // inputs were observed from a Jason execution.
                    var first=cast.actors().stream().filter(a->a.sourceActor().equals("Participant")).findFirst().orElseThrow();
                    var mixed=engine.step(taskTuples(engine,expanded,Set.of(first.player())));
                    assertTrue(mixed.history().goals().isEmpty(),"Canonical Auction Goals have no observed leaf condition; do not fabricate one for a call counter");
                    assertFalse(mixed.originalApiCalls().contains("GoalMarking.update"));
                    assertFalse(mixed.aggregate().goalMarkings().isEmpty());
                    assertTrue(mixed.aggregate().goalMarkings().values().stream().allMatch(org.vnu.sme.goal.verify.conformance.semantics.GoalMarking.class::isInstance),"Original IStarMarking.initial/withGoalTask use original GoalMarking.initial/fromStatus for derived Goals");
                    assertTrue(mixed.originalApiCalls().contains("TaskMarking.update"));
                    assertTrue(mixed.originalApiCalls().contains("GoalModelInstanceTranslator.propagateDependencies"));
                    assertTrue(engine.topology().occurrences().values().stream().filter(o->expanded.trace().get(o.sourceElementId()).source().equals("O_bid")&&expanded.trace().get(o.sourceElementId()).kind().equals("SOURCE_ELEMENT")).allMatch(o->mixed.aggregate().goalTaskStatus(o.occurrenceId())!=GoalTaskStatus.FULFILLED));
                    for(var occurrence:engine.topology().occurrences().values()) {
                        var trace=expanded.trace().get(occurrence.sourceElementId());
                        if(trace.source().equals("P_bid_t2"))assertEquals(trace.player().equals(first.player())?GoalTaskStatus.FULFILLED:GoalTaskStatus.PENDING,mixed.history().tasks().get(occurrence.occurrenceId()).status());
                    }
                    var all=engine.step(taskTuples(engine,expanded,cast.actors().stream().filter(a->a.sourceActor().equals("Participant")).map(AuctionOccurrenceBindingAdapter.Player::player).collect(java.util.stream.Collectors.toSet())));
                    assertTrue(engine.topology().occurrences().values().stream().filter(o->expanded.trace().get(o.sourceElementId()).source().equals("O_bid")&&expanded.trace().get(o.sourceElementId()).kind().equals("SOURCE_ELEMENT")).allMatch(o->all.aggregate().goalTaskStatus(o.occurrenceId())==GoalTaskStatus.FULFILLED));
                }
            }
            assertEquals(original,fixture.program.goalHash());fixture.program.assertUnchanged();
        }
    }
    @Test void productionOwnerUsesTwoExpandedModelsOnTheSameNativeBoundaryAndDoesNotInventStepTruth()throws Exception {
        try(var fixture=new AuctionCastFixture(4)) {
            var coordinator=fixture.projector.coordinator();var completed=coordinator.awaitEvaluation(coordinator.latest(),15000);
            var core=CanonicalJson.object(completed.semantic().observation().get("originalEngine"));
            assertEquals("PINNED_NATIVE_CUT",core.get("evaluated"),completed.diagnostic());
            var contexts=((List<?>)core.get("contexts")).stream().map(CanonicalJson::object).toList();assertEquals(2,contexts.size());
            for(var context:contexts) {
                assertEquals(AuctionGoalInstanceExpander.VERSION,context.get("expansionRecipe"));
                assertNotEquals(fixture.program.goalHash(),context.get("executionGoalHash"));
                var occurrences=CanonicalJson.object(context.get("occurrences")).values().stream().map(CanonicalJson::object).toList();
                assertEquals(4,occurrences.stream().filter(o->o.get("sourceElement").equals("P_bid_t2")).map(o->o.get("player")).distinct().count());
                assertTrue(occurrences.stream().filter(o->o.get("sourceElement").equals("P_bid_t2")).allMatch(o->!o.get("quality").equals("KNOWN")),"No exact Jason step evidence in this fixture");
            }
            assertEquals(completed.stateHash(),core.get("stateHash"));
            var evidence=java.nio.file.Path.of("target/auction-implementation/I03-production-evidence.json");java.nio.file.Files.createDirectories(evidence.getParent());
            java.nio.file.Files.write(evidence,CanonicalJson.encode(Map.of("scope","I03_REAL_BOARDS_NATIVE_PROJECTOR_STRUCTURAL_ENGINE_NOT_E01_OR_TASK_INPUT_ACCEPTANCE","consumerPid",ProcessHandle.current().pid(),"core",core)));
        }
    }
    @Test void emptyRelevantSetAndMissingCurrentPlayerCannotCertifyAUniversalBid()throws Exception {
        try(var fixture=new AuctionCastFixture(1,0)) {
            assertTrue(scopes(fixture).stream().allMatch(c->!c.complete()&&c.unavailable().contains("AUCTION_CAST_RELEVANT_COMMITMENTS_EMPTY_OR_UNPROVEN")));
        }
        try(var fixture=new AuctionCastFixture(1)) {
            var cut=fixture.org.cut();var absent=cut.facts().stream().filter(f->f.kind()!=org.jacamo.bridge.contract.RuntimeFactKind.AGENT||!fixture.participants.getFirst().getName().equals(f.values().get("name"))).toList();
            fixture.projector.applySnapshot(new org.jacamo.bridge.contract.RuntimeSnapshot("missing-player",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,absent,Map.of("cartago",org.jacamo.bridge.contract.Completeness.PARTIAL),"missing-player"),fixture.org.live.run.id(),fixture.org.live.run.generation());
            assertTrue(scopes(fixture).stream().allMatch(c->!c.complete()),"Retained native identity cannot supply a missing current performer");
        }
    }
    @Test void actualLateCommitRebuildsOnlyOneSchemeAndFirstSatisfactionFreezesItsRequiredSet()throws Exception {
        try(var fixture=new AuctionCastFixture(4,1)) {
            var initial=scopes(fixture);var before=initial.getFirst();var expansion=new AuctionGoalInstanceExpander(fixture.program).expand(before);
            try(var first=session(fixture,expansion)) {
                var alice=before.actors().stream().filter(p->p.name().equals(fixture.participants.getFirst().getName())).findFirst().orElseThrow();
                first.step(taskTuples(first,expansion,Set.of(alice.player())));
                fixture.participants.get(1).doAction(fixture.schemes.getFirst(),new cartago.Op("commitMission","mParticipant"),5000);fixture.resync();
                var after=scopes(fixture);assertNotEquals(before.revision(),after.getFirst().revision());assertEquals(initial.get(1).revision(),after.get(1).revision());
                var nextExpansion=new AuctionGoalInstanceExpander(fixture.program).expand(after.getFirst());
                try(var next=session(fixture,nextExpansion);var other=session(fixture,new AuctionGoalInstanceExpander(fixture.program).expand(after.get(1)))) {
                    var episodes=taskTuples(next,nextExpansion,Set.of()).tasks().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().episode()));
                    next.seedCompatibleLeafHistory(first,episodes);assertFalse(next.checkpoint().tasks().isEmpty());
                    assertTrue(next.checkpoint().tasks().keySet().stream().allMatch(first.checkpoint().tasks()::containsKey));
                    other.seedCompatibleLeafHistory(first,taskTuples(other,new AuctionGoalInstanceExpander(fixture.program).expand(after.get(1)),Set.of()).tasks().entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().episode())));
                    assertTrue(other.checkpoint().tasks().isEmpty(),"Another actual scheme cannot import Alice's history");
                }
                fixture.org.live.actor.doAction(fixture.schemes.getFirst(),new cartago.Op("goalAchieved","start"),5000);
                fixture.participants.getFirst().doAction(fixture.schemes.getFirst(),new cartago.Op("goalAchieved","bid"),5000);fixture.participants.get(1).doAction(fixture.schemes.getFirst(),new cartago.Op("goalAchieved","bid"),5000);fixture.resync();
                var frozen=scopes(fixture).getFirst();assertTrue(frozen.complete(),frozen.toString());
                fixture.participants.get(2).doAction(fixture.schemes.getFirst(),new cartago.Op("commitMission","mParticipant"),5000);fixture.resync();
                assertEquals(frozen.revision(),scopes(fixture).getFirst().revision(),"Late commitment cannot rewrite the first-satisfaction universe");
                fixture.participants.get(1).doAction(fixture.schemes.getFirst(),new cartago.Op("leaveMission","mParticipant"),5000);fixture.resync();
                assertEquals(frozen.revision(),scopes(fixture).getFirst().revision(),"Later decommitment does not erase a required frozen participant");
            }
        }
    }
    @Test void leaveRejoinHasANewObservedIntervalAndMultipleRelevantRoleContextsAreRejected()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var before=scopes(fixture).getFirst();var player=before.actors().stream().filter(p->!p.role().isBlank()&&p.name().equals(fixture.participants.getFirst().getName())).findFirst().orElseThrow();
            // The pinned MOISE engine rejects mission_left before the
            // mission is achieved. Exercise a legal completed departure;
            // MOISE done still is not credited as an Artifact bid or Task.
            for(var scheme:fixture.schemes) {
                fixture.org.live.actor.doAction(scheme,new cartago.Op("goalAchieved","start"),5000);
                fixture.participants.getFirst().doAction(scheme,new cartago.Op("goalAchieved","bid"),5000);
                fixture.participants.getFirst().doAction(scheme,new cartago.Op("leaveMission","mParticipant"),5000);
            }
            fixture.participants.getFirst().doAction(fixture.group,new cartago.Op("leaveRole","participant"),5000);
            AuctionMonitorProjectionTest.await(()->fixture.org.live.boards.stream().allMatch(b->b.getSchState().getGroupsResponsibleFor().stream().noneMatch(g->g.hasPlayer(fixture.participants.getFirst().getName(),"participant"))));fixture.resync();
            assertTrue(scopes(fixture).stream().allMatch(c->!c.complete()));
            fixture.participants.getFirst().doAction(fixture.group,new cartago.Op("adoptRole","participant"),5000);
            AuctionMonitorProjectionTest.await(()->fixture.org.live.boards.stream().allMatch(b->b.getSchState().getGroupsResponsibleFor().stream().anyMatch(g->g.hasPlayer(fixture.participants.getFirst().getName(),"participant"))));fixture.resync();
            for(var scheme:fixture.schemes)fixture.participants.getFirst().doAction(scheme,new cartago.Op("commitMission","mParticipant"),5000);fixture.resync();
            var rejoined=scopes(fixture).getFirst();assertTrue(rejoined.complete(),rejoined.toString());var next=rejoined.actors().stream().filter(p->p.player().equals(player.player())).findFirst().orElseThrow();
            assertEquals(player.role(),next.role());assertNotEquals(player.roleInterval(),next.roleInterval());assertNotEquals(player.instance(),next.instance());
            var another=fixture.org.group("anotherActualGroup");fixture.participants.getFirst().doAction(another,new cartago.Op("adoptRole","participant"),5000);
            fixture.org.live.actor.doAction(another,new cartago.Op("addScheme",fixture.schemes.getFirst().getName()),5000);
            AuctionMonitorProjectionTest.await(()->fixture.org.live.boards.getFirst().getSchState().getGroupsResponsibleFor().size()==2);fixture.resync();
            assertFalse(scopes(fixture).getFirst().complete(),"No first-enactment fallback across two responsible contexts");
        }
    }
    private static List<AuctionOccurrenceBindingAdapter.Scope> scopes(AuctionCastFixture fixture){return new AuctionOccurrenceBindingAdapter(fixture.program).resolve(fixture.projector.mutations(),fixture.org.live.run).stream().sorted(Comparator.comparing(c->org.jacamo.bridge.contract.BridgeEntityId.parse(c.scheme()).localId())).toList();}
    private static Use4GoalRuntimeSessionAdapter session(AuctionCastFixture fixture,AuctionGoalInstanceExpander.Expansion expanded){return new Use4GoalRuntimeSessionAdapter(expanded.model(),expanded.cast(),(a,x,b,y)->expanded.scope().actors().stream().anyMatch(p->p.actorType().equals(a)&&p.instance().equals(x))&&expanded.scope().actors().stream().anyMatch(p->p.actorType().equals(b)&&p.instance().equals(y)),expanded.scope().revision(),fixture.program.profile().engine(),org.tzi.use.semantic.engine.runtime.EngineTopologyPolicy.SINGLE_OWNER_CONTEXT_V1);}
    private static EngineInputs taskTuples(Use4GoalRuntimeSessionAdapter engine,AuctionGoalInstanceExpander.Expansion expanded,Set<String> fulfilled) {
        var tasks=new TreeMap<String,EngineInputs.TaskInput>();var resources=new HashMap<EngineInputs.ResourceKey,EngineInputs.ResourceInput>();
        for(var occurrence:engine.topology().occurrences().values())if(expanded.model().findElement(occurrence.sourceElementId()).orElse(null) instanceof Task) {
            var trace=expanded.trace().get(occurrence.sourceElementId());String episode="engine-test:"+expanded.scope().scheme()+":"+trace.role();
            boolean known=fulfilled.contains(trace.player());tasks.put(occurrence.occurrenceId(),new EngineInputs.TaskInput(episode,true,true,known));
            expanded.model().getActors().stream().flatMap(a->a.neededBys().stream()).filter(n->n.task().equals(occurrence.sourceElementId())).forEach(n->resources.put(new EngineInputs.ResourceKey(occurrence.occurrenceId(),n.resource()),new EngineInputs.ResourceInput(episode,true,expanded.scope().artifact())));
        }
        return new EngineInputs(Map.of(),tasks,Map.of(),resources);
    }
}
