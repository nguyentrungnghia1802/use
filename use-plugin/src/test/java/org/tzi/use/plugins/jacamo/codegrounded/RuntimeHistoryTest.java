package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class RuntimeHistoryTest {
    @TempDir Path root;
    @Test void journalPreservesABAAndDuplicateVersionsAndProfileIntervals() throws Exception {
        try (var projector = projector()) {
            var coordinator=projector.coordinator(); coordinator.loadProfileSource("a.ocl", RuntimeVerificationCoordinatorTest.PROFILE);
            coordinator.manualVerify(); projector.apply(delta("B",1,"B")); projector.apply(delta("A",2,"A")); coordinator.manualVerify();
            coordinator.loadProfileSource("same.ocl",RuntimeVerificationCoordinatorTest.PROFILE);
            var page=coordinator.journal().tailPage(128);
            var outcomes=page.entries().stream().flatMap(entry->entry.result().outcomes().stream())
                    .filter(outcome->outcome.constraintId().equals("EXTERNAL:LiveRuntimePropertyArtifact::NoB"))
                    .map(outcome->outcome.outcome()).toList();
            int failed=outcomes.indexOf(VerificationOutcome.FAIL);
            assertTrue(failed>0 && failed<outcomes.size()-1, outcomes.toString());
            assertEquals(VerificationOutcome.PASS,outcomes.getFirst());
            assertEquals(VerificationOutcome.PASS,outcomes.getLast());
            assertTrue(page.entries().stream().map(entry->entry.result().stateVersion()).distinct().count()<page.entries().size());
            assertEquals(page.entries(),coordinator.journal().page(0,128).entries());
            assertTrue(page.entries().stream().anyMatch(entry->entry.result().sourceId().equals("cartago") && entry.result().sourceSequence()==1));
            assertTrue(page.entries().stream().map(RuntimeHistoryPage.Entry::intervalId).distinct().count()>1);
        }
    }
    @Test void evidenceOnlyDoesNotReplaceFormalOutcomesAndJournalRetainsCoverageGap() throws Exception {
        try(var projector=projector()) {
            var coordinator=projector.coordinator(); coordinator.loadProfileSource("a.ocl",RuntimeVerificationCoordinatorTest.PROFILE);
            coordinator.transaction("EVENT","observation","jason",1,java.time.Instant.EPOCH,java.util.Map.of(),false,
                    () -> new RuntimeVerificationCoordinator.Mutation<>(null,false));
            long version=coordinator.latest().stateVersion(); coordinator.coverageGap("test GAP");
            var page=coordinator.journal().tailPage(128);
            assertTrue(page.entries().stream().anyMatch(entry->entry.kind().equals("EVENT")
                    && entry.result().eventId().equals("observation")
                    && entry.result().outcomes().stream().allMatch(outcome->outcome.constraintId().startsWith("OBSERVED:"))));
            assertTrue(page.entries().stream().anyMatch(entry->entry.kind().equals("COVERAGE")));
            assertEquals(version,coordinator.latest().stateVersion());
        }
    }
    @Test void memoryEvictionIsNotDiskLossAndPersistedPagesValidateIntegrityOffEdt() throws Exception {
        try(var projector=projector()) {
            var source=projector.coordinator().latest();
            var journal=new RuntimeEventJournal(root,2,128*1024);
            for(int index=0;index<4;index++) assertTrue(journal.append("MANUAL",java.util.Map.of(),source));
            assertEquals(2,journal.tailPage(2).retainedFromOrdinal()); assertFalse(journal.tailPage(2).gap());
            assertEquals(2,journal.page(0,2).entries().size()); assertEquals(4,journal.page(2,2).nextOrdinal());
            javax.swing.SwingUtilities.invokeAndWait(()->assertThrows(IllegalStateException.class,()->journal.page(0,2)));
            Files.writeString(journal.path(),Files.readString(journal.path()).replace("manual","tampered"));
            // Fixture eventId isn't necessarily manual: always corrupt a hash, without attempting a repair.
            Files.writeString(journal.path(),Files.readString(journal.path()).replace("\"previousHash\":\"\"","\"previousHash\":\"broken\""));
            assertThrows(RuntimeException.class,()->journal.page(0,2));
        }
    }
}
