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
            assertEquals("VIOLATED",row(held,ids.get("Done")).get("executionStatus"),"AND parent of a certified terminal task failure is falsified");
            assertTrue(row(held,task).get("executionEvidence").toString().contains("source-invocation"));
            assertEquals(unchanged.tasks(),session.checkpoint().tasks());
            assertEquals("FAILED",row(SemanticResultAdapter.unavailableExecution(org.jacamo.bridge.contract.CanonicalJson.object(held),session,cut(2,true),ledger,List.of("missing")),task).get("executionStatus"));
            var reopened=new ExecutionEvidence();reopened.restore(ledger.snapshot());assertEquals(held,SemanticResultAdapter.execution(raw,session,cut(2,false),reopened));
            assertTrue(reopened.terminal("other-context",key).isEmpty());
            assertTrue(reopened.terminal("context",new org.tzi.use.semantic.engine.runtime.EngineOccurrenceKey(key.sourceElementId(),key.bindingStack(),"new-episode")).isEmpty());
            ledger.restore(image);assertTrue(ledger.snapshot().isEmpty(),"Rollback restores terminal outcomes as well as success");reopened.retain(Set.of());assertTrue(reopened.snapshot().isEmpty());
        }
    }
    @Test void threeValuedAndOrAndNonLatchingTaskPreconditionFollowPaperSemantics() {
        var compiled=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar PaperTruth { role R { goal AndRoot : Achieve goal OrChoice : Achieve > AndRoot task StepOne > AndRoot pre {[ true ]} post {[ true ]} task OptA > or OrChoice pre {[ true ]} post {[ true ]} task OptB > or OrChoice pre {[ true ]} post {[ true ]} } }");
        assertTrue(compiled.ok(),compiled.errors().toString());
        var cast=List.of(new org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance("alice","R"));
        try(var session=new org.tzi.use.semantic.engine.runtime.Use4GoalRuntimeSessionAdapter(compiled.model(),cast,(a,x,b,y)->true,"cast")) {
            var ids=new HashMap<String,String>();session.topology().occurrences().forEach((id,b)->ids.put(b.sourceElementId(),id));
            String stepOne=ids.get("StepOne"),optA=ids.get("OptA"),optB=ids.get("OptB"),orChoice=ids.get("OrChoice"),andRoot=ids.get("AndRoot");
            // 1. StepOne evaluated with (true, true, false) -> PENDING; then (true, false, true) must NOT certify completion
            var context=new JaCaMoOccurrenceBindingAdapter.ContextCast("context","cast",cast,Map.of(),Map.of(),List.of());
            var ledger=new ExecutionEvidence();
            var s0=session.step(new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(),Map.of(stepOne,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("ep",true,true,false)),Map.of()));
            assertEquals(GoalTaskStatus.PENDING,session.checkpoint().tasks().get(stepOne).status());
            var inFalsePre=new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(),Map.of(stepOne,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("ep",true,false,true)),Map.of());
            var sFalsePre=session.step(inFalsePre);
            var rawFalsePre=SemanticResultAdapter.context(context,session,sFalsePre,s0.history(),inFalsePre,Map.of(stepOne,"KNOWN",optA,"UNAVAILABLE",optB,"UNAVAILABLE"),true);
            var exFalsePre=SemanticResultAdapter.execution(rawFalsePre,session,cut(0,false),ledger);
            assertEquals("UNAVAILABLE:PRECONDITION_FALSE",row(exFalsePre,stepOne).get("executionQuality"),"pre=false must evaluate to Unknown, never certifying prior preSeen=true into Complete");
            assertEquals("Unknown",org.tzi.use.plugins.jacamo.ui.use4goal.conformance.PresentationResults.agentResult(Objects.toString(row(exFalsePre,stepOne).get("aggregateStatus"),""),Objects.toString(row(exFalsePre,stepOne).get("executionQuality"),"")));
            // 2. OptA fails terminally while OptB is unknown -> OrChoice stays UNKNOWN, not VIOLATED
            var in1=new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(),Map.of(optA,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("ep",true,true,false)),Map.of());
            var before=session.checkpoint();var s1=session.step(in1);
            ledger.terminalFailure("context",session.checkpoint().keys().get(optA),"inv-optA",true,1,"hash1");
            var raw1=SemanticResultAdapter.context(context,session,s1,before,in1,Map.of(optA,"KNOWN",optB,"UNAVAILABLE",stepOne,"UNAVAILABLE"),true);
            var ex1=SemanticResultAdapter.execution(raw1,session,cut(1,false),ledger);
            assertNull(row(ex1,orChoice).get("executionStatus"),"One failed OR branch with an unknown sibling must stay Unknown");
            // 3. When OptB also fails terminally -> all OR branches are BOTTOM -> OrChoice and AndRoot become VIOLATED
            var in2=new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(),Map.of(optB,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("ep",true,true,false)),Map.of());
            before=session.checkpoint();var s2=session.step(in2);
            ledger.terminalFailure("context",session.checkpoint().keys().get(optB),"inv-optB",true,2,"hash2");
            var raw2=SemanticResultAdapter.context(context,session,s2,before,in2,Map.of(optA,"CURRENT_STEP_MARKER_MISSING",optB,"KNOWN",stepOne,"UNAVAILABLE"),true);
            var ex2=SemanticResultAdapter.execution(raw2,session,cut(2,false),ledger);
            assertEquals("VIOLATED",row(ex2,orChoice).get("executionStatus"),"OR with all branches falsified is VIOLATED");
            assertEquals("VIOLATED",row(ex2,andRoot).get("executionStatus"),"AND with one falsified child is VIOLATED even when StepOne is unavailable");
        }
    }
    @Test void goalTypeNoneActiveFalseTrueUnknownAndTrueToFalseTransitionsFollowDecisionMatrix() {
        var compiled=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("""
                istar NoneMatrix {
                    role R {
                        goal Plain : None condition {[ true ]}
                        task StepA > Plain pre {[ true ]} post {[ true ]}
                        task StepB > Plain pre {[ true ]} post {[ true ]}
                        goal Sub : None > Plain
                        goal DepPlain : None
                        goal Ach : Achieve condition {[ true ]}
                    }
                    role S {
                        goal SubSupplied : None condition {[ true ]}
                        goal Supplied : None condition {[ true ]}
                    }
                    depend R.Sub -> goal d1 -> S.SubSupplied
                    depend R.DepPlain -> goal d2 -> S.Supplied
                }
                """);
        assertTrue(compiled.ok(),compiled.errors().toString());
        var cast=List.of(new org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance("alice","R"),new org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance("bob","S"));
        try(var session=new org.tzi.use.semantic.engine.runtime.Use4GoalRuntimeSessionAdapter(compiled.model(),cast,(a,x,b,y)->true,"cast")) {
            var ids=new HashMap<String,String>();session.topology().occurrences().forEach((id,b)->ids.put(b.sourceElementId(),id));
            String plain=ids.get("Plain"),stepA=ids.get("StepA"),stepB=ids.get("StepB"),subSupplied=ids.get("SubSupplied"),depPlain=ids.get("DepPlain"),supplied=ids.get("Supplied"),ach=ids.get("Ach");
            var context=new JaCaMoOccurrenceBindingAdapter.ContextCast("context","cast",cast,Map.of(),Map.of(),List.of());
            var ledger=new ExecutionEvidence();
            var tasksFulfilled=Map.of(
                    stepA,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("ep",true,true,true),
                    stepB,new org.tzi.use.semantic.engine.runtime.EngineInputs.TaskInput("ep",true,true,true));
            // 1. Active GoalType.NONE with unknown condition (even when refinement children & sub-dependency are FULFILLED) -> Unknown (UNKNOWN)
            var inUnknown=new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(
                    plain,new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("ep",true,null),
                    subSupplied,new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("ep",true,true),
                    supplied,new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("ep",true,null),
                    ach,new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("ep",true,null)),tasksFulfilled,Map.of());
            var before=session.checkpoint();var sUnknown=session.step(inUnknown);
            var rawUnknown=SemanticResultAdapter.context(context,session,sUnknown,before,inUnknown,Map.of(
                    plain,"UNAVAILABLE:CONDITION_UNDEFINED",subSupplied,"KNOWN",supplied,"UNAVAILABLE:CONDITION_UNDEFINED",ach,"UNAVAILABLE:CONDITION_UNDEFINED",stepA,"KNOWN",stepB,"KNOWN"),true);
            var exUnknown=SemanticResultAdapter.execution(rawUnknown,session,cut(1,false),ledger);
            for(String target:List.of(plain,supplied,depPlain)) {
                assertNull(row(exUnknown,target).get("executionStatus"),"Undetermined condition must not be VIOLATED for "+target);
                assertEquals("UNKNOWN",org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews.qualifiedStatus(org.jacamo.bridge.contract.CanonicalJson.object(exUnknown),row(exUnknown,target)));
                assertEquals("Unknown",org.tzi.use.plugins.jacamo.ui.use4goal.conformance.PresentationResults.agentResult("UNKNOWN",Objects.toString(row(exUnknown,target).get("executionQuality"),"")));
            }
            // 2. Active GoalType.NONE with condition=false (KNOWN) + refinement/dependency -> Incomplete (VIOLATED), while Achieve stays PENDING/Unknown
            var inFalse=new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(
                    plain,new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("ep",true,false),
                    subSupplied,new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("ep",true,true),
                    supplied,new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("ep",true,false),
                    ach,new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("ep",true,false)),tasksFulfilled,Map.of());
            before=session.checkpoint();var sFalse=session.step(inFalse);
            var rawFalse=SemanticResultAdapter.context(context,session,sFalse,before,inFalse,Map.of(
                    plain,"KNOWN",subSupplied,"KNOWN",supplied,"KNOWN",ach,"KNOWN",stepA,"KNOWN",stepB,"KNOWN"),true);
            var exFalse=SemanticResultAdapter.execution(rawFalse,session,cut(2,false),ledger);
            for(String target:List.of(plain,supplied,depPlain)) {
                assertEquals("VIOLATED",row(exFalse,target).get("executionStatus"),"Active GoalType.NONE with known false condition must be VIOLATED for "+target);
                assertEquals("VIOLATED",org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews.qualifiedStatus(org.jacamo.bridge.contract.CanonicalJson.object(exFalse),row(exFalse,target)));
                assertEquals("Incomplete",org.tzi.use.plugins.jacamo.ui.use4goal.conformance.PresentationResults.agentResult("VIOLATED",Objects.toString(row(exFalse,target).get("executionQuality"),"")));
            }
            assertNull(row(exFalse,ach).get("executionStatus"),"Achieve with condition=false must remain PENDING, not Incomplete");
            assertEquals("PENDING",org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews.qualifiedStatus(org.jacamo.bridge.contract.CanonicalJson.object(exFalse),row(exFalse,ach)));
            assertEquals("Unknown",org.tzi.use.plugins.jacamo.ui.use4goal.conformance.PresentationResults.agentResult("PENDING",Objects.toString(row(exFalse,ach).get("executionQuality"),"")));
            // 3. Active GoalType.NONE with condition=true (KNOWN) -> Complete (FULFILLED)
            var inTrue=new org.tzi.use.semantic.engine.runtime.EngineInputs(Map.of(
                    plain,new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("ep",true,true),
                    subSupplied,new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("ep",true,true),
                    supplied,new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("ep",true,true),
                    ach,new org.tzi.use.semantic.engine.runtime.EngineInputs.GoalInput("ep",true,true)),tasksFulfilled,Map.of());
            before=session.checkpoint();var sTrue=session.step(inTrue);
            var rawTrue=SemanticResultAdapter.context(context,session,sTrue,before,inTrue,Map.of(
                    plain,"KNOWN",subSupplied,"KNOWN",supplied,"KNOWN",ach,"KNOWN",stepA,"KNOWN",stepB,"KNOWN"),true);
            var exTrue=SemanticResultAdapter.execution(rawTrue,session,cut(3,false),ledger);
            for(String target:List.of(plain,supplied,depPlain)) {
                assertNull(row(exTrue,target).get("executionStatus"));
                assertEquals("FULFILLED",org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews.qualifiedStatus(org.jacamo.bridge.contract.CanonicalJson.object(exTrue),row(exTrue,target)));
                assertEquals("Complete",org.tzi.use.plugins.jacamo.ui.use4goal.conformance.PresentationResults.agentResult("FULFILLED",Objects.toString(row(exTrue,target).get("executionQuality"),"")));
            }
            // 4. Transition true -> false -> true while active: GoalType.NONE transitions Complete -> Incomplete -> Complete, while Achieve latches Complete
            before=session.checkpoint();var sTrueToFalse=session.step(inFalse);
            var rawTrueToFalse=SemanticResultAdapter.context(context,session,sTrueToFalse,before,inFalse,Map.of(
                    plain,"KNOWN",subSupplied,"KNOWN",supplied,"KNOWN",ach,"KNOWN",stepA,"KNOWN",stepB,"KNOWN"),true);
            var exTrueToFalse=SemanticResultAdapter.execution(rawTrueToFalse,session,cut(4,false),ledger);
            for(String target:List.of(plain,supplied,depPlain)) {
                assertEquals("VIOLATED",row(exTrueToFalse,target).get("executionStatus"));
                assertEquals("VIOLATED",org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews.qualifiedStatus(org.jacamo.bridge.contract.CanonicalJson.object(exTrueToFalse),row(exTrueToFalse,target)));
                assertEquals("Incomplete",org.tzi.use.plugins.jacamo.ui.use4goal.conformance.PresentationResults.agentResult("VIOLATED",Objects.toString(row(exTrueToFalse,target).get("executionQuality"),"")));
            }
            assertEquals("FULFILLED",org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews.qualifiedStatus(org.jacamo.bridge.contract.CanonicalJson.object(exTrueToFalse),row(exTrueToFalse,ach)),"Achieve latches FULFILLED after true -> false");
            before=session.checkpoint();var sFalseToTrue=session.step(inTrue);
            var rawFalseToTrue=SemanticResultAdapter.context(context,session,sFalseToTrue,before,inTrue,Map.of(
                    plain,"KNOWN",subSupplied,"KNOWN",supplied,"KNOWN",ach,"KNOWN",stepA,"KNOWN",stepB,"KNOWN"),true);
            var exFalseToTrue=SemanticResultAdapter.execution(rawFalseToTrue,session,cut(5,false),ledger);
            for(String target:List.of(plain,supplied,depPlain)) {
                assertNull(row(exFalseToTrue,target).get("executionStatus"));
                assertEquals("FULFILLED",org.tzi.use.plugins.jacamo.ui.use4goal.state.RuntimeStateViews.qualifiedStatus(org.jacamo.bridge.contract.CanonicalJson.object(exFalseToTrue),row(exFalseToTrue,target)));
                assertEquals("Complete",org.tzi.use.plugins.jacamo.ui.use4goal.conformance.PresentationResults.agentResult("FULFILLED",Objects.toString(row(exFalseToTrue,target).get("executionQuality"),"")));
            }
        }
    }
    private static RuntimeEngineSessionOwner.Cut cut(int version,boolean lost){return new RuntimeEngineSessionOwner.Cut("owner",new RuntimeEngineSessionOwner.Identity("run",1,"revision","program","profile"),1,version,"boundary"+version,"hash"+version,null,List.of(),true,lost,List.of(),null,0,Map.of(),List.of());}
    private static Map<String,Object> row(Object context,String id){var root=org.jacamo.bridge.contract.CanonicalJson.object(context);return org.jacamo.bridge.contract.CanonicalJson.object(org.jacamo.bridge.contract.CanonicalJson.object(root.get("occurrences")).get(id));}
}
