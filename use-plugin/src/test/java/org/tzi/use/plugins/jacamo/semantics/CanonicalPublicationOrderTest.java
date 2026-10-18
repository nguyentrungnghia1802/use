package org.tzi.use.plugins.jacamo.semantics;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalPublicationOrderTest {
    @Test void alreadyCompletedBootstrapCutDoesNotWaitForANewerBlockedEvaluation() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();var c=f.projector.coordinator();var requested=c.manualVerify();f.settled();var gate=installGate(f);
            var worker=Executors.newSingleThreadExecutor();
            try {
                cut(c,"later-live-observation",true);assertTrue(gate.entered.await(5,TimeUnit.SECONDS));assertEquals("PENDING_EVALUATION",c.latest().freshness());
                var completed=worker.submit(()->c.awaitEvaluation(requested,5000)).get(1,TimeUnit.SECONDS);
                assertEquals(requested.semantic().boundary(),completed.semantic().boundary());assertEquals("CURRENT_OBSERVED",completed.freshness());
                assertEquals("later-live-observation",c.latest().eventId());assertEquals("PENDING_EVALUATION",c.latest().freshness());
            } finally {gate.release.countDown();worker.shutdownNow();assertTrue(worker.awaitTermination(5,TimeUnit.SECONDS));}
        }
    }
    @Test void evidenceOnlyCallbackCannotStrandTheFormalCutAsPermanentlyPending() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();var c=f.projector.coordinator();var gate=installGate(f);var before=c.originalEngine().checkpoint().get(5,TimeUnit.SECONDS);
            try {
                cut(c,"formal-cut",true);assertTrue(gate.entered.await(5,TimeUnit.SECONDS));cut(c,"evidence-only",false);assertEquals("PENDING_EVALUATION",c.latest().freshness());
            }finally{gate.release.countDown();}
            f.settled();assertEquals("CURRENT_OBSERVED",c.latest().freshness());assertEquals("formal-cut",c.latest().eventId());assertEquals("evidence-only",c.lastObservation().eventId());assertEquals(c.latest().stateHash(),c.lastObservation().stateHash());
            var after=c.originalEngine().checkpoint().get(5,TimeUnit.SECONDS);before.histories().forEach((id,image)->assertEquals(image.boundary()+1,after.histories().get(id).boundary()));
        }
    }
    @Test void delayedEvaluationDoesNotPublishOverANewerNativeVersion() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();var c=f.projector.coordinator();var gate=installGate(f);var published=new CopyOnWriteArrayList<String>();c.snapshotObserver(snapshot->published.add(snapshot.result().eventId()));
            RuntimeVerificationResult requested;
            try {cut(c,"older",true);requested=c.latest();assertTrue(gate.entered.await(5,TimeUnit.SECONDS));cut(c,"newer",true);}finally{gate.release.countDown();}
            f.settled();assertEquals("newer",c.latest().eventId());assertEquals("CURRENT_OBSERVED",c.latest().freshness());assertEquals(List.of("newer"),published);assertEquals(c.verificationSnapshot().currentVersion(),c.latest().stateVersion());
            var exact=c.awaitEvaluation(requested,5000);assertEquals("older",exact.eventId());assertEquals(requested.stateVersion(),exact.stateVersion());assertEquals("older",c.completedSnapshot(requested,5000).result().eventId());
            javax.swing.SwingUtilities.invokeAndWait(()->assertEquals("ENGINE_COMPLETION_WAIT_ON_EDT",assertThrows(IllegalStateException.class,()->c.awaitEvaluation(requested,5000)).getMessage()));
            var results=c.history().stream().filter(r->Set.of("older","newer").contains(r.eventId())&&r.semantic()!=null&&r.semantic().observation().get("originalEngine") instanceof Map<?,?> core&&"PINNED_NATIVE_CUT".equals(core.get("evaluated"))).toList();assertEquals(2,results.size());assertTrue(results.getFirst().stateVersion()<results.getLast().stateVersion());
        }
    }
    private static Gate installGate(CanonicalRuntimeFixture f){var gate=new Gate();f.projector.coordinator().read(()->{
        // Wall-clock equality must never be an ownership/publication key.
        var clock=RuntimeVerificationCoordinator.class.getDeclaredField("appliedClock");clock.setAccessible(true);clock.set(f.projector.coordinator(),(java.util.function.Supplier<Instant>)()->Instant.EPOCH);
        new UseModelApi(f.projector.system().model()).createInvariantEx("Slow","Counter",gate,false);return null;});return gate;}
    private static void cut(RuntimeVerificationCoordinator c,String id,boolean advance){c.transaction(advance?"SNAPSHOT":"EVENT",id,"unit-order",0,Instant.now(),Map.of(),advance,()->new RuntimeVerificationCoordinator.Mutation<>(null,advance));}
    private static final class Gate extends Expression {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        Gate(){super(TypeFactory.mkBoolean());}
        @Override public Value eval(EvalContext context){assertFalse(javax.swing.SwingUtilities.isEventDispatchThread());entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("TEST_GATE_TIMEOUT");return BooleanValue.TRUE;}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}}
        @Override public StringBuilder toString(StringBuilder text){return text.append("true");}
        @Override public void processWithVisitor(ExpressionVisitor visitor){}
        @Override protected boolean childExpressionRequiresPreState(){return false;}
    }
}
