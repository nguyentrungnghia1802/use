package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.util.Map;
import org.jacamo.bridge.contract.Completeness;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;

class VerificationSnapshotContractTest {
    @Test void immutableProjectedCutHasExactIdentityRelationsAndCheckpointMetadata() throws Exception {
        try(var projector=projector()) {
            var coordinator=projector.coordinator();var system=projector.system();var state=system.state();
            coordinator.runtimeCapabilities(Map.of("runtime.snapshot","COMPLETE"),Map.of("fixture-producer","1"));
            projector.applySnapshot(snapshot("capability-cut",0));
            var before=coordinator.verificationSnapshot();
            var encoded=org.jacamo.bridge.contract.CanonicalJson.encode(before.toMap());
            assertEquals(encoded.length,before.estimatedBytes());
            assertTrue(before.estimatedBytes()>org.jacamo.bridge.contract.CanonicalJson.encode(before.image().toMap()).length);
            assertEquals(CheckpointType.SNAPSHOT,before.metadata().checkpoint());assertEquals(Completeness.COMPLETE,before.metadata().completeness());
            assertEquals(VerificationSnapshot.SynchronizationState.LIVE,before.metadata().lifecycle());
            assertEquals(Map.of("fixture-producer","1"),before.metadata().sourceVersions());
            var artifact=projector.mutations().objectForSemanticId(ARTIFACT);String name=artifact.name();
            assertEquals("'A'",before.image().objects().get(name).attributes().get("status"));
            assertTrue(before.image().objects().get(name).exactIdentities().contains(ARTIFACT));
            assertTrue(before.image().links().stream().anyMatch(l->l.participants().contains(name)));
            assertThrows(UnsupportedOperationException.class,()->before.image().objects().clear());
            projector.apply(delta("changed",1,"B"));var after=coordinator.verificationSnapshot();
            assertEquals(CheckpointType.AFTER_MUTATION,after.metadata().checkpoint());
            assertTrue(after.metadata().boundarySequence()>before.metadata().boundarySequence());
            assertEquals("'A'",before.image().objects().get(name).attributes().get("status"));
            assertEquals("'B'",after.image().objects().get(name).attributes().get("status"));
            assertNotEquals(before.result().stateHash(),after.result().stateHash());
            assertSame(system,projector.system());assertSame(state,system.state());
            assertFalse(before.image().objects().values().stream().anyMatch(o->o.className().equals("PlanLibrary")));
        }
    }
    @Test void oversizedDiagnosticCutCannotLeaveARecordedPassingVerification() throws Exception {
        String previous=System.getProperty("use.jacamo.snapshot.history.bytes");
        System.setProperty("use.jacamo.snapshot.history.bytes","100000");
        try(var p=projector()) {
            p.coordinator().loadProfileSource("size.ocl","context Agent inv SizeControl: true");
            p.apply(delta("too-large",1,"x".repeat(200000)));
            assertEquals("STALE",p.coordinator().latest().freshness());
            assertTrue(p.coordinator().journal().hasGap());
            assertTrue(p.coordinator().latest().diagnostic().startsWith("SNAPSHOT_CAPTURE_FAILED:"));
            assertNull(p.coordinator().verificationSnapshot().image());
            assertEquals(0,p.coordinator().latest().count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.PASS));
        } finally {if(previous==null)System.clearProperty("use.jacamo.snapshot.history.bytes");else System.setProperty("use.jacamo.snapshot.history.bytes",previous);}
    }
}
