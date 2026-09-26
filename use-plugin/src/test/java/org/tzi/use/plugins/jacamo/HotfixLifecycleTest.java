package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tzi.use.plugins.jacamo.runtime.MirrorState;

/** Atomic workspace replacement regression at the authoritative Bridge boundary. */
class HotfixLifecycleTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"rebuild", "profile", "reimport"})
    void liveWorkspaceReplacementKeepsBridgeAndFullVerificationTogether(String action) throws Exception {
        Path entry = Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm");
        try (var facade = BridgeFacadeTestSupport.facade(entry)) {
            assertEquals(MirrorState.OFFLINE, facade.runtimeStatus().state());
            var initial = facade.importProject(entry);
            assertEquals(MirrorState.LIVE, facade.runtimeStatus().state());
            switch (action) {
                case "rebuild" -> assertEquals(initial.projectId(), facade.rebuild().projectId());
                case "profile" -> facade.loadVerificationProfile(Files.writeString(temporary.resolve("extra.ocl"),
                        "context Agent inv HotfixAgentName: self.name.size() > 0"));
                default -> assertEquals(initial.projectId(), facade.importProject(entry).projectId());
            }
            assertEquals(MirrorState.LIVE, facade.runtimeStatus().state());
            assertTrue(facade.projectSummary().structureValid());
            assertNotNull(facade.runFullVerification());
            facade.resyncRuntime();
            assertEquals(MirrorState.LIVE, facade.runtimeStatus().state());
            facade.disconnectRuntime();
            assertEquals(MirrorState.STALE, facade.runtimeStatus().state());
            facade.connectRuntime();
            assertEquals(MirrorState.LIVE, facade.runtimeStatus().state());
        }
    }

    @Test void failedBridgeRebuildLeavesTheLiveWorkspaceIntact() throws Exception {
        Path entry = Path.of("src/test/resources/canonical-cases/hello-world/helloworld.jcm");
        var unavailable = new java.util.concurrent.atomic.AtomicBoolean();
        try (var facade = BridgeFacadeTestSupport.facade(entry, unavailable::get)) {
            var summary = facade.importProject(entry);
            unavailable.set(true);
            assertThrows(RuntimeException.class, facade::rebuild);
            assertEquals(summary, facade.projectSummary());
            assertEquals(MirrorState.LIVE, facade.runtimeStatus().state());
        }
    }
}
