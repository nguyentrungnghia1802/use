package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class NativeOclLaunchEvidenceTest {
    @TempDir Path directory;

    @Test void consumerKeepsOldArgumentsAndAcceptsOnlyExplicitExistingOclProfile() throws Exception {
        Path secret = Files.writeString(directory.resolve("secret"), "test");
        Path jcm = Files.writeString(directory.resolve("project.jcm"), "mas test {} ");
        String[] old = {"12345", secret.toString(), "1".repeat(64), jcm.toString(), directory.toString(),
                directory.toString(), "1", "1024", "5000", "true"};
        assertNull(JaCaMoBridgeNativeConsumerMain.Options.parse(old).oclProfile());
        String[] next = java.util.Arrays.copyOf(old, 11);
        Path ocl = Files.writeString(directory.resolve("constraints.ocl"), "context Agent inv Test: true");
        next[10] = ocl.toString();
        assertEquals(ocl.toAbsolutePath().normalize(), JaCaMoBridgeNativeConsumerMain.Options.parse(next).oclProfile());
        next[10] = directory.resolve("missing.ocl").toString();
        assertThrows(IllegalArgumentException.class, () -> JaCaMoBridgeNativeConsumerMain.Options.parse(next));
        next[10] = jcm.toString();
        assertThrows(IllegalArgumentException.class, () -> JaCaMoBridgeNativeConsumerMain.Options.parse(next));
        String[] barrier = java.util.Arrays.copyOf(old, 12);
        barrier[10] = ocl.toString(); barrier[11] = directory.resolve("consumer-ready.flag").toString();
        assertEquals(Path.of(barrier[11]), JaCaMoBridgeNativeConsumerMain.Options.parse(barrier).consumerReadyFile());
        Files.writeString(Path.of(barrier[11]), "stale readiness");
        assertThrows(IllegalArgumentException.class, () -> JaCaMoBridgeNativeConsumerMain.Options.parse(barrier));
        barrier[10] = "";
        assertThrows(IllegalArgumentException.class, () -> JaCaMoBridgeNativeConsumerMain.Options.parse(barrier));
    }

    @Test void summaryCountsOnlyMutationsAndPreservesFailuresCoverageAndOutcomeChanges() throws Exception {
        var baseline = result(1, "profile:hash", "ocl", "state-a", VerificationOutcome.PASS, false);
        var changed = result(2, "property-update", "cartago", "state-b", VerificationOutcome.FAIL, false);
        var skipped = result(2, "gap", "bridge", "state-b", VerificationOutcome.SKIPPED, true);
        Path journal = directory.resolve("runtime.jsonl");
        Files.writeString(journal, line("PROFILE", baseline) + line("EVENT", baseline)
                + line("EVENT", changed) + line("MANUAL", changed) + line("COVERAGE", skipped));
        var summary = NativeOclLaunchEvidence.summarizeJournal(journal, baseline);
        assertEquals(1L, summary.get("postLoadRuntimeRechecks"));
        assertEquals(1L, summary.get("changedRuntimeStatesRetainedWithExternalFail"));
        assertEquals(1L, summary.get("staleEntries"));
        var constraints = CanonicalJson.object(summary.get("perConstraint"));
        var evidence = CanonicalJson.object(constraints.get("EXTERNAL:ObservablePropertySnapshot::Check"));
        assertEquals(List.of("PASS", "FAIL", "SKIPPED"), evidence.get("observedOutcomes"));
        assertEquals(true, evidence.get("outcomeChanged"));
    }

    @Test void journalGapIsNeverReportedAsSuccessfulRuntimeEvidence() throws Exception {
        Path journal = Files.writeString(directory.resolve("runtime.jsonl"), "{\"kind\":\"GAP\"}\n");
        assertThrows(IllegalStateException.class, () -> NativeOclLaunchEvidence.summarizeJournal(journal,
                result(1, "profile:hash", "ocl", "a", VerificationOutcome.PASS, false)));
    }

    private static String line(String kind, RuntimeVerificationResult result) {
        return new String(CanonicalJson.encode(Map.of("kind", kind, "result", result.toMap())), StandardCharsets.UTF_8) + "\n";
    }
    private static RuntimeVerificationResult result(long version, String event, String source, String hash,
            VerificationOutcome outcome, boolean stale) {
        return new RuntimeVerificationResult("session", 1, "revision", version, event, source, version,
                Instant.EPOCH, Instant.EPOCH, Instant.EPOCH, "", "constraints", hash,
                List.of(new ExternalOclConstraintService.Outcome("EXTERNAL:ObservablePropertySnapshot::Check",
                        "ObservablePropertySnapshot", outcome, stale ? "COVERAGE_LOST" : outcome.name(), "true")),
                0, stale ? "INCOMPLETE" : "COMPLETE_OBSERVED", stale ? "STALE" : "CURRENT_OBSERVED", "");
    }
}
