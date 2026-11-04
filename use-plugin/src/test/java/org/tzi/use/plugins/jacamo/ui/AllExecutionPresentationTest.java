package org.tzi.use.plugins.jacamo.ui;

import java.awt.geom.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.gui.DiagramEdgeRouter;
import static org.junit.jupiter.api.Assertions.*;

class AllExecutionPresentationTest {
    @Test void allRetainsScopedFailureAfterRecoveryWithoutChangingNativeCuts() throws Exception {
        var original=ExecutionScopeViewsTest.cut("failed","open","open",List.of(ExecutionScopeViewsTest.goal("one","FULFILLED"),ExecutionScopeViewsTest.goal("two","FULFILLED")));
        var first=new TreeMap<String,Object>(ExecutionScopeViewsTest.context("one","x"));
        first.put("executionConstraints",List.of(ExecutionScopeViewsTest.check("x","FAIL")));
        var evidence=new TreeMap<String,Object>(original.visualEvidence());
        evidence.put("originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","contexts",List.of(first,ExecutionScopeViewsTest.context("two","y"))));
        var failed=new Cut(original.id(),original.trace(),original.state(),original.evaluations(),original.coverage(),original.freshness(),false,"first","","",false,true,false,evidence);
        var recovered=ExecutionScopeViewsTest.cut("recovered","done","done",original.evaluations());
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());panel.setCuts(List.of(failed,recovered));
            panel.selectExecution(ExecutionScopeViews.ALL);
            assertEquals("VIOLATION",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
            var checks=DetachedAuthoredWorkflowTest.find(panel,"live-ocl-checks",JTable.class);
            assertEquals(List.of("Name","Body","Result"),java.util.stream.IntStream.range(0,checks.getColumnCount()).mapToObj(checks::getColumnName).toList());
            assertTrue(checks.getValueAt(0,0).toString().contains("Execution 1"));
            assertEquals("self.valid",checks.getValueAt(0,1));assertEquals("True",checks.getValueAt(0,2));
            var goals=DetachedAuthoredWorkflowTest.find(panel,"live-goal-status",JTable.class);
            assertEquals(2,goals.getColumnCount());
            for(int i=0;i<goals.getRowCount();i++)assertTrue(Set.of("Complete","Incomplete","Unknown").contains(goals.getValueAt(i,1)));
            panel.selectExecution("two");assertEquals("COMPLETE_OBSERVED",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
            panel.selectExecution(ExecutionScopeViews.ALL);assertEquals("VIOLATION",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
        });
        assertFalse(failed.failed());assertEquals("PASS",ExecutionScopeViewsTest.check("y","PASS").get("verdict"));
    }
    @Test void displayVocabularyPreservesUnavailableTruthAndUnassignedGoals(){
        assertEquals("Unknown",PresentationResults.goal("NOT_ASSIGNED",false));
        assertEquals("Incomplete",PresentationResults.goal("FULFILLED",true));
        assertEquals("Unknown",PresentationResults.truth("SKIPPED"));
        assertEquals("Error",PresentationResults.truth("ERROR"));
        assertEquals("False",PresentationResults.truth("CONDITION_FALSE"));
    }
    @Test void routingAvoidsAnInterveningBox(){
        var box=new Rectangle2D.Double(40,-20,30,40);
        var route=DiagramEdgeRouter.route(new Point2D.Double(0,0),new Point2D.Double(110,0),List.of(box));
        assertTrue(route.size()>2);
        for(int i=1;i<route.size();i++)assertFalse(box.intersectsLine(route.get(i-1).getX(),route.get(i-1).getY(),route.get(i).getX(),route.get(i).getY()));
    }
    @Test void refinementLayoutPlacesChildrenBelowParentsWithoutOverlappingSiblings(){
        var model=org.vnu.sme.goal.dsl.istar.parser.IStarCompiler.compile("istar Layers { role Worker { goal Root : Achieve task Alpha > Root task Beta > Root } }").model();
        var layout=org.tzi.use.plugins.jacamo.ui.use4goal.istar.IStarLayoutBuilder.build(model,org.tzi.use.plugins.jacamo.ui.use4goal.istar.IStarViewMode.SR);
        var root=layout.nodes.get("Root");var a=layout.nodes.get("Alpha");var b=layout.nodes.get("Beta");
        assertTrue(root.y+root.h<a.y);assertEquals(a.y,b.y);
        assertFalse(new java.awt.Rectangle(a.x,a.y,a.w,a.h).intersects(b.x,b.y,b.w,b.h));
    }
}
