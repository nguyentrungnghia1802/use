package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

/** Snapshot/resync half of confirmation; real control ACK coverage is in the adapter control tests. */
class PausedResyncSnapshotTest {
    @Test void authoritativePostRequestCutCannotOverwriteTheFailingState() throws Exception {
        try(var projector=projector()) {
            var coordinator=projector.coordinator();var object=projector.mutations().objectForSemanticId(ARTIFACT);
            coordinator.loadProfileSource("value.ocl","context "+object.cls().name()+" inv Value: self.status = 'A'");
            projector.apply(delta("failure",1,"B"));var failure=coordinator.verificationSnapshot();
            assertEquals(1,failure.result().count(VerificationOutcome.FAIL));coordinator.snapshots().pinFailure(failure.snapshotId());
            projector.applySnapshot(snapshot("after-request",1));var confirmed=coordinator.verificationSnapshot();
            coordinator.snapshots().pinConfirmation(confirmed);
            assertEquals(VerificationOutcome.PASS,confirmed.result().outcomes().stream()
                    .filter(o->o.constraintId().equals("EXTERNAL:"+object.cls().name()+"::Value")).findFirst().orElseThrow().outcome());
            assertEquals("'B'",coordinator.snapshots().failure().image().objects().get(object.name()).attributes().get("status"));
            assertEquals("'A'",confirmed.image().objects().get(object.name()).attributes().get("status"));
            coordinator.coverageGap("DISCONNECTED");assertEquals("STALE",coordinator.verificationSnapshot().result().freshness());
            assertEquals(failure.snapshotId(),coordinator.snapshots().failure().snapshotId());
        }
    }
}
