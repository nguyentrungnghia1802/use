package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import cartago.Op;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeInvocationEvaluation;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.type.TypeFactory;
import org.tzi.use.uml.sys.MSystemState;
import static org.junit.jupiter.api.Assertions.*;

/** Real native/CArtAgO state with explicitly constructed source-contract inputs.
 * The separate live TCP test verifies capture; this is not the E01 vertical slice. */
class AuctionTaskOclBindingsTest {
    @Test void queryEntryAndKnownFailureUseCompleteCallbackValuesWithoutPromotingMissingArtifactReference()throws Exception {
        for(String task:List.of("A_decide_t1","P_bid_t1"))try(var f=new Component(task)) {
            var entry=callbackWithoutArtifact(f,"ENABLED");var before=new MSystemState("actual-query-entry",f.fixture.projector.system().state());
            var prepared=f.prepare(true,entry,null);assertEquals(SemanticVerdict.PASS,evaluate(prepared).verdict());assertEquals("PARTIAL",prepared.witness().get("rawStepFrameQuality"));
            var failed=f.prepare(false,callbackWithoutArtifact(f,"FAILED"),before);assertEquals(SemanticVerdict.FAIL,evaluate(failed).verdict(),"Known explicit query failure is observed independently of its missing output handle");
            var missing=AuctionStepDecoderTest.variant(entry,data->CanonicalJson.object(data.get("bindings")).remove("stepValueQuality"));assertEquals(SemanticVerdict.SKIPPED,evaluate(f.prepare(true,missing,null)).verdict());
        }
    }
    static VerificationObservation callbackWithoutArtifact(Component f,String phase)throws Exception {
        var value=AuctionStepDecoderTest.variant(f.frame(phase),data->{data.put("artifactIdentity","");var bindings=CanonicalJson.object(data.get("bindings"));bindings.put("artifact",Map.of());bindings.put("sourceFailure",phase.equals("FAILED")?"GOAL_LISTENER_FAILED:EXPLICIT_COMPONENT_FAILURE":"");bindings.put("stepValueQuality",Map.of("version","AUCTION_STEP_VALUES/1.0","enabled","COMPLETE_CALLBACK","succeeded",phase.equals("ENABLED")?"UNAVAILABLE":"COMPLETE_CALLBACK","enabledAuthority","PINNED_PLAN_BODY_ENTRY_MARKER","succeededAuthority",phase.equals("ENABLED")?"UNAVAILABLE":"PUBLIC_GOAL_LISTENER_FAILURE"));});
        return new VerificationObservation(value.kind(),value.subject(),Completeness.PARTIAL,"EXACT_ARTIFACT_REFERENCE_UNAVAILABLE",value.sourceTime(),value.data());
    }
    @Test void productionOwnerCompilesAllTwelveAuthoredTaskSlotsBeforeAnyHistoryIsAdmitted()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            assertEquals(12,f.projector.coordinator().originalEngine().metrics().get("expressions"),"Every authored Task pre/post slot must be prepared by the production OCL owner");
        }
    }
    @Test void unchangedTwelvePredicatesExecuteOnExactSelfTypedStepAndActualService()throws Exception {
        for(String task:tasks())try(var f=new Component(task)) {
            assertEquals(6,f.binding.slots().size());
            assertEquals(Set.of("step"),f.binding.slots().get("P_bid_t2").pre().specification().bindings().keySet());
            assertTrue(f.binding.slots().get("A_start_t2").pre().specification().bindings().isEmpty(),"Unused service must not suppress an independent precondition");
            assertEquals(task.equals("A_start_t2")||task.equals("A_decide_t2")?AuctionTaskOclBindings.Kind.CARTAGO_OPERATION:AuctionTaskOclBindings.Kind.JASON_STEP_COMPOSITE,f.binding.slots().get(task).kind());
            var pre=f.prepare(true,f.frame("ENABLED"),null);assertEquals(SemanticVerdict.PASS,evaluate(pre).verdict(),task+":pre "+pre.unavailable());
            var before=new MSystemState("actual-task-pre",f.fixture.projector.system().state());f.perform();
            var post=f.prepare(false,f.frame("SUCCEEDED"),before);assertEquals(SemanticVerdict.PASS,evaluate(post).verdict(),task+":post "+post.unavailable());
            var authored=(org.vnu.sme.goal.dsl.istar.mm.Task)f.fixture.program.goalModel().findElement(task).orElseThrow();
            assertEquals(authored.preconditions().getFirst().oclBody(),pre.text());assertEquals(authored.postconditions().getFirst().oclBody(),post.text());
            assertEquals(f.scope.artifact(),post.source());assertEquals(f.actor.player(),post.witness().get("player"));assertEquals("COMPLETE_FOR_EXPRESSION",post.witness().get("observationQuality"));
        }
    }
    @Test void everyTaskRejectsMissingWrongPlayerSchemeArtifactInvocationAndGapWithoutFalseViolation()throws Exception {
        for(String task:tasks())try(var f=new Component(task)) {
            var before=new MSystemState("matching-pre",f.fixture.projector.system().state());f.perform();
            assertEquals(SemanticVerdict.SKIPPED,evaluate(f.prepare(false,null,before)).verdict(),task);
            for(String wrong:List.of("player","scheme","artifact","invocation")) {
                var frame=AuctionStepDecoderTest.variant(wrong.equals("player")?f.frame("SUCCEEDED",f.agent("wrong")):f.frame("SUCCEEDED"),data->{
                    if(wrong.equals("scheme")) {var other=org.jacamo.bridge.contract.BridgeEntityId.parse(f.otherScope().scheme());data.put("schemeIdentity",other.canonical());var typed=CanonicalJson.object(CanonicalJson.object(data.get("bindings")).get("schemeArtifact"));typed.put("name",other.localId());typed.put("uuid",other.incarnation().substring(f.fixture.run().id().length()+1));}
                    if(wrong.equals("artifact")) {data.put("artifactIdentity",f.otherScope().artifact());CanonicalJson.object(CanonicalJson.object(data.get("bindings")).get("artifact")).put("semanticId",f.otherScope().artifact());}
                    if(wrong.equals("invocation"))data.put("stepInvocation","jason-step:unrelated");
                });
                var condition=f.prepare(false,frame,before);
                if(!wrong.equals("invocation"))assertEquals("EXACT_STEP_"+wrong.toUpperCase(java.util.Locale.ROOT)+"_MISMATCH",condition.unavailable(),task+":"+wrong);
                assertNotEquals(SemanticVerdict.PASS,evaluate(condition).verdict(),task+":"+wrong);
                assertNotEquals(SemanticVerdict.FAIL,evaluate(condition).verdict(),task+":"+wrong);
            }
            var view=UseStateReadView.pin(before,f.fixture.projector.system().state());
            var gap=f.binding.prepare(task,false,f.fixture.projector.mutations(),f.scope,f.actor,f.fixture.run(),f.frame("SUCCEEDED"),view,Map.of("cartago",Completeness.COMPLETE),true);
            assertEquals(SemanticVerdict.SKIPPED,evaluate(gap).verdict());assertEquals("COVERAGE_LOST",gap.unavailable());
            assertEquals(SemanticVerdict.SKIPPED,evaluate(f.prepare(false,f.frame("SUCCEEDED"),null)).verdict());
        }
    }
    @Test void explicitCompositeFailureIsFalseButOperationFailureSkipsPostAndMissingNeverBecomesFalse()throws Exception {
        for(String task:tasks())try(var f=new Component(task)) {
            var before=new MSystemState("pre",f.fixture.projector.system().state());
            var failed=evaluate(f.prepare(false,f.frame("FAILED"),before));
            assertEquals(task.equals("A_start_t2")||task.equals("A_decide_t2")?SemanticVerdict.SKIPPED:SemanticVerdict.FAIL,failed.verdict(),task);
            var raw=f.frame("FAILED");var partial=new VerificationObservation(raw.kind(),raw.subject(),Completeness.PARTIAL,"EXPLICIT_COMPONENT_CAPTURE_LOSS",raw.sourceTime(),raw.data());
            assertEquals(SemanticVerdict.SKIPPED,evaluate(f.prepare(false,partial,before)).verdict());
        }
    }
    @Test void serviceMismatchMissingTypedInputResourceAbsenceAndUnsupportedVariableTypesFailClosed()throws Exception {
        try(var f=new Component("A_start_t2")) {
            var before=new MSystemState("pre",f.fixture.projector.system().state());f.perform();
            var mismatch=AuctionStepDecoderTest.variant(f.frame("SUCCEEDED"),data->{var bindings=CanonicalJson.object(data.get("bindings"));var term=org.jacamo.bridge.adapter.JasonStructuredTerms.term(jason.asSyntax.ASSyntax.createString("different actual bound input"));bindings.put("values",Map.of("S",term));var proof=CanonicalJson.object(bindings.get("serviceInput"));proof.put("value","different actual bound input");proof.put("sourceTermHash",SemanticJson.hash(term));});
            assertEquals(SemanticVerdict.FAIL,evaluate(f.prepare(false,mismatch,before)).verdict(),"Known complete source input differs from this actual object's task");
            var missing=AuctionStepDecoderTest.variant(f.frame("SUCCEEDED"),data->CanonicalJson.object(data.get("bindings")).remove("serviceInput"));
            assertEquals(SemanticVerdict.SKIPPED,evaluate(f.prepare(false,missing,before)).verdict());
            assertEquals(SemanticVerdict.PASS,evaluate(f.prepare(false,f.frame("SUCCEEDED"),before)).verdict());
            assertThrows(IllegalArgumentException.class,()->f.ocl.compile(new OclContractSpec("unsupported-step",OclContractSpec.Kind.TASK_PRE,f.fixture.projector.system().model().getClass("Auction"),null,"step.enabled",Map.of("step",TypeFactory.mkString()))));
            f.fixture.disposeArtifact(0);f.fixture.resync();
            assertEquals(SemanticVerdict.SKIPPED,evaluate(f.prepare(false,f.frame("SUCCEEDED"),before)).verdict());
        }
    }
    private static List<String> tasks(){return List.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1","P_bid_t2");}
    private static SemanticConstraintResult evaluate(NativeInvocationEvaluation.Condition condition){return NativeInvocationEvaluation.evaluate(new NativeInvocationEvaluation.Work(List.of(condition),List.of(),List.of(),0),1).getFirst();}
    static final class Component implements AutoCloseable {
        final AuctionCastFixture fixture;final NativeOclBackend ocl;final AuctionTaskOclBindings binding;final String task;final AuctionOccurrenceBindingAdapter.Scope scope;final AuctionOccurrenceBindingAdapter.Player actor;
        final Map<String,Map<String,Object>> recipes=new HashMap<>();final String service="flight 'quoted' \\ route";final BridgeEntityId sourceAgent;
        Component(String task)throws Exception {
            this.task=task;
            fixture=new AuctionCastFixture(1);ocl=new NativeOclBackend(fixture.projector.system().model(),fixture.program.hash(),"O03_COMPONENT");binding=new AuctionTaskOclBindings(fixture.program,fixture.projector.system().model(),ocl);
            scope=new AuctionOccurrenceBindingAdapter(fixture.program).resolve(fixture.projector.mutations(),fixture.run()).stream().filter(s->fixture.projector.mutations().metadata(fixture.projector.mutations().objectForSemanticId(s.artifact()),"name").equals("castOne")).findFirst().orElseThrow();
            actor=scope.actors().stream().filter(p->p.sourceActor().equals(task.startsWith("P_")?"Participant":"Auctioneer")).findFirst().orElseThrow();sourceAgent=agent("actual");alias();
            if(task.equals("A_decide_t2")||task.equals("P_bid_t2")){fixture.org.live.actor.doAction(fixture.artifacts.getFirst(),new Op("start",service),5000);fixture.resync();alias();}
        }
        private void alias()throws Exception {var method=fixture.projector.mutations().getClass().getDeclaredMethod("cslPort");assertTrue(method.trySetAccessible());var port=(SemanticObservationMapper.Port)method.invoke(fixture.projector.mutations());port.alias(sourceAgent.canonical(),fixture.projector.mutations().objectForSemanticId(actor.player()));}
        BridgeEntityId agent(String variant){return new BridgeEntityId("jason","agent","runtime-agent","auction",variant.equals("wrong")?"giacomo":task.startsWith("P_")?"alice":"bob","O03-component-"+variant);}
        AuctionOccurrenceBindingAdapter.Scope otherScope(){return new AuctionOccurrenceBindingAdapter(fixture.program).resolve(fixture.projector.mutations(),fixture.run()).stream().filter(s->!s.id().equals(scope.id())).findFirst().orElseThrow();}
        void perform()throws Exception {if(task.equals("A_start_t2"))fixture.org.live.actor.doAction(fixture.artifacts.getFirst(),new Op("start",service),5000);if(task.equals("A_decide_t2"))fixture.org.live.actor.doAction(fixture.artifacts.getFirst(),new Op("stop"),5000);fixture.resync();alias();}
        NativeInvocationEvaluation.Condition prepare(boolean pre,VerificationObservation observation,MSystemState before){return binding.prepare(task,pre,fixture.projector.mutations(),scope,actor,fixture.run(),observation,UseStateReadView.pin(before,fixture.projector.system().state()),Map.of("cartago",Completeness.COMPLETE),false);}
        VerificationObservation frame(String phase)throws Exception {
            return frame(phase,sourceAgent);
        }
        VerificationObservation frame(String phase,BridgeEntityId agent)throws Exception {
            var data=new LinkedHashMap<String,Object>();var bindings=new LinkedHashMap<String,Object>();data.put("bindings",bindings);bindings.put("intentionAuthority","ACTUAL_INTENDED_MEANS_TRIGGER_IDENTITY");
            String goal=task.startsWith("A_start")?"start":task.startsWith("A_decide")?"decide":"bid";
            int first=task.equals("A_start_t1")||task.equals("P_bid_t1")||task.equals("A_decide_t1")?0:task.equals("A_start_t2")?5:task.equals("A_decide_t2")?2:3;
            int after=task.equals("A_start_t1")?5:task.equals("A_start_t2")?6:task.equals("A_decide_t1")?2:task.equals("A_decide_t2")?3:task.equals("P_bid_t1")?3:4;
            String declaration=agent.localId();var source=fixture.org.live.source.semanticContract().jasonPrograms().stream().filter(p->p.declarationId().equals(declaration)).findFirst().orElseThrow();var plan=source.planLibrary().plans().stream().filter(p->p.trigger().literal().equals(goal+"[scheme(Sch)]")).findFirst().orElseThrow();
            String invocation="jason-step:"+fixture.run().id()+":"+agent.incarnation()+":1",operation="O03-source-action:1";
            data.put("agentIdentity",agent.canonical());data.put("goalInvocation","jason-goal:"+fixture.run().id()+":"+agent.incarnation()+":1");data.put("stepInvocation",invocation);data.put("sourceStepId",task);data.put("phase",phase);data.put("artifactIdentity",scope.artifact());data.put("schemeIdentity",scope.scheme());data.put("operationInvocation",phase.equals("ENABLED")?"":operation);
            bindings.put("taskCell",task);bindings.put("agentDeclaration",declaration);bindings.put("agentName",declaration);bindings.put("capturedRun",Map.of("sessionId",fixture.run().id(),"generation",fixture.run().generation(),"modelRevision",fixture.run().modelRevision()));
            bindings.put("sourceLocation",Map.of("planId",plan.metadata().semanticId(),"firstOrdinal",first,"afterOrdinal",after,"line",plan.body().get(first).metadata().evidence().getFirst().startLine()));
            var scheme=BridgeEntityId.parse(scope.scheme());bindings.put("schemeArtifact",Map.of("kind","ARTIFACT_ID","typeHint","ora4mas.nopl.SchemeBoard","name",scheme.localId(),"uuid",scheme.incarnation().substring(fixture.run().id().length()+1)));
            bindings.put("artifact",Map.of("kind","ARTIFACT_ID","typeHint","auction_env.AuctionArtifact","semanticId",scope.artifact()));
            bindings.put("actions",phase.equals("ENABLED")?List.of():List.of(Map.of("authority","JASON_ACTIONEXEC_IDENTITY","correlation",operation,"operationName",goal.equals("decide")?"stop":goal.equals("start")?"start":"bid","result",phase.equals("FAILED")?"FAILED":"SUCCEEDED")));
            var term=org.jacamo.bridge.adapter.JasonStructuredTerms.term(jason.asSyntax.ASSyntax.createString(service));bindings.put("values",Map.of("S",term));bindings.put("serviceInput",Map.of("authority","JACA_PUBLIC_TERM_TO_OBJECT_START_VARIABLE_V1","javaType","java.lang.String","value",service,"sourceTermHash",SemanticJson.hash(term)));
            // Obtain the real source-owned recipe; never reconstruct framework AST rows.
            if(!recipes.containsKey(declaration)) {
                var parsed=AuctionStagingInstrumentationTest.parse();
                var ts=new jason.asSemantics.TransitionSystem(parsed,new jason.asSemantics.Circumstance(),new jason.runtime.Settings(),new jason.architecture.AgArch(){@Override public String getAgName(){return declaration;}});
                try(var owner=new org.jacamo.bridge.adapter.AuctionStepObservation(ts,agent,fixture.run().id(),fixture.run().generation(),fixture.run().modelRevision(),source,AuctionStagingInstrumentationTest.ROOT,ignored->{},ignored->{})){recipes.put(declaration,owner.manifest());}
                finally{parsed.stopAg();}
            }
            bindings.put("instrumentation",recipes.get(declaration));bindings.put("instrumentationHash",SemanticJson.hash(recipes.get(declaration)));
            return new VerificationObservation(VerificationObservation.Kind.JASON_STEP,invocation,Completeness.COMPLETE,"",VerificationObservation.SourceTime.unavailable("EXPLICIT_O03_COMPONENT_INPUT"),data);
        }
        public void close()throws Exception{ocl.close();fixture.close();}
    }
}
