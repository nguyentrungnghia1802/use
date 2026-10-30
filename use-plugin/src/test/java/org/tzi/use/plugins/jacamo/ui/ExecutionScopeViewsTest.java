package org.tzi.use.plugins.jacamo.ui;
import java.util.*;import javax.swing.*;import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;
class ExecutionScopeViewsTest {
 static Map<String,Object> context(String id,String entity){return Map.of("context",id,"executionConstraints",List.of(check(entity,"PASS")),"scopeView",Map.of("version","BOUND_EXECUTION_VIEW/2.0","complete",true,"owners",Map.of(entity,Map.of("type","Parcel")),"entities",Map.of("Parcel:"+entity,Map.of("identity","source:"+entity,"attributes",Map.of("id","'"+entity+"'")))));}
 static Map<String,Object> check(String entity,String status){return Map.of("id","Rule","kind","INVARIANT","contextClass","Parcel","contextObject",entity,"verdict",status,"backendValue",status.equals("PASS")?"true":"false","expression","self.valid","diagnostic","EXACT_SCOPE","witness",Map.of("owner",entity));}
 static Evaluation goal(String scope,String status){return new Evaluation("Done","UNAVAILABLE",status,"KNOWN_HISTORY","exact evidence","retained",true,"GOAL_TASK",status,"player",scope,"occurrence:"+scope);}
 static Cut cut(String id,String first,String second,List<Evaluation> rows){
  return new Cut(id,"trace","native",rows,"INCOMPLETE","CURRENT_OBSERVED",false,id,"Requirements: KNOWN","state",false,true,false,
   Map.of("originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","contexts",List.of(context("one","x"),context("two","y"))),
    "presentationState",Map.of("qualifiedValues",true,"objects",Map.of("Parcel:x",Map.of("id","'x'","state",first),"Parcel:y",Map.of("id","'y'","state",second))),
    "requirementInventory",Map.of("selectedIntentionalElements",List.of("Done"))));
 }
 @Test void independentRequiredGoalsCannotBorrowCompletionAcrossContexts(){
  var all=cut("1","open","done",List.of(goal("one","PENDING"),goal("two","FULFILLED")));
  var one=ExecutionScopeViews.project(all,"one");var two=ExecutionScopeViews.project(all,"two");
  assertEquals("PENDING",ExecutionScopeViews.goalVerdict(one));assertEquals("COMPLETE_OBSERVED",ExecutionScopeViews.goalVerdict(two));
  assertEquals(1,one.evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")).count());assertEquals("one",one.evaluations().getFirst().context());
  assertEquals("INCOMPLETE",ExecutionScopeViews.goalVerdict(ExecutionScopeViews.project(all,"unknown")));
  assertEquals("VIOLATION",ExecutionScopeViews.goalVerdict(ExecutionScopeViews.project(cut("2","x","y",List.of(goal("one","VIOLATED"),goal("two","FULFILLED"))),"one")));
 }
 @Test void entityStateAndMilestonesAreScopedButNativeAuditAndGlobalOclRemainExact(){
  var failure=new Evaluation("Invariant","false","FAIL","KNOWN","evidence","",true,"INVARIANT_BODY","","","global","invocation");
  var a=cut("1","open","open",List.of(goal("one","FULFILLED"),goal("two","PENDING"),failure));
  var b=cut("2","closed","open",a.evaluations());var c=cut("3","closed","done",a.evaluations());
  var first=List.of(a,b,c).stream().map(x->ExecutionScopeViews.project(x,"one")).toList();
  assertNotEquals(first.get(0).businessKey(),first.get(1).businessKey());assertEquals(first.get(1).businessKey(),first.get(2).businessKey());
  var second=List.of(a,b,c).stream().map(x->ExecutionScopeViews.project(x,"two")).toList();
  assertEquals(second.get(0).businessKey(),second.get(1).businessKey());assertNotEquals(second.get(1).businessKey(),second.get(2).businessKey());
  assertEquals("native",first.getFirst().state());assertFalse(first.getFirst().evaluations().contains(failure),"Other/global constraint rows cannot replace native per-execution checks");
  assertTrue(first.getFirst().requirements().startsWith("Selected execution"));
  assertEquals("COMPLETE_OBSERVED",ExecutionScopeViews.goalVerdict(first.getFirst()),"Goal completion is explicitly separate from retained global constraint FAIL");
  assertFalse(first.getFirst().businessState().contains("Parcel:y"));
  assertEquals(Set.of("Parcel:x"),ExecutionScopeViews.object(ExecutionScopeViews.object(first.getFirst().visualEvidence().get("presentationState")).get("objects")).keySet());
  assertTrue(ExecutionScopeViews.choices(List.of(a)).get("one").contains("'x'"));
 }
 @Test void executionSelectorKeepsTwoViewsAndChangingSelectionKeepsExactContextAndLatestState()throws Exception {
  var model=LiveGoalSummaryTest.model();var a=cut("1","open","open",List.of(goal("one","PENDING"),goal("two","FULFILLED")));
  var b=cut("2","closed","open",List.of(goal("one","FULFILLED"),goal("two","FULFILLED")));
  SwingUtilities.invokeAndWait(()->{
   var panel=ObservedConformancePanel.liveResults(model);panel.setCuts(List.of(a,b));
   var selector=DetachedAuthoredWorkflowTest.find(panel,"execution-scope-selector",JComboBox.class);assertEquals(2,selector.getItemCount());assertNotEquals("",selector.getSelectedItem());
   panel.selectExecution("one");assertEquals("one",ExecutionScopeViews.selected(panel.selectedCut()));
   assertTrue(panel.selectedCut().evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")).allMatch(r->r.context().equals("one")));
   panel.selectExecution("two");assertEquals("two",ExecutionScopeViews.selected(panel.selectedCut()));
   panel.selectExecution("");assertEquals("two",ExecutionScopeViews.selected(panel.selectedCut()),"Aggregate selection is no longer available");
  });
 }
 @Test void privateConstraintFailureDoesNotLeakAndStructuralMembershipIsNotParticipation()throws Exception {
  var original=cut("1","x","y",List.of(goal("one","FULFILLED"),goal("two","FULFILLED")));
  var evidence=new TreeMap<String,Object>(original.visualEvidence());
  var first=new TreeMap<String,Object>(context("one","x"));first.put("executionConstraints",List.of(check("x","FAIL")));
  var view=new TreeMap<String,Object>(ExecutionScopeViews.object(first.get("scopeView")));
  view.put("owners",Map.of("x",Map.of(),"r1",Map.of()));view.put("participation",List.of(Map.of("role","r1","scope","one","interval","epoch1")));
  first.put("scopeView",view);
  evidence.put("originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","contexts",List.of(first,context("two","y"))));
  evidence.put("presentationState",Map.of("qualifiedValues",true,"objects",Map.of("Parcel:x",Map.of("id","'x'"),"Parcel:y",Map.of("id","'y'"),"Role:r1",Map.of(),"Role:r2",Map.of()),"links",List.of()));
  var source=new Cut("1","trace","native",original.evaluations(),"INCOMPLETE","CURRENT_OBSERVED",true,"key","global violation","state",false,true,false,evidence);
  var one=ExecutionScopeViews.project(source,"one");var two=ExecutionScopeViews.project(source,"two");
  assertTrue(one.failed());assertFalse(two.failed());assertTrue(two.evaluations().stream().noneMatch(r->r.status().equals("FAIL")));
  var projection=ExecutionScopeViews.object(one.visualEvidence().get("presentationState"));
  var links=(List<?>)projection.get("links");assertEquals(1,links.size());assertFalse(links.toString().contains("r2"));
  SwingUtilities.invokeAndWait(()->{var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());panel.setCuts(List.of(source));
   panel.selectExecution("one");assertEquals("VIOLATION",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
   panel.selectExecution("two");assertEquals("COMPLETE_OBSERVED",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
  });
 }

 @Test void otherEntityUndefinedFieldsCannotHideQualifiedSelectedEntityState(){
  var original=cut("1","valid","undefined",List.of(goal("one","FULFILLED"),goal("two","UNKNOWN")));
  var evidence=new TreeMap<String,Object>(original.visualEvidence());
  var projection=new TreeMap<String,Object>(ExecutionScopeViews.object(evidence.get("presentationState")));projection.put("qualifiedValues",false);
  projection.put("objectQuality",Map.of("Parcel:x",true,"Parcel:y",false));evidence.put("presentationState",projection);
  var source=new Cut("1","trace","native",original.evaluations(),"INCOMPLETE","CURRENT_OBSERVED",false,"","known","",false,true,false,evidence);
  var selected=ExecutionScopeViews.project(source,"one");assertTrue(selected.businessState().contains("Parcel:x"));assertFalse(selected.businessKey().isBlank());
  assertEquals(Boolean.TRUE,ExecutionScopeViews.object(selected.visualEvidence().get("presentationState")).get("qualifiedValues"));
  assertEquals(Boolean.FALSE,ExecutionScopeViews.object(ExecutionScopeViews.project(source,"two").visualEvidence().get("presentationState")).get("qualifiedValues"));
 }

 @Test void authenticAlternatingUnavailableCutsDoNotCreateEntityMilestones()throws Exception {
  byte[] raw;try(var stream=new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(java.nio.file.Path.of("src/test/resources/semantics/scoped-timeline-original-projections.json.gz")))){raw=stream.readAllBytes();}
  var cuts=new ArrayList<Cut>();
  for(Object item:(List<?>)org.jacamo.bridge.contract.CanonicalJson.decode(raw)){
   var sample=ExecutionScopeViews.object(item);
   cuts.add(new Cut(sample.get("id").toString(),"frozen exact cut","native",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false,"raw","","",false,true,false,
    Map.of("originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","contexts",sample.get("contexts")),"presentationState",sample.get("presentationState"))));
  }
  var choices=ExecutionScopeViews.choices(cuts);assertEquals(2,choices.size());
  for(String id:choices.keySet()){
   var timeline=BusinessTimeline.summarize(cuts.stream().map(c->ExecutionScopeViews.project(c,id)).toList());
   assertFalse(timeline.isEmpty());assertTrue(timeline.size()<=30,"Diagnostic availability must not inflate business states: "+timeline.size());
  }
 }

}
