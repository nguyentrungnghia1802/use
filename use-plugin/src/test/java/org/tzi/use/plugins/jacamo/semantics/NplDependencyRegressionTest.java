package org.tzi.use.plugins.jacamo.semantics;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.NplRuntimeProfile;
class NplDependencyRegressionTest {
    @Test void consumerAndLiveLauncherClasspathUsesTheSameAuditedNplAsProducerTests() {
        assertDoesNotThrow(NplRuntimeProfile::requireSupported);assertEquals("0.6.1",NplRuntimeProfile.evidence().get("artifactVersion"));
        assertEquals("956818edfe9aeff7c4fde35bf1dd276cd71e1da62daf77a2a15ab08f87903332",NplRuntimeProfile.evidence().get("artifactSha256"));
    }
}
