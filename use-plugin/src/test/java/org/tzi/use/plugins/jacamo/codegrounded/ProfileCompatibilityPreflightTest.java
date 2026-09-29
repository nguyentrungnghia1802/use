package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.NativeProfileCompatibilityPreflight;

class ProfileCompatibilityPreflightTest {
    @TempDir Path temporary;

    @Test void nativePhaseRejectsExternalV2ProfileBeforeChangingTheActivatedSystem() throws Exception {
        Session session = new Session();
        Path hello = Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm")
                .toAbsolutePath().normalize();
        var result = CodeGroundedTestFixtures.helloPipeline();
        new NativeUseSessionActivator().activate(session, result);
        var previous = session.system();
        Path profile = Files.writeString(temporary.resolve("legacy-v2.ocl"),
                "context Organization inv LegacyOnly: true");
        var preflight = new NativeProfileCompatibilityPreflight().inspect(profile, previous.model());
        assertFalse(preflight.compatible());
        assertEquals(java.util.List.of("NATIVE_PROFILE_CONTEXT_MISSING:Organization"), preflight.diagnostics());
        assertSame(previous, session.system(), "preflight evidence alone must not mutate the session");
        assertNull(previous.model().getClass("Organization"));
    }
}
