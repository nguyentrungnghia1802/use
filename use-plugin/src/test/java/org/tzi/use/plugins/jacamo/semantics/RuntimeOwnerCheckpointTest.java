package org.tzi.use.plugins.jacamo.semantics;

import java.util.*;
import java.util.concurrent.*;
import cartago.Op;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeOwnerCheckpointTest {
    @Test void savepointRestoresOnlyHistoryAndRejectsForeignOrRevokedOwner() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();var owner=f.projector.coordinator().originalEngine();var before=owner.checkpoint().get(5,TimeUnit.SECONDS);
            f.alice.doAction(f.artifact,new Op("increment"));f.await(()->f.value("p").equals("1"));f.settled();f.resync();f.settled();f.target.set(null);
            var after=owner.checkpoint().get(5,TimeUnit.SECONDS);assertNotEquals(before.histories(),after.histories());
            owner.restore(before).get(5,TimeUnit.SECONDS);assertEquals(before,owner.checkpoint().get(5,TimeUnit.SECONDS));assertEquals("1",f.value("p"),"The engine is a consumer, never an M0 rollback authority");
            owner.restore(after).get(5,TimeUnit.SECONDS);assertEquals(after,owner.checkpoint().get(5,TimeUnit.SECONDS));
            var foreign=new RuntimeEngineSessionOwner.Checkpoint("foreign",after.ticket(),after.identity(),after.histories(),after.results(),after.gaps(),after.discontinuous());
            assertThrows(ExecutionException.class,()->owner.restore(foreign).get(5,TimeUnit.SECONDS));assertEquals(after,owner.checkpoint().get(5,TimeUnit.SECONDS));
            f.projector.coordinator().read(()->{owner.rebind("replacement",2,f.model.modelRevision());return null;});
            assertThrows(ExecutionException.class,()->owner.restore(after).get(5,TimeUnit.SECONDS));
        }
    }
    @Test void boundedPinnedCutsNeverWaitOnEdtAndCloseCompletesQueuedControlsAndFencesResults() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();f.target.set(null);var owner=f.projector.coordinator().originalEngine();var blocked=new BlockingExpression();
            try {
                var prepared=new ExternalOclConstraintService.PreparedEvaluation(f.projector.system().model(),List.of(new ExternalOclConstraintService.PreparedInvariant("test:block","Counter",new ExpConstBoolean(true),blocked,new ExpConstBoolean(false),"","")));
                var work=new ArrayList<CompletableFuture<RuntimeEngineSessionOwner.Evaluation>>();work.add(owner.submit(cut(f,prepared,"blocked")));assertTrue(blocked.entered.await(5,TimeUnit.SECONDS));
                for(int i=1;i<8;i++)work.add(owner.submit(cut(f,prepared,"queued-"+i)));
                assertEquals(8,((Number)owner.metrics().get("pendingPins")).intValue());assertThrows(ExecutionException.class,()->owner.submit(cut(f,prepared,"overflow")).get(5,TimeUnit.SECONDS));
                var queued=owner.checkpoint();var drain=owner.drained();long start=System.nanoTime();
                javax.swing.SwingUtilities.invokeAndWait(owner::close);assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1000,"EDT close cannot wait for native evaluation");
                for(var future:work)assertTrue(future.isDone());assertTrue(queued.isCompletedExceptionally());assertTrue(drain.isCompletedExceptionally());assertEquals(0,((Number)owner.metrics().get("pendingPins")).intValue());
                var latest=owner.latest();blocked.release.countDown();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(!Boolean.TRUE.equals(owner.metrics().get("terminated"))&&System.nanoTime()<end)Thread.sleep(10);
                assertEquals(true,owner.metrics().get("terminated"));assertSame(latest,owner.latest(),"A revoked computation cannot publish");assertEquals(0L,((Number)owner.metrics().get("pinnedSerializedBytes")).longValue());
                assertEquals(0,((Number)owner.metrics().get("expressions")).intValue(),"The owner must release its compiled-expression references after termination");
            } finally {blocked.release.countDown();}
        }
    }
    @Test void cutAndCheckpointFromAnotherSessionCannotEnterEvenWithSameNativeSystemAndProducerKey() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();f.target.set(null);var owner=f.projector.coordinator().originalEngine();
            try(var other=new RuntimeEngineSessionOwner(f.projector.mutations(),"canonical-run",1,f.model.modelRevision())) {
                var check=other.checkpoint().get(5,TimeUnit.SECONDS);assertThrows(ExecutionException.class,()->owner.restore(check).get(5,TimeUnit.SECONDS));
                var prepared=new ExternalOclConstraintService.PreparedEvaluation(f.projector.system().model(),List.of());
                var foreign=f.projector.coordinator().read(()->other.prepare(100,"foreign","cut",Map.of(),false,false,List.of(),prepared,1024));
                assertThrows(ExecutionException.class,()->owner.submit(foreign).get(5,TimeUnit.SECONDS));assertEquals(0,((Number)owner.metrics().get("pendingPins")).intValue());
            }
        }
    }
    private static RuntimeEngineSessionOwner.Cut cut(CanonicalRuntimeFixture f,ExternalOclConstraintService.PreparedEvaluation prepared,String boundary) {
        return f.projector.coordinator().read(()->f.projector.coordinator().originalEngine().prepare(100,boundary,"pinned",Map.of("cartago",Completeness.COMPLETE),false,false,List.of(),prepared,1024));
    }
    /** Deterministic unit fault injection; production native OCL has no cancellation API. */
    private static final class BlockingExpression extends Expression {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        BlockingExpression(){super(TypeFactory.mkBoolean());}
        @Override public Value eval(EvalContext context){entered.countDown();boolean interrupted=false;try{while(true)try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("TEST_BLOCK_TIMEOUT");break;}catch(InterruptedException error){interrupted=true;}return BooleanValue.TRUE;}finally{if(interrupted)Thread.currentThread().interrupt();}}
        @Override public StringBuilder toString(StringBuilder text){return text.append("test-block");}
        @Override public void processWithVisitor(ExpressionVisitor visitor){}
        @Override protected boolean childExpressionRequiresPreState(){return false;}
    }
}
