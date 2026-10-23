package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.time.Instant;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.NativeObjectBindings;
import org.tzi.use.plugins.jacamo.semantics.auction.AuctionOperationBindingsTest;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.semantic.engine.use75.NativeOclCompilation;
import org.tzi.use.uml.mm.MElementAnnotation;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystem;
import static org.junit.jupiter.api.Assertions.*;

/** Checkpoint component fixture; synthetic admitted events are not the E01 TCP/live producer gate. */
class AuctionOperationCheckpointTest {
    record Fixture(UseSystemApi api,NativeRuntimeMutationEngine engine,NativeOperationCheckpoints checkpoints,BridgeEntityId first,BridgeEntityId second) { }
    private Fixture fixture()throws Exception {
        var binding=AuctionOperationBindingsTest.preparedModel();
        binding.model().addAnnotation(new MElementAnnotation("CanonicalSemanticProgram",Map.of("testFixture","O02_CHECKPOINT_COMPONENT")));
        var start=binding.model().getClass("Auction").operation("start",false);
        NativeOclCompilation.condition(new UseModelApi(binding.model()),start,"O02AtPreFixture","not self.running@pre and self.running and self.task = service",false);
        var system=new MSystem(binding.model());var api=UseSystemApi.create(system,false);var index=new NativeObjectBindings();
        var ids=new ArrayList<BridgeEntityId>();
        for(String name:List.of("a1","a2")) {
            var object=api.createObject("Auction",name);api.setAttributeValue(name,"running","false");api.setAttributeValue(name,"task","'no_task'");
            var id=new BridgeEntityId("cartago","environment","artifact","/main",name,UUID.randomUUID().toString());ids.add(id);
            index.put(id.canonical(),object);index.metadata(object,"uuid",id.incarnation());
            index.metadata(object,"propertySlot:running","accepted-running-property");index.metadata(object,"propertySlot:task","accepted-task-property");
        }
        var engine=new NativeRuntimeMutationEngine(system,index,new CodeGroundedRuntimeRuleRegistry());
        return new Fixture(api,engine,new NativeOperationCheckpoints(engine),ids.getFirst(),ids.getLast());
    }
    private RuntimeEvent event(Fixture f,BridgeEntityId target,String correlation,String phase,long sequence,String performer,List<?> signature,List<String> values,List<String> types) {
        var data=new LinkedHashMap<String,Object>();data.put("artifactSemanticId",target.canonical());data.put("phase",phase);data.put("operationSignature",signature);
        data.put("argumentValues",values);data.put("argumentTypes",types);data.put("performerSemanticId",performer);data.put("performerAuthority","CARTAGO_OPID_AGENT_BODY_ID");
        data.put("invocationBoundary",Map.of("version","1.0","authority","ICARTAGOLOGGER_OBSERVED_PREFIX","sourceId","cartago","artifactUuid",target.incarnation(),"previousWatermark",sequence-1));
        return new RuntimeEvent("op:"+sequence,"O02-fixture",1,"O02-revision","cartago","cartago",sequence,Instant.ofEpochMilli(sequence),phase.equals("started")?RuntimeEventKind.STARTED:RuntimeEventKind.SUCCEEDED,
                RuntimeFactKind.ARTIFACT,ProjectionStatus.EVIDENCE_ONLY,target,null,correlation,"",Map.of(),data,new SourceWatermark("cartago",sequence),Completeness.COMPLETE,List.of());
    }
    private List<SemanticConstraintResult> observe(Fixture f,RuntimeEvent event){f.checkpoints().observe(event,Map.of("cartago",Completeness.COMPLETE),false,Set.of());return f.checkpoints().drainDetailed();}
    private static List<?> signature(){return List.of("auction_env.AuctionArtifact","start",List.of("java.lang.String"),"void");}
    @Test void removedContextReleasesItsPreStateAndReportsUnavailableInsteadOfAnOperationFailure()throws Exception {
        var f=fixture();observe(f,event(f,f.first(),"retired","started",1,"player-A",signature(),List.of("service"),List.of("java.lang.String")));
        assertEquals(1,f.checkpoints().savepoint().active().size());var before=f.checkpoints().savepoint();
        f.api().deleteObject("a1");
        var disposal=new RuntimeEvent("dispose","O02-fixture",1,"O02-revision","cartago","cartago",2,Instant.ofEpochMilli(2),RuntimeEventKind.DISPOSED,RuntimeFactKind.ARTIFACT,
                ProjectionStatus.MATERIALIZED_FAITHFULLY,f.first(),null,"","",Map.of(),Map.of("normalizedEventKind","DELETE_CARTAGO_ARTIFACT","semanticId",f.first().canonical()),new SourceWatermark("cartago",2),Completeness.COMPLETE,List.of());
        var rows=observe(f,disposal);assertEquals(0,f.checkpoints().savepoint().active().size(),"Retirement must release the old pre-state at the admitted boundary");
        assertTrue(rows.stream().anyMatch(r->r.kind().equals("INVOCATION_RETIRED")&&r.verdict()==SemanticVerdict.INCONCLUSIVE));assertTrue(rows.stream().noneMatch(r->r.verdict()==SemanticVerdict.FAIL));
        f.checkpoints().restore(before);assertEquals(1,f.checkpoints().savepoint().active().size(),"The checkpoint participates in the native rollback image");
    }
    @Test void existingCheckpointBindsServiceAndNativeAtPreOnOneSystemWithoutCreditingTaskHistory()throws Exception {
        var f=fixture();String service="flight 'quoted' \\ route";var system=f.engine().system();
        var pre=observe(f,event(f,f.first(),"start-one","started",1,"player-A",signature(),List.of(service),List.of("java.lang.String")));
        assertTrue(pre.stream().anyMatch(r->r.kind().equals("PRE")&&r.verdict()==SemanticVerdict.PASS),pre.toString());
        f.api().setAttributeValue("a1","running","true");f.api().setAttributeValueEx(system.state().objectByName("a1"),system.model().getClass("Auction").attribute("task",true),new StringValue(service));
        var post=observe(f,event(f,f.first(),"start-one","completed",2,"player-A",signature(),List.of(service),List.of("java.lang.String")));
        assertEquals(2,post.stream().filter(r->r.kind().equals("POST")&&r.verdict()==SemanticVerdict.PASS).count(),post.toString());
        assertTrue(post.stream().anyMatch(r->"false".equals(r.witness().get("before.running"))&&"true".equals(r.witness().get("after.running"))));
        assertTrue(post.stream().noneMatch(r->r.kind().equals("TASK_HISTORY")||r.kind().equals("TASK_CONFORMANCE")));
        assertSame(system,f.engine().system());
    }
    @Test void wrongArityTypeOverloadPerformerAndOtherAuctionNeverGainPostSuccess()throws Exception {
        var f=fixture();
        var arity=observe(f,event(f,f.first(),"arity","started",1,"player-A",signature(),List.of(),List.of()));
        assertTrue(arity.stream().anyMatch(r->r.diagnostic().equals("PRE_ARGUMENT_EVIDENCE_UNAVAILABLE")));
        var type=observe(f,event(f,f.first(),"type","started",2,"player-A",signature(),List.of("17"),List.of("java.lang.Integer")));
        assertTrue(type.stream().anyMatch(r->r.diagnostic().equals("PRE_ARGUMENT_TYPE_MISMATCH")));
        var overload=observe(f,event(f,f.first(),"overload","started",3,"player-A",List.of("auction_env.AuctionArtifact","start",List.of("int"),"void"),List.of("17"),List.of("java.lang.Integer")));
        assertTrue(overload.stream().anyMatch(r->r.diagnostic().equals("PRE_EXACT_OPERATION_UNAVAILABLE")));
        observe(f,event(f,f.first(),"actor","started",4,"player-A",signature(),List.of("service"),List.of("java.lang.String")));
        f.api().setAttributeValue("a1","running","true");f.api().setAttributeValue("a1","task","'service'");
        var actor=observe(f,event(f,f.first(),"actor","completed",5,"player-B",signature(),List.of("service"),List.of("java.lang.String")));
        assertTrue(actor.stream().anyMatch(r->r.diagnostic().equals("POST_INVOCATION_DESCRIPTOR_MISMATCH")));assertTrue(actor.stream().noneMatch(r->r.kind().equals("POST")&&r.verdict()==SemanticVerdict.PASS));
        f.api().setAttributeValue("a1","running","false");observe(f,event(f,f.first(),"self","started",6,"player-A",signature(),List.of("service"),List.of("java.lang.String")));
        var self=observe(f,event(f,f.second(),"self","completed",7,"player-A",signature(),List.of("service"),List.of("java.lang.String")));
        assertTrue(self.stream().anyMatch(r->r.diagnostic().equals("POST_MATCHING_PRE_UNAVAILABLE")));assertTrue(self.stream().noneMatch(r->r.kind().equals("POST")&&r.verdict()==SemanticVerdict.PASS));
    }
    @Test void duplicateMissingStaleAndLostPreEvidenceDoNotManufactureFalsePostconditions()throws Exception {
        var f=fixture();var missing=observe(f,event(f,f.first(),"missing","completed",1,"player-A",signature(),List.of("service"),List.of("java.lang.String")));
        assertTrue(missing.stream().anyMatch(r->r.diagnostic().equals("POST_MATCHING_PRE_UNAVAILABLE")&&r.verdict()==SemanticVerdict.INCONCLUSIVE));
        observe(f,event(f,f.first(),"duplicate","started",2,"player-A",signature(),List.of("service"),List.of("java.lang.String")));
        var duplicate=observe(f,event(f,f.first(),"duplicate","started",3,"player-A",signature(),List.of("service"),List.of("java.lang.String")));
        assertTrue(duplicate.stream().anyMatch(r->r.diagnostic().equals("PRE_DUPLICATE_CORRELATION_INVALIDATED")));
        assertTrue(observe(f,event(f,f.first(),"duplicate","completed",4,"player-A",signature(),List.of("service"),List.of("java.lang.String"))).stream().noneMatch(r->r.kind().equals("POST")));
        observe(f,event(f,f.first(),"once","started",5,"player-A",signature(),List.of("service"),List.of("java.lang.String")));
        f.api().setAttributeValue("a1","running","true");f.api().setAttributeValue("a1","task","'service'");
        assertTrue(observe(f,event(f,f.first(),"once","completed",6,"player-A",signature(),List.of("service"),List.of("java.lang.String"))).stream().anyMatch(r->r.kind().equals("POST")&&r.verdict()==SemanticVerdict.PASS));
        var repeated=observe(f,event(f,f.first(),"once","completed",7,"player-A",signature(),List.of("service"),List.of("java.lang.String")));
        assertTrue(repeated.stream().anyMatch(r->r.diagnostic().equals("POST_MATCHING_PRE_UNAVAILABLE")));assertTrue(repeated.stream().noneMatch(r->r.verdict()==SemanticVerdict.FAIL));
        observe(f,event(f,f.first(),"stale","started",9,"player-A",signature(),List.of("service"),List.of("java.lang.String")));
        var stale=observe(f,event(f,f.first(),"stale","completed",8,"player-A",signature(),List.of("service"),List.of("java.lang.String")));
        assertTrue(stale.stream().anyMatch(r->r.diagnostic().equals("POST_SOURCE_BOUNDARY_NOT_AFTER_PRE")&&r.verdict()==SemanticVerdict.ERROR));
        f.api().setAttributeValue("a1","running","false");
        f.checkpoints().observe(event(f,f.first(),"gap","started",10,"player-A",signature(),List.of("service"),List.of("java.lang.String")),Map.of("cartago",Completeness.PARTIAL),true,Set.of());
        var pre=f.checkpoints().drainDetailed();assertTrue(pre.stream().allMatch(r->r.verdict()==SemanticVerdict.SKIPPED));
        f.api().setAttributeValue("a1","running","true");
        var post=observe(f,event(f,f.first(),"gap","completed",11,"player-A",signature(),List.of("service"),List.of("java.lang.String")));
        assertTrue(post.stream().anyMatch(r->r.id().endsWith("::O02AtPreFixture")&&r.verdict()==SemanticVerdict.SKIPPED&&r.diagnostic().startsWith("PRE_STATE_")),post.toString());
        assertTrue(post.stream().noneMatch(r->r.verdict()==SemanticVerdict.FAIL),"A lost PRE cannot be Booleanized to a violation");
    }
}
