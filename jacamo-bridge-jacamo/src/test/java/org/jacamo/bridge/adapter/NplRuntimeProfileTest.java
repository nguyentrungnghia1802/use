package org.jacamo.bridge.adapter;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class NplRuntimeProfileTest {
    @Test void actualLoadedBinaryAndObservationVersionAgreeWithThePinnedProfile() {
        assertDoesNotThrow(NplRuntimeProfile::requireSupported);
        var evidence=NplRuntimeProfile.evidence();assertEquals("0.6.1-checkpoint-SNAPSHOT",evidence.get("artifactVersion"));
        assertTrue(jacamo.checkpoint.CheckpointBuildIdentity.supports("npl",npl.NormInstance.class));
        try {
            var source=java.nio.file.Path.of(npl.NormInstance.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            assertEquals(org.jacamo.bridge.contract.DistributionFingerprint.digestPath(source),evidence.get("artifactSha256"));
        }catch(java.net.URISyntaxException impossible){throw new AssertionError(impossible);}
        var value=NplActivationCrosswalkTest.instance("obligation");var observation=new NplActivationCrosswalk().observe(java.util.UUID.randomUUID().toString(),value,"SNAPSHOT","ACTIVE");assertEquals("0.6.1-checkpoint-SNAPSHOT",observation.get("sourceVersion"));assertEquals(evidence,observation.get("sourceArtifact"));
    }
}
