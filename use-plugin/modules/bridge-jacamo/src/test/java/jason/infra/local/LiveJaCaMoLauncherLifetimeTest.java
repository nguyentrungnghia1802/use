package jason.infra.local;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiveJaCaMoLauncherLifetimeTest {
    @TempDir Path temporary;

    @Test void interactiveLauncherUsesOfficialMasConsoleAndHeadlessKeepsConsoleHandler() {
        String interactive = LiveJaCaMoLauncherMain.launchLogProperties(false);
        assertTrue(interactive.contains("handlers=jason.runtime.MASConsoleLogHandler"));
        assertTrue(interactive.contains("MASConsoleLogFormatter"));
        assertFalse(interactive.contains("handlers=java.util.logging.ConsoleHandler"));

        String headless = LiveJaCaMoLauncherMain.launchLogProperties(true);
        assertTrue(headless.contains("handlers=java.util.logging.ConsoleHandler"));
        assertFalse(headless.contains("MASConsoleLogHandler"));
    }

    @Test void evidenceProcessCapturesBootstrapErrorsWithoutReplacingTheNativeConsole() {
        String old=System.getProperty("jacamo.launch.captureConsole");
        try {
            System.setProperty("jacamo.launch.captureConsole","true");
            assertTrue(LiveJaCaMoLauncherMain.launchLogProperties(false)
                    .contains("handlers=jason.runtime.MASConsoleLogHandler,java.util.logging.ConsoleHandler"));
        } finally {
            if(old==null) System.clearProperty("jacamo.launch.captureConsole"); else System.setProperty("jacamo.launch.captureConsole",old);
        }
    }

    @Test void onlyAuditedLocalPlatformsHaveControlledStartAuthority() {
        assertDoesNotThrow(() -> LiveJaCaMoLauncherMain.validateControlledPlatforms(java.util.List.of(
                "jacamo.platform.Cartago", "jacamo.platform.Moise", "jacamo.platform.Sai", "org.jacamo.bridge.adapter.JaCaMoBridgePlatform")));
        for (String name : java.util.List.of("jacamo.platform.Jade", "example.CustomPlatform"))
            assertTrue(assertThrows(IllegalStateException.class,
                    () -> LiveJaCaMoLauncherMain.validateControlledPlatforms(java.util.List.of(name))).getMessage().startsWith("UNSUPPORTED_STARTUP_CONTROL"));
    }

    @Test void interactiveLifetimeSurvivesABoundedDeadlineAndStopsOnlyOnStopFile() throws Exception {
        Path stop = temporary.resolve("stop");
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> { LiveJaCaMoLauncherMain.awaitStopFile(stop, 0); return null; });
            try {
                assertThrows(java.util.concurrent.TimeoutException.class, () -> future.get(1200, TimeUnit.MILLISECONDS));
                assertFalse(future.isDone());
            } finally { Files.writeString(stop, "stop"); }
            assertNull(future.get(2, TimeUnit.SECONDS));
        }
    }

    @Test void boundedLifetimeStillTimesOutAndExistingStopFileCompletes() throws Exception {
        Path stop = temporary.resolve("stop");
        assertEquals("REAL_JACAMO_TIMEOUT", assertThrows(IllegalStateException.class,
                () -> LiveJaCaMoLauncherMain.awaitStopFile(stop, 1)).getMessage());
        Files.writeString(stop, "stop");
        assertDoesNotThrow(() -> LiveJaCaMoLauncherMain.awaitStopFile(stop, 1));
        assertThrows(IllegalArgumentException.class, () -> LiveJaCaMoLauncherMain.awaitStopFile(stop, -1));
    }

    @Test void startupBarrierWaitsForTheConsumerAndDoesNotStartOnTheStopFile() throws Exception {
        Path ready = temporary.resolve("ready"), stop = temporary.resolve("stop");
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> { LiveJaCaMoLauncherMain.awaitConsumerReady(ready, stop, 5); return null; });
            assertThrows(java.util.concurrent.TimeoutException.class, () -> future.get(200, TimeUnit.MILLISECONDS));
            Files.writeString(ready, "compiled baseline");
            assertNull(future.get(2, TimeUnit.SECONDS));
        }
        Files.delete(ready); Files.writeString(stop, "cancel");
        assertEquals("REAL_JACAMO_START_CANCELLED", assertThrows(IllegalStateException.class,
                () -> LiveJaCaMoLauncherMain.awaitConsumerReady(ready, stop, 1)).getMessage());
    }

    @Test void startupBarrierCannotWaitForeverOrAcceptADirectoryAsReadiness() throws Exception {
        Path ready = Files.createDirectory(temporary.resolve("not-a-ready-file"));
        assertEquals("REAL_JACAMO_CONSUMER_READY_TIMEOUT", assertThrows(IllegalStateException.class,
                () -> LiveJaCaMoLauncherMain.awaitConsumerReady(ready, temporary.resolve("stop"), 1)).getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> LiveJaCaMoLauncherMain.awaitConsumerReady(ready, temporary.resolve("stop"), 0));
    }
}
