package org.tzi.use.plugins.jacamo.ui;

import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;

class FailurePointNavigationTest {
    Evaluation task(String occurrence,String status,String quality) {
        return new Evaluation("Work","PENDING",status,quality,"exact proof "+occurrence,"receipt",false,"GOAL_TASK","PENDING","player", "context",occurrence);
    }
    Cut cut(int n,List<Evaluation> rows) {
        return new Cut("cut-"+n,"trace "+n,"exact native state "+n,rows,"INCOMPLETE","CURRENT_OBSERVED",false,"same","requirements","selected",false);
    }
    @Test void allFailuresFilterAndRecurrenceOpenExactStatesWithoutTreatingUnknownAsFailure()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());
            var task=task("episode-1","FAILED","KNOWN_HISTORY");
            var ocl=new Evaluation("deadline","false","FAIL","KNOWN","object one","",true,"INVARIANT_BODY");
            var error=new Evaluation("unsupported","undefined","ERROR","UNAVAILABLE","compiler diagnostic","",true,"INVARIANT");
            var cuts=List.of(cut(0,List.of(task("not-proven","FAILED","UNAVAILABLE"))),cut(1,List.of(task)),cut(2,List.of(task)),cut(3,List.of(ocl)),cut(4,List.of(error,task("episode-1","FULFILLED","KNOWN_HISTORY"))),cut(5,List.of(task)));
            panel.setCuts(cuts);
            var points=DetachedAuthoredWorkflowTest.find(panel,"observed-failure-points",JComboBox.class);
            var filter=DetachedAuthoredWorkflowTest.find(panel,"observed-failure-filter",JComboBox.class);
            var button=DetachedAuthoredWorkflowTest.find(panel,"observed-failure",JButton.class);
            assertEquals("Failure Point",button.getText());assertEquals(4,points.getItemCount());
            assertTrue(points.getItemAt(0).toString().startsWith("Milestone 1"),points.getItemAt(0).toString());
            button.doClick();assertEquals("cut-1",panel.selectedCut().id());
            assertFalse(DetachedAuthoredWorkflowTest.find(panel,"observed-details",JCheckBox.class).isSelected());
            var position=DetachedAuthoredWorkflowTest.find(panel,"observed-state-position",JLabel.class);
            assertTrue(position.getText().startsWith("Milestone 1 / 2"),position.getText());assertFalse(position.getText().contains("Boundary"));
            var audit=DetachedAuthoredWorkflowTest.find(panel,"observed-audit-toggle",JToggleButton.class);
            audit.doClick();assertTrue(points.getItemAt(0).toString().startsWith("Boundary 2"));
            assertEquals("cut-1",panel.selectedCut().id());assertEquals("Boundary 2 / 6",position.getText());
            audit.doClick();assertEquals("cut-1",panel.selectedCut().id());assertTrue(points.getItemAt(0).toString().startsWith("Milestone 1"));
            assertTrue(position.getText().startsWith("Milestone 1 / 2"));
            assertTrue(DetachedAuthoredWorkflowTest.find(panel,"live-evidence-details",JTextArea.class).getText().contains("episode-1"));
            points.setSelectedIndex(1);assertEquals("cut-3",panel.selectedCut().id());
            assertEquals("exact native state 3",panel.selectedCut().state());
            assertEquals("VIOLATION",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText(),"An explicit native FAIL cannot be hidden by a stale summary flag");
            assertEquals(0,DetachedAuthoredWorkflowTest.find(panel,"live-ocl-checks",JTable.class).getSelectedRow());
            String failureEvidence=DetachedAuthoredWorkflowTest.find(panel,"live-evidence-details",JTextArea.class).getText();
            panel.setCuts(cuts);assertEquals("cut-3",panel.selectedCut().id());
            assertEquals(failureEvidence,DetachedAuthoredWorkflowTest.find(panel,"live-evidence-details",JTextArea.class).getText(),"Background refresh must preserve the selected failure evidence");
            LiveGoalSummaryTest.renderPanel(panel,"failure-navigation.png");
            filter.setSelectedItem("Goal / Task");assertEquals(2,points.getItemCount());
            points.setSelectedIndex(1);assertEquals("cut-5",panel.selectedCut().id());
            filter.setSelectedItem("Diagnostics");assertEquals(1,points.getItemCount());button.doClick();assertEquals("cut-4",panel.selectedCut().id());
            DetachedAuthoredWorkflowTest.find(panel,"observed-milestones",JButton.class).doClick();
            assertFalse(DetachedAuthoredWorkflowTest.find(panel,"observed-details",JCheckBox.class).isSelected());
            assertFalse(Arrays.stream(panel.getComponents()).anyMatch(c->"observed-interval-details".equals(c.getName())));
            panel.setCuts(List.of());assertEquals(0,points.getItemCount());assertFalse(button.isEnabled());
        });
    }
    @Test void twoObjectsFailingSameInvariantRemainTwoSelectablePoints()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());
            var one=new Evaluation("rule","false","FAIL","KNOWN","object one","",true,"INVARIANT_BODY","","","one","");
            var two=new Evaluation("rule","false","FAIL","KNOWN","object two","",true,"INVARIANT_BODY","","","two","");
            panel.setCuts(List.of(cut(0,List.of(one,two))));
            var points=DetachedAuthoredWorkflowTest.find(panel,"observed-failure-points",JComboBox.class);assertEquals(2,points.getItemCount());
            points.setSelectedIndex(1);assertTrue(DetachedAuthoredWorkflowTest.find(panel,"live-evidence-details",JTextArea.class).getText().contains("object two"));
        });
    }
    @Test void pendingAndSkippedDoNotRecoverFailureAndBodyAggregateAreOneCounterexample()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());
            var body=new Evaluation("population","false","FAIL","KNOWN","exact group","",true,"INVARIANT_BODY","","","group-one","");
            var aggregate=new Evaluation("population","false","FAIL","QUANTIFIED_NATIVE_INVARIANT;POPULATION_COMPLETE","summary","",true,"INVARIANT");
            var skipped=new Evaluation("population","","SKIPPED","CHECKPOINT_NOT_APPLICABLE","","",true,"INVARIANT_BODY","","","group-one","");
            var pass=new Evaluation("population","true","PASS","KNOWN","recovered","",true,"INVARIANT_BODY","","","group-one","");
            panel.setCuts(List.of(cut(0,List.of(body,aggregate)),cut(1,List.of()),cut(2,List.of(skipped)),cut(3,List.of(body,aggregate))));
            var points=DetachedAuthoredWorkflowTest.find(panel,"observed-failure-points",JComboBox.class);assertEquals(1,points.getItemCount());points.setSelectedIndex(0);assertEquals("cut-0",panel.selectedCut().id());
            panel.setCuts(List.of(cut(0,List.of(body,aggregate)),cut(1,List.of()),cut(2,List.of(pass)),cut(3,List.of(body,aggregate))));
            assertEquals(2,points.getItemCount());points.setSelectedIndex(1);assertEquals("cut-3",panel.selectedCut().id());
        });
    }
    @Test void hiddenFailureArrivingWithoutBusinessStateChangeStillRefreshesFailureList()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());
            var first=cut(0,List.of());panel.setCuts(List.of(first));
            panel.setCuts(List.of(first,cut(1,List.of(task("new","FAILED","KNOWN_HISTORY"))),first));
            var points=DetachedAuthoredWorkflowTest.find(panel,"observed-failure-points",JComboBox.class);
            assertEquals(1,points.getItemCount());points.setSelectedIndex(0);assertEquals("cut-1",panel.selectedCut().id());
        });
    }
}
