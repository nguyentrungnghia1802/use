package org.tzi.use.plugins.jacamo.ui;

import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;

class ScopedFailureNavigationTest {
    private Map<String,Object> check(String object,String kind,String verdict,String proof) {
        return Map.of("id","Rule","kind",kind,"contextClass","Sensor","contextObject",object,
            "verdict",verdict,"backendValue",verdict.equals("PASS")?"true":"false",
            "expression","self.valid","diagnostic","NATIVE_RESULT","witness",Map.of("proof",proof));
    }
    private Map<String,Object> context(String id,String entity,List<Map<String,Object>> checks) {
        var context=new TreeMap<String,Object>(ExecutionScopeViewsTest.context(id,entity));
        context.put("executionConstraints",checks);return context;
    }
    private Cut cut(int n,List<Map<String,Object>> one,List<Map<String,Object>> two,List<Evaluation> tasks) {
        var rows=new ArrayList<>(List.of(ExecutionScopeViewsTest.goal("one","FULFILLED"),ExecutionScopeViewsTest.goal("two","FULFILLED")));
        rows.addAll(tasks);
        return new Cut("scope-cut-"+n,"exact trace "+n,"native "+n,rows,"INCOMPLETE","CURRENT_OBSERVED",false,"business","requirements","state",false,true,false,
            Map.of("originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","contexts",List.of(context("one","x",one),context("two","y",two))),
                "presentationState",Map.of("qualifiedValues",true,"objects",Map.of("Parcel:x",Map.of("id","'x'"),"Parcel:y",Map.of("id","'y'"))),
                "requirementInventory",Map.of("selectedIntentionalElements",List.of("Done"))));
    }
    private Evaluation task(String scope) {
        return new Evaluation("Work","PENDING","FAILED","KNOWN_HISTORY","task proof "+scope,"",false,"GOAL_TASK","PENDING","worker",scope,"work:"+scope);
    }
    @Test void scopedObjectFailuresRecurrenceAndTaskFailuresRemainIndividuallyNavigable()throws Exception {
        var pass=List.of(check("","INVARIANT","PASS","aggregate"));
        var failed=List.of(check("sensor-1","INVARIANT_BODY","FAIL","first object"),check("sensor-2","INVARIANT_BODY","FAIL","second object"),check("","INVARIANT","FAIL","aggregate"));
        // A false raw body under a passing (for example negated) aggregate is not a violation.
        var negated=List.of(check("foreign-sensor","INVARIANT_BODY","FAIL","foreign raw body"),check("","INVARIANT","PASS","negated aggregate"));
        var history=List.of(cut(0,pass,negated,List.of()),cut(1,failed,negated,List.of(task("one"),task("two"))),
            cut(2,pass,negated,List.of()),cut(3,failed,negated,List.of()));
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());panel.setCuts(history);panel.selectExecution("one");
            var points=DetachedAuthoredWorkflowTest.find(panel,"observed-failure-points",JComboBox.class);
            var filter=DetachedAuthoredWorkflowTest.find(panel,"observed-failure-filter",JComboBox.class);
            var button=DetachedAuthoredWorkflowTest.find(panel,"observed-failure",JButton.class);
            assertEquals(5,points.getItemCount());button.doClick();assertEquals("scope-cut-1",panel.selectedCut().id());
            filter.setSelectedItem("OCL / constraints");assertEquals(4,points.getItemCount());
            points.setSelectedIndex(1);assertEquals("scope-cut-1",panel.selectedCut().id());
            var detail=DetachedAuthoredWorkflowTest.find(panel,"live-evidence-details",JTextArea.class);
            assertTrue(detail.getText().contains("second object"));assertTrue(detail.getText().contains("sensor-2"));
            assertFalse(detail.getText().contains("foreign raw body"));
            assertEquals(1,DetachedAuthoredWorkflowTest.find(panel,"live-ocl-checks",JTable.class).getRowCount(),"Body witnesses must not become duplicate required OCL table rows");
            assertTrue(points.getItemAt(0).toString().startsWith("Milestone "));
            DetachedAuthoredWorkflowTest.find(panel,"observed-audit-toggle",JToggleButton.class).doClick();
            assertTrue(points.getItemAt(0).toString().startsWith("Boundary 2"));
            points.setSelectedIndex(3);assertEquals("scope-cut-3",panel.selectedCut().id());
            LiveGoalSummaryTest.renderPanel(panel,"scoped-failure-points.png");
            panel.selectExecution("two");assertEquals(0,points.getItemCount());assertFalse(button.isEnabled());
            filter.setSelectedItem("Goal / Task");assertEquals(1,points.getItemCount());button.doClick();
            assertTrue(detail.getText().contains("task proof two"));assertFalse(detail.getText().contains("task proof one"));
            filter.setSelectedItem("All failures");assertEquals(1,points.getItemCount());
            panel.selectExecution("one");assertEquals(5,points.getItemCount());
        });
    }
    @Test void unmetConditionCanBeInspectedSeparatelyWithoutBecomingFailureOrBorrowingAnotherExecution()throws Exception {
        var pass=check("","INVARIANT","PASS","native pass");
        var notMet=new TreeMap<String,Object>(check("x","GOAL_CONDITION","FAIL","no completion yet"));notMet.put("id","Condition");
        var met=new TreeMap<String,Object>(notMet);met.put("verdict","PASS");met.put("backendValue","true");
        var history=List.of(cut(0,List.of(pass,notMet),List.of(pass),List.of()),cut(1,List.of(pass,met),List.of(pass),List.of()),cut(2,List.of(pass,notMet),List.of(pass),List.of()));
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());panel.setCuts(history);panel.selectExecution("one");
            var points=DetachedAuthoredWorkflowTest.find(panel,"observed-failure-points",JComboBox.class);
            var filter=DetachedAuthoredWorkflowTest.find(panel,"observed-failure-filter",JComboBox.class);
            var button=DetachedAuthoredWorkflowTest.find(panel,"observed-failure",JButton.class);
            assertEquals(0,points.getItemCount());assertFalse(button.isEnabled());
            assertNotEquals("VIOLATION",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
            filter.setSelectedItem("Unmet conditions");assertEquals(2,points.getItemCount());assertTrue(button.isEnabled());
            button.doClick();assertEquals("scope-cut-0",panel.selectedCut().id());
            assertTrue(DetachedAuthoredWorkflowTest.find(panel,"live-evidence-details",JTextArea.class).getText().contains("CONDITION_FALSE"));
            points.setSelectedIndex(1);assertEquals("scope-cut-2",panel.selectedCut().id());
            LiveGoalSummaryTest.renderPanel(panel,"unmet-condition-points.png");
            panel.selectExecution("two");assertEquals(0,points.getItemCount());assertFalse(button.isEnabled());
        });
    }
}
