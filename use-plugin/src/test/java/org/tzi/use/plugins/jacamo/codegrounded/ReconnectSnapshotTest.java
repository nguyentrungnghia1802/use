package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import org.junit.jupiter.api.Test;

class ReconnectSnapshotTest {
    @Test void sameProducerResyncPreservesPinsButNewGenerationResetsDiagnosticHistory() throws Exception {
        try(var projector=projector()) {
            var coordinator=projector.coordinator();var system=projector.system();
            projector.apply(delta("fail",1,"B"));var failing=coordinator.verificationSnapshot();coordinator.snapshots().pinFailure(failing.snapshotId());
            projector.applySnapshot(snapshot("same-generation",1));assertEquals(failing.snapshotId(),coordinator.snapshots().failure().snapshotId());
            var confirmed=coordinator.verificationSnapshot();coordinator.snapshots().pinConfirmation(confirmed);
            assertNotEquals(failing.snapshotId(),confirmed.snapshotId());assertSame(system,projector.system());
            projector.applySnapshot(snapshot("new-generation",2),SESSION,2);
            assertNull(coordinator.snapshots().failure());assertEquals(1,coordinator.snapshots().snapshots().size());
            assertEquals(2,coordinator.verificationSnapshot().metadata().generation());
        }
    }
}
