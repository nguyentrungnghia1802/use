package org.tzi.use.plugins.jacamo.ui;
import java.util.*;import javax.swing.*;import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;
class LiveGoalSummaryTest {
 @Test void evidencePanelIsRemovedAndSuccessfulTaskCertificateDoesNotBecomeAnUnknownInvocation()throws Exception {
  var model=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar Work { role Worker { goal Parent : Achieve task One > Parent pre {[ true ]} post {[ true ]} task Two > Parent pre {[ true ]} post {[ true ]} } }").model();
  var certificate=Map.<String,Object>of("policy","CERTIFIED_EXECUTION/1.0","kind","TASK","stateVersion",6,"stateHash","hash6");
  var occurrence=Map.<String,Object>of("sourceElement","Parent","elementKind","GOAL","player","worker","executionEvidence",Map.of("One",certificate));
  var context=Map.<String,Object>of("context","scope","occurrences",Map.of("parent-occ",occurrence));
  var evaluation=new Evaluation("Parent","PENDING","UNKNOWN","UNAVAILABLE","Goal still waiting for another Task","",false,"GOAL_TASK","PENDING","worker","scope","parent-occ");
  var cut=new Cut("state6","trace","native",List.of(evaluation),"INCOMPLETE","CURRENT_OBSERVED",false,"business","requirements","state",true,true,false,
    Map.of("originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","contexts",List.of(context))));
  SwingUtilities.invokeAndWait(()->{
   var panel=ObservedConformancePanel.liveResults(model);panel.setCuts(List.of(cut));
   assertNull(DetachedAuthoredWorkflowTest.find(panel.istarVerificationPanel(),"live-evidence-details",JTextArea.class));
   assertNull(DetachedAuthoredWorkflowTest.find(panel.istarVerificationPanel(),"occurrence-evidence-split",JSplitPane.class));
   var goals=DetachedAuthoredWorkflowTest.find(panel,"live-goal-status",JTable.class);assertEquals("Unknown",goals.getValueAt(0,1));goals.setRowSelectionInterval(0,0);
   var agents=DetachedAuthoredWorkflowTest.find(panel,"live-goal-instances",JTable.class);agents.setRowSelectionInterval(0,0);
   var history=DetachedAuthoredWorkflowTest.find(panel,"live-agent-occurrences",JTable.class);assertEquals(2,history.getRowCount());
   assertEquals("Occurrence",history.getValueAt(0,0));assertEquals("Unknown",history.getValueAt(0,4));
   assertTrue(history.getValueAt(1,0).toString().startsWith("Execution evidence (TASK"));assertEquals("—",history.getValueAt(1,2));assertEquals("Complete",history.getValueAt(1,4));
   assertEquals("Unknown",goals.getValueAt(0,1),"A child Task certificate never completes the parent Goal");
   assertEquals(List.of(evaluation),cut.evaluations());assertTrue(panel.selectedAuditText().contains("CERTIFIED_EXECUTION/1.0"));
  });
 }
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
   var goals=DetachedAuthoredWorkflowTest.find(panel,"live-goal-status",JTable.class);
   var tasks=DetachedAuthoredWorkflowTest.find(panel,"live-task-status",JTable.class);
   assertEquals(1,goals.getRowCount());assertEquals("Observer::Done",goals.getValueAt(0,0));assertEquals("Complete",goals.getValueAt(0,1));
   assertEquals(1,tasks.getRowCount());assertEquals("Observer::Work",tasks.getValueAt(0,0));assertEquals("Unknown",tasks.getValueAt(0,1));
   tasks.setRowSelectionInterval(0,0);
   var instances=DetachedAuthoredWorkflowTest.find(panel,"live-goal-instances",JTable.class);
   assertEquals(List.of("Agent / Player","Execution","Result"),java.util.stream.IntStream.range(0,instances.getColumnCount()).mapToObj(instances::getColumnName).toList());
   assertEquals(2,instances.getRowCount());
   assertEquals("worker",instances.getValueAt(0,0));assertEquals("cut-a",instances.getValueAt(0,1));assertEquals("Complete",instances.getValueAt(0,2));
   assertEquals("observer",instances.getValueAt(1,0));assertEquals("cut-a",instances.getValueAt(1,1));assertEquals("Unknown",instances.getValueAt(1,2));
   instances.setRowSelectionInterval(1,1);assertTrue(panel.selectedAuditText().contains("work-two"));
   renderPanel(panel,"requirement-scope.png");
   panel.setCuts(List.of());assertEquals(0,instances.getRowCount());assertTrue(panel.selectedAuditText().isEmpty());
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
    var tasks=DetachedAuthoredWorkflowTest.find(panel,"live-task-status",JTable.class);int row=-1;
    for(int i=0;i<tasks.getRowCount();i++)if(tasks.getValueAt(i,0).toString().split(" · ",2)[0].endsWith("::A_start_t2"))row=i;
    assertTrue(row>=0);tasks.setRowSelectionInterval(row,row);
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
   var goals=DetachedAuthoredWorkflowTest.find(panel,"live-goal-status",JTable.class);
   var tasks=DetachedAuthoredWorkflowTest.find(panel,"live-task-status",JTable.class);
   assertEquals(1,goals.getRowCount());assertEquals(1,tasks.getRowCount());
   var failure=new Evaluation("Invariant","false","FAIL","KNOWN","object=source; expression=false; witness=actual","",true,"INVARIANT");
   panel.setCuts(List.of(new Cut("run:1","trace","accepted state",List.of(instance("Done","FULFILLED","alice"),failure),"INCOMPLETE","CURRENT_OBSERVED",true)));
   assertEquals(1,goals.getRowCount());assertEquals(1,tasks.getRowCount());assertEquals("Complete",goals.getValueAt(0,1));var checks=DetachedAuthoredWorkflowTest.find(panel,"live-ocl-checks",JTable.class);assertEquals(1,checks.getRowCount());assertEquals("False",checks.getValueAt(0,2));checks.setRowSelectionInterval(0,0);assertTrue(panel.selectedAuditText().contains("witness=actual"));
   panel.setCuts(List.of(new Cut("run:2","trace","state2",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false)));assertEquals(1,goals.getRowCount());assertEquals(1,tasks.getRowCount());assertEquals("Unknown",goals.getValueAt(0,1));
  });
 }
 @Test void splitGoalAndTaskTablesKeepOwnerNamesAndSwitchOrientationWhenNarrow()throws Exception {
  var multiOwner=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar Disambiguated { role Buyer { goal G_shared : Achieve condition {[ true ]} task T_shared pre {[ true ]} post {[ true ]} } role Seller { goal G_second : Achieve condition {[ true ]} task T_second pre {[ true ]} post {[ true ]} } }").model();
  SwingUtilities.invokeAndWait(()->{
   var panel=ObservedConformancePanel.liveResults(multiOwner);
   var goals=DetachedAuthoredWorkflowTest.find(panel,"live-goal-status",JTable.class);
   var tasks=DetachedAuthoredWorkflowTest.find(panel,"live-task-status",JTable.class);
   var split=DetachedAuthoredWorkflowTest.find(panel,"goal-task-split",JSplitPane.class);
   assertEquals(List.of("Goal","Result"),java.util.stream.IntStream.range(0,goals.getColumnCount()).mapToObj(goals::getColumnName).toList());
   assertEquals(List.of("Task","Result"),java.util.stream.IntStream.range(0,tasks.getColumnCount()).mapToObj(tasks::getColumnName).toList());
   assertEquals(2,goals.getRowCount());assertEquals(2,tasks.getRowCount());
   assertEquals("Buyer::G_shared",goals.getValueAt(0,0));assertEquals("Seller::G_second",goals.getValueAt(1,0));
   assertEquals("Buyer::T_shared",tasks.getValueAt(0,0));assertEquals("Seller::T_second",tasks.getValueAt(1,0));
   var owner=new JDialog((java.awt.Frame)null);
   try{
    owner.add(panel);owner.setSize(1450,850);owner.addNotify();owner.validate();
    assertEquals(JSplitPane.HORIZONTAL_SPLIT,split.getOrientation(),"Wide window places Goals on left and Tasks on right");
    panel.istarVerificationPanel().setSize(450,800);panel.istarVerificationPanel().doLayout();
    assertEquals(JSplitPane.VERTICAL_SPLIT,split.getOrientation(),"Narrow window places Goals on top and Tasks on bottom");
    panel.istarVerificationPanel().setSize(1100,800);panel.istarVerificationPanel().doLayout();
    assertEquals(JSplitPane.HORIZONTAL_SPLIT,split.getOrientation());
   }finally{owner.dispose();}
  });
 }
 @Test void agentInstancesHideConfirmedNonAssignmentAndKeepMissingEvidenceFailedAndUnboundSubject()throws Exception {
  SwingUtilities.invokeAndWait(()->{
   var panel=ObservedConformancePanel.liveResults(model());
   var schemeRoot=new Evaluation("Done","FULFILLED","FULFILLED","KNOWN","scheme proof","",true,"GOAL_TASK","FULFILLED","beid:v1:moise/scheme/runtime-scheme/auctionGroup/doAuction1/inc1","ctx-1","occ-root-1");
   var unboundRoot=new Evaluation("Done","PENDING","UNKNOWN","UNAVAILABLE","missing subject","",true,"GOAL_TASK","UNKNOWN","","ctx-2","occ-root-2");
   var fulfilledAgent=new Evaluation("Work","FULFILLED","FULFILLED","KNOWN","completed proof","cert-1",false,"GOAL_TASK","FULFILLED","beid:v1:jason/agent/runtime-agent/example/alice/1","ctx-1","occ-alice");
   var failedAgent=new Evaluation("Work","PENDING","FAILED","KNOWN_HISTORY","CERTIFIED_TERMINAL_EXECUTION/1.0","cert-fail",false,"GOAL_TASK","PENDING","beid:v1:jason/agent/runtime-agent/example/bob/2","ctx-1","occ-bob");
   var confirmedUnassigned=new Evaluation("Work","PENDING","NOT_ASSIGNED","SOURCE_NOT_ASSIGNED","role has no assignment","",false,"GOAL_TASK","PENDING","beid:v1:jason/agent/runtime-agent/example/carol/3","ctx-1","occ-carol");
   var unconfirmedUnassigned=new Evaluation("Work","PENDING","NOT_ASSIGNED","UNAVAILABLE","missing source proof","",false,"GOAL_TASK","PENDING","beid:v1:jason/agent/runtime-agent/example/dave/4","ctx-1","occ-dave");
   var cut=new Cut("c1","trace","state",List.of(schemeRoot,unboundRoot,fulfilledAgent,failedAgent,confirmedUnassigned,unconfirmedUnassigned),"INCOMPLETE","CURRENT_OBSERVED",false,"key","Requirements: KNOWN","state",false,true);
   panel.setCuts(List.of(cut));
   var goals=DetachedAuthoredWorkflowTest.find(panel,"live-goal-status",JTable.class);
   var tasks=DetachedAuthoredWorkflowTest.find(panel,"live-task-status",JTable.class);
   var instances=DetachedAuthoredWorkflowTest.find(panel,"live-goal-instances",JTable.class);
   var selectedLabel=DetachedAuthoredWorkflowTest.find(panel,"selected-goal-task-label",JLabel.class);
   assertTrue(selectedLabel.getText().contains("No Goal or Task selected"));
   goals.setRowSelectionInterval(0,0);
   assertEquals(-1,tasks.getSelectedRow(),"Selecting a Goal clears Task selection");
   assertTrue(selectedLabel.getText().contains("Selected Goal: Observer::Done"));
   assertEquals(2,instances.getRowCount());
   assertEquals("Scheme: doAuction1",instances.getValueAt(0,0));assertEquals("ctx-1",instances.getValueAt(0,1));assertEquals("Complete",instances.getValueAt(0,2));
   assertEquals("Unavailable",instances.getValueAt(1,0));assertEquals("ctx-2",instances.getValueAt(1,1));assertEquals("Unknown",instances.getValueAt(1,2));
   tasks.setRowSelectionInterval(0,0);
   assertEquals(-1,goals.getSelectedRow(),"Selecting a Task clears Goal selection");
   assertTrue(selectedLabel.getText().contains("Selected Task: Observer::Work"));
   assertEquals(3,instances.getRowCount());
   assertEquals("alice",instances.getValueAt(0,0));assertEquals("Complete",instances.getValueAt(0,2));
   assertEquals("bob",instances.getValueAt(1,0));assertEquals("Incomplete",instances.getValueAt(1,2));
   assertEquals("dave",instances.getValueAt(2,0));assertEquals("Unknown",instances.getValueAt(2,2),"NOT_ASSIGNED without SOURCE_NOT_ASSIGNED evidence must stay Unknown");
   assertEquals(6,cut.evaluations().size(),"Hiding bookkeeping never deletes native evaluation evidence");
  });
 }
 @Test void multipleOccurrencesAndInvocationsRetainHistoryAndShowShortAndFullIds()throws Exception {
  String fullPlayer="beid:v1:jason/agent/runtime-agent/example/francois/incarnation987654321";
  String fullOcc1="Work_111122223333444455556666";
  String fullOcc2="Work_aaaabbbbccccddddeeeeffff";
  String fullInv1="beid:v1:jason/intention/step/auction/francois/inv1111222233334444";
  String fullInv2="beid:v1:jason/intention/step/auction/francois/inv9999888877776666";
  var occMap1=Map.<String,Object>of("sourceElement","Work","elementKind","TASK","expansionKind","SOURCE_ELEMENT","player",fullPlayer,"role","Participant_role","key",Map.of("episode","ep-first-attempt-1234567890abcdef"),"executionEvidence",Map.of("Work",Map.of("policy","CERTIFIED_TERMINAL_EXECUTION/1.0","kind","TASK_FAILED","invocation",fullInv1,"stateVersion",1,"stateHash","hash-1","demandEpisode","ep-first-attempt-1234567890abcdef","failureReason","bid_rejected")));
  var constr1=Map.<String,Object>of("id","XML_TASK_POST:Work","kind","TASK_POST","verdict","FAIL","backendValue","false","diagnostic","POST_FALSE","stateVersion",1,"invocation",fullInv1,"expression","self.accepted","witness",Map.of("targetTaskId","Work","player",fullPlayer,"occurrence",fullOcc1,"demandEpisode","ep-first-attempt-1234567890abcdef"));
  var ctx1=Map.<String,Object>of("context","ctx-auction-1","occurrences",Map.of(fullOcc1,occMap1),"executionConstraints",List.of(constr1),"scopeView",Map.of("complete",true,"entities",Map.of("Auction:a1",Map.of("attributes",Map.of("id","'a1'")))));
  var eval1=new Evaluation("Work","PENDING","FAILED","KNOWN_HISTORY","first attempt failed","cert-1",false,"GOAL_TASK","PENDING",fullPlayer,"ctx-auction-1",fullOcc1);
  var cut1=new Cut("boundary-cut-0001-long-identifier-alpha","trace 1","state 1",List.of(eval1),"COMPLETE_OBSERVED","CURRENT_OBSERVED",false,"b1","Requirements: KNOWN","Auction:a1\n  state = open\n",false,true,false,Map.of("originalEngine",Map.of("identity","owner-1","evaluated","PINNED_NATIVE_CUT","contexts",List.of(ctx1)),"presentationState",Map.of("qualifiedValues",true,"objects",Map.of("Auction:a1",Map.of("id","'a1'","state","open")))));

  var occMap2=Map.<String,Object>of("sourceElement","Work","elementKind","TASK","expansionKind","SOURCE_ELEMENT","player",fullPlayer,"role","Participant_role","key",Map.of("episode","ep-second-attempt-fedcba0987654321"),"executionEvidence",Map.of("Work",Map.of("policy","CERTIFIED_EXECUTION/1.0","kind","TASK_COMPLETED","invocation",fullInv2,"stateVersion",2,"stateHash","hash-2","demandEpisode","ep-second-attempt-fedcba0987654321")));
  var constr2=Map.<String,Object>of("id","XML_TASK_POST:Work","kind","TASK_POST","verdict","PASS","backendValue","true","diagnostic","POST_TRUE","stateVersion",2,"invocation",fullInv2,"expression","self.accepted","witness",Map.of("targetTaskId","Work","player",fullPlayer,"occurrence",fullOcc2,"demandEpisode","ep-second-attempt-fedcba0987654321"));
  var ctx2=Map.<String,Object>of("context","ctx-auction-1","occurrences",Map.of(fullOcc2,occMap2),"executionConstraints",List.of(constr2),"scopeView",Map.of("complete",true,"entities",Map.of("Auction:a1",Map.of("attributes",Map.of("id","'a1'")))));
  var eval2=new Evaluation("Work","FULFILLED","FULFILLED","KNOWN","second attempt succeeded","cert-2",false,"GOAL_TASK","FULFILLED",fullPlayer,"ctx-auction-1",fullOcc2);
  var cut2=new Cut("boundary-cut-0002-long-identifier-beta","trace 2","state 2",List.of(eval2),"COMPLETE_OBSERVED","CURRENT_OBSERVED",false,"b2","Requirements: KNOWN","Auction:a1\n  state = closed\n",false,true,false,Map.of("originalEngine",Map.of("identity","owner-1","evaluated","PINNED_NATIVE_CUT","contexts",List.of(ctx2)),"presentationState",Map.of("qualifiedValues",true,"objects",Map.of("Auction:a1",Map.of("id","'a1'","state","closed")))));

  SwingUtilities.invokeAndWait(()->{
   var panel=ObservedConformancePanel.liveResults(model());
   panel.setCuts(List.of(cut1,cut2));
   String initialVerdict=DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText();
   var tasks=DetachedAuthoredWorkflowTest.find(panel,"live-task-status",JTable.class);
   var instances=DetachedAuthoredWorkflowTest.find(panel,"live-goal-instances",JTable.class);
   var occurrences=DetachedAuthoredWorkflowTest.find(panel,"live-agent-occurrences",JTable.class);
   assertNull(DetachedAuthoredWorkflowTest.find(panel,"live-evidence-details",JTextArea.class));

   tasks.setRowSelectionInterval(0,0);
   assertEquals(1,instances.getRowCount(),"Multiple occurrences/invocations of the same agent in the same execution are grouped under one agent row");
   assertEquals("francois",instances.getValueAt(0,0));
   assertEquals("Complete",instances.getValueAt(0,2));
   instances.setRowSelectionInterval(0,0);
   assertTrue(occurrences.getRowCount()>=4,"Both occurrences and both invocations across the execution prefix must be retained without losing history");
   assertTrue(occurrences.getValueAt(0,1).toString().startsWith("Work#11112222"),"Occurrence column shows short ID");
   assertEquals("—",occurrences.getValueAt(0,2),"Occurrence row keeps invocation distinct (empty)");
   assertTrue(occurrences.getValueAt(1,2).toString().contains("francois#inv11112"),"Invocation row shows short invocation ID");
   assertNotEquals(occurrences.getValueAt(1,1),occurrences.getValueAt(1,2),"Occurrence and Invocation are never conflated");
   assertNotEquals(occurrences.getValueAt(1,2),occurrences.getValueAt(1,3),"Invocation and Checkpoint are never conflated");

   String fullText=panel.selectedAuditText();
   assertTrue(fullText.contains(fullPlayer),"Detail view shows full player BridgeEntityId");
   assertTrue(fullText.contains(fullOcc1)&&fullText.contains(fullOcc2),"Detail view shows both full occurrence IDs");
   assertTrue(fullText.contains(fullInv1)&&fullText.contains(fullInv2),"Detail view shows both full invocation IDs");
   assertTrue(fullText.contains("boundary-cut-0001-long-identifier-alpha")&&fullText.contains("boundary-cut-0002-long-identifier-beta"),"Detail view shows full checkpoint boundary IDs");

   occurrences.setRowSelectionInterval(1,1);
   assertTrue(panel.selectedAuditText().contains("--- Selected Record ---"));
   assertTrue(panel.selectedAuditText().contains("bid_rejected"));

   // Updating cuts preserves selected Task, selected Agent, and selected Occurrence/Invocation without changing verdict
   panel.setCuts(List.of(cut1,cut2));
   assertEquals(0,tasks.getSelectedRow());
   assertEquals(0,instances.getSelectedRow());
   assertEquals(1,occurrences.getSelectedRow());
   assertEquals(initialVerdict,DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText(),"Selecting rows must never alter system verdict");
  });
 }
}
