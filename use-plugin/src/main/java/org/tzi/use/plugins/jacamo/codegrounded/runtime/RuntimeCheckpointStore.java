package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;

/** Baseline is never evicted; subsequent persisted checkpoints have a bounded retention window. */
public final class RuntimeCheckpointStore {
    public record Checkpoint(String id, long stateVersion, String stateHash, Path commands) { }
    private final Path directory;
    private final ArrayDeque<Checkpoint> tail = new ArrayDeque<>();
    private Checkpoint baseline;
    public RuntimeCheckpointStore(Path directory) { this.directory = directory; }
    public Checkpoint store(String id, long version, String hash, String commands) {
        if (commands.getBytes(StandardCharsets.UTF_8).length > 16 * 1024 * 1024)
            throw new IllegalStateException("CHECKPOINT_TOO_LARGE");
        try {
            Files.createDirectories(directory);
            Path output = Files.createTempFile(directory, "state-" + version + "-", ".cmd");
            Files.writeString(output, commands, StandardCharsets.UTF_8);
            Checkpoint value = new Checkpoint(id, version, hash, output);
            if (baseline == null) baseline = value;
            else {
                if (tail.size() == 8) Files.deleteIfExists(tail.removeFirst().commands());
                tail.addLast(value);
            }
            return value;
        } catch (java.io.IOException error) { throw new IllegalStateException("CHECKPOINT_IO_FAILED", error); }
    }
    public Checkpoint baseline() { return baseline; }
    public List<Checkpoint> checkpoints() { return List.copyOf(tail); }
}
