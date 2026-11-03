package org.tzi.use.plugins.jacamo.semantics;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.NplRuntimeProfile;
class NplDependencyRegressionTest {
    @Test void consumerAndLiveLauncherClasspathUsesTheSameAuditedNplAsProducerTests() {
        assertDoesNotThrow(NplRuntimeProfile::requireSupported);
        String version=(String)NplRuntimeProfile.evidence().get("artifactVersion");
        assertTrue("0.6.1".equals(version)||"0.6.1-checkpoint-SNAPSHOT".equals(version),version);
        assertNotNull(NplRuntimeProfile.evidence().get("artifactSha256"));
    }
}
