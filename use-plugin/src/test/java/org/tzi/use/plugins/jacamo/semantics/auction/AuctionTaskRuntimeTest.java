package org.tzi.use.plugins.jacamo.semantics.auction;

import java.time.Instant;
import java.util.*;
import cartago.Op;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionSchemeObservationDecoder;
import static org.junit.jupiter.api.Assertions.*;

/** Native production-owner integration with explicit source-contract fixtures.
 * Full external source/installed USE acceptance remains E01/E02. */
class AuctionTaskRuntimeTest {
    @Test void creationEntryBeforeArtifactExistsMustBindOnlyItsOwnLaterCreatedInstance()throws Exception {
        try(var f=new AuctionTaskOclBindingsTest.Component("A_start_t1")) {
            f.fixture.disposeArtifact(0);f.fixture.resync();
            var entry=AuctionStepDecoderTest.variant(f.frame("ENABLED"),data->{data.put("artifactIdentity","");CanonicalJson.object(data.get("bindings")).put("artifact",Map.of());});
            apply(f,entry);
            f.fixture.org.live.auction("castOne");resync(f);
            var fresh=new org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionOccurrenceBindingAdapter(f.fixture.program).resolve(f.fixture.projector.mutations(),f.fixture.run()).stream().filter(s->s.scheme().equals(f.scope.scheme())&&s.complete()).findFirst().orElseThrow();
            var terminal=AuctionStepDecoderTest.variant(f.frame("SUCCEEDED"),data->{data.put("artifactIdentity",fresh.artifact());CanonicalJson.object(CanonicalJson.object(data.get("bindings")).get("artifact")).put("semanticId",fresh.artifact());});
            apply(f,terminal);var core=CanonicalJson.object(settled(f).semantic().observation().get("originalEngine"));
            var context=((List<?>)core.get("contexts")).stream().map(CanonicalJson::object).filter(c->c.get("context").equals(fresh.id())).findFirst().orElseThrow();
            var task=CanonicalJson.object(context.get("occurrences")).values().stream().map(CanonicalJson::object).filter(r->r.get("sourceElement").equals(f.task)).findFirst().orElseThrow();
            assertEquals("FULFILLED",task.get("leafStatus"),"The creation Task entry has no domain self yet; preserve its source-qualified tuple PRE without fabricating an Artifact");
        }
    }
    @Test void allSixTasksUseTheirOwnActualSchemeDemandAndNativePredicatePair()throws Exception {
        for(String task:List.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1","P_bid_t2"))try(var f=new AuctionTaskOclBindingsTest.Component(task)) {
            if(task.startsWith("P_")||task.startsWith("A_decide")){f.fixture.org.live.actor.doAction(f.fixture.schemes.getFirst(),new Op("goalAchieved","start"),5000);}
            if(task.startsWith("A_decide"))f.fixture.participants.getFirst().doAction(f.fixture.schemes.getFirst(),new Op("goalAchieved","bid"),5000);
            f.fixture.resync();apply(f,f.frame("ENABLED"));
            if(task.equals("A_start_t2"))start(f);
            if(task.equals("A_decide_t2")){var marks=f.fixture.projector.sourceWatermarks();f.fixture.org.live.actor.doAction(f.fixture.artifacts.getFirst(),new Op("stop"),5000);for(var event:f.fixture.eventsSince(marks)){f.fixture.projector.apply(event);settled(f);}}
            apply(f,f.frame("SUCCEEDED"));assertEquals("FULFILLED",task(f).get("leafStatus"),task);
            var before=f.fixture.projector.coordinator().originalEngine().admission();
            apply(f,f.frame("ENABLED"));assertEquals("UNKNOWN",task(f).get("leafStatus"),"Closed invocation cannot reopen: "+task);
            assertTrue(settled(f).semantic().constraints().stream().anyMatch(r->r.diagnostic().equals("DUPLICATE_TASK_ENTRY_INVALIDATED")));
            assertTrue(before.steps().seen().contains(f.frame("ENABLED").subject()));
        }
    }
    @Test void administrativeBidSatisfactionNeverSuppliesMissingTaskExecutionAndResetWithoutDoneDoesNotInventReactivation()throws Exception {
        try(var f=new AuctionTaskOclBindingsTest.Component("P_bid_t2")) {
            f.fixture.org.live.actor.doAction(f.fixture.schemes.getFirst(),new Op("goalAchieved","start"),5000);f.fixture.resync();
            assertEquals("UNKNOWN",task(f).get("leafStatus"));
            f.fixture.org.live.actor.doAction(f.fixture.schemes.getFirst(),new Op("admCommand","goalSatisfied(bid)"),5000);f.fixture.resync();
            assertEquals("UNKNOWN",task(f).get("leafStatus"));
            assertNotEquals("FULFILLED",task(f).get("aggregateStatus"));
            f.fixture.org.live.actor.doAction(f.fixture.schemes.getFirst(),new Op("resetGoal","bid"),5000);f.fixture.resync();
            java.nio.file.Files.write(java.nio.file.Path.of("target/auction-implementation/I04-admin-reset-result.json"),CanonicalJson.encode(Map.of("semantic",settled(f).semantic().toMap(),"metadata",f.fixture.projector.mutations().objectMetadata(f.fixture.projector.mutations().objectForSemanticId(f.scope.id())))));
            var source=new AuctionSchemeObservationDecoder(f.fixture.program).decode(CanonicalJson.object(CanonicalJson.decode(f.fixture.projector.mutations().metadata(f.fixture.projector.mutations().objectForSemanticId(f.scope.id()),"auctionMonitorSource").getBytes(java.nio.charset.StandardCharsets.UTF_8))),f.fixture.run().id(),f.fixture.run().generation(),f.fixture.run().modelRevision());
            // Pinned Scheme.resetGoal clears satisfiedGoals only if removeDoneGoal
            // changes a source done fact. Administrative satisfaction supplied no done.
            assertEquals("SATISFIED",source.goal("bid").state());assertTrue(source.doneBy().isEmpty());assertEquals("UNKNOWN",task(f).get("leafStatus"));
        }
    }
    @Test void actualDoneThenResetIsARealHistoryBreakAndCannotReactivateOldTaskHistory()throws Exception {
        try(var f=new AuctionTaskOclBindingsTest.Component("P_bid_t2")) {
            f.fixture.org.live.actor.doAction(f.fixture.schemes.getFirst(),new Op("goalAchieved","start"),5000);f.fixture.resync();
            apply(f,f.frame("ENABLED"));apply(f,f.frame("SUCCEEDED"));assertEquals("FULFILLED",task(f).get("leafStatus"));
            f.fixture.participants.getFirst().doAction(f.fixture.schemes.getFirst(),new Op("goalAchieved","bid"),5000);resync(f);
            f.fixture.org.live.actor.doAction(f.fixture.schemes.getFirst(),new Op("resetGoal","bid"),5000);resync(f);
            var contexts=(List<?>)CanonicalJson.object(settled(f).semantic().observation().get("originalEngine")).get("contexts");
            assertTrue(contexts.stream().map(CanonicalJson::object).anyMatch(c->c.get("context").equals(f.scope.id())&&"UNAVAILABLE".equals(c.get("quality"))),"Actual SATISFIED reset loses producer continuous history; it cannot certify an old marking as current");
        }
    }
    @Test void aDifferentInvocationCannotBorrowHistoricalPreAndStateOnlySuccessCannotFulfillIt()throws Exception {
        try(var f=new AuctionTaskOclBindingsTest.Component("A_start_t2")) {
            apply(f,f.frame("ENABLED"));start(f);apply(f,invocation(f,"SUCCEEDED",2));
            assertEquals("UNKNOWN",task(f).get("leafStatus"));
            assertTrue(settled(f).semantic().constraints().stream().anyMatch(r->r.diagnostic().equals("TASK_TERMINAL_MATCHING_DEMAND_PRE_UNAVAILABLE")));
            // The Artifact is already running. Invocation3 has a known false
            // pre and true state post; original recurrence must stay UNKNOWN.
            apply(f,invocation(f,"ENABLED",3));apply(f,invocation(f,"SUCCEEDED",3));
            assertEquals("UNKNOWN",task(f).get("leafStatus"));
            var clauses=settled(f).semantic().constraints();
            assertTrue(clauses.stream().anyMatch(r->r.kind().equals("TASK_PRE")&&r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL));
            assertTrue(clauses.stream().anyMatch(r->r.kind().equals("TASK_POST")&&r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS));
            assertTrue(clauses.stream().noneMatch(r->r.kind().equals("TASK_CONFORMANCE")&&r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS));
        }
    }
    @Test void sourceCompositeFailureStaysOriginalPendingAndMissingTerminalHasNoFalsePost()throws Exception {
        try(var f=new AuctionTaskOclBindingsTest.Component("A_start_t1")) {
            apply(f,f.frame("ENABLED"));assertEquals("UNKNOWN",task(f).get("leafStatus"));
            assertTrue(settled(f).semantic().constraints().stream().noneMatch(r->r.kind().equals("TASK_POST")));
            apply(f,f.frame("FAILED"));assertEquals("PENDING",task(f).get("leafStatus"));
            var result=settled(f);assertTrue(result.semantic().constraints().stream().anyMatch(r->r.kind().equals("SOURCE_EXECUTION_FAILURE")));
            assertNotEquals("VIOLATED",task(f).get("leafStatus"));assertNotEquals("VIOLATED",task(f).get("aggregateStatus"));
        }
    }
    @Test void gapResyncAndNativeRollbackCannotCarryAnOpenPreIntoLaterCompletion()throws Exception {
        try(var f=new AuctionTaskOclBindingsTest.Component("A_start_t2")) {
            var coordinator=f.fixture.projector.coordinator();var probe=coordinator.getClass().getDeclaredMethod("faultProbe",java.util.function.Consumer.class);assertTrue(probe.trySetAccessible());
            var before=coordinator.originalEngine().admission();
            probe.invoke(coordinator,(java.util.function.Consumer<String>)(point->{if(point.equals("AFTER_NATIVE_ACTION"))throw new IllegalStateException("I04_INJECTED_AFTER_STEP_ENTRY");}));
            try{assertEquals("I04_INJECTED_AFTER_STEP_ENTRY",assertThrows(IllegalStateException.class,()->apply(f,f.frame("ENABLED"))).getMessage());}
            finally{probe.invoke(coordinator,(java.util.function.Consumer<String>)(ignored->{}));}
            assertEquals(before.steps(),coordinator.originalEngine().admission().steps(),"Open step/pre-cut participates in the same writer rollback");
            assertTrue(coordinator.originalEngine().freshContexts().isEmpty(),"The rejected outer protocol marks coverage lost after native rollback");
            resync(f);apply(f,invocation(f,"ENABLED",2));assertFalse(coordinator.originalEngine().admission().steps().active().isEmpty());
            coordinator.coverageGap("I04_TEST_RECORDED_GAP");resync(f);
            assertTrue(coordinator.originalEngine().admission().steps().active().isEmpty());
            apply(f,invocation(f,"SUCCEEDED",2));assertEquals("UNKNOWN",task(f).get("leafStatus"));
            assertTrue(settled(f).semantic().constraints().stream().filter(r->r.kind().equals("TASK_POST")).noneMatch(r->r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS));
        }
    }
    @Test void acceptedStartStepUsesItsOwnBeforeAfterAndActualDemandToAdvanceOriginalTaskMarking()throws Exception {
        try(var f=new AuctionTaskOclBindingsTest.Component("A_start_t2")) {
            var engine=f.fixture.projector.mutations();var monitor=engine.objectForSemanticId(f.scope.id());
            var source=new AuctionSchemeObservationDecoder(f.fixture.program).decode(CanonicalJson.object(CanonicalJson.decode(engine.metadata(monitor,"auctionMonitorSource").getBytes(java.nio.charset.StandardCharsets.UTF_8))),f.fixture.run().id(),f.fixture.run().generation(),f.fixture.run().modelRevision());
            assertEquals("ENABLED",source.goal("start").state(),"Demand is actual SchemeBoard state plus mission commitment, not inferred from role membership");
            apply(f,f.frame("ENABLED"));
            var marks=f.fixture.projector.sourceWatermarks();f.fixture.org.live.actor.doAction(f.fixture.artifacts.getFirst(),new Op("start",f.service),5000);
            AuctionMonitorProjectionTest.await(()->f.fixture.eventsSince(marks).stream().anyMatch(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"start".equals(e.after().get("operation"))));
            for(var event:f.fixture.eventsSince(marks)) {f.fixture.projector.apply(event);settled(f);}
            apply(f,f.frame("SUCCEEDED"));var result=settled(f);
            var context=context(f);var task=task(f);
            java.nio.file.Files.write(java.nio.file.Path.of("target/auction-implementation/I04-start-result.json"),CanonicalJson.encode(Map.of("semantic",result.semantic().toMap(),"admission",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(f.fixture.projector.coordinator().originalEngine().admission()))));
            assertEquals("FULFILLED",task.get("leafStatus"),"The source-qualified matching invocation must reach original TaskMarking");
            assertTrue(((List<?>)context.get("originalApiCalls")).contains("TaskMarking.update"));
            var core=CanonicalJson.object(result.semantic().observation().get("originalEngine"));
            for(var other:((List<?>)core.get("contexts")).stream().map(CanonicalJson::object).filter(c->!c.get("context").equals(f.scope.id())).toList())
                assertTrue(CanonicalJson.object(other.get("occurrences")).values().stream().map(CanonicalJson::object).filter(r->r.get("sourceElement").equals(f.task)).noneMatch(r->"FULFILLED".equals(r.get("leafStatus"))),"The same actor cannot credit its other Auction");
        }
    }
    static void start(AuctionTaskOclBindingsTest.Component f)throws Exception {
        var marks=f.fixture.projector.sourceWatermarks();f.fixture.org.live.actor.doAction(f.fixture.artifacts.getFirst(),new Op("start",f.service),5000);
        AuctionMonitorProjectionTest.await(()->f.fixture.eventsSince(marks).stream().anyMatch(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"start".equals(e.after().get("operation"))));
        for(var event:f.fixture.eventsSince(marks)){f.fixture.projector.apply(event);settled(f);}
    }
    static void resync(AuctionTaskOclBindingsTest.Component f) {
        var source=f.fixture.cut();var marks=new TreeMap<>(source.endWatermarks());var quality=new TreeMap<>(source.sourceCompleteness());
        Long observed=f.fixture.projector.sourceWatermarks().get(VerificationObservation.SOURCE_ID);
        if(observed!=null){marks.put(VerificationObservation.SOURCE_ID,new SourceWatermark(VerificationObservation.SOURCE_ID,observed));quality.put(VerificationObservation.SOURCE_ID,Completeness.PARTIAL);}
        // Explicit component producer retains its injected observer watermark;
        // no absent frame becomes a complete history or a source false value.
        var cut=new RuntimeSnapshot(source.snapshotId()+":explicit-observer-prefix",source.modelRevision(),source.captureStartedAt(),source.captureEndedAt(),marks,marks,source.validationAttempts(),source.facts(),quality,source.stateFingerprint(),source.captureMetadata());
        f.fixture.projector.applySnapshot(cut,f.fixture.run().id(),f.fixture.run().generation());settled(f);
    }
    static Map<String,Object> context(AuctionTaskOclBindingsTest.Component f){var core=CanonicalJson.object(settled(f).semantic().observation().get("originalEngine"));return ((List<?>)core.get("contexts")).stream().map(CanonicalJson::object).filter(c->c.get("context").equals(f.scope.id())).findFirst().orElseThrow();}
    static Map<String,Object> task(AuctionTaskOclBindingsTest.Component f){return CanonicalJson.object(context(f).get("occurrences")).values().stream().map(CanonicalJson::object).filter(r->r.get("sourceElement").equals(f.task)&&r.get("player").equals(f.actor.player())).findFirst().orElseThrow();}
    static VerificationObservation invocation(AuctionTaskOclBindingsTest.Component f,String phase,int n)throws Exception {
        var base=f.frame(phase);var data=new LinkedHashMap<>(base.data());String invocation="jason-step:"+f.fixture.run().id()+":"+f.sourceAgent.incarnation()+":"+n;
        data.put("stepInvocation",invocation);data.put("goalInvocation","jason-goal:"+f.fixture.run().id()+":"+f.sourceAgent.incarnation()+":"+n);
        return new VerificationObservation(base.kind(),invocation,base.quality(),base.diagnostic(),base.sourceTime(),data);
    }
    static void apply(AuctionTaskOclBindingsTest.Component f,VerificationObservation observation) {
        long sequence=f.fixture.projector.sourceWatermarks().getOrDefault(VerificationObservation.SOURCE_ID,0L)+1;
        f.fixture.projector.apply(observation.event(f.fixture.run().id(),f.fixture.run().generation(),f.fixture.run().modelRevision(),sequence,Instant.now(),"",""));settled(f);
    }
    static org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult settled(AuctionTaskOclBindingsTest.Component f){var owner=f.fixture.projector.coordinator();return owner.awaitEvaluation(owner.latest(),15000);}
}
