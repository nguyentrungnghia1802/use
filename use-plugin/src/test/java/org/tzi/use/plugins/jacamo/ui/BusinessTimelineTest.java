package org.tzi.use.plugins.jacamo.ui;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;
class BusinessTimelineTest {
 @Test void diagnosticOnlyCutsDoNotBecomeDomainStatesButFailureButtonStillOpensExactAuditCut()throws Exception {
  javax.swing.SwingUtilities.invokeAndWait(()->{
   var input=new ArrayList<Cut>();
   for(int n=0;n<5;n++)input.add(new Cut("run:"+n,"audit "+n,"native state "+n,n==1?List.of(new Evaluation("Invariant","false","FAIL","KNOWN","self / exact witness")):List.of(),"INCOMPLETE",n==2?"STALE":"CURRENT_OBSERVED",n==1,n<3?"opened":"closed","","domain",false));
   var summary=BusinessTimeline.summarize(input);assertEquals(2,summary.size());assertTrue(summary.stream().noneMatch(Cut::failed));
   var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());panel.setCuts(input);
   var button=DetachedAuthoredWorkflowTest.find(panel,"observed-failure",javax.swing.JButton.class);assertTrue(button.isEnabled());button.doClick();
   assertEquals("run:1",panel.selectedCut().id());assertTrue(panel.selectedCut().failed());assertEquals("native state 1",panel.selectedCut().state());
   var toggle=DetachedAuthoredWorkflowTest.find(panel,"observed-details",javax.swing.JCheckBox.class);assertTrue(toggle.isSelected());toggle.doClick();assertFalse(toggle.isSelected());assertEquals(2,((javax.swing.JComboBox<?>)components(panel).stream().filter(c->c instanceof javax.swing.JComboBox).findFirst().orElseThrow()).getItemCount());
  });
 }
 @Test void xmlDomainPolicyExcludesPerPlayerStepNoiseButKeepsFailuresAndAuditInput() {
  var input=new ArrayList<Cut>();
  for(int n=0;n<500;n++) {
   var success=new Evaluation("Task","FULFILLED","FULFILLED","KNOWN_HISTORY","context=c; occurrence="+n+"; player="+n+"; raw propagated=FULFILLED");
   input.add(new Cut("run:"+n,"trace","full state",List.of(success),"INCOMPLETE","CURRENT_OBSERVED",false,n<100?"created":n<400?"running":"closed","","business projection",false));
  }
  assertEquals(3,BusinessTimeline.summarize(input).size());assertEquals(500,input.size());assertEquals("run:499",BusinessTimeline.summarize(input).getLast().id());
  input.add(new Cut("run:500","trace","state",List.of(new Evaluation("Invariant","false","FAIL","KNOWN","object")),"INCOMPLETE","CURRENT_OBSERVED",true,"closed","","projection",false));
  assertTrue(BusinessTimeline.summarize(input).getLast().failed());
 }
 Cut cut(int n,String domain,List<Evaluation> rows,String freshness){return new Cut("run:"+n,"boundary "+n,"state",rows,"INCOMPLETE",freshness,rows.stream().anyMatch(r->r.status().equals("FAIL")),domain,"");}
 @Test void thousandsOfDuplicateEvidenceCutsKeepDomainMilestonesAndExactLatestWithoutDeletingInput() {
  var audit=new ArrayList<Cut>();for(int n=0;n<1284;n++)audit.add(cut(n,n<100?"created":n<900?"opened":"closed",List.of(),"CURRENT_OBSERVED"));
  var summary=BusinessTimeline.summarize(audit);assertEquals(3,summary.size());assertEquals("run:1283",summary.getLast().id());assertEquals(1284,audit.size());
 }
 @Test void failuresQualityLossAndExactOccurrenceProgressCannotDisappearBehindUnchangedDomain() {
  var fulfilled=new Evaluation("Goal","FULFILLED","FULFILLED","KNOWN_DERIVED","context=c; occurrence=a; player=p; raw propagated=FULFILLED");
  var failed=new Evaluation("Invariant","false","FAIL","KNOWN","invocation=one");
  var input=List.of(cut(0,"same",List.of(),"CURRENT_OBSERVED"),cut(1,"same",List.of(fulfilled),"CURRENT_OBSERVED"),cut(2,"same",List.of(failed),"CURRENT_OBSERVED"),cut(3,"same",List.of(),"STALE"),cut(4,"same",List.of(),"CURRENT_OBSERVED"));
  assertEquals(5,BusinessTimeline.summarize(input).size());
 }
 @Test void panelShowsCompactProjectionAndCanInspectOneIntervalOrAllAuditBoundaries()throws Exception {
  javax.swing.SwingUtilities.invokeAndWait(()->{
   var panel=new ObservedConformancePanel();var input=new ArrayList<Cut>();
   for(int n=0;n<30;n++)input.add(new Cut("run:"+n,"trace","full audit state",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false,n<10?"initial":n<20?"opened":"closed","Requirements: KNOWN","small selected projection"));
   panel.setCuts(input);var combo=(javax.swing.JComboBox<?>)components(panel).stream().filter(c->c instanceof javax.swing.JComboBox).findFirst().orElseThrow();assertEquals(3,combo.getItemCount());
   assertTrue(components(panel).stream().filter(c->c instanceof javax.swing.JTextArea).map(c->((javax.swing.JTextArea)c).getText()).anyMatch(t->t.equals("small selected projection")));
   ((javax.swing.JButton)components(panel).stream().filter(c->"observed-interval-details".equals(c.getName())).findFirst().orElseThrow()).doClick();
   assertEquals(10,combo.getItemCount());assertEquals("run:29",panel.selectedCut().id());
   var details=(javax.swing.JCheckBox)components(panel).stream().filter(c->"observed-details".equals(c.getName())).findFirst().orElseThrow();details.doClick();assertEquals(3,combo.getItemCount());details.doClick();assertEquals(30,combo.getItemCount());
   assertTrue(components(panel).stream().filter(c->c instanceof javax.swing.JTextArea).map(c->((javax.swing.JTextArea)c).getText()).anyMatch(t->t.equals("full audit state")));
  });
 }

 @Test void domainSummarySkipsPendingBootstrapAndIncompleteAuditOnlyCutsEvenAtTheTail() {
  var initial=new Cut("pending","pending","!new Infrastructure('internal')",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false,"","","",false);
  var first=new Cut("world1","verified","full image",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false,"one","","selected one",false);
  var next=new Cut("world2","verified","full image",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false,"two","","selected two",false);
  assertEquals(List.of(),BusinessTimeline.summarize(List.of(initial)));
  assertEquals(List.of(first,next),BusinessTimeline.summarize(List.of(initial,first,next,initial)));
 }
 @Test void knownRemovalOfLastSelectedObjectRemainsARealDomainState() {
  var empty=new Cut("empty","baseline","!new Internal('x')",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false,"empty-hash","","selected empty",false,true,true);
  var created=new Cut("created","source","native",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false,"object-hash","","selected object",false,true,false);
  var removed=new Cut("removed","source","native",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false,"empty-hash","","selected empty",false,true,true);
  assertTrue(BusinessTimeline.summarize(List.of(empty)).isEmpty());assertEquals(List.of(created,removed),BusinessTimeline.summarize(List.of(empty,created,removed)));
 }
 private List<java.awt.Component> components(java.awt.Container parent){var result=new ArrayList<java.awt.Component>();for(var child:parent.getComponents()){result.add(child);if(child instanceof java.awt.Container nested)result.addAll(components(nested));}return result;}
}
