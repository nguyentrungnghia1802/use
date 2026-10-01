package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.ui.RuntimeHistoryRows;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class RuntimeHistoryTest {
    @TempDir Path root;
    @Test void perConstraintChangesPreserveABAAndDuplicateVersionsAndProfileIntervals() throws Exception {
        try (var projector = projector()) {
            var coordinator=projector.coordinator(); coordinator.loadProfileSource("a.ocl", RuntimeVerificationCoordinatorTest.PROFILE);
            coordinator.manualVerify(); projector.apply(delta("B",1,"B")); projector.apply(delta("A",2,"A")); coordinator.manualVerify();
            coordinator.loadProfileSource("same.ocl",RuntimeVerificationCoordinatorTest.PROFILE);
            var page=coordinator.journal().tailPage(128);
            var changed=RuntimeHistoryRows.expand(page,true,false).stream()
                    .filter(row -> row.outcome().constraintId().equals("EXTERNAL:ObservablePropertySnapshot::NoB")).toList();
            assertEquals(List.of(VerificationOutcome.PASS,VerificationOutcome.FAIL,VerificationOutcome.PASS,VerificationOutcome.PASS),
                    changed.stream().map(row -> row.outcome().outcome()).toList());
            assertTrue(page.entries().stream().map(entry->entry.result().stateVersion()).distinct().count()<page.entries().size());
            assertEquals(page.entries(),coordinator.journal().page(0,128).entries());
            assertTrue(changed.get(1).detail().contains("Source/sequence: cartago / 1"));
            assertNotEquals(changed.get(2).entry().intervalId(),changed.get(3).entry().intervalId());
        }
    }
    @Test void evidenceOnlyDoesNotReplaceFormalOutcomesAndGapRemainsVisibleThroughFilter() throws Exception {
        try(var projector=projector()) {
            var coordinator=projector.coordinator(); coordinator.loadProfileSource("a.ocl",RuntimeVerificationCoordinatorTest.PROFILE);
            coordinator.transaction("EVENT","observation","jason",1,java.time.Instant.EPOCH,java.util.Map.of(),false,
                    () -> new RuntimeVerificationCoordinator.Mutation<>(null,false));
            long version=coordinator.latest().stateVersion(); coordinator.coverageGap("test GAP");
            var page=coordinator.journal().tailPage(128);
            assertTrue(RuntimeHistoryRows.expand(page,true,true).stream().anyMatch(row->row.event().contains("OBSERVED/EVIDENCE_ONLY")));
            assertTrue(RuntimeHistoryRows.expand(page,true,false).stream().anyMatch(row->row.entry().kind().equals("COVERAGE")));
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
