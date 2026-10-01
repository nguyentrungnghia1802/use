package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class NativeRuntimeReanalysisTest {
    @TempDir Path root;
    @Test void lateProfileReconstructsEarlierABAWithNewResultsAndCannotMutateLiveHistoryOrBundle() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var projector=new NativeRuntimeProjector(pipeline,SESSION,1,REVISION)) {
            projector.applySnapshot(snapshot("initial",0)); projector.apply(delta("B",1,"B")); projector.apply(delta("A",2,"A"));
            var coordinator=projector.coordinator(); coordinator.loadProfileSource("late.ocl",RuntimeVerificationCoordinatorTest.PROFILE);
            Path bundle=root.resolve("recorded"), output=root.resolve("analysis");
            new NativeRuntimeReplay().exportBundle(projector,pipeline.export().useText(),bundle);
            var before=coordinator.verificationSnapshot(); var history=coordinator.history();
            var originalFiles=new java.util.LinkedHashMap<String,String>();
            try(var files=Files.list(bundle)) { for(Path file:files.toList()) originalFiles.put(file.getFileName().toString(),Files.readString(file)); }
            var report=new NativeRuntimeReanalysis().analyze(bundle,"current.ocl",RuntimeVerificationCoordinatorTest.PROFILE,Map.of(),output);
            assertTrue(report.complete(),report.diagnostics().toString()); assertEquals("REPLAY_REANALYSIS",report.origin());
            assertEquals(coordinator.journal().persistedEntries(),report.checkedEntries());
            var events=report.retainedTail().stream().filter(entry->entry.kind().equals("EVENT")).toList();
            assertEquals(List.of(VerificationOutcome.FAIL,VerificationOutcome.PASS),events.stream().map(entry->entry.result().outcomes().stream()
                    .filter(value->value.constraintId().equals("EXTERNAL:ObservablePropertySnapshot::NoB")).findFirst().orElseThrow().outcome()).toList());
            assertNotEquals(events.getFirst().originalConstraintSet(),events.getFirst().result().constraintSetHash());
            assertNotEquals(events.getFirst().originalVersion(),events.getFirst().result().stateVersion());
            assertEquals(before,coordinator.verificationSnapshot()); assertEquals(history,coordinator.history());
            for(var file:originalFiles.entrySet()) assertEquals(file.getValue(),Files.readString(bundle.resolve(file.getKey())));
            assertTrue(new NativeRuntimeReplay().replay(bundle).complete());
            assertTrue(Files.readString(output.resolve("reanalysis-summary.json")).contains("\"recordedResultParity\":false"));
        }
    }
    @Test void corruptionGapMissingBaselineInvalidProfileAndOriginalOutputAreRejected() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var projector=new NativeRuntimeProjector(pipeline,SESSION,1,REVISION)) {
            projector.applySnapshot(snapshot("initial",0)); Path bundle=root.resolve("bundle");
            new NativeRuntimeReplay().exportBundle(projector,pipeline.export().useText(),bundle);
            var analysis=new NativeRuntimeReanalysis();
            assertFalse(analysis.analyze(bundle,"bad.ocl","context Missing inv Bad: true",Map.of(),root.resolve("bad")).complete());
            assertFalse(Files.exists(root.resolve("bad/reanalysis.jsonl")));
            assertFalse(analysis.analyze(bundle,"p.ocl",RuntimeVerificationCoordinatorTest.PROFILE,Map.of(),bundle).complete());
            Files.writeString(bundle.resolve("runtime.jsonl"),"corrupt\n",java.nio.file.StandardOpenOption.APPEND);
            var corrupt=analysis.analyze(bundle,"p.ocl",RuntimeVerificationCoordinatorTest.PROFILE,Map.of(),root.resolve("corrupt"));
            assertFalse(corrupt.complete()); assertTrue(corrupt.diagnostics().toString().contains("VALIDATION_FAILED"));
            Files.delete(bundle.resolve("baseline.cmd"));
            assertFalse(analysis.analyze(bundle,"p.ocl",RuntimeVerificationCoordinatorTest.PROFILE,Map.of(),root.resolve("missing")).complete());
            Path gap=root.resolve("gap"); coordinatorGap(projector); new NativeRuntimeReplay().exportBundle(projector,pipeline.export().useText(),gap);
            assertFalse(analysis.analyze(gap,"p.ocl",RuntimeVerificationCoordinatorTest.PROFILE,Map.of(),root.resolve("gap-output")).complete());
        }
    }
    @Test void currentNegatedAndDisabledFlagsAreReanalyzedWithoutChangingRecordedProfile() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var projector=new NativeRuntimeProjector(pipeline,SESSION,1,REVISION)) {
            projector.applySnapshot(snapshot("initial",0));
            var coordinator=projector.coordinator();
            coordinator.loadProfileSource("original.ocl",RuntimeVerificationCoordinatorTest.PROFILE);
            Path bundle=root.resolve("original-flags");
            new NativeRuntimeReplay().exportBundle(projector,pipeline.export().useText(),bundle);
            String id="ObservablePropertySnapshot::NoB";
            var negated=new NativeRuntimeReanalysis().analyze(bundle,"current.ocl",RuntimeVerificationCoordinatorTest.PROFILE,
                    Map.of(id,true),Map.of(id,true),root.resolve("negated"));
            assertTrue(negated.complete(),negated.diagnostics().toString());
            assertEquals(VerificationOutcome.FAIL,externalNoB(negated.retainedTail().getLast().result()));
            var disabled=new NativeRuntimeReanalysis().analyze(bundle,"current.ocl",RuntimeVerificationCoordinatorTest.PROFILE,
                    Map.of(id,false),Map.of(id,true),root.resolve("disabled"));
            assertTrue(disabled.complete(),disabled.diagnostics().toString());
            assertEquals(VerificationOutcome.SKIPPED,externalNoB(disabled.retainedTail().getLast().result()));
            assertEquals(VerificationOutcome.PASS,externalNoB(coordinator.latest()));
            coordinator.loadProfileSource("negated.ocl",RuntimeVerificationCoordinatorTest.PROFILE,Map.of(id,true),Map.of(id,true));
            Path negatedBundle=root.resolve("recorded-negated");
            new NativeRuntimeReplay().exportBundle(projector,pipeline.export().useText(),negatedBundle);
            assertTrue(new NativeRuntimeReplay().replay(negatedBundle).complete());
        }
    }
    private static VerificationOutcome externalNoB(RuntimeVerificationResult result) {
        return result.outcomes().stream().filter(value->value.constraintId().equals("EXTERNAL:ObservablePropertySnapshot::NoB"))
                .findFirst().orElseThrow().outcome();
    }
    private void coordinatorGap(NativeRuntimeProjector projector) { projector.coordinator().coverageGap("TEST_SOURCE_GAP"); }
}
