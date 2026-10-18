package org.tzi.use.plugins.jacamo.semantics;

import java.util.*;
import java.util.concurrent.*;
import cartago.Op;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.plugins.jacamo.codegrounded.NativeUseSessionActivator;
import org.tzi.use.main.Session;
import static org.junit.jupiter.api.Assertions.*;

class OriginalEngineSessionIntegrationTest {
    @Test void actualAcceptedCutCallsOriginalMarkingAndPropagationOnOneHostSystem() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            var host=new Session();new NativeUseSessionActivator().activate(host,f.built.nativeResult());assertSame(host.system(),f.projector.coordinator().system());
            f.settled();assertEquals("PENDING",element(f,"left").get("aggregateStatus"));assertEquals("PENDING",element(f,"all").get("aggregateStatus"));
            var first=f.projector.coordinator().latest();assertEquals("PINNED_NATIVE_CUT",core(first).get("evaluated"));assertEquals(f.program.hash(),core(first).get("programHash"));assertTrue(first.semantic().requirements().isEmpty(),"No conversion to the local TemporalGoalMonitor M1");
            f.alice.doAction(f.artifact,new Op("increment"));f.await(()->f.value("p").equals("1"));f.settled();f.resync();f.settled();
            assertEquals("FULFILLED",element(f,"left").get("aggregateStatus"));assertEquals("PENDING",element(f,"right").get("aggregateStatus"));assertEquals("PENDING",element(f,"all").get("aggregateStatus"));assertEquals("FULFILLED",element(f,"any").get("aggregateStatus"));
            // The original translator instantiates both [Reviewer] and
            // [Reviewer,Worker]. Only the actual dependency endpoint propagates.
            assertEquals("UNKNOWN",element(f,"requested",1).get("aggregateStatus"));assertEquals("FULFILLED",element(f,"requested",2).get("aggregateStatus"));
            // A resync of an already admitted watermark/state reads the last marking;
            // it must not invoke the recurrence for the same source cut a second time.
            var context=context(f.projector.coordinator().latest());assertEquals(false,context.get("advance"));assertTrue(((List<?>)context.get("originalApiCalls")).isEmpty());
            var calls=f.projector.coordinator().history().stream().filter(r->r.semantic()!=null).map(OriginalEngineSessionIntegrationTest::core)
                    .flatMap(c->((List<?>)c.getOrDefault("contexts",List.of())).stream()).map(CanonicalJson::object)
                    .flatMap(c->((List<?>)c.getOrDefault("originalApiCalls",List.of())).stream()).toList();
            assertTrue(calls.contains("GoalMarking.update"));assertTrue(calls.contains("GoalModelInstanceTranslator.propagateDependencies"));
            f.alice.doAction(f.artifact,new Op("increment"));f.await(()->f.value("p").equals("2"));f.settled();f.resync();f.settled();assertEquals("FULFILLED",element(f,"all").get("aggregateStatus"));
            assertSame(host.system(),f.projector.system());assertEquals(f.projector.coordinator().latest().stateHash(),core(f.projector.coordinator().latest()).get("stateHash"));assertEquals(0,((Number)f.projector.coordinator().originalEngine().metrics().get("pendingPins")).intValue());
        }
    }
    @Test void realNativeAtPreTaskInputRequiresExactPerformerAndPinnedEnactmentToken() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();f.alice.doAction(f.artifact,new Op("increment"));f.await(()->f.value("p").equals("1"));f.settled();
            var rows=f.projector.coordinator().history().stream().filter(r->r.semantic()!=null).flatMap(r->r.semantic().constraints().stream()).toList();
            var contract=rows.stream().filter(r->r.kind().equals("INVOCATION_CONFORMANCE")&&r.verdict()==SemanticVerdict.PASS).findFirst().orElseThrow(()->new AssertionError(rows.toString()));assertEquals("0",contract.witness().get("before.p"));assertEquals("1",contract.witness().get("after.p"));assertEquals("PASS",contract.witness().get("preVerdict"));assertEquals("PASS",contract.witness().get("postVerdict"));assertFalse(contract.witness().get("taskBindingTokens").equals("{}"));
            f.resync();f.settled();assertEquals("FULFILLED",element(f,"increment").get("leafStatus"));assertEquals(Map.of("preSeen",true,"postSeen",true),element(f,"increment").get("task"));
            assertTrue(f.projector.coordinator().history().stream().filter(r->r.semantic()!=null).map(OriginalEngineSessionIntegrationTest::core).flatMap(c->((List<?>)c.getOrDefault("contexts",List.of())).stream()).map(CanonicalJson::object).anyMatch(c->((List<?>)c.getOrDefault("originalApiCalls",List.of())).contains("TaskMarking.update")));
        }
    }
    @Test void missingPropertyDefersRawHistoryAndGapIsNotHealedBySameRunResync() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.alice.doAction(f.artifact,new Op("increment"));f.await(()->f.value("p").equals("1"));f.settled();f.resync();f.settled();var before=element(f,"left").get("goal");
            f.alice.doAction(f.artifact,new Op("remove"));f.await(()->f.projector.coordinator().read(()->f.counter().state(f.projector.system().state()).attributeValue("p").isUndefined()));f.settled();f.resync();f.settled();assertEquals(before,element(f,"left").get("goal"));assertTrue(element(f,"left").get("quality").toString().contains("UNAVAILABLE"));
            f.projector.coordinator().coverageGap("test-gap");f.settled();f.resync();f.settled();assertEquals("GAP",core(f.projector.coordinator().latest()).get("historyQuality"));assertEquals("HISTORY_GAP",element(f,"left").get("quality"));assertEquals(before,element(f,"left").get("goal"));
        }
    }
    @Test void newProducerGenerationResetsHistoryAndCloseFencesCallbacksWithoutBlockingEdt() throws Exception {
        RuntimeEngineSessionOwner owner;
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            owner=f.projector.coordinator().originalEngine();f.alice.doAction(f.artifact,new Op("increment"));f.await(()->f.value("p").equals("1"));f.settled();f.resync();f.settled();assertEquals("FULFILLED",element(f,"left").get("leafStatus"));
            f.alice.doAction(f.artifact,new Op("set",0));f.await(()->f.value("p").equals("0"));f.settled();f.target.set(null);var snapshot=f.captures.capture(f.model.modelRevision(),3).snapshot();f.projector.applySnapshot(snapshot,"new-producer-run",2);f.settled();assertEquals("PENDING",element(f,"left").get("leafStatus"));assertEquals("new-producer-run",CanonicalJson.object(core(f.projector.coordinator().latest()).get("identity")).get("run"));
            long start=System.nanoTime();javax.swing.SwingUtilities.invokeAndWait(owner::close);assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1000);assertThrows(IllegalStateException.class,()->owner.rebind("run",1,"revision"));owner.close();
        }
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!Boolean.TRUE.equals(owner.metrics().get("terminated"))&&System.nanoTime()<end)Thread.sleep(10);assertEquals(true,owner.metrics().get("terminated"));assertEquals(0,((Number)owner.metrics().get("pendingPins")).intValue());
    }
    @Test void nativeViolationCommitsObservedFactAndExternalOclRemainsInSameResult() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.projector.coordinator().loadProfileSource("authored.ocl","context Counter inv Limit: self.p <= 1");f.settled();
            f.alice.doAction(f.artifact,new Op("set",-1));f.await(()->f.value("p").equals("-1"));f.settled();f.resync();f.settled();
            var result=f.projector.coordinator().latest();assertTrue(result.outcomes().stream().anyMatch(r->r.constraintId().startsWith("NATIVE:")&&r.outcome()==VerificationOutcome.FAIL));assertTrue(result.outcomes().stream().anyMatch(r->r.constraintId().startsWith("EXTERNAL:")&&r.outcome()==VerificationOutcome.PASS));assertEquals("-1",f.value("p"));assertTrue(result.semantic().requirements().isEmpty());
        }
    }
    static Map<String,Object> core(org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult result){return CanonicalJson.object(result.semantic().observation().get("originalEngine"));}
    static Map<String,Object> context(org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult result){return CanonicalJson.object(((List<?>)core(result).get("contexts")).getFirst());}
    static Map<String,Object> element(CanonicalRuntimeFixture f,String source){return CanonicalJson.object(CanonicalJson.object(context(f.projector.coordinator().latest()).get("occurrences")).values().stream().map(CanonicalJson::object).filter(o->source.equals(o.get("sourceElement"))).findFirst().orElseThrow());}
    static Map<String,Object> element(CanonicalRuntimeFixture f,String source,int stack){return CanonicalJson.object(context(f.projector.coordinator().latest()).get("occurrences")).values().stream().map(CanonicalJson::object).filter(o->source.equals(o.get("sourceElement"))&&((List<?>)o.get("stack")).size()==stack).findFirst().orElseThrow();}
}
