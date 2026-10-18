package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.time.Instant;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.adapter.*;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.NativeObjectBindings;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalOperationCheckpointsTest {
    record Fixture(NativeRuntimeMutationEngine engine,UseSystemApi api,BridgeEntityId artifact,NativeOperationCheckpoints checkpoints){}
    Fixture fixture() throws Exception {
        var source=CanonicalGoalMappingTest.source();var program=new JaCaMoStaticMappingAdapter().map(source,JaCaMoMappingProfile.observedV1(),CanonicalGoalMappingTest.rules(source));
        return fixture(source,program);
    }
    Fixture fixture(ModelSnapshot source,SemanticProgram program) throws Exception {
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);var system=built.nativeResult().state().system();
        var api=UseSystemApi.create(system,false);var counter=api.createObject("Counter","counter");api.setAttributeValue("counter","p","0");
        var artifact=new BridgeEntityId("cartago","environment","artifact","/main","counter",UUID.randomUUID().toString());var bindings=new NativeObjectBindings();bindings.put(artifact.canonical(),counter);bindings.metadata(counter,"uuid",artifact.incarnation());bindings.metadata(counter,"propertySlot:p","accepted-property-p");
        var engine=new NativeRuntimeMutationEngine(system,bindings,new CodeGroundedRuntimeRuleRegistry());return new Fixture(engine,api,artifact,new NativeOperationCheckpoints(engine));
    }
    RuntimeEvent event(Fixture fixture,String correlation,String phase,long sequence) {
        var kind=phase.equals("started")?RuntimeEventKind.STARTED:phase.equals("completed")?RuntimeEventKind.SUCCEEDED:RuntimeEventKind.FAILED;
        var values=Map.<String,Object>of("artifactSemanticId",fixture.artifact().canonical(),"phase",phase,"operationSignature",List.of(CslCounterArtifact.class.getName(),"increment",List.of(),"void"),"argumentValues",List.of(),"argumentTypes",List.of(),
                "invocationBoundary",Map.of("version","1.0","authority","ICARTAGOLOGGER_OBSERVED_PREFIX","sourceId","cartago","artifactUuid",fixture.artifact().incarnation(),"previousWatermark",sequence-1));
        return new RuntimeEvent("event:"+sequence,"run",1,"revision","cartago","cartago",sequence,Instant.ofEpochMilli(sequence),kind,RuntimeFactKind.ARTIFACT,ProjectionStatus.EVIDENCE_ONLY,fixture.artifact(),null,correlation,"",Map.of(),values,new SourceWatermark("cartago",sequence),Completeness.COMPLETE,List.of());
    }
    List<SemanticConstraintResult> observe(Fixture f,RuntimeEvent event){f.checkpoints().observe(event,Map.of("cartago",Completeness.COMPLETE),false,Set.of());return f.checkpoints().drainDetailed();}
    @Test void reusedCheckpointCapturesActualPreAndSeparatesInvocationConformanceFromTaskHistory() throws Exception {
        var f=fixture();assertTrue(f.engine().canonicalProfile());assertFalse(f.engine().cslProfile());var system=f.engine().system();
        assertTrue(observe(f,event(f,"one","started",1)).stream().anyMatch(r->r.id().equals("increment:pre")&&r.verdict()==SemanticVerdict.PASS));
        f.api().setAttributeValue("counter","p","1");var pass=observe(f,event(f,"one","completed",2));var post=pass.stream().filter(r->r.id().equals("increment:post")).findFirst().orElseThrow();
        assertEquals(SemanticVerdict.PASS,post.verdict());assertEquals("0",post.witness().get("before.p"));assertEquals("1",post.witness().get("after.p"));
        assertTrue(pass.stream().anyMatch(r->r.kind().equals("INVOCATION_CONFORMANCE")&&r.witness().get("historySemantics").equals("ORIGINAL_TASK_MARKING_IS_SEPARATE")));
        observe(f,event(f,"two","started",3));f.api().setAttributeValue("counter","p","3");var fail=observe(f,event(f,"two","completed",4));assertTrue(fail.stream().anyMatch(r->r.id().equals("increment:post")&&r.verdict()==SemanticVerdict.FAIL));
        assertSame(system,f.engine().system());assertTrue(fail.stream().noneMatch(r->r.kind().equals("TASK_HISTORY")));
    }
    @Test void missingPreWrongIncarnationAndOverlapPreserveExactInterval() throws Exception {
        var f=fixture();assertTrue(observe(f,event(f,"missing","completed",1)).stream().anyMatch(r->r.diagnostic().equals("POST_MATCHING_PRE_UNAVAILABLE")));
        observe(f,event(f,"A","started",2));f.api().setAttributeValue("counter","p","1");observe(f,event(f,"B","started",3));f.api().setAttributeValue("counter","p","2");
        var b=observe(f,event(f,"B","completed",4)).stream().filter(r->r.id().equals("increment:post")).findFirst().orElseThrow();assertEquals(SemanticVerdict.PASS,b.verdict());assertEquals("1",b.witness().get("before.p"));assertTrue(b.witness().get("interference").contains("A"));
        var a=observe(f,event(f,"A","completed",5)).stream().filter(r->r.id().equals("increment:post")).findFirst().orElseThrow();assertEquals(SemanticVerdict.FAIL,a.verdict());assertEquals("0",a.witness().get("before.p"));
        observe(f,event(f,"C","started",6));var old=event(f,"C","completed",7);var wrong=new BridgeEntityId(old.entityId().authority(),old.entityId().dimension(),old.entityId().kind(),old.entityId().scope(),old.entityId().localId(),UUID.randomUUID().toString());
        var event=new RuntimeEvent(old.eventId(),old.sessionId(),old.generation(),old.modelRevision(),old.subsystem(),old.sourceId(),old.sourceSequence(),old.observedAt(),old.kind(),old.factKind(),old.projectionStatus(),wrong,null,old.correlationId(),"",old.before(),old.after(),old.watermark(),old.completeness(),old.evidence());
        assertTrue(observe(f,event).stream().noneMatch(r->r.kind().equals("POST")&&r.verdict()==SemanticVerdict.PASS));
    }
    @Test void requiredCanonicalPropertyCoverageBlocksUndefinedEqualityPass() throws Exception {
        var f=fixture();var counter=f.engine().objectForSemanticId(f.artifact().canonical());var bindings=new NativeObjectBindings();bindings.put(f.artifact().canonical(),counter);bindings.metadata(counter,"uuid",f.artifact().incarnation());
        var engine=new NativeRuntimeMutationEngine(f.engine().system(),bindings,new CodeGroundedRuntimeRuleRegistry());var checkpoints=new NativeOperationCheckpoints(engine);var empty=new Fixture(engine,f.api(),f.artifact(),checkpoints);
        var pre=observe(empty,event(empty,"unknown","started",1));assertTrue(pre.stream().anyMatch(r->r.verdict()==SemanticVerdict.SKIPPED&&r.diagnostic().startsWith("REQUIRED_PROPERTY_OBSERVATION_UNAVAILABLE")));
        empty.api().setAttributeValue("counter","p","1");var post=observe(empty,event(empty,"unknown","completed",2));assertTrue(post.stream().noneMatch(r->r.kind().equals("POST")&&r.verdict()==SemanticVerdict.PASS));
    }
    @Test void mappedNativeParametersUseExactDescriptorAndCompletionCannotChangeArguments() throws Exception {
        var source=CanonicalGoalMappingTest.source();var base=CanonicalGoalMappingTest.rules(source);
        String text=base.iStarSource().replace("task increment pre {[ self.p >= 0 ]} post {[ self.p = self.p@pre + 1 ]}","task increment pre {[ p0 >= 0 ]} post {[ self.p = p0 and self.p@pre >= 0 ]}");
        var signature=List.<Object>of(CslCounterArtifact.class.getName(),"set",List.of("int"),"void");var rules=operationRules(base,text,signature,"p0 >= 0","self.p = p0 and self.p@pre >= 0");
        var f=fixture(source,new JaCaMoStaticMappingAdapter().map(source,JaCaMoMappingProfile.observedV1(),rules));
        var pre=observe(f,withInvocation(event(f,"set","started",1),signature,List.of("7"),List.of("java.lang.Integer")));assertTrue(pre.stream().anyMatch(r->r.id().equals("increment:pre")&&r.verdict()==SemanticVerdict.PASS));
        f.api().setAttributeValue("counter","p","7");var post=observe(f,withInvocation(event(f,"set","completed",2),signature,List.of("7"),List.of("java.lang.Integer")));assertTrue(post.stream().anyMatch(r->r.id().equals("increment:post")&&r.verdict()==SemanticVerdict.PASS));
        observe(f,withInvocation(event(f,"mismatch","started",3),signature,List.of("7"),List.of("java.lang.Integer")));var mismatch=observe(f,withInvocation(event(f,"mismatch","completed",4),signature,List.of("8"),List.of("java.lang.Integer")));
        assertTrue(mismatch.stream().anyMatch(r->r.diagnostic().equals("POST_INVOCATION_DESCRIPTOR_MISMATCH")));assertTrue(mismatch.stream().noneMatch(r->r.kind().equals("POST")&&r.verdict()==SemanticVerdict.PASS));
    }
    @Test void realDeclaredReturnTypeIsCompiledButMissingProducerResultNeverSynthesized() throws Exception {
        Path original=Path.of("src/test/resources/semantics/original-engine");Path stage=Path.of("target/semantic-migration/return-project");
        for(String path:List.of("src/agt/idle.asl","src/org/organization.xml")){Files.createDirectories(stage.resolve(path).getParent());Files.copy(original.resolve(path),stage.resolve(path),StandardCopyOption.REPLACE_EXISTING);}
        var jcm=stage.resolve("monitor.jcm").toAbsolutePath();Files.writeString(jcm,Files.readString(original.resolve("monitor.jcm")).replace(CslCounterArtifact.class.getName(),CounterWithReturnArtifact.class.getName()));
        var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm);var base=CanonicalGoalMappingTest.rules(source);
        String postBody="result = self.p and self.p = self.p@pre";String text=base.iStarSource().replace("self.p = self.p@pre + 1",postBody);
        var signature=List.<Object>of(CounterWithReturnArtifact.class.getName(),"returnP",List.of(),"int");var rules=operationRules(base,text,signature,"self.p >= 0",postBody);
        var f=fixture(source,new JaCaMoStaticMappingAdapter().map(source,JaCaMoMappingProfile.observedV1(),rules));
        var pre=observe(f,withInvocation(event(f,"return","started",1),signature,List.of(),List.of()));assertTrue(pre.stream().anyMatch(r->r.id().equals("increment:pre")&&r.verdict()==SemanticVerdict.PASS));
        var outcomes=f.checkpoints().observe(withInvocation(event(f,"return","completed",2),signature,List.of(),List.of()),Map.of("cartago",Completeness.COMPLETE),false,Set.of());
        var rows=f.checkpoints().drainDetailed();assertTrue(rows.stream().anyMatch(r->r.kind().equals("POST")&&r.verdict()==SemanticVerdict.UNSUPPORTED&&r.diagnostic().equals("OPERATION_RETURN_VALUE_NOT_OBSERVED_BY_ICARTAGOLOGGER")));
        assertTrue(outcomes.stream().allMatch(o->o.outcome()==org.tzi.use.plugins.jacamo.verification.VerificationOutcome.SKIPPED));
    }
    private MappingRuleSet operationRules(MappingRuleSet base,String text,List<Object> signature,String pre,String post) {
        var contracts=base.contracts().stream().map(c->new MappingRuleSet.Contract(c.id(),c.kind(),c.contextDeclaration(),c.targetElementId(),c.kind()==org.tzi.use.semantic.engine.use75.OclContractSpec.Kind.TASK_PRE?pre:post,signature,c.variableTypes(),c.origin())).toList();
        return new MappingRuleSet(base.version(),base.id(),base.profileId(),base.profileVersion(),"",base.cslSource(),text,base.bindings(),contracts,base.decisions(),base.options());
    }
    private RuntimeEvent withInvocation(RuntimeEvent event,List<Object> signature,List<String> values,List<String> types) {
        var after=new HashMap<>(event.after());after.put("operationSignature",signature);after.put("argumentValues",values);after.put("argumentTypes",types);
        return new RuntimeEvent(event.eventId(),event.sessionId(),event.generation(),event.modelRevision(),event.subsystem(),event.sourceId(),event.sourceSequence(),event.observedAt(),event.kind(),event.factKind(),event.projectionStatus(),event.entityId(),null,event.correlationId(),"",event.before(),after,event.watermark(),event.completeness(),event.evidence());
    }
}
