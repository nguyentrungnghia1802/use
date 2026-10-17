package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.NativeObjectBindings;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.CslCounterArtifact;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.*;
import org.tzi.use.api.UseSystemApi;

class NativeInvocationSemanticsTest {
    private void observe(NativeOperationCheckpoints monitor,RuntimeEvent event) {
        monitor.observe(event,Map.of("cartago",Completeness.COMPLETE),false,Set.of());
    }
    record Fixture(NativeRuntimeMutationEngine engine,UseSystemApi api,BridgeEntityId artifact){}
    Fixture fixture()throws Exception {
        var path=Path.of("src/test/resources/semantics/profile-v1/monitor.jcm").toAbsolutePath();var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(path),path);
        var type=new CslSchema.Classifier("domain",DomainProjection.artifactId(CslCounterArtifact.class.getName()),"Counter",CslSchema.Kind.ENTITY,"",false,List.of(new CslSchema.Attribute("p","p","Integer","p",false)));
        var contracts=List.of(new CslSchema.OperationContract("increment","domain",List.of(CslCounterArtifact.class.getName(),"increment",List.of(),"void"),"self.p >= 0","self.p = self.p@pre + 1"),new CslSchema.OperationContract("set","domain",List.of(CslCounterArtifact.class.getName(),"set",List.of("int"),"void"),"true","true"));
        var config=new OrganizationalMappingRules().derive(source,List.of(type),List.of(),List.of(),contracts,new RequirementModel(List.of()));var pipeline=new CodeGroundedNativePipeline().build(source,NativeProjectionMode.AUTO,config);var system=pipeline.state().system();var api=UseSystemApi.create(system,false);var object=api.createObject("Counter","counter");api.setAttributeValue("counter","p","0");var id=new BridgeEntityId("cartago","environment","artifact","/main","counter",UUID.randomUUID().toString());var index=new NativeObjectBindings();index.put("artifact",object);index.metadata(object,"uuid",id.incarnation());index.metadata(object,"propertySlot:p","actual-property");return new Fixture(new NativeRuntimeMutationEngine(system,index,new CodeGroundedRuntimeRuleRegistry()),api,id);
    }
    RuntimeEvent event(Fixture f,String correlation,String phase,long sequence,long millis) {
        var values=new HashMap<String,Object>();values.put("artifactSemanticId","artifact");values.put("phase",phase);values.put("opId",correlation);values.put("operationSignature",List.of(CslCounterArtifact.class.getName(),"increment",List.of(),"void"));values.put("argumentValues",List.of());values.put("argumentTypes",List.of());values.put("invocationBoundary",Map.of("version","1.0","authority","ICARTAGOLOGGER_OBSERVED_PREFIX","sourceId","cartago","artifactUuid",f.artifact().incarnation(),"previousWatermark",sequence-1));
        RuntimeEventKind kind=switch(phase){case "started"->RuntimeEventKind.STARTED;case "completed"->RuntimeEventKind.SUCCEEDED;case "failed"->RuntimeEventKind.FAILED;default->RuntimeEventKind.CHANGED;};return new RuntimeEvent("event:"+sequence,"run",1,"revision","cartago","cartago",sequence,java.time.Instant.ofEpochMilli(millis),kind,RuntimeFactKind.ARTIFACT,ProjectionStatus.EVIDENCE_ONLY,f.artifact(),null,correlation,"",Map.of(),values,new SourceWatermark("cartago",sequence),Completeness.COMPLETE,List.of());
    }
    @Test void overlappingInvocationsKeepTheirOwnPreStateAndSuspendResumeNeverOverwriteIt()throws Exception {
        var f=fixture();var monitor=new NativeOperationCheckpoints(f.engine());observe(monitor,event(f,"A","started",1,0));f.api().setAttributeValue("counter","p","1");observe(monitor,event(f,"B","started",2,1));f.api().setAttributeValue("counter","p","2");observe(monitor,event(f,"A","suspended",3,2));assertTrue(monitor.drainDetailed().stream().anyMatch(r->r.diagnostic().equals("SUSPENDED_PRE_STATE_RETAINED")));observe(monitor,event(f,"A","resumed",4,3));
        observe(monitor,event(f,"B","completed",5,4));var b=monitor.drainDetailed().stream().filter(r->r.id().equals("increment:post")).findFirst().orElseThrow();assertEquals(SemanticVerdict.PASS,b.verdict());assertEquals("1",b.witness().get("before.p"));assertEquals("2",b.witness().get("after.p"));assertTrue(b.witness().get("interference").contains("A"));
        observe(monitor,event(f,"A","completed",6,5));var a=monitor.drainDetailed().stream().filter(r->r.id().equals("increment:post")).findFirst().orElseThrow();assertEquals(SemanticVerdict.FAIL,a.verdict());assertEquals("0",a.witness().get("before.p"));assertTrue(a.witness().get("phases").contains("suspended"));assertTrue(a.expression().contains("@pre"));
    }
    @Test void sourceFailureDuplicateCapacityTimeoutAndMissingBoundaryNeverProducePostPass()throws Exception {
        var f=fixture();var monitor=new NativeOperationCheckpoints(f.engine(),new InvocationCoveragePolicy(1,java.time.Duration.ofMillis(10)));observe(monitor,event(f,"A","started",1,0));observe(monitor,event(f,"B","started",2,1));assertTrue(monitor.drainDetailed().stream().anyMatch(r->r.verdict()==SemanticVerdict.INCONCLUSIVE && r.diagnostic().contains("CAPACITY")));
        observe(monitor,event(f,"A","failed",3,2));var failed=monitor.drainDetailed();assertFalse(failed.stream().anyMatch(r->r.kind().equals("POST") && r.verdict()==SemanticVerdict.PASS));assertTrue(failed.stream().anyMatch(r->r.kind().equals("TASK_CONFORMANCE") && r.verdict()==SemanticVerdict.FAIL));
        observe(monitor,event(f,"C","started",4,3));observe(monitor,event(f,"C","started",5,4));assertTrue(monitor.drainDetailed().stream().anyMatch(r->r.diagnostic().contains("DUPLICATE")));observe(monitor,event(f,"C","completed",6,5));assertTrue(monitor.drainDetailed().stream().anyMatch(r->r.diagnostic().equals("POST_MATCHING_PRE_UNAVAILABLE")));
        observe(monitor,event(f,"D","started",7,6));observe(monitor,event(f,"D","completed",8,30));assertTrue(monitor.drainDetailed().stream().anyMatch(r->r.kind().equals("INVOCATION_TIMEOUT") && r.verdict()==SemanticVerdict.INCONCLUSIVE));
        var tagged=event(f,"E","started",9,31);var raw=new HashMap<>(tagged.after());raw.remove("invocationBoundary");var unconfirmed=new RuntimeEvent(tagged.eventId(),tagged.sessionId(),tagged.generation(),tagged.modelRevision(),tagged.subsystem(),tagged.sourceId(),tagged.sourceSequence(),tagged.observedAt(),tagged.kind(),tagged.factKind(),tagged.projectionStatus(),tagged.entityId(),null,tagged.correlationId(),"",Map.of(),raw,tagged.watermark(),tagged.completeness(),List.of());observe(monitor,unconfirmed);assertTrue(monitor.drainDetailed().stream().anyMatch(r->r.verdict()==SemanticVerdict.INCONCLUSIVE));
    }
    @Test void wrongIncarnationSignatureAndCompletionArgumentsCannotBorrowAMatchingPre()throws Exception {
        for(String mismatch:List.of("incarnation","signature","arguments")) {
            var f=fixture();var monitor=new NativeOperationCheckpoints(f.engine());observe(monitor,event(f,"A","started",1,0));monitor.drainDetailed();f.api().setAttributeValue("counter","p","1");var base=event(f,"A","completed",2,1);var values=new HashMap<>(base.after());var id=base.entityId();
            if(mismatch.equals("incarnation")){id=new BridgeEntityId(id.authority(),id.dimension(),id.kind(),id.scope(),id.localId(),"different-uuid");var proof=new HashMap<>(CanonicalJson.object(values.get("invocationBoundary")));proof.put("artifactUuid",id.incarnation());values.put("invocationBoundary",proof);}else if(mismatch.equals("signature"))values.put("operationSignature",List.of(CslCounterArtifact.class.getName(),"differentOperation",List.of(),"void"));else{values.put("argumentValues",List.of("1"));values.put("argumentTypes",List.of("java.lang.Integer"));}
            var wrong=new RuntimeEvent(base.eventId(),base.sessionId(),base.generation(),base.modelRevision(),base.subsystem(),base.sourceId(),base.sourceSequence(),base.observedAt(),base.kind(),base.factKind(),base.projectionStatus(),id,null,base.correlationId(),"",Map.of(),values,base.watermark(),base.completeness(),List.of());observe(monitor,wrong);var result=monitor.drainDetailed();assertFalse(result.stream().anyMatch(r->r.kind().equals("POST") && r.verdict()==SemanticVerdict.PASS),mismatch);assertTrue(result.stream().anyMatch(r->r.verdict()==SemanticVerdict.INCONCLUSIVE && r.diagnostic().equals(mismatch.equals("incarnation")?"POST_MATCHING_PRE_UNAVAILABLE":"POST_INVOCATION_DESCRIPTOR_MISMATCH")),result.toString());
        }
    }
    @Test void unsupportedArgumentTypeIsNotAnEvaluatorError()throws Exception {
        var f=fixture();var monitor=new NativeOperationCheckpoints(f.engine());var tagged=event(f,"arg","started",1,0);var values=new HashMap<>(tagged.after());values.put("operationSignature",List.of(CslCounterArtifact.class.getName(),"set",List.of("int"),"void"));values.put("argumentValues",List.of("1"));values.put("argumentTypes",List.of("java.lang.Long"));var event=new RuntimeEvent(tagged.eventId(),tagged.sessionId(),tagged.generation(),tagged.modelRevision(),tagged.subsystem(),tagged.sourceId(),tagged.sourceSequence(),tagged.observedAt(),tagged.kind(),tagged.factKind(),tagged.projectionStatus(),tagged.entityId(),null,tagged.correlationId(),"",Map.of(),values,tagged.watermark(),tagged.completeness(),List.of());observe(monitor,event);assertTrue(monitor.drainDetailed().stream().anyMatch(r->r.verdict()==SemanticVerdict.UNSUPPORTED && r.diagnostic().contains("ARGUMENT_TYPE")));
    }
}
