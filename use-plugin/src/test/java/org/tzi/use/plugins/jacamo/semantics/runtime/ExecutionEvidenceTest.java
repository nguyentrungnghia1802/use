package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.semantic.engine.runtime.EngineOccurrenceKey;
import org.vnu.sme.goal.dsl.istar.mm.GoalType;
import org.vnu.sme.goal.verify.conformance.semantics.*;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionEvidenceTest {
    private final EngineOccurrenceKey key=new EngineOccurrenceKey("task",List.of(),"episode");
    @Test void taskCompletionSurvivesMissingMarkerAndGapButNeverInventsAnUnseenCertificate() {
        var evidence=new ExecutionEvidence();
        assertTrue(evidence.leaf("context",key,null,new TaskMarking(true,true),false,1,"hash1").isEmpty());
        var certified=evidence.leaf("context",key,null,new TaskMarking(true,true),true,2,"hash2");
        assertEquals(2,certified.orElseThrow().stateVersion());
        assertEquals(certified,evidence.leaf("context",key,null,new TaskMarking(true,true),false,3,"hash3"));
        assertTrue(evidence.leaf("other-player-context",key,null,new TaskMarking(true,true),false,4,"hash4").isEmpty());
        assertTrue(evidence.leaf("context",new EngineOccurrenceKey("task",List.of(),"new-episode"),null,new TaskMarking(true,true),false,4,"hash4").isEmpty());
    }
    @Test void onlyAchievementLatchesAndOriginalResetInvalidatesCertificate() {
        var evidence=new ExecutionEvidence();
        for(var type:GoalType.values()) {
            var goal=new GoalMarking(type,true,true,true);
            var proof=evidence.leaf(type.name(),key,goal,null,true,1,"hash");
            assertEquals(type==GoalType.ACHIEVE,proof.isPresent());
        }
        assertTrue(evidence.leaf(GoalType.ACHIEVE.name(),key,GoalMarking.initial(GoalType.ACHIEVE),null,false,2,"next").isEmpty());
        assertTrue(evidence.leaf(GoalType.ACHIEVE.name(),key,new GoalMarking(GoalType.ACHIEVE,true,true,true),null,false,3,"next").isEmpty());
    }
    @Test void checkpointRollbackPruningAndRestartNeverRetainLaterProof() {
        var evidence=new ExecutionEvidence();var before=evidence.snapshot();
        evidence.leaf("context",key,null,new TaskMarking(true,true),true,1,"hash");
        evidence.restore(before);assertTrue(evidence.leaf("context",key,null,new TaskMarking(true,true),false,2,"hash2").isEmpty());
        evidence.leaf("context",key,null,new TaskMarking(true,true),true,3,"hash3");
        evidence.retain(Set.of());assertTrue(evidence.snapshot().isEmpty());
    }
    @Test void originalEngineAndProductionRendererRetainOnlyAdmittedCompletionAndRecomputeParent() {
        var compiled=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar Evidence { role R { goal Done : Achieve task Work > Done pre {[ true ]} post {[ true ]} task Other > Done pre {[ true ]} post {[ true ]} } }");
        assertTrue(compiled.ok(),compiled.errors().toString());var model=compiled.model();
        var cast=List.of(new org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance("alice","R"));
        try(var session=new org.tzi.use.semantic.engine.runtime.Use4GoalRuntimeSessionAdapter(model,cast,(a,x,b,y)->true,"cast")) {
            var ledger=new ExecutionEvidence();var topology=session.topology().occurrences();
            String task=topology.entrySet().stream().filter(e->e.getValue().sourceElementId().equals("Work")).findFirst().orElseThrow().getKey();
            String other=topology.entrySet().stream().filter(e->e.getValue().sourceElementId().equals("Other")).findFirst().orElseThrow().getKey();
            String parent=topology.entrySet().stream().filter(e->e.getValue().sourceElementId().equals("Done")).findFirst().orElseThrow().getKey();
            var input=new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(),Map.of(task,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("episode",true,true,true),other,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("episode",true,true,true)),Map.of());
            var before=session.checkpoint();var step=session.step(input);
            var context=new JaCaMoOccurrenceBindingAdapter.ContextCast("context","cast",cast,Map.of(),Map.of(),List.of());
            var raw=SemanticResultAdapter.context(context,session,step,before,input,Map.of(task,"KNOWN",other,"KNOWN"),true);
            var first=SemanticResultAdapter.execution(raw,session,cut(1,false),ledger);
            assertEquals("KNOWN",row(first,task).get("executionQuality"));
            var missingInput=new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(),Map.of(task,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("episode",null,null,null),other,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("episode",null,null,null)),Map.of());
            before=session.checkpoint();step=session.step(missingInput);
            raw=SemanticResultAdapter.context(context,session,step,before,missingInput,Map.of(task,"XML_TASK_TUPLE_UNAVAILABLE:CURRENT_STEP_MARKER_MISSING",other,"UNAVAILABLE"),true);
            var held=SemanticResultAdapter.execution(raw,session,cut(2,false),ledger);
            assertEquals("FULFILLED",row(held,task).get("aggregateStatus"));
            assertEquals("KNOWN_HISTORY",row(held,task).get("executionQuality"));
            assertEquals("KNOWN_DERIVED_HISTORY",row(held,parent).get("executionQuality"));
            assertEquals("DERIVED_INPUT_UNAVAILABLE",row(held,parent).get("quality"),"Current source quality must remain unchanged");
            var missingScope=SemanticResultAdapter.unavailableExecution(org.jacamo.bridge.contract.CanonicalJson.object(held),session,cut(2,false),ledger,List.of("CURRENT_SCOPE_ROW_UNAVAILABLE"));
            assertEquals("KNOWN_HISTORY",row(missingScope,task).get("executionQuality"));
            assertEquals("KNOWN_DERIVED_HISTORY",row(missingScope,parent).get("executionQuality"));
            assertEquals("UNAVAILABLE:CURRENT_SCOPE",row(missingScope,task).get("quality"));
            assertEquals("UNAVAILABLE:CURRENT_SCOPE",row(SemanticResultAdapter.unavailableExecution(org.jacamo.bridge.contract.CanonicalJson.object(held),session,cut(2,false),new ExecutionEvidence(),List.of("CURRENT_SCOPE_ROW_UNAVAILABLE")),task).get("executionQuality"),"Uncertified raw history never qualifies through a missing scope");
            var reopened=new ExecutionEvidence();reopened.restore(ledger.snapshot());
            assertEquals(held,SemanticResultAdapter.execution(raw,session,cut(2,true),reopened),"A gap cannot erase an already certified historical completion");
            assertEquals("XML_TASK_TUPLE_UNAVAILABLE:CURRENT_STEP_MARKER_MISSING",row(SemanticResultAdapter.execution(raw,session,cut(2,false),new ExecutionEvidence()),task).get("executionQuality"),"Raw history alone is never a certificate");
            var next=new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(),Map.of(task,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("new-invocation",null,null,null)),Map.of());
            before=session.checkpoint();step=session.step(next);raw=SemanticResultAdapter.context(context,session,step,before,next,Map.of(task,"UNAVAILABLE"),true);
            assertEquals("UNAVAILABLE",row(SemanticResultAdapter.execution(raw,session,cut(3,false),ledger),task).get("executionQuality"));
        }
    }
    @Test void originalOrProofDoesNotRequireTheUnsuccessfulBranchAndAndStillRequiresAllChildren() {
        var compiled=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar Choices { role R { goal Root : Achieve goal Choice : Achieve > Root goal Stable : Achieve > Root condition {[ true ]} task Good > or Choice pre {[ true ]} post {[ true ]} task Missing > or Choice pre {[ true ]} post {[ true ]} } }");
        assertTrue(compiled.ok(),compiled.errors().toString());
        var cast=List.of(new org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance("alice","R"));
        try(var session=new org.tzi.use.semantic.engine.runtime.Use4GoalRuntimeSessionAdapter(compiled.model(),cast,(a,x,b,y)->true,"cast")) {
            var ids=new HashMap<String,String>();session.topology().occurrences().forEach((id,b)->ids.put(b.sourceElementId(),id));
            var ledger=new ExecutionEvidence();var context=new JaCaMoOccurrenceBindingAdapter.ContextCast("context","cast",cast,Map.of(),Map.of(),List.of());
            var input=new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(ids.get("Stable"),new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("episode",true,true)),Map.of(ids.get("Good"),new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("episode",true,true,true),ids.get("Missing"),new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("episode",true,true,false)),Map.of());
            var before=session.checkpoint();var step=session.step(input);
            var raw=SemanticResultAdapter.context(context,session,step,before,input,Map.of(ids.get("Stable"),"KNOWN",ids.get("Good"),"KNOWN",ids.get("Missing"),"UNAVAILABLE"),true);
            var rendered=SemanticResultAdapter.execution(raw,session,cut(1,false),ledger);
            assertEquals("FULFILLED",row(rendered,ids.get("Choice")).get("aggregateStatus"));
            assertTrue(Set.of("KNOWN_DERIVED","KNOWN_DERIVED_HISTORY").contains(row(rendered,ids.get("Choice")).get("executionQuality")),"A certified successful OR branch is sufficient; another branch must not contaminate it");
            assertTrue(Set.of("KNOWN_DERIVED","KNOWN_DERIVED_HISTORY").contains(row(rendered,ids.get("Root")).get("executionQuality")));
            var unavailable=SemanticResultAdapter.context(context,session,step,before,input,Map.of(ids.get("Stable"),"UNAVAILABLE",ids.get("Good"),"UNAVAILABLE",ids.get("Missing"),"UNAVAILABLE"),true);
            assertEquals("DERIVED_INPUT_UNAVAILABLE",row(SemanticResultAdapter.execution(unavailable,session,cut(2,false),new ExecutionEvidence()),ids.get("Choice")).get("executionQuality"),"Raw original FULFILLED is not a proof");
            var missingAnd=SemanticResultAdapter.context(context,session,step,before,input,Map.of(ids.get("Stable"),"UNAVAILABLE",ids.get("Good"),"KNOWN",ids.get("Missing"),"UNAVAILABLE"),true);
            assertEquals("DERIVED_INPUT_UNAVAILABLE",row(SemanticResultAdapter.execution(missingAnd,session,cut(3,false),new ExecutionEvidence()),ids.get("Root")).get("executionQuality"),"AND still requires its other child");
        }
    }

    @Test void terminalFailureIsRetainedWithoutChangingOriginalMarkingOrCreditingOtherEpisodes() {
        var compiled=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar EvidenceFailure { role R { goal Done : Achieve task Work > Done pre {[ true ]} post {[ true ]} task Other > Done pre {[ true ]} post {[ true ]} } }");
        assertTrue(compiled.ok(),compiled.errors().toString());
        var cast=List.of(new org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance("alice","R"));
        try(var session=new org.tzi.use.semantic.engine.runtime.Use4GoalRuntimeSessionAdapter(compiled.model(),cast,(a,x,b,y)->true,"cast")) {
            var ids=new HashMap<String,String>();session.topology().occurrences().forEach((id,b)->ids.put(b.sourceElementId(),id));String task=ids.get("Work");
            var context=new JaCaMoOccurrenceBindingAdapter.ContextCast("context","cast",cast,Map.of(),Map.of(),List.of());var ledger=new ExecutionEvidence();
            var input=new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(),Map.of(task,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("failed-episode",true,true,false),ids.get("Other"),new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("other-episode",true,true,true)),Map.of());
            var before=session.checkpoint();var step=session.step(input);var key=session.checkpoint().keys().get(task);
            var raw=SemanticResultAdapter.context(context,session,step,before,input,Map.of(task,"KNOWN",ids.get("Other"),"KNOWN"),true);
            var unchanged=session.checkpoint();var image=ledger.snapshot();
            ledger.terminalFailure("context",key,"source-invocation",false,1,"hash1");assertTrue(ledger.snapshot().isEmpty(),"Unadmitted false is not a terminal failure receipt");
            ledger.terminalFailure("context",key,"source-invocation",true,1,"hash1");SemanticResultAdapter.execution(raw,session,cut(1,false),ledger);
            var missing=new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(),Map.of(task,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("failed-episode",null,null,null)),Map.of());
            before=session.checkpoint();step=session.step(missing);raw=SemanticResultAdapter.context(context,session,step,before,missing,Map.of(task,"CURRENT_STEP_MARKER_MISSING"),true);
            var held=SemanticResultAdapter.execution(raw,session,cut(2,false),ledger);
            assertEquals("FAILED",row(held,task).get("executionStatus"));assertEquals("KNOWN_HISTORY",row(held,task).get("executionQuality"));
            assertEquals("PENDING",row(held,task).get("aggregateStatus"),"Original TaskMarking has no FAILED status");
            assertEquals("KNOWN_DERIVED_HISTORY",row(held,ids.get("Done")).get("executionQuality"));
            assertTrue(row(held,task).get("executionEvidence").toString().contains("source-invocation"));
            assertEquals(unchanged.tasks(),session.checkpoint().tasks());
            assertEquals("FAILED",row(SemanticResultAdapter.unavailableExecution(org.jacamo.bridge.contract.CanonicalJson.object(held),session,cut(2,true),ledger,List.of("missing")),task).get("executionStatus"));
            var reopened=new ExecutionEvidence();reopened.restore(ledger.snapshot());assertEquals(held,SemanticResultAdapter.execution(raw,session,cut(2,false),reopened));
            assertTrue(reopened.terminal("other-context",key).isEmpty());
            assertTrue(reopened.terminal("context",new org.tzi.use.semantic.engine.runtime.EngineOccurrenceKey(key.sourceElementId(),key.bindingStack(),"new-episode")).isEmpty());
            ledger.restore(image);assertTrue(ledger.snapshot().isEmpty(),"Rollback restores terminal outcomes as well as success");reopened.retain(Set.of());assertTrue(reopened.snapshot().isEmpty());
        }
    }
    private static RuntimeEngineSessionOwner.Cut cut(int version,boolean lost){return new RuntimeEngineSessionOwner.Cut("owner",new RuntimeEngineSessionOwner.Identity("run",1,"revision","program","profile"),1,version,"boundary"+version,"hash"+version,null,List.of(),true,lost,List.of(),null,0,Map.of(),List.of());}
    private static Map<String,Object> row(Object context,String id){var root=org.jacamo.bridge.contract.CanonicalJson.object(context);return org.jacamo.bridge.contract.CanonicalJson.object(org.jacamo.bridge.contract.CanonicalJson.object(root.get("occurrences")).get(id));}
}
