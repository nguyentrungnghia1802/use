package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay;

class NativeRuntimeReplayTest {
    @TempDir Path directory;
    @Test void exportedUseSoilOclAndJournalReplaySameVersionsStatesAndResults() throws Exception {
        var pipeline = CodeGroundedTestFixtures.helloPipeline();
        var projector = new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector(pipeline, SESSION, 1, REVISION);
        projector.applySnapshot(snapshot("initial", 0));
        var coordinator = projector.coordinator();
        coordinator.loadProfileSource("constraints.ocl", RuntimeVerificationCoordinatorTest.PROFILE);
        projector.apply(delta("B", 1, "B")); projector.apply(delta("A", 2, "A")); coordinator.manualVerify();
        projector.applySnapshot(snapshot("resync", 2), SESSION, 2);
        var replay = new NativeRuntimeReplay(); replay.exportBundle(projector, pipeline.export().useText(), directory);
        var report = replay.replay(directory);
        assertTrue(report.complete(), report.diagnostics().toString());
        assertEquals(coordinator.journal().persistedEntries(), report.checkedEntries());
        assertEquals(coordinator.latest().stateHash(), report.finalStateHash());
        assertEquals(coordinator.latest().resultHash(), report.finalResultHash());
        assertEquals(5, Files.list(directory).count());
    }
    @Test void corruptionMissingFileAndGapFailClosed() throws Exception {
        var pipeline = CodeGroundedTestFixtures.helloPipeline();
        var projector = new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector(pipeline, SESSION, 1, REVISION);
        projector.applySnapshot(snapshot("initial", 0)); projector.coordinator().coverageGap("SOURCE_OVERFLOW");
        var replay = new NativeRuntimeReplay(); replay.exportBundle(projector, pipeline.export().useText(), directory);
        // A marked incomplete observed timeline is replayable but must retain SKIPPED, never manufacture PASS.
        assertFalse(replay.replay(directory).complete());
        assertEquals("REPLAY_PARTIAL_COVERAGE_GAP", replay.replay(directory).diagnostics().getFirst());
        Files.writeString(directory.resolve("runtime.jsonl"), "{}\n", java.nio.file.StandardOpenOption.APPEND);
        var corrupt = replay.replay(directory);
        assertFalse(corrupt.complete()); assertTrue(corrupt.diagnostics().getFirst().contains("HASH_MISMATCH"));
        Files.delete(directory.resolve("constraints.ocl"));
        assertFalse(replay.replay(directory).complete());
    }
}
