package org.tzi.use.plugins.jacamo.ui;

import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;

class PopulationOverviewTest {
    static org.vnu.sme.goal.dsl.istar.mm.GoalModel model(){
        var compiled=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("""
          istar Shipping {
            agent Office { goal Done : Achieve }
            role Carrier { goal Deliver : Achieve }
            role Skills {
              Skills participates-in Carrier
              goal Able : Achieve
              task Collect > Able pre {[ true ]} post {[ true ]}
              task HandOver > Able pre {[ true ]} post {[ true ]}
            }
            depend Office.Done -> goal shipment -> Carrier.Deliver
          }
          """,org.tzi.use.semantic.engine.api.EngineSemanticsProfile.ROLE_SPECIALIZATION_V1.validationPolicy());
        assertTrue(compiled.ok(),compiled.errors().toString());return compiled.model();
    }
    static Map<String,Object> mapping(String policy){return Map.of("dependencyPopulations",Map.of("shipment",policy),"goalRealizations",Map.of("Deliver",Map.of("source","Able")));}
    static Evaluation row(String element,String context,String player,String status){return ExecutionGroupsTest.goal(element,context,status,false,player);}
    static LiveGoalSummary.Row find(LiveGoalSummary summary,List<Evaluation> rows,String id){return summary.rows(rows).stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();}

    @Test void explicitAnyReachesTheMappedViewAndItsTasksWithoutChangingIndividuals(){
        var goals=model();String before=org.tzi.use.semantic.engine.api.OwnedGoalModel.fingerprint(goals);
        var rows=new ArrayList<Evaluation>();for(String element:List.of("Deliver","Able","HandOver")){
            rows.add(row(element,"run","one","FULFILLED"));rows.add(row(element,"run","two","VIOLATED"));rows.add(row(element,"run","other","NOT_ASSIGNED"));
        }
        var original=List.copyOf(rows);var summary=new LiveGoalSummary(goals,mapping("ANY"));
        for(String element:List.of("Deliver","Able","HandOver")){
            var result=find(summary,rows,element);assertEquals("Complete",result.resultText());assertEquals(2,result.displayInstances().size());assertEquals(3,result.instances().size());
            assertFalse(result.detail().contains("NOT_ASSIGNED"));assertFalse(result.resultText().contains("1 "));
            assertEquals("Incomplete",PresentationResults.agentResult(result.displayInstances().get(1)));
        }
        assertEquals(original,rows);assertEquals(before,org.tzi.use.semantic.engine.api.OwnedGoalModel.fingerprint(goals));
    }
    @Test void allAndUnknownFollowTheDeclaredPopulationRule(){
        var any=new LiveGoalSummary(model(),mapping("ANY"));var all=new LiveGoalSummary(model(),mapping("ALL"));
        var mixed=List.of(row("Able","run","one","FULFILLED"),row("Able","run","two","VIOLATED"));
        assertEquals("Complete",find(any,mixed,"Able").resultText());assertEquals("Incomplete",find(all,mixed,"Able").resultText());
        var unresolved=List.of(row("Able","run","one","VIOLATED"),row("Able","run","two","UNKNOWN"));
        assertEquals("Unknown",find(any,unresolved,"Able").resultText());
        assertEquals("Incomplete",find(any,List.of(row("Able","run","one","VIOLATED"),row("Able","run","two","VIOLATED")),"Able").resultText());
    }
    @Test void anyCannotBorrowCompletionFromAnotherExecution(){
        var summary=new LiveGoalSummary(model(),mapping("ANY"));
        var rows=List.of(row("Able","first","one","FULFILLED"),row("Able","first","two","VIOLATED"),row("Able","second","one","VIOLATED"),row("Able","second","two","VIOLATED"));
        assertEquals("Incomplete",find(summary,rows,"Able").resultText());
        assertEquals("Unknown",find(summary,List.of(rows.getFirst(),row("Able","second","two","UNKNOWN")),"Able").resultText());
    }
    @Test void uiHidesConfirmedNonAssignmentButRetainsFailureAndRawRows()throws Exception {
        var rows=List.of(row("Able","run","one","FULFILLED"),row("Able","run","two","VIOLATED"),row("Able","run","other","NOT_ASSIGNED"));
        var cut=new Cut("cut","trace","native",rows,"COMPLETE","CURRENT_OBSERVED",false);
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(model(),mapping("ANY"));panel.setCuts(List.of(cut));
            var goals=DetachedAuthoredWorkflowTest.find(panel,"live-goal-status",JTable.class);int selected=-1;
            for(int i=0;i<goals.getRowCount();i++)if(goals.getValueAt(i,0).equals("Skills::Able"))selected=i;
            assertTrue(selected>=0);assertEquals("Complete",goals.getValueAt(selected,1));goals.setRowSelectionInterval(selected,selected);
            var agents=DetachedAuthoredWorkflowTest.find(panel,"live-goal-instances",JTable.class);assertEquals(2,agents.getRowCount());
            assertEquals("Incomplete",agents.getValueAt(1,2));
            for(int i=0;i<agents.getRowCount();i++)assertNotEquals("Not assigned",agents.getValueAt(i,2));
            assertEquals(3,panel.selectedCut().evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")).count());
        });
    }
}
