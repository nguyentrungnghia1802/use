package org.tzi.use.plugins.jacamo.ui;
import java.util.*;import javax.swing.*;import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import static org.junit.jupiter.api.Assertions.*;
class RestoredStateBrowserTest {
 @Test void detachedResultsRestoreStateNavigationAndBottomVerdict()throws Exception{
  SwingUtilities.invokeAndWait(()->{var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());assertNotNull(DetachedAuthoredWorkflowTest.find(panel,"observed-previous",JButton.class));assertNotNull(DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class));});
 }
 @Test void dependencyIsNotAnExtraMarkedActorElementInTheOriginalEngine(){
  var model=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar Dependencies { role Observer { goal Done : Achieve condition {[ true ]} } role Other { goal Go : Achieve condition {[ true ]} } depend Observer.Done -> goal Link -> Other.Go }").model();
  assertEquals(2,new LiveGoalSummary(model).rows(List.of()).size(),"Original marking indexes actor Goal/Task elements; a dependum is a connection, not an extra marked occurrence");
 }
 @Test void selectedStateKeepsOriginalHistorySeparateFromMissingCurrentAndRestoresFooter()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());
   var historical=new ObservedConformancePanel.Evaluation("Done","FULFILLED","UNKNOWN","CURRENT_STEP_MARKER_MISSING","player=alice","",true,"GOAL_TASK","FULFILLED");
   var first=new ObservedConformancePanel.Cut("run:1","first","accepted state ONE",List.of(historical),"INCOMPLETE","CURRENT_OBSERVED",false);
   var second=new ObservedConformancePanel.Cut("run:2","second","accepted state TWO",List.of(new ObservedConformancePanel.Evaluation("Invariant","false","FAIL","","actual witness")),"COMPLETE","CURRENT_OBSERVED",true);
   panel.setCuts(List.of(first,second));assertEquals("VIOLATION",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
   DetachedAuthoredWorkflowTest.find(panel,"observed-previous",JButton.class).doClick();assertEquals("run:1",panel.selectedCut().id());
   var goals=DetachedAuthoredWorkflowTest.find(panel,"live-goal-status",JTable.class);assertEquals(2,goals.getRowCount());assertEquals("FULFILLED",goals.getValueAt(0,1));assertEquals("UNKNOWN",goals.getValueAt(0,2));assertEquals("INCOMPLETE",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
   assertTrue(texts(panel).contains("accepted state ONE"));assertFalse(texts(panel).contains("accepted state TWO"));
   panel.setCuts(List.of(first,second,new ObservedConformancePanel.Cut("run:3","third","THREE",List.of(),"INCOMPLETE","CURRENT_OBSERVED",false)));assertEquals("run:1",panel.selectedCut().id(),"New live cuts must not move a user's historical selection");
   DetachedAuthoredWorkflowTest.find(panel,"observed-next",JButton.class).doClick();assertEquals("run:2",panel.selectedCut().id());
   panel.setCuts(List.of(new ObservedConformancePanel.Cut("new-run:1","restart","NEW",List.of(new ObservedConformancePanel.Evaluation("Invariant","true","PASS","KNOWN","actual")),"COMPLETE_OBSERVED","CURRENT_OBSERVED",false)));assertEquals("INCOMPLETE",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText(),"Passing native OCL cannot certify absent Goal/Task evidence");
   panel.setCuts(List.of(new ObservedConformancePanel.Cut("new-run:2","known","KNOWN",List.of(LiveGoalSummaryTest.instance("Done","FULFILLED","alice"),LiveGoalSummaryTest.instance("Work","FULFILLED","alice")),"COMPLETE_OBSERVED","CURRENT_OBSERVED",false)));assertEquals("COMPLETE_OBSERVED",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
   var owner=new JDialog((java.awt.Frame)null);try{owner.add(panel);owner.setSize(1250,850);owner.addNotify();owner.validate();var image=new java.awt.image.BufferedImage(1250,850,java.awt.image.BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();try{panel.printAll(graphics);}finally{graphics.dispose();}try{var file=java.nio.file.Path.of("verification/restored-state-browser/ui-fixture.png");java.nio.file.Files.createDirectories(file.getParent());javax.imageio.ImageIO.write(image,"png",file.toFile());}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}}finally{owner.dispose();}
  });
 }
 private static List<String> texts(java.awt.Container parent){var result=new ArrayList<String>();for(var child:parent.getComponents()){if(child instanceof JTextArea t)result.add(t.getText());if(child instanceof java.awt.Container nested)result.addAll(texts(nested));}return result;}

}
