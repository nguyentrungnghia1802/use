package org.tzi.use.plugins.jacamo;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalIngressBackpressureTest {
    @Test void receiverBurstKeepsOriginalHistoryWithinTheUnchangedPinBudget()throws Exception {
        try(var fixture=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            fixture.settled();var projector=fixture.projector();var c=projector.coordinator();var system=projector.system();
            for(int i=0;i<128;i++){DefaultJaCaMoFacade.awaitCanonicalIngress(projector);c.manualVerify();}
            DefaultJaCaMoFacade.awaitCanonicalIngress(projector);
            assertSame(system,projector.system());assertFalse(c.journal().hasGap());assertEquals(0,c.originalEngine().metrics().get("pendingPins"));
            assertTrue(c.history().stream().filter(r->r.semantic()!=null).flatMap(r->r.semantic().constraints().stream()).noneMatch(r->r.kind().equals("EVALUATION_ERROR")));
            assertEquals("OBSERVED_PREFIX",((Map<?,?>)c.latest().semantic().observation().get("originalEngine")).get("historyQuality"));
        }
    }
    @Test void receiverWaitsOutsideEdtAndNativeUiRemainsAvailableDuringSlowCoreWork()throws Exception {
        try(var fixture=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            fixture.settled();var projector=fixture.projector();var c=projector.coordinator();var owner=c.originalEngine();var blocked=new BlockingExpression();
            try {
                var prepared=new ExternalOclConstraintService.PreparedEvaluation(projector.system().model(),List.of(new ExternalOclConstraintService.PreparedInvariant("test:slow","Counter",new ExpConstBoolean(true),blocked,new ExpConstBoolean(false),"","")));
                var cut=c.read(()->owner.prepare(100,"test-slow","cut",Map.of(),false,false,List.of(),prepared,1024));owner.submit(cut);
                assertTrue(blocked.entered.await(5,TimeUnit.SECONDS));var waiting=CompletableFuture.runAsync(()->DefaultJaCaMoFacade.awaitCanonicalIngress(projector));
                var heartbeat=new CompletableFuture<Boolean>();javax.swing.SwingUtilities.invokeLater(()->{
                    assertEquals("CANONICAL_INGRESS_WAIT_ON_EDT",assertThrows(IllegalStateException.class,()->DefaultJaCaMoFacade.awaitCanonicalIngress(projector)).getMessage());heartbeat.complete(true);
                });
                assertTrue(heartbeat.get(2,TimeUnit.SECONDS));assertFalse(waiting.isDone());assertSame(projector.system(),c.read(c::system));
                blocked.release.countDown();waiting.get(5,TimeUnit.SECONDS);assertEquals(0,owner.metrics().get("pendingPins"));
            }finally{blocked.release.countDown();}
        }
    }
    private static final class BlockingExpression extends Expression {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        BlockingExpression(){super(TypeFactory.mkBoolean());}
        @Override public Value eval(EvalContext context){entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("TEST_SLOW_CORE_TIMEOUT");return BooleanValue.TRUE;}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}}
        @Override public StringBuilder toString(StringBuilder text){return text.append("test-slow");}
        @Override public void processWithVisitor(ExpressionVisitor visitor) { }
        @Override protected boolean childExpressionRequiresPreState(){return false;}
    }
}
