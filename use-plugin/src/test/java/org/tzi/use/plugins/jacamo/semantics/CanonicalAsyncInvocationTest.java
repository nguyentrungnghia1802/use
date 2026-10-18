package org.tzi.use.plugins.jacamo.semantics;

import java.util.*;
import java.util.concurrent.*;
import cartago.Op;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.RuntimeConstraintPolicy;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointType;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.semantics.OriginalEngineSessionIntegrationTest.*;

class CanonicalAsyncInvocationTest {
    @Test void ambiguousInvocationContextCannotCreditEveryEnactmentOfOnePerformer() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules,2)) {
            f.settled();assertEquals(2,((List<?>)core(f.projector.coordinator().latest()).get("contexts")).size());f.alice.doAction(f.artifact,new Op("increment"));f.await(()->f.value("p").equals("1"));f.settled();
            for(var context:(List<?>)core(f.projector.coordinator().latest()).get("contexts")) {
                var occurrences=org.jacamo.bridge.contract.CanonicalJson.object(org.jacamo.bridge.contract.CanonicalJson.object(context).get("occurrences"));
                var task=occurrences.values().stream().map(org.jacamo.bridge.contract.CanonicalJson::object).filter(o->o.get("sourceElement").equals("increment")).findFirst().orElseThrow();
                assertEquals(org.vnu.sme.goal.verify.conformance.semantics.TaskMarking.initial().status().name(),task.get("leafStatus"));assertEquals(Map.of("preSeen",false,"postSeen",false),task.get("task"));assertNotEquals("KNOWN",task.get("quality"));
            }
            assertTrue(f.projector.coordinator().history().stream().filter(r->r.semantic()!=null).flatMap(r->r.semantic().constraints().stream()).anyMatch(r->r.kind().equals("INVOCATION_CONFORMANCE")&&r.verdict()==SemanticVerdict.PASS),"The artifact contract is known even when the organizational Task context is ambiguous");
        }
    }
    @Test void actualZeroToTwoFailsNativeAtPreWhileOriginalHistoricalTaskRemainsPending() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,source->{
            var base=CanonicalGoalMappingTest.rules(source);var contracts=base.contracts().stream().map(c->new MappingRuleSet.Contract(c.id(),c.kind(),c.contextDeclaration(),c.targetElementId(),c.body(),List.of(CslCounterArtifact.class.getName(),"incrementTwo",List.of(),"void"),c.variableTypes(),c.origin())).toList();
            return new MappingRuleSet(base.version(),base.id(),base.profileId(),base.profileVersion(),base.expectedSourceHash(),base.cslSource(),base.iStarSource(),base.bindings(),contracts,base.decisions(),base.options());
        })) {
            f.settled();f.alice.doAction(f.artifact,new Op("incrementTwo"));f.await(()->f.value("p").equals("2"));f.settled();
            var post=f.projector.coordinator().history().stream().filter(r->r.semantic()!=null).flatMap(r->r.semantic().constraints().stream()).filter(r->r.kind().equals("INVOCATION_CONFORMANCE")&&r.verdict()==SemanticVerdict.FAIL).findFirst().orElseThrow();
            assertEquals("0",post.witness().get("before.p"));assertEquals("2",post.witness().get("after.p"));assertEquals("PASS",post.witness().get("preVerdict"));assertEquals("FAIL",post.witness().get("postVerdict"));assertEquals("2",f.value("p"));
            assertEquals("PENDING",element(f,"increment").get("leafStatus"));assertEquals(Map.of("preSeen",true,"postSeen",false),element(f,"increment").get("task"));
        }
    }
    @Test void aBlockedNativeOperationPreconditionDoesNotHoldWriterOrCartagoAction() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();var expression=new BlockingExpression();
            f.projector.coordinator().read(()->{var operation=f.counter().cls().allOperations().stream().filter(op->op.preConditions().stream().anyMatch(c->"increment:pre".equals(org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.decode(c.getAnnotationValue("CslConstraint","id64"))))).findFirst().orElseThrow();new UseModelApi(f.projector.system().model()).createPrePostConditionEx("Probe",operation,true,expression);return null;});
            var action=CompletableFuture.runAsync(()->{try{f.alice.doAction(f.artifact,new Op("increment"));}catch(Exception error){throw new CompletionException(error);}});
            try {
                assertTrue(expression.entered.await(5,TimeUnit.SECONDS));action.get(5,TimeUnit.SECONDS);assertEquals("1",f.value("p"));
                long start=System.nanoTime();javax.swing.SwingUtilities.invokeAndWait(()->assertEquals("1",f.value("p")));assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<1000);
            } finally {expression.release.countDown();}
            f.settled();assertEquals("FULFILLED",element(f,"increment").get("leafStatus"));assertEquals(0,((Number)f.projector.coordinator().originalEngine().metrics().get("pendingPins")).intValue());
        }
    }
    @Test void canonicalOperationPolicyUsesTheSameNamespacedConditionIdsAsWorkerResults() throws Exception {
        try(var f=new CanonicalRuntimeFixture(CslCounterArtifact.class,CanonicalGoalMappingTest::rules)) {
            f.settled();var service=f.projector.coordinator().constraints();
            f.projector.coordinator().read(()->{
                assertTrue(service.runtimeRegistry().stream().anyMatch(r->r.id().equals("increment:post")));
                var policy=new RuntimeConstraintPolicy(ConstraintOrigin.USER,RuntimeConstraintPolicy.Severity.HARD,RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL,Set.of(CheckpointType.OPERATION_POST),Set.of(),true,"Explicit test approval");service.configurePolicy("increment:post",policy);assertTrue(service.mayPause("increment:post"));assertFalse(service.mayPause("increment:pre"));assertFalse(service.conditionFingerprint("increment:post").isBlank());return null;
            });
        }
    }
    private static final class BlockingExpression extends Expression {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        BlockingExpression(){super(TypeFactory.mkBoolean());}
        @Override public Value eval(EvalContext context){assertFalse(javax.swing.SwingUtilities.isEventDispatchThread());entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("TEST_NATIVE_PRE_TIMEOUT");return BooleanValue.TRUE;}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}}
        @Override public StringBuilder toString(StringBuilder text){return text.append("true");}
        @Override public void processWithVisitor(ExpressionVisitor visitor){}
        @Override protected boolean childExpressionRequiresPreState(){return false;}
    }
}
