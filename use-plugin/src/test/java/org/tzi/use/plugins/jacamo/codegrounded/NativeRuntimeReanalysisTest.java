package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.*;
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
                    .filter(value->value.constraintId().equals("EXTERNAL:LiveRuntimePropertyArtifact::NoB")).findFirst().orElseThrow().outcome()).toList());
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
            String id="LiveRuntimePropertyArtifact::NoB";
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
    @Test void newBeliefDemandUsesRecordedRawCutsAndKeepsEveryOtherDomainObjectAndLinkIdentical() throws Exception {
        var pipeline=CodeGroundedTestFixtures.helloPipeline();
        try(var projector=new NativeRuntimeProjector(pipeline,SESSION,1,REVISION)) {
            projector.applySnapshot(snapshot("initial",0));
            var declaration=pipeline.source().snapshot().agentDeclarations().getFirst();
            var agent=new BridgeEntityId("jason","agent","runtime-agent",pipeline.source().project().name(),declaration.name(),"belief-analysis");
            var literal=Map.<String,Object>of("semanticId",org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection
                    .occurrenceId("runtime-belief",agent.canonical(),"ready(a)"),"sourceIdentity","ready(a)","literal","ready(a)",
                    "predicateIndicator","ready/1","domainAuthored",true,"authoritativeElsewhere",false);
            var payload=Map.<String,Object>of("normalizedEventKind","UPSERT_JASON_AGENT_STATE","semanticId",agent.canonical(),
                    "agentDeclarationSemanticId",declaration.metadata().semanticId(),"name",declaration.name(),"beliefs",List.of(literal),"goals",List.of());
            projector.apply(new RuntimeEvent("authored-cut",SESSION,1,REVISION,"jason","jason",1,java.time.Instant.EPOCH,
                    RuntimeEventKind.CHANGED,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,agent,null,"","",Map.of(),payload,
                    new SourceWatermark("jason",1),Completeness.COMPLETE,List.of()));
            assertEquals(0,projector.system().state().objectsOfClass(projector.system().model().getClass("Belief")).size());
            Path bundle=root.resolve("raw-beliefs"), output=root.resolve("selected-beliefs");
            new NativeRuntimeReplay().exportBundle(projector,pipeline.export().useText(),bundle);
            var before=projector.coordinator().verificationSnapshot();
            var report=new NativeRuntimeReanalysis().analyze(bundle,"beliefs.ocl",
                    "context Agent inv RelevantBeliefs: self.beliefs->forAll(b | not b.literal.oclIsUndefined())",Map.of(),output);
            assertTrue(report.complete(),report.diagnostics().toString());
            assertTrue(report.diagnostics().toString().contains("SELECTIVE_BELIEF_PROJECTION_PER_CURRENT_PROFILE"));
            assertNotEquals(before.result().stateHash(),report.finalStateHash());
            var summary=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(output.resolve("reanalysis-summary.json"))));
            assertEquals(false,summary.get("recordedStateParity")); assertEquals(true,summary.get("domainStateParity"));
            for(String line:Files.readAllLines(output.resolve("reanalysis.jsonl"))) {
                var row=CanonicalJson.object(CanonicalJson.decode(line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                assertEquals(row.get("recordedDomainStateHash"),row.get("analysisDomainStateHash"));
            }
            assertEquals(before,projector.coordinator().verificationSnapshot());
            assertTrue(new NativeRuntimeReplay().replay(bundle).complete());
        }
    }
    private static VerificationOutcome externalNoB(RuntimeVerificationResult result) {
        return result.outcomes().stream().filter(value->value.constraintId().equals("EXTERNAL:LiveRuntimePropertyArtifact::NoB"))
                .findFirst().orElseThrow().outcome();
    }
    private void coordinatorGap(NativeRuntimeProjector projector) { projector.coordinator().coverageGap("TEST_SOURCE_GAP"); }
}
