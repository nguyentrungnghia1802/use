package org.tzi.use.plugins.jacamo.ui;
import java.util.*;import javax.swing.*;import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;
class LiveGoalSummaryTest {
 @Test void selectedRequirementAndSupportingSkillRemainSeparateWithoutHidingUnknownInstances()throws Exception {
  var root=new Evaluation("Done","FULFILLED","FULFILLED","KNOWN","root proof","",true,"GOAL_TASK","FULFILLED","organization","cut-a","root");
  var completed=new Evaluation("Work","FULFILLED","FULFILLED","KNOWN_HISTORY","verified task certificate","certificate",false,"GOAL_TASK","FULFILLED","beid:v1:jason/agent/runtime-agent/example/worker/one","cut-a","work-one");
  var unavailable=new Evaluation("Work","UNAVAILABLE","UNKNOWN","DEMAND_INCOMPLETE","No demand evidence; not proof of non-applicability","",false,"GOAL_TASK","UNKNOWN","beid:v1:jason/agent/runtime-agent/example/observer/two","cut-a","work-two");
  var values=List.of(root,completed,unavailable);var rows=new LiveGoalSummary(model()).rows(values);
  assertEquals("Selected requirement",rows.getFirst().resultRole());assertEquals("Supporting",rows.get(1).resultRole());
  assertEquals("MIXED",rows.get(1).originalStatus());assertEquals("UNKNOWN",rows.get(1).status());assertEquals("1 FULFILLED, 1 UNKNOWN",rows.get(1).counts());assertEquals(2,rows.get(1).instances().size());
  SwingUtilities.invokeAndWait(()->{
   var panel=ObservedConformancePanel.liveResults(model());
   panel.setCuts(List.of(new Cut("one","trace","state",values,"INCOMPLETE","CURRENT_OBSERVED",false,"key","Requirements: KNOWN","state",false,true)));
   assertEquals("COMPLETE_OBSERVED",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
   var scope=DetachedAuthoredWorkflowTest.find(panel,"observed-requirement-scope",JLabel.class);assertTrue(scope.getText().contains("Observer::Done"));assertFalse(scope.getText().contains("Observer::Work"));
   var goals=DetachedAuthoredWorkflowTest.find(panel,"live-goal-status",JTable.class);assertEquals("Supporting",goals.getValueAt(1,3));goals.setRowSelectionInterval(1,1);
   var instances=DetachedAuthoredWorkflowTest.find(panel,"live-goal-instances",JTable.class);assertEquals(2,instances.getRowCount());assertEquals("worker",instances.getValueAt(0,0));assertEquals("observer",instances.getValueAt(1,0));assertEquals("UNKNOWN",instances.getValueAt(1,3));
   instances.setRowSelectionInterval(1,1);assertTrue(DetachedAuthoredWorkflowTest.find(panel,"live-evidence-details",JTextArea.class).getText().contains("work-two"));
   renderPanel(panel,"requirement-scope.png");
   panel.setCuts(List.of());assertEquals(0,instances.getRowCount());assertTrue(DetachedAuthoredWorkflowTest.find(panel,"live-evidence-details",JTextArea.class).getText().isEmpty());
  });
 }
 @Test void actualRecordedAgentResultKeepsTwoCertifiedResponsibilitiesAndEightUnknownSkills()throws Exception {
  byte[] bytes;try(var input=new java.util.zip.GZIPInputStream(getClass().getResourceAsStream("/semantics/recorded-agent-result.json.gz"))){bytes=input.readAllBytes();}
  var payload=org.jacamo.bridge.contract.CanonicalJson.object(org.jacamo.bridge.contract.CanonicalJson.decode(bytes));
  var result=org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult.fromMap(payload);
  assertEquals("88399046ab97842f2f493d299eb6e18dfd82b443ffd3d3c13fae05816168bff7",result.resultHash(),"Frozen real TCP cut, not synthetic all-pass input");
  var facade=new org.tzi.use.plugins.jacamo.JaCaMoFacade(){public String status(){return "recorded";}public org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult runtimeVerificationResult(){return result;}};
  var cut=new ObservedConformancePresenter(facade).capture().getLast();
  var model=new org.tzi.use.semantic.engine.frontend.IStarFrontend().compile(java.nio.file.Files.readString(java.nio.file.Path.of("examples/auction/auction.istar")),"current",org.tzi.use.semantic.engine.api.EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
  var skill=new LiveGoalSummary(model).rows(cut.evaluations()).stream().filter(r->r.id().equals("A_start_t2")).findFirst().orElseThrow();
  assertEquals("2 FULFILLED, 8 UNKNOWN",skill.counts());assertEquals("Supporting",skill.resultRole());assertEquals("UNKNOWN",skill.status());
  assertTrue(skill.instances().stream().filter(i->i.status().equals("FULFILLED")).allMatch(i->org.jacamo.bridge.contract.BridgeEntityId.parse(i.player()).localId().equals("bob")));
  assertTrue(skill.instances().stream().allMatch(i->!i.player().isBlank()&&!i.context().isBlank()&&!i.occurrence().isBlank()));
  SwingUtilities.invokeAndWait(()->{
   var panel=ObservedConformancePanel.liveResults(model);panel.setCuts(List.of(cut));
   var choices=ExecutionScopeViews.choices(List.of(cut));assertEquals(2,choices.size());
   for(String context:choices.keySet()){
    panel.selectExecution(context);
    var goals=DetachedAuthoredWorkflowTest.find(panel,"live-goal-status",JTable.class);int row=-1;
    for(int i=0;i<goals.getRowCount();i++)if(goals.getValueAt(i,0).toString().split(" · ",2)[0].endsWith("::A_start_t2"))row=i;
    assertTrue(row>=0);goals.setRowSelectionInterval(row,row);
    assertEquals(5,DetachedAuthoredWorkflowTest.find(panel,"live-goal-instances",JTable.class).getRowCount());
    assertEquals("INCOMPLETE",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText(),"This historical receipt predates scoped native OCL results and cannot certify a current per-execution view");
   }
   renderPanel(panel,"recorded-agent-scope.png");
  });
 }
 static void renderPanel(JPanel panel,String name){
  var owner=new JDialog((java.awt.Frame)null);
  try{owner.add(panel);owner.setSize(1400,900);owner.addNotify();owner.validate();RuntimeStateViewsTest.render(panel,name);}catch(Exception e){throw new RuntimeException(e);}finally{owner.dispose();}
 }
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
