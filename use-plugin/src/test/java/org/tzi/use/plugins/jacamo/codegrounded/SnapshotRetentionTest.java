package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import org.junit.jupiter.api.Test;

class SnapshotRetentionTest {
    @Test void pinsFailurePreviousAndConfirmationAcrossTailEviction() throws Exception {
        try(var projector=projector()) {
            var coordinator=projector.coordinator();var history=coordinator.snapshots();
            var previous=coordinator.verificationSnapshot();
            projector.apply(delta("failing",1,"B"));var failing=coordinator.verificationSnapshot();history.pinFailure(failing.snapshotId());
            assertEquals(previous.snapshotId(),history.previousFailure().snapshotId());
            assertNotNull(history.passingBeforeFailure());
            for(int i=2;i<35;i++) projector.apply(delta("tail-"+i,i,"value-"+i));
            assertTrue(history.snapshots().size()<=8);assertFalse(history.snapshots().contains(failing));
            history.pinFailure(coordinator.verificationSnapshot().snapshotId());assertEquals(failing.snapshotId(),history.failure().snapshotId());
            history.pinConfirmation(coordinator.verificationSnapshot());assertNotEquals(history.failure().snapshotId(),history.confirmation().snapshotId());
            assertTrue(history.failure().image().soil().contains("'B'"));assertTrue(history.retainedBytes()<16L*1024*1024);
            assertThrows(IllegalArgumentException.class,()->history.pinConfirmation(failing));
            history.clear();assertTrue(history.snapshots().isEmpty());assertNull(history.failure());
            assertNull(history.previousFailure());assertNull(history.confirmation());assertEquals(0,history.retainedBytes());
        }
    }
}
