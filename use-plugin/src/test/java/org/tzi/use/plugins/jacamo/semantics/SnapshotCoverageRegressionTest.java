package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.profile.*;

class SnapshotCoverageRegressionTest {
    private VerificationLoadScenarioTest.Fixture fixture() throws Exception {
        return VerificationLoadScenarioTest.fixture(new VerificationLoadScenarioTest.Workload(1,1,1,1,1,1,0,1));
    }
    @Test void staleSnapshotCannotRewindAnAcceptedViolationOrItsWatermark() throws Exception {
        var f=fixture(); var built=new CodeGroundedNativePipeline().build(f.source(),NativeProjectionMode.AUTO,f.config());
        try(var p=new NativeRuntimeProjector(built,"benchmark",1,f.source().modelRevision())) {
            var snapshot=VerificationLoadScenarioTest.snapshot(f); p.applySnapshot(snapshot);
            var object=p.mutations().objectForSemanticId(f.artifacts().getFirst().canonical());
            p.apply(VerificationLoadScenarioTest.delta(f,"cartago",1,0,-1));
            assertThrows(RuntimeException.class,()->p.applySnapshot(snapshot));
            assertSame(object,p.mutations().objectForSemanticId(f.artifacts().getFirst().canonical()));
            assertEquals("-1",object.state(p.system().state()).attributeValue("p0").toString());
            assertEquals(1L,p.sourceWatermarks().get("cartago")); assertTrue(p.resyncRequired());
            assertNotEquals("CURRENT_OBSERVED",p.coordinator().latest().freshness());
            assertTrue(p.coordinator().latest().semantic().constraints().stream().noneMatch(r->r.verdict()==SemanticVerdict.PASS));
        }
    }
    @Test void rejectedEpochAndStaleCutAreReproducedByReplayWithoutRewindingState()throws Exception {
        var f=fixture();var built=new CodeGroundedNativePipeline().build(f.source(),NativeProjectionMode.AUTO,f.config());
        try(var p=new NativeRuntimeProjector(built,"benchmark",1,f.source().modelRevision())) {
            var s=VerificationLoadScenarioTest.snapshot(f);p.applySnapshot(s);p.apply(VerificationLoadScenarioTest.delta(f,"cartago",1,0,-1));
            assertThrows(NativeRuntimeProtocolException.class,()->p.applySnapshot(s));
            assertThrows(NativeRuntimeProtocolException.class,()->p.applySnapshot(s,"benchmark",0));
            var out=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"),"rejected-snapshot-replay-");new NativeRuntimeReplay().exportBundle(p,built.export().useText(),out);var replay=new NativeRuntimeReplay().replay(out);assertEquals(p.coordinator().latest().stateHash(),replay.finalStateHash());assertEquals(p.coordinator().latest().resultHash(),replay.finalResultHash());
            p.applySnapshot(s,"benchmark",2);assertEquals(0L,p.sourceWatermarks().get("cartago"));assertFalse(p.resyncRequired());
        }
    }
    @Test void everyPopulationAuthorityAndAllOrganizationScopesMustBeComplete()throws Exception {
        var f=fixture();var built=new CodeGroundedNativePipeline().build(f.source(),NativeProjectionMode.AUTO,f.config());
        try(var p=new NativeRuntimeProjector(built,"benchmark",1,f.source().modelRevision())) {
            var complete=Map.of("cartago",Completeness.COMPLETE,"jason",Completeness.COMPLETE,"moise:org",Completeness.COMPLETE,"moise:dynamic",Completeness.COMPLETE,"npl",Completeness.COMPLETE);
            var resolver=new org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver();
            for(var pair:List.of(List.of("AtomicAgent","jason"),List.of("AtomicAgent","cartago"),List.of("RuntimePlayer","jason"),List.of("RuntimePlayer","moise:dynamic"),List.of("WorkspaceObservation","cartago"),List.of("RoleOccurrence","moise:dynamic"),List.of("OrgContext","moise:dynamic"),List.of("DomainEntity","cartago"),List.of("MentalStateObservation","jason"),List.of("FunctionalSchemeObservation","moise:dynamic"),List.of("NormOccurrence","npl"))) {
                String type=pair.getFirst();if(type.equals("NormOccurrence"))type=org.tzi.use.plugins.jacamo.semantics.normative.NormativeUseBinding.TYPE;
                var expr=org.tzi.use.plugins.jacamo.semantics.ocl.SemanticOclCompiler.predicate(p.system().model(),p.system().model().getClass("LoadEntity"),"population",type+".allInstances()->isEmpty()");
                assertEquals("",resolver.unavailable(expr,p.mutations(),complete,false),type);
                var partial=new HashMap<>(complete);partial.put(pair.get(1),Completeness.PARTIAL);assertFalse(resolver.unavailable(expr,p.mutations(),partial,false).isBlank(),type);
            }
        }
    }
    @Test void newUnprojectedAgentInvalidatesPopulationProofUntilACompleteResync()throws Exception {
        var f=fixture();var n=f.config().requirements().nodes().getFirst();var node=new RequirementModel.Node(n.id(),n.id(),n.kind(),RequirementModel.Temporal.NONE,n.binding(),"AtomicAgent.allInstances()->size() = 1","true",RequirementModel.Refinement.NONE,List.of(),"","");
        var cfg=new CslMonitorConfiguration(f.config().version(),f.config().profile(),f.config().schema(),new RequirementModel(List.of(node)),f.config().decisions());var built=new CodeGroundedNativePipeline().build(f.source(),NativeProjectionMode.AUTO,cfg);
        try(var p=new NativeRuntimeProjector(built,"benchmark",1,f.source().modelRevision())) {
            var s=VerificationLoadScenarioTest.snapshot(f);var wm=new HashMap<>(s.endWatermarks());wm.put("jason",new SourceWatermark("jason",0));var complete=new HashMap<>(s.sourceCompleteness());complete.put("jason",Completeness.COMPLETE);
            p.applySnapshot(new RuntimeSnapshot("initial-complete",s.modelRevision(),s.captureStartedAt(),s.captureEndedAt(),wm,wm,1,s.facts(),complete,"initial-complete"));assertEquals(IntentionalMarking.Status.SATISFIED,p.coordinator().latest().semantic().requirements().get(n.id()).evaluated());
            var id=new BridgeEntityId("jason","agent","runtime-agent","project","newAgent","new-incarnation");
            p.apply(new RuntimeEvent("new-agent","benchmark",1,s.modelRevision(),"jason","jason",1,java.time.Instant.EPOCH,RuntimeEventKind.CREATED,RuntimeFactKind.AGENT,ProjectionStatus.EVIDENCE_ONLY,id,null,"","",Map.of(),Map.of("agent","newAgent"),new SourceWatermark("jason",1),Completeness.PARTIAL,List.of()));
            assertNull(p.mutations().objectForSemanticId(id.canonical()));assertEquals(IntentionalMarking.Status.UNDETERMINED,p.coordinator().lastObservation().semantic().requirements().get(n.id()).evaluated());assertEquals("PARTIAL",p.coordinator().lastObservation().semantic().coverage().get("jason"));
            p.coordinator().manualVerify();assertEquals(IntentionalMarking.Status.UNDETERMINED,p.coordinator().latest().semantic().requirements().get(n.id()).evaluated());
            wm.put("jason",new SourceWatermark("jason",1));p.applySnapshot(new RuntimeSnapshot("complete-new-cut",s.modelRevision(),s.captureStartedAt(),s.captureEndedAt(),wm,wm,1,s.facts(),complete,"complete-new-cut"));assertEquals(IntentionalMarking.Status.SATISFIED,p.coordinator().latest().semantic().requirements().get(n.id()).evaluated());
        }
    }
    @Test void partialAgentPopulationCannotProveAnIntentionalAllInstancesEquality() throws Exception {
        var f=fixture(); var n=f.config().requirements().nodes().getFirst();
        var node=new RequirementModel.Node(n.id(),n.id(),n.kind(),RequirementModel.Temporal.NONE,n.binding(),"AtomicAgent.allInstances()->size() = 1","true",RequirementModel.Refinement.NONE,List.of(),"","");
        var cfg=new CslMonitorConfiguration(f.config().version(),f.config().profile(),f.config().schema(),new RequirementModel(List.of(node)),f.config().decisions());
        var built=new CodeGroundedNativePipeline().build(f.source(),NativeProjectionMode.AUTO,cfg);
        try(var p=new NativeRuntimeProjector(built,"benchmark",1,f.source().modelRevision())) {
            var s=VerificationLoadScenarioTest.snapshot(f); var coverage=new HashMap<>(s.sourceCompleteness()); coverage.put("jason",Completeness.PARTIAL);
            p.applySnapshot(new RuntimeSnapshot("partial-agent",s.modelRevision(),s.captureStartedAt(),s.captureEndedAt(),s.startWatermarks(),s.endWatermarks(),1,s.facts(),coverage,"partial"));
            var row=p.coordinator().latest().semantic().constraints().stream().filter(r->r.id().equals("REQUIREMENT:"+n.id())).findFirst().orElseThrow();
            assertEquals(SemanticVerdict.SKIPPED,row.verdict());
            assertEquals(IntentionalMarking.Status.UNDETERMINED,p.coordinator().latest().semantic().requirements().get(n.id()).evaluated());
        }
    }
}
