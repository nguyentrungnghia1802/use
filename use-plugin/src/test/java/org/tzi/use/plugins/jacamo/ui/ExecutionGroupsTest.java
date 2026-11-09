package org.tzi.use.plugins.jacamo.ui;

import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionGroupsTest {
    static String scheme(String incarnation){return new BridgeEntityId("moise","organisation","scheme-board","team","delivery",incarnation).canonical();}
    static Map<String,Object> context(String id,String definition,String column,String ref,boolean domain){
        var bindings=new TreeMap<String,Object>();bindings.put("requestDescription","item-17");if(!ref.isEmpty())bindings.put(column,ref);
        return Map.of("context",id,"scopeDefinition",definition,"scopeView",Map.of("version","BOUND_EXECUTION_VIEW/2.0","definition",definition,
                "complete",true,"domainBound",domain,"bindings",bindings,"owners",Map.of("Worker:w",Map.of("type","Worker")),
                "entities",domain?Map.of("Parcel:p",Map.of("attributes",Map.of("id","'item-17'"))):Map.of()),"executionConstraints",List.of());
    }
    static Evaluation goal(String target,String scope,String status,boolean required,String player){return new Evaluation(target,status,status,
            status.equals("NOT_ASSIGNED")?"SOURCE_NOT_ASSIGNED":"KNOWN",scope+":"+player,"",required,"GOAL_TASK",status,player,scope,scope+":"+target+":"+player);}
    static Cut cut(String id,List<Map<String,Object>> contexts,List<Evaluation> rows){
        return new Cut(id,"frozen","native",rows,"COMPLETE","CURRENT_OBSERVED",false,"business","requirements","state",true,true,false,
                Map.of("originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","contexts",contexts),"requirementInventory",Map.of("selectedIntentionalElements",List.of("Prepare","Finish"),
                    "scopeSelectedIntentionalElements",Map.of("bootstrap",List.of("Prepare"),"delivery",List.of("Finish"))),
                    "presentationState",Map.of("qualifiedValues",true,"objects",Map.of("Worker:w",Map.of("name","worker"),"Parcel:p",Map.of("id","'item-17'")),
                        "objectQuality",Map.of("Worker:w",true,"Parcel:p",true),"links",List.of())));
    }
    @Test void qualifiedSourceIdentityJoinsPhasesWithRenamedColumnsAndKeepsNativeOwners(){
        var source=cut("final",List.of(context("init","bootstrap","createdCoordination",scheme("one"),false),context("run","delivery","workReference",scheme("one"),true)),
                List.of(goal("Prepare","init","FULFILLED",true,"worker"),goal("Finish","run","FULFILLED",true,"team"),goal("Skill","run","VIOLATED",false,"other")));
        var groups=ExecutionGroups.of(List.of(source));assertEquals(1,groups.ids().size());assertEquals(groups.canonical("init"),groups.canonical("run"));
        var projected=groups.project(source,"run");assertEquals(Set.of("init","run"),new HashSet<>((List<?>)projected.visualEvidence().get("selectedExecutionContexts")));
        assertEquals(source.visualEvidence().get("originalEngine"),projected.visualEvidence().get("originalEngine"));
        assertTrue(projected.evaluations().containsAll(source.evaluations()));assertEquals("COMPLETE_OBSERVED",ExecutionScopeViews.goalVerdict(projected));
        assertEquals(Set.of("Prepare","Finish"),new HashSet<>((List<?>)ExecutionScopeViews.object(projected.visualEvidence().get("requirementInventory")).get("selectedIntentionalElements")));
        assertTrue(projected.businessState().contains("bootstrap"));assertTrue(projected.businessState().contains("delivery"));
    }
    @Test void repeatedDisplayNamesIncarnationsAndUnqualifiedStringsNeverJoin(){
        var source=cut("cut",List.of(context("a","bootstrap","x",scheme("one"),false),context("b","delivery","y",scheme("two"),true),
                context("c","bootstrap","x","delivery",false),context("d","delivery","x","delivery",true)),List.of());
        assertEquals(4,ExecutionGroups.of(List.of(source)).ids().size());
    }
    @Test void initialFailureBeforeSchemeCreationStaysVisibleWithoutBorrowingFutureState(){
        var before=cut("before",List.of(context("init","bootstrap","x","",false)),List.of(goal("Prepare","init","VIOLATED",true,"worker")));
        var later=cut("later",List.of(context("init","bootstrap","x",scheme("one"),false),context("run","delivery","y",scheme("one"),true)),List.of(goal("Prepare","init","VIOLATED",true,"worker"),goal("Finish","run","FULFILLED",true,"team")));
        var groups=ExecutionGroups.of(List.of(before,later));var projected=groups.project(before,"init");
        assertEquals(List.of("init"),projected.visualEvidence().get("selectedExecutionContexts"));
        assertTrue(projected.evaluations().stream().noneMatch(r->r.element().equals("Finish")));assertEquals("VIOLATION",ExecutionScopeViews.goalVerdict(projected));
        assertEquals(1,ExecutionGroups.of(List.of(before)).ids().size());
    }
    @Test void mixedPopulationUsesCountsAndIndividualFailureRemainsIncomplete()throws Exception {
        var model=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar Delivery { role Worker { goal Work : Achieve condition {[ true ]} } }").model();
        var rows=List.of(goal("Work","run","FULFILLED",false,"a"),goal("Work","run","FULFILLED",false,"b"),goal("Work","run","FULFILLED",false,"c"),goal("Work","run","VIOLATED",false,"d"),goal("Work","run","NOT_ASSIGNED",false,"e"));
        var summary=new LiveGoalSummary(model).rows(rows).getFirst();assertEquals("3 Complete · 1 Incomplete · 1 Not assigned",summary.resultText());
        assertEquals("Incomplete",PresentationResults.agentResult(rows.get(3)));
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(model);panel.setCuts(List.of(new Cut("cut","trace","state",rows,"COMPLETE","CURRENT_OBSERVED",false)));
            var table=DetachedAuthoredWorkflowTest.find(panel,"live-goal-status",JTable.class);assertEquals(summary.resultText(),table.getValueAt(0,1));
        });
    }
}
