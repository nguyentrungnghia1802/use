package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.List;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.RuntimeConstraintPolicy;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointType;
import org.tzi.use.plugins.jacamo.verification.*;

class CheckpointRoutingTest {
    @Test void nativeHardSeverityDoesNotGrantPauseAuthorityWithoutExplicitApproval() throws Exception {
        var pipeline=new CodeGroundedNativePipeline().build(CodeGroundedTestFixtures.helloSnapshot());
        var constraints=new org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService(pipeline.state().system());
        var core=constraints.runtimeRegistry().stream().filter(c->c.policy().severity()==RuntimeConstraintPolicy.Severity.HARD).toList();
        assertFalse(core.isEmpty());
        for(var constraint:core) {
            assertEquals(ConstraintOrigin.CORE,constraint.policy().origin());
            assertEquals(RuntimeConstraintPolicy.Enforcement.REPORT_ONLY,constraint.policy().enforcement());
            assertFalse(constraint.policy().approved());assertFalse(constraints.mayPause(constraint.id()));
        }
        var selected=core.getFirst();
        constraints.configurePolicy(selected.id(),new RuntimeConstraintPolicy(ConstraintOrigin.CORE,RuntimeConstraintPolicy.Severity.HARD,
                RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL,Set.of(CheckpointType.SNAPSHOT,CheckpointType.STREAM_BOUNDARY),
                selected.policy().requiredCapabilities(),true,"Explicit supported coherent-cut inspection policy"));
        assertTrue(constraints.mayPause(selected.id()));
        assertTrue(core.stream().skip(1).noneMatch(c->constraints.mayPause(c.id())));
    }
    @Test void compilerDependenciesSelectChangedFeatureAndUnknownFeatureFallsBack() throws Exception {
        try(var p=projector()) {
            var c=p.coordinator();var owner=p.mutations().objectForSemanticId(ARTIFACT).cls().name();
            c.loadProfileSource("routing.ocl","context "+owner+" inv State: self.status = 'A'\ncontext "+owner+" inv Name: self.name <> ''");
            projectorDelta(p);
            assertEquals(VerificationOutcome.FAIL,c.latest().outcomes().stream().filter(o->o.constraintId().endsWith("::State")).findFirst().orElseThrow().outcome());
            assertEquals(VerificationOutcome.SKIPPED,c.latest().outcomes().stream().filter(o->o.constraintId().endsWith("::Name")).findFirst().orElseThrow().outcome());
            var fallback=c.read(()->c.constraints().evaluate(Map.of("cartago",Completeness.COMPLETE),false,
                    CheckpointType.AFTER_MUTATION,Set.of("unrecognized:feature"),Map.of()));
            assertTrue(fallback.stream().filter(o->o.constraintId().startsWith("EXTERNAL:")).noneMatch(o->o.outcome()==VerificationOutcome.SKIPPED));
        }
    }
    private static void projectorDelta(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector p) {p.apply(delta("changed-state",1,"B"));}
    @Test void explicitBoundaryRechecksAllAndCannotGuessCorrelation() throws Exception {
        try(var p=projector()) {
            var c=p.coordinator();String owner=p.mutations().objectForSemanticId(ARTIFACT).cls().name();
            c.loadProfileSource("boundary.ocl","context "+owner+" inv State: self.status = 'A'");
            var entity=new BridgeEntityId("bridge","runtime","verification-boundary",SESSION,"batch","parent-event");
            p.apply(delta("parent-event",1,"A"));
            p.apply(new RuntimeEvent("boundary",SESSION,1,REVISION,"bridge","bridge:platform",1,Instant.EPOCH,
                    RuntimeEventKind.STREAM_BOUNDARY,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,entity,null,
                    "operation-1","parent-event",Map.of(),Map.of("boundarySource","cartago","boundaryEventId","parent-event","boundarySourceSequence",1),new SourceWatermark("bridge:platform",1),Completeness.COMPLETE,List.of()));
            var cut=c.verificationSnapshot();assertEquals(CheckpointType.STREAM_BOUNDARY,cut.metadata().checkpoint());
            assertEquals(Set.of("operation-1"),cut.metadata().correlations());
            assertEquals(VerificationOutcome.PASS,c.latest().outcomes().stream().filter(o->o.constraintId().startsWith("EXTERNAL:")).findFirst().orElseThrow().outcome());
            assertThrows(RuntimeException.class,()->p.apply(new RuntimeEvent("bad-boundary",SESSION,1,REVISION,"bridge","bridge:platform",2,Instant.EPOCH,
                    RuntimeEventKind.STREAM_BOUNDARY,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,entity,null,"","",Map.of(),Map.of(),
                    new SourceWatermark("bridge:platform",2),Completeness.COMPLETE,List.of())));
        }
    }
    @Test void onlyApprovedHardConstraintCanPauseAndChangedExpressionRevokesApproval() throws Exception {
        try(var p=projector()) {
            var c=p.coordinator();String owner=p.mutations().objectForSemanticId(ARTIFACT).cls().name();
            c.loadProfileSource("policy.ocl","context "+owner+" inv State: self.status = 'A'");
            var id="EXTERNAL:"+owner+"::State";assertFalse(c.read(()->c.constraints().mayPause(id)));
            assertThrows(IllegalArgumentException.class,()->new RuntimeConstraintPolicy(ConstraintOrigin.USER,
                    RuntimeConstraintPolicy.Severity.SOFT,RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL,Set.of(CheckpointType.SNAPSHOT),Set.of(),true,"approval"));
            c.read(()->{c.constraints().configurePolicy(id,new RuntimeConstraintPolicy(ConstraintOrigin.CASE,RuntimeConstraintPolicy.Severity.HARD,
                    RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL,Set.of(CheckpointType.SNAPSHOT),Set.of("missing.capability"),true,"explicit domain approval"));return null;});
            assertTrue(c.read(()->c.constraints().mayPause(id)));c.manualVerify();
            assertTrue(c.latest().outcomes().stream().filter(o->o.constraintId().equals(id)).allMatch(o->o.outcome()==VerificationOutcome.SKIPPED));
            c.read(()->{p.system().model().classInvariants().stream().filter(i->id.endsWith(i.qualifiedName())).findFirst().orElseThrow().setNegated(true);return null;});
            assertFalse(c.read(()->c.constraints().mayPause(id)),"Negation changes the approved condition's meaning");
            c.loadProfileSource("policy.ocl","context "+owner+" inv State: self.status = 'B'");
            assertFalse(c.read(()->c.constraints().mayPause(id)));
        }
    }
}
