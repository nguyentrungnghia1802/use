package org.jacamo.bridge.contract;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Process-local launch control only. Never a Bridge connect flag, global pause or reasoning engine. */
public final class ManagedStartupControl {
    public static final String DIRECTORY_PROPERTY = "use.jacamo.startup.directory";
    public static final String RUN_PROPERTY = "use.jacamo.startup.run-id";
    private ManagedStartupControl() { }

    public static Map<String,Object> owner(String run, Path project, String session, long generation,
            String revision, long pid) {
        if (run.isBlank() || session.isBlank() || generation < 1 || revision.isBlank())
            throw new IllegalArgumentException("STARTUP_OWNER_INVALID");
        return Map.of("runId", run, "project", project.toAbsolutePath().normalize().toString(),
                "sessionId", session, "generation", generation, "modelRevision", revision, "producerPid", pid);
    }
    public static void waiting(Path directory, Map<String,Object> owner, long timeoutSeconds) {
        if (timeoutSeconds < 1) throw new IllegalArgumentException("STARTUP_TIMEOUT_INVALID");
        write(directory, "waiting.json", Map.of("owner", owner, "bootstrapAt", Instant.now().toString(),
                "deadline", Instant.now().plusSeconds(timeoutSeconds).toString()), false);
    }
    public static Map<String,Object> read(Path directory, String name) {
        Path file = child(directory, name);
        try {
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Map.of();
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 8192)
                throw new IllegalStateException("STARTUP_CONTROL_FILE_INVALID:" + name);
            return Map.copyOf(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(file), 8192, 8, 4096)));
        } catch (java.io.IOException error) { throw new IllegalStateException("STARTUP_CONTROL_READ_FAILED", error); }
    }
    public static void requireOwner(Map<String,Object> expected, Map<String,Object> message) {
        Object actual = message.get("owner");
        if (actual == null || !Arrays.equals(CanonicalJson.encode(expected), CanonicalJson.encode(actual)))
            throw new IllegalStateException("STARTUP_OWNERSHIP_MISMATCH");
    }
    public static void request(Path directory, Map<String,Object> owner) {
        requireOwner(owner, read(directory, "waiting.json"));
        try { Files.createFile(child(directory, "request.lock")); }
        catch (java.io.IOException error) { throw new IllegalStateException("STARTUP_ALREADY_REQUESTED", error); }
        write(directory, "request.json", Map.of("owner", owner, "requestedAt", Instant.now().toString()), false);
    }
    public static Map<String,Object> awaitRequest(Path directory, Map<String,Object> owner, Path stop)
            throws InterruptedException {
        var waiting = read(directory, "waiting.json"); requireOwner(owner, waiting);
        Instant deadline = Instant.parse(waiting.get("deadline").toString());
        while (true) {
            if (Files.exists(stop, LinkOption.NOFOLLOW_LINKS) || !read(directory, "cancel.json").isEmpty())
                throw new IllegalStateException("STARTUP_CANCELLED");
            if (!Instant.now().isBefore(deadline)) throw new IllegalStateException("STARTUP_TIMEOUT");
            var request = read(directory, "request.json");
            if (!request.isEmpty()) { requireOwner(owner, request); return request; }
            Thread.sleep(20);
        }
    }
    public static void acknowledged(Path directory, Map<String,Object> request) {
        var ack = new LinkedHashMap<>(request);
        ack.put("startedAt", Instant.now().toString());
        write(directory, "started.json", ack, false);
    }
    public static void cancel(Path directory, String reason) {
        write(directory, "cancel.json", Map.of("reason", reason, "at", Instant.now().toString()), true);
    }
    private static Path child(Path directory, String name) {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IllegalStateException("STARTUP_CONTROL_DIRECTORY_INVALID");
        return root.resolve(name);
    }
    private static void write(Path directory, String name, Map<String,Object> message, boolean replace) {
        Path target = child(directory, name);
        try {
            if (!replace && Files.exists(target, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("STARTUP_ALREADY_REQUESTED:" + name);
            Path temporary = Files.createTempFile(directory, "control-", ".tmp");
            try {
                Files.write(temporary, CanonicalJson.encode(message));
                if (replace) Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                else Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary); }
        } catch (java.io.IOException error) { throw new IllegalStateException("STARTUP_CONTROL_WRITE_FAILED", error); }
    }
}
