package org.tzi.use.plugins.jacamo.ui;

import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckpointPresentationRegressionTest {
    @Test void allGroupsRepeatedSourceCutsAndKeepsEveryAuditToken() throws Exception {
        var cuts=new ArrayList<Cut>();
        for(var cut:CheckpointMilestoneIsolationTest.recordedCuts()){
            cuts.add(cut);
            // A second control/audit token with identical native presentation
            // must not create another State; retain both immutable audit rows.
            cuts.add(new Cut(cut.id()+":control-update",cut.trace()+"\nControl update",cut.state(),cut.evaluations(),cut.coverage(),cut.freshness(),cut.failed(),cut.businessKey(),cut.requirements(),cut.businessState(),cut.executionMilestones(),cut.selectedRequirements(),cut.businessPopulationEmpty(),cut.visualEvidence()));
        }
        var prepared=new PreparationCache().prepare(cuts,ExecutionScopeViews.ALL,true,true);
        assertEquals(cuts.size(),prepared.audit().size());
        assertTrue(prepared.milestones().size()<cuts.size(),"All must apply the same grouping policy as each execution");
        assertEquals(cuts.getLast().id(),prepared.milestones().getLast().id());
        assertEquals(cuts.size(),prepared.numbers().size());
        for(var cut:cuts)assertTrue(prepared.numbers().containsKey(cut.id()));
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());panel.showCheckpoint(prepared);
            var selector=DetachedAuthoredWorkflowTest.find(panel,"observed-state-selector",JComboBox.class);
            assertEquals(prepared.milestones().size(),selector.getItemCount());
            DetachedAuthoredWorkflowTest.find(panel,"observed-audit-toggle",JToggleButton.class).doClick();
            assertEquals(cuts.size(),selector.getItemCount(),"Grouping never drops the original checkpoint evidence");
        });
        System.out.println("ALL_CHECKPOINT_GROUPING audit="+cuts.size()+" states="+prepared.milestones().size());
    }

    @Test void aBodyCounterexampleWithoutAnAggregateIsNeverSilentlyHidden() {
        var body=new Evaluation("Legacy rule","false","FAIL","NATIVE_BODY_ONLY","expression=self.valid","",false,"INVARIANT_BODY","","","object","invocation");
        var cut=ExecutionScopeViewsTest.cut("body-only","open","open",List.of(body));
        assertTrue(PresentationResults.checks(cut).contains(body));
        assertEquals("False",PresentationResults.truth(body.status()));
    }

    @Test void authenticFailedTaskAndDeadlineStayFailedWithoutBeingDuplicateOclPredicates() throws Exception {
        byte[] bytes;
        try(var stream=new java.util.zip.GZIPInputStream(getClass().getResourceAsStream("/semantics/checkpoint-display-user-result-20261105.json.gz"))){bytes=stream.readAllBytes();}
        // Verify the original recorded result hash. No edited verdicts/constraints.
        var result=RuntimeVerificationResult.fromMap(CanonicalJson.object(CanonicalJson.decode(bytes)));
        String hash=result.resultHash();
        var snapshot=new VerificationSnapshot(result.stateVersion(),result,null);
        var presenter=new ObservedConformancePresenter(new JaCaMoFacade(){public String status(){return "frozen user result";}public VerificationSnapshot verificationSnapshot(){return snapshot;}});
        var view=new VerifiedCheckpointService.View("VERIFYING","recorded-token","recorded-cut","","",result.stateHash(),hash,Set.of(),false,false,Map.of());
        var checks=new TreeMap<String,VerifiedCheckpointProtocol.Check>();
        for(var outcome:result.outcomes())if(outcome.outcome()==org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL)
            checks.put(outcome.constraintId(),new VerifiedCheckpointProtocol.Check("FAIL","APPLICABLE",outcome.diagnostic()));
        var cuts=presenter.captureCheckpoint(snapshot,view,checks);
        var original=cuts.getLast();var all=ExecutionScopeViews.all(original);
        assertEquals(2,original.evaluations().stream().filter(r->r.kind().equals("EXECUTION_OUTCOME")).count(),"Both original/view certificates remain in audit");
        assertTrue(original.evaluations().stream().anyMatch(r->r.kind().equals("CHECKPOINT_CHECK")));
        var shown=PresentationResults.checks(all);
        assertTrue(shown.stream().noneMatch(r->Set.of("CHECKPOINT_CHECK","EXECUTION_OUTCOME","INVARIANT_BODY").contains(r.kind())));
        var globalFailure=shown.stream().filter(r->r.status().equals("FAIL")&&r.context().equals(ExecutionScopeViews.WHOLE_SYSTEM)).toList();
        assertFalse(globalFailure.isEmpty(),"An unlocalized native deadline failure must remain visible");
        assertTrue(globalFailure.stream().allMatch(r->PresentationResults.body(r).contains("self.")),"Show the actual OCL expression, not an inventory diagnostic");
        assertTrue(all.failed());assertEquals(hash,result.resultHash());
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());panel.setCuts(cuts);panel.selectExecution(ExecutionScopeViews.ALL);
            var table=DetachedAuthoredWorkflowTest.find(panel,"live-ocl-checks",JTable.class);
            assertEquals(shown.size(),table.getRowCount());
            boolean found=false;
            for(int i=0;i<table.getRowCount();i++){
                var name=table.getValueAt(i,0).toString();assertFalse(name.startsWith("EXECUTION:"));assertFalse(name.startsWith("Checkpoint (whole MAS)"));
                if(name.endsWith("Whole system")&&table.getValueAt(i,2).equals("False"))found=true;
            }
            assertTrue(DetachedAuthoredWorkflowTest.find(panel,"checkpoint-diagnostics-note",JLabel.class).getText().contains("Task failures: 1"),"Two viewpoint certificates describe one failed source invocation");
            assertTrue(found);assertEquals("VIOLATION",DetachedAuthoredWorkflowTest.find(panel,"observed-verdict",JLabel.class).getText());
            assertDoesNotThrow(()->DetachedAuthoredWorkflowTest.find(panel,"observed-failure",JButton.class).doClick());
        });
        System.out.println("AUTHENTIC_CHECK_PRESENTATION original="+original.evaluations().size()+" mainChecks="+shown.size()+" retainedResult="+hash);
    }
}
