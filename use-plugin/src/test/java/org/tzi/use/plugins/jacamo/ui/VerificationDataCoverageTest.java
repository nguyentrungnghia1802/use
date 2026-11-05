package org.tzi.use.plugins.jacamo.ui;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
import static org.junit.jupiter.api.Assertions.*;
class VerificationDataCoverageTest {
    static Cut cut(String status,boolean required){return new Cut("cut","source history retained","native",List.of(new Evaluation("Rule","",status,"native","evidence","",required,"INVARIANT")),"INCOMPLETE","CURRENT_OBSERVED",status.equals("FAIL"));}
    @Test void knownFalseHasCompleteDataAndRawSourceHistoryStaysPartial(){
        var failed=cut("FAIL",true);var assessment=VerificationDataCoverage.assess(failed);
        assertEquals("COMPLETE",assessment.status());assertTrue(failed.failed());assertEquals("INCOMPLETE",failed.coverage());
        assertTrue(VerificationDataCoverage.diagnostics(failed,assessment).contains("Overall source coverage (includes historical/global observation): INCOMPLETE"));
        assertEquals("COMPLETE",VerificationDataCoverage.assess(cut("PASS",true)).status());
    }
    @Test void missingRequiredEvidenceNeverBecomesCompleteAndSupportingGapsRemainSeparate(){
        for(String truth:List.of("UNKNOWN","ERROR","SKIPPED","UNDEFINED","UNSUPPORTED","INCONCLUSIVE"))assertEquals("INCOMPLETE",VerificationDataCoverage.assess(cut(truth,true)).status());
        assertEquals("UNAVAILABLE",VerificationDataCoverage.assess(cut("SKIPPED",false)).status());
        var known=cut("PASS",true);var rows=new ArrayList<>(known.evaluations());rows.addAll(cut("SKIPPED",false).evaluations());
        var withSupporting=new Cut(known.id(),known.trace(),known.state(),rows,known.coverage(),known.freshness(),false);
        assertEquals("COMPLETE",VerificationDataCoverage.assess(withSupporting).status());
        assertEquals("INCOMPLETE",withSupporting.coverage());
    }
    @Test void allAndEachExecutionKeepTheirOwnCoverageAndHistoricalFailures()throws Exception {
        var original=ExecutionScopeViewsTest.cut("both","open","open",List.of(ExecutionScopeViewsTest.goal("one","FULFILLED"),ExecutionScopeViewsTest.goal("two","FULFILLED")));
        var one=new TreeMap<String,Object>(ExecutionScopeViewsTest.context("one","x"));
        one.put("executionConstraints",List.of(ExecutionScopeViewsTest.check("x","SKIPPED")));
        var evidence=new TreeMap<String,Object>(original.visualEvidence());evidence.put("originalEngine",Map.of("evaluated","PINNED_NATIVE_CUT","contexts",List.of(one,ExecutionScopeViewsTest.context("two","y"))));
        var cut=new Cut(original.id(),original.trace(),original.state(),original.evaluations(),original.coverage(),original.freshness(),false,original.businessKey(),original.requirements(),original.businessState(),false,true,false,evidence);
        assertEquals("INCOMPLETE",VerificationDataCoverage.assess(ExecutionScopeViews.project(cut,"one")).status());
        assertEquals("COMPLETE",VerificationDataCoverage.assess(ExecutionScopeViews.project(cut,"two")).status());
        assertEquals("INCOMPLETE",VerificationDataCoverage.assess(ExecutionScopeViews.all(cut)).status());
        javax.swing.SwingUtilities.invokeAndWait(()->{
            var panel=ObservedConformancePanel.liveResults(LiveGoalSummaryTest.model());panel.setCuts(List.of(cut));panel.selectExecution("two");
            var label=DetachedAuthoredWorkflowTest.find(panel,"observed-quality",javax.swing.JLabel.class);
            assertTrue(label.getText().contains("Data coverage: COMPLETE"));
            assertTrue(label.getToolTipText().contains("historical/global observation): INCOMPLETE"));
            assertTrue(javax.swing.SwingUtilities.isDescendingFrom(label,panel.cslVerificationPanel()));
            assertTrue(label.getParent().isVisible());
            var istarLabel=DetachedAuthoredWorkflowTest.find(panel,"istar-data-coverage",javax.swing.JLabel.class);
            assertEquals(label.getText(),istarLabel.getText());assertTrue(javax.swing.SwingUtilities.isDescendingFrom(istarLabel,panel.istarVerificationPanel()));
        });
    }
    @Test void originalAdmissionApplicabilityKeepsFutureGoalsSeparateButRequiredGapsStillBlockCoverage(){
        var pending=new Evaluation("Future goal","PENDING","PENDING","DERIVED_INPUT_UNAVAILABLE","future","",true,"GOAL_TASK");
        for(String truth:List.of("PASS","FAIL","UNKNOWN")) {
            var cut=new Cut("cut","trace","native",List.of(pending),"INCOMPLETE","CURRENT_OBSERVED",truth.equals("FAIL"),"","","",false,true,false,
                    Map.of("nativeAdmissionChecks",Map.of("native.applicable",Map.of("truth",truth,"applicability","APPLICABLE"),"future-condition",Map.of("truth","PENDING","applicability","FUTURE"))));
            assertEquals(truth.equals("UNKNOWN")?"INCOMPLETE":"COMPLETE",VerificationDataCoverage.assess(cut).status());
            assertEquals("PENDING",cut.evaluations().getFirst().status());
        }
    }
}
