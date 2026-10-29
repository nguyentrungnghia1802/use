package org.tzi.use.plugins.jacamo.ui;
import java.util.*;import javax.swing.*;import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;
class LiveGoalSummaryTest {
 static org.vnu.sme.goal.dsl.istar.mm.GoalModel model(){return org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar Stable { role Observer { goal Done : Achieve condition {[ true ]} task Work pre {[ true ]} post {[ true ]} } }").model();}
 static Evaluation instance(String id,String status,String player){return new Evaluation(id,"FULFILLED",status,status.equals("UNKNOWN")?"UNAVAILABLE":"KNOWN","context=c; player="+player,"",true,"GOAL_TASK");}
 @Test void fixedModelRowsDoNotGrowWithContextsChecksOrInvocationCountAndMissingNeverBecomesFalse(){
  var summary=new LiveGoalSummary(model());assertEquals(2,summary.rows(List.of()).size());assertTrue(summary.rows(List.of()).stream().allMatch(r->r.status().equals("UNKNOWN")));
  var rows=new ArrayList<Evaluation>();rows.add(instance("Done","FULFILLED","alice"));rows.add(instance("Done","UNKNOWN","bob"));
  for(int i=0;i<100;i++)rows.add(new Evaluation("check"+i,"true","PASS","","invocation="+i,"",true,"POST"));
  var view=summary.rows(rows);assertEquals(2,view.size());assertEquals("UNKNOWN",view.getFirst().status());assertTrue(view.getFirst().detail().contains("player=bob"));
  rows.set(1,instance("Done","FULFILLED","bob"));assertEquals("FULFILLED",summary.rows(rows).getFirst().status());
  rows.set(1,instance("Done","VIOLATED","bob"));assertEquals("VIOLATED",summary.rows(rows).getFirst().status());
  rows.set(1,new Evaluation("Done","VIOLATED","VIOLATED","UNAVAILABLE","player=bob","",true,"GOAL_TASK"));assertEquals("UNKNOWN",summary.rows(rows).getFirst().status());
 }
 @Test void failedTaskExecutionStaysSeparateFromOriginalPendingAndMissingEvidence(){
  var rows=List.of(instance("Work","FULFILLED","alice"),new Evaluation("Work","PENDING","FAILED","KNOWN_HISTORY","player=bob; invocation=failed","",true,"GOAL_TASK","PENDING"));
  var row=new LiveGoalSummary(model()).rows(rows).stream().filter(r->r.id().equals("Work")).findFirst().orElseThrow();assertEquals("FAILED",row.status());assertEquals("MIXED",row.originalStatus());
  assertEquals("UNKNOWN",new LiveGoalSummary(model()).rows(List.of(new Evaluation("Work","PENDING","FAILED","UNAVAILABLE","player=bob","",true,"GOAL_TASK"))).stream().filter(r->r.id().equals("Work")).findFirst().orElseThrow().status());
 }
 @Test void liveResultsKeepStableTablesWithSelectedStateNavigation()throws Exception {
  SwingUtilities.invokeAndWait(()->{var panel=ObservedConformancePanel.liveResults(model());assertNotNull(DetachedAuthoredWorkflowTest.find(panel,"observed-previous",JButton.class));assertNotNull(DetachedAuthoredWorkflowTest.find(panel,"observed-details",JCheckBox.class));
   var goals=DetachedAuthoredWorkflowTest.find(panel,"live-goal-status",JTable.class);assertEquals(2,goals.getRowCount());
   var failure=new Evaluation("Invariant","false","FAIL","KNOWN","object=source; expression=false; witness=actual","",true,"INVARIANT");
   panel.setCuts(List.of(new Cut("run:1","trace","accepted state",List.of(instance("Done","FULFILLED","alice"),failure),"INCOMPLETE","CURRENT_OBSERVED",true)));
   assertEquals(2,goals.getRowCount());assertEquals("FULFILLED",goals.getValueAt(0,1));var checks=DetachedAuthoredWorkflowTest.find(panel,"live-ocl-checks",JTable.class);assertEquals(1,checks.getRowCount());assertEquals("FAIL",checks.getValueAt(0,1));checks.setRowSelectionInterval(0,0);assertTrue(DetachedAuthoredWorkflowTest.find(panel,"live-evidence-details",JTextArea.class).getText().contains("witness=actual"));
   panel.setCuts(List.of(new Cut("run:2","trace","state2",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false)));assertEquals(2,goals.getRowCount());assertEquals("UNKNOWN",goals.getValueAt(0,1));
  });
 }
}
