package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.profile.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;

class TaskPerformerRegressionTest {
    static List<Object> signature(){return List.of(TaskCounterArtifact.class.getName(),"increment",List.of(),"void");}
    VerificationLoadScenarioTest.Fixture fixture(String pre,String post) throws Exception {
        var f=VerificationLoadScenarioTest.fixture(new VerificationLoadScenarioTest.Workload(2,1,1,2,2,1,0,1),TaskCounterArtifact.class);
        var s=f.config().schema();var nodes=f.config().requirements().nodes().stream().map(n->new RequirementModel.Node(n.id(),n.requirementId(),RequirementModel.Kind.TASK,RequirementModel.Temporal.NONE,n.binding(),"","true",RequirementModel.Refinement.NONE,List.of(),"","increment")).toList();
        var schema=new CslSchema(s.id(),s.classifiers(),s.associations(),s.invariants(),List.of(new CslSchema.OperationContract("increment","domain",signature(),pre,post)),s.policy());
        var cfg=new CslMonitorConfiguration(f.config().version(),f.config().profile(),schema,new RequirementModel(nodes),f.config().decisions());
        return new VerificationLoadScenarioTest.Fixture(f.source(),cfg,f.facts(),f.artifacts());
    }
    NativeRuntimeProjector projector(VerificationLoadScenarioTest.Fixture f) {
        return new NativeRuntimeProjector(new CodeGroundedNativePipeline().build(f.source(),NativeProjectionMode.AUTO,f.config()),"benchmark",1,f.source().modelRevision());
    }
    RuntimeEvent operation(VerificationLoadScenarioTest.Fixture f,long sequence,String phase,String performer) {
        var id=f.artifacts().getFirst();var values=new HashMap<String,Object>();values.put("artifactSemanticId",id.canonical());values.put("operationSignature",signature());values.put("argumentValues",List.of());values.put("argumentTypes",List.of());values.put("phase",phase);values.put("opId","invocation");
        if(performer!=null){values.put("performerSemanticId",performer);values.put("performerAuthority","CARTAGO_OPID_AGENT_BODY_ID");}
        values.put("invocationBoundary",Map.of("version","1.0","authority","ICARTAGOLOGGER_OBSERVED_PREFIX","sourceId","cartago","artifactUuid",id.incarnation(),"previousWatermark",sequence-1));
        return new RuntimeEvent("operation:"+sequence,"benchmark",1,f.source().modelRevision(),"cartago","cartago",sequence,Instant.EPOCH,phase.equals("started")?RuntimeEventKind.STARTED:RuntimeEventKind.SUCCEEDED,RuntimeFactKind.ARTIFACT,ProjectionStatus.EVIDENCE_ONLY,id,null,"invocation","",Map.of(),values,new SourceWatermark("cartago",sequence),Completeness.COMPLETE,List.of());
    }
    SemanticConstraintResult row(NativeRuntimeProjector p,String id){return p.coordinator().latest().semantic().constraints().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();}
    @Test void operationOfAOnlyCreditsTheTaskBoundToAAndReplayRetainsAttribution()throws Exception {
        var f=fixture("true","self.p0 = self.p0@pre + 1");var built=new CodeGroundedNativePipeline().build(f.source(),NativeProjectionMode.AUTO,f.config());
        try(var p=new NativeRuntimeProjector(built,"benchmark",1,f.source().modelRevision())) {
            p.applySnapshot(VerificationLoadScenarioTest.snapshot(f));String a=VerificationLoadScenarioTest.actor(0).canonical();
            p.apply(operation(f,1,"started",a));p.apply(VerificationLoadScenarioTest.delta(f,"cartago",2,0,1));p.apply(operation(f,3,"completed",a));
            assertEquals(SemanticVerdict.PASS,row(p,"increment:post").verdict());
            assertEquals(IntentionalMarking.Status.SATISFIED,p.coordinator().latest().semantic().requirements().get("goal0").evaluated());
            assertEquals(IntentionalMarking.Status.UNDETERMINED,p.coordinator().latest().semantic().requirements().get("goal1").evaluated());
            assertEquals("TASK_PERFORMER_OR_PRE_BINDING_MISMATCH",row(p,"REQUIREMENT:goal1").diagnostic());
            var out=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"),"task-attribution-replay-");new NativeRuntimeReplay().exportBundle(p,built.export().useText(),out);var replay=new NativeRuntimeReplay().replay(out);assertTrue(replay.complete(),replay.diagnostics().toString());assertEquals(p.coordinator().latest().resultHash(),replay.finalResultHash());
        }
    }
    @Test void legacyInvocationWithoutPerformerKeepsOclResultButCannotProveEitherActorTask()throws Exception {
        var f=fixture("true","self.p0 = self.p0@pre + 1");try(var p=projector(f)) {
            p.applySnapshot(VerificationLoadScenarioTest.snapshot(f));p.apply(operation(f,1,"started",null));p.apply(VerificationLoadScenarioTest.delta(f,"cartago",2,0,1));p.apply(operation(f,3,"completed",null));
            assertEquals(SemanticVerdict.PASS,row(p,"increment:post").verdict());assertEquals(SemanticVerdict.UNSUPPORTED,row(p,"REQUIREMENT:goal0").verdict());assertEquals(SemanticVerdict.UNSUPPORTED,row(p,"REQUIREMENT:goal1").verdict());
        }
    }
    @Test void completionCannotReplaceThePerformerPinnedAtPre()throws Exception {
        var f=fixture("true","true");try(var p=projector(f)) {
            p.applySnapshot(VerificationLoadScenarioTest.snapshot(f));p.apply(operation(f,1,"started",VerificationLoadScenarioTest.actor(0).canonical()));p.apply(operation(f,2,"completed",VerificationLoadScenarioTest.actor(1).canonical()));
            assertTrue(p.coordinator().latest().semantic().constraints().stream().anyMatch(r->r.diagnostic().equals("POST_INVOCATION_DESCRIPTOR_MISMATCH")));assertTrue(p.coordinator().latest().semantic().requirements().values().stream().noneMatch(r->r.evaluated()==IntentionalMarking.Status.SATISFIED));
        }
    }
    @Test void readoptionCannotCreditAnInvocationStartedInTheOldRoleOccurrence()throws Exception {
        var f=fixture("true","true");try(var p=projector(f)) {
            p.applySnapshot(VerificationLoadScenarioTest.snapshot(f));String a=VerificationLoadScenarioTest.actor(0).canonical();p.apply(operation(f,1,"started",a));
            var group=f.facts().stream().filter(x->x.kind()==RuntimeFactKind.GROUP_BOARD).findFirst().orElseThrow();
            for(int i=1;i<=2;i++){var data=new HashMap<>(group.values());if(i==1)data.put("players",List.of());p.apply(new RuntimeEvent("role:"+i,"benchmark",1,f.source().modelRevision(),"moise","moise:org",i,Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.GROUP_BOARD,ProjectionStatus.MATERIALIZED_FAITHFULLY,group.id(),null,"","",Map.of(),data,new SourceWatermark("moise:org",i),Completeness.COMPLETE,List.of()));}
            p.apply(operation(f,2,"completed",a));assertEquals(SemanticVerdict.SKIPPED,row(p,"REQUIREMENT:goal0").verdict());
        }
    }
    @Test void incompletePreContextCannotBeRepairedByAFreshContextOnlyAtCompletion()throws Exception {
        var f=fixture("true","true");try(var p=projector(f)) {
            var s=VerificationLoadScenarioTest.snapshot(f);p.applySnapshot(s);
            p.applySnapshot(new RuntimeSnapshot("partial-context",s.modelRevision(),s.captureStartedAt(),s.captureEndedAt(),s.startWatermarks(),s.endWatermarks(),1,s.facts().stream().filter(x->x.kind()!=RuntimeFactKind.GROUP_BOARD).toList(),s.sourceCompleteness(),"partial-context"));
            String a=VerificationLoadScenarioTest.actor(0).canonical();p.apply(operation(f,1,"started",a));
            var group=f.facts().stream().filter(x->x.kind()==RuntimeFactKind.GROUP_BOARD).findFirst().orElseThrow();p.apply(new RuntimeEvent("fresh-group","benchmark",1,f.source().modelRevision(),"moise","moise:org",1,Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.GROUP_BOARD,ProjectionStatus.MATERIALIZED_FAITHFULLY,group.id(),null,"","",Map.of(),group.values(),new SourceWatermark("moise:org",1),Completeness.COMPLETE,List.of()));
            p.apply(operation(f,2,"completed",a));assertEquals(SemanticVerdict.SKIPPED,row(p,"REQUIREMENT:goal0").verdict());
        }
    }
    @Test void incompletePopulationInOperationConditionsNeverBecomesPassingTaskEvidence()throws Exception {
        for(boolean pre:List.of(true,false)){var f=fixture(pre?"AtomicAgent.allInstances()->size() = 2":"true",pre?"true":"AtomicAgent.allInstances()->size() = 2");try(var p=projector(f)) {
            p.applySnapshot(VerificationLoadScenarioTest.snapshot(f));String a=VerificationLoadScenarioTest.actor(0).canonical();p.apply(operation(f,1,"started",a));p.apply(operation(f,2,"completed",a));
            if(!pre)assertEquals(SemanticVerdict.SKIPPED,row(p,"increment:post").verdict());assertEquals(SemanticVerdict.SKIPPED,row(p,"REQUIREMENT:goal0").verdict());
        }}
    }
}
