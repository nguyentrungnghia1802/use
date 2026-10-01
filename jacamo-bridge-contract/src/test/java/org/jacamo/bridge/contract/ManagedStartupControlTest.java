package org.jacamo.bridge.contract;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManagedStartupControlTest {
    @TempDir Path root;
    Map<String,Object> owner() { return ManagedStartupControl.owner("run-1", root.resolve("p.jcm"), "session", 1, "revision", ProcessHandle.current().pid()); }
    @Test void waitsForOwnedRequestAndAcknowledgesExactlyOnce() throws Exception {
        var owner = owner(); ManagedStartupControl.waiting(root, owner, 5);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var waiting = pool.submit(() -> ManagedStartupControl.awaitRequest(root, owner, root.resolve("stop")));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> waiting.get(100, TimeUnit.MILLISECONDS));
            assertFalse(Files.exists(root.resolve("started.json")));
            ManagedStartupControl.request(root, owner);
            ManagedStartupControl.acknowledged(root, waiting.get(2, TimeUnit.SECONDS));
            ManagedStartupControl.requireOwner(owner, ManagedStartupControl.read(root, "started.json"));
            assertThrows(IllegalStateException.class, () -> ManagedStartupControl.request(root, owner));
        }
    }
    @Test void staleOwnershipCancelAndTimeoutFailClosed() throws Exception {
        var owner = owner(); ManagedStartupControl.waiting(root, owner, 1);
        assertThrows(IllegalStateException.class, () -> ManagedStartupControl.request(root,
                ManagedStartupControl.owner("old-run", root.resolve("p.jcm"), "session", 1, "revision", ProcessHandle.current().pid())));
        assertEquals("STARTUP_TIMEOUT", assertThrows(IllegalStateException.class,
                () -> ManagedStartupControl.awaitRequest(root, owner, root.resolve("stop"))).getMessage());
        ManagedStartupControl.cancel(root, "close");
        assertEquals("STARTUP_CANCELLED", assertThrows(IllegalStateException.class,
                () -> ManagedStartupControl.awaitRequest(root, owner, root.resolve("stop"))).getMessage());
    }
    @Test void directoryAndOversizedFilesCannotMasqueradeAsControl() throws Exception {
        Files.createDirectory(root.resolve("waiting.json"));
        assertThrows(IllegalStateException.class, () -> ManagedStartupControl.read(root, "waiting.json"));
        Files.writeString(root.resolve("request.json"), "x".repeat(8193));
        assertThrows(IllegalStateException.class, () -> ManagedStartupControl.read(root, "request.json"));
    }
}
