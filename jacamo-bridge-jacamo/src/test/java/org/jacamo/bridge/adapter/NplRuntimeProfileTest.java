package org.jacamo.bridge.adapter;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class NplRuntimeProfileTest {
    @Test void actualLoadedBinaryAndObservationVersionAgreeWithThePinnedProfile() {
        assertDoesNotThrow(NplRuntimeProfile::requireSupported);
        var evidence=NplRuntimeProfile.evidence();assertEquals("0.6.1",evidence.get("artifactVersion"));
        assertEquals("956818edfe9aeff7c4fde35bf1dd276cd71e1da62daf77a2a15ab08f87903332",evidence.get("artifactSha256"));
        var value=NplActivationCrosswalkTest.instance("obligation");var observation=new NplActivationCrosswalk().observe(java.util.UUID.randomUUID().toString(),value,"SNAPSHOT","ACTIVE");assertEquals("0.6.1",observation.get("sourceVersion"));assertEquals(evidence,observation.get("sourceArtifact"));
    }
}
