package org.tzi.use.plugins.jacamo.ui;

import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckpointMilestoneIsolationTest {
    static List<Cut> recordedCuts() throws Exception {
        byte[] raw;
        try(var stream=new java.util.zip.GZIPInputStream(CheckpointMilestoneIsolationTest.class.getResourceAsStream("/semantics/checkpoint-milestone-user-run.json.gz"))){raw=stream.readAllBytes();}
        var presenter=new ObservedConformancePresenter(new JaCaMoFacade(){public String status(){return "frozen user recording";}});
        List<Cut> cuts=List.of();
        for(Object item:(List<?>)CanonicalJson.decode(raw,32*1024*1024,CanonicalJson.DEFAULT_MAX_DEPTH,CanonicalJson.DEFAULT_MAX_STRING)){
            var sample=CanonicalJson.object(item);var request=CanonicalJson.object(sample.get("request"));
            var identity=CanonicalJson.object(request.get("identity"));
            // fromMap verifies the original native result hash; no rewritten verdicts.
            var result=RuntimeVerificationResult.fromMap(CanonicalJson.object(sample.get("result")));
            var view=new VerifiedCheckpointService.View("VERIFYING",identity.get("token").toString(),request.get("checkpointId").toString(),"","",result.stateHash(),result.resultHash(),Set.of(),false,false,request);
            cuts=presenter.captureCheckpoint(new VerificationSnapshot(result.stateVersion(),result,null),view,Map.of());
        }
        return cuts;
    }

    @Test void stateMilestonesGroupIdenticalCslWhilePreservingEveryNativeTaskTransition() throws Exception {
        var cuts=recordedCuts();assertEquals(8,cuts.size());
        String scope=ExecutionScopeViews.admittedChoices(cuts).keySet().iterator().next();
        var cache=new PreparationCache();var prepared=cache.prepare(cuts,scope,true,true);
        assertEquals(8,prepared.audit().size(),"Every original exact cut remains in audit");
        assertEquals(4,prepared.milestones().size(),"Task progress belongs to its CSL state's audit interval");
        assertEquals(List.of(1,1,2,2,3,3,4,4),cuts.stream().map(c->prepared.numbers().get(c.id())).toList());
        var before=ExecutionScopeViews.project(cuts.get(2),scope);var after=ExecutionScopeViews.project(cuts.get(3),scope);
        assertEquals(before.businessState(),after.businessState(),"Identical CSL alone does not imply identical Task evidence");
        assertNotEquals(before.evaluations().stream().map(Evaluation::status).toList(),after.evaluations().stream().map(Evaluation::status).toList());
        assertEquals(cuts.getLast().id(),prepared.milestones().getLast().id(),"The grouped state must still display the exact latest source token");
        assertEquals("COMPLETE_OBSERVED",ExecutionScopeViews.goalVerdict(prepared.milestones().getLast()));
        System.out.println("USER_RECORDING_MILESTONES scope="+scope+" boundaries=8 milestones=4 assignment="+cuts.stream().map(c->prepared.numbers().get(c.id())).toList());
    }

    @Test void groupedRepresentativeUpdateKeepsHistoricalNavigationAndOriginalAudit() throws Exception {
        var cuts=recordedCuts();String scope=ExecutionScopeViews.admittedChoices(cuts).keySet().iterator().next();
        SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());var cache=new PreparationCache();
            panel.showCheckpoint(cache.prepare(cuts.subList(0,7),scope,true,true));
            var selector=DetachedAuthoredWorkflowTest.find(panel,"observed-state-selector",JComboBox.class);
            selector.setSelectedIndex(2);
            panel.showCheckpoint(cache.prepare(cuts,scope,true,true));assertEquals(2,selector.getSelectedIndex());
            for(String other:ExecutionScopeViews.admittedChoices(cuts).keySet())if(!other.equals(scope))panel.selectExecution(other);
            panel.selectExecution(scope);assertEquals(2,selector.getSelectedIndex());assertEquals(4,selector.getItemCount());
            assertEquals(cuts.get(5).id(),panel.selectedCut().id());
            DetachedAuthoredWorkflowTest.find(panel,"observed-audit-toggle",JToggleButton.class).doClick();
            assertEquals(8,selector.getItemCount());
        });
    }

    @Test void proofOrderingAndHistoryLabelsDoNotHideAnActualScopedFailure() {
        var a=ExecutionScopeViewsTest.goal("one","FULFILLED");
        var current=new Evaluation(a.element(),a.raw(),a.status(),"KNOWN",a.evidence(),a.certifiedHistory(),a.required(),a.kind(),a.originalStatus(),a.player(),a.context(),a.occurrence());
        var other=new Evaluation("Second","FULFILLED","FULFILLED","KNOWN","proof","",true,"GOAL_TASK","FULFILLED","other player","one","second occurrence");
        var failed=new Evaluation(a.element(),a.raw(),"FAILED","KNOWN_HISTORY","original failure","",true,a.kind(),"FAILED",a.player(),a.context(),a.occurrence());
        var cuts=List.of(ExecutionScopeViewsTest.cut("a","same","initial",List.of(current,other)),
                ExecutionScopeViewsTest.cut("b","same","changed elsewhere",List.of(other,a)),
                ExecutionScopeViewsTest.cut("c","same","changed elsewhere",List.of(other,failed)));
        var prepared=new PreparationCache().prepare(cuts,"one",true,true);
        assertEquals(List.of(1,1,1),cuts.stream().map(c->prepared.numbers().get(c.id())).toList());
        assertEquals(3,prepared.audit().size());assertEquals("c",prepared.milestones().getFirst().id());
        assertEquals("PENDING",ExecutionScopeViews.goalVerdict(prepared.milestones().getLast()),"A failed Task retains the existing native retry/pending aggregation");
        assertTrue(prepared.audit().getLast().evaluations().stream().anyMatch(r->r.status().equals("FAILED")&&r.evidence().equals("original failure")));
    }
}
