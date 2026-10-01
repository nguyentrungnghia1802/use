package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;

/** Bounded memory tail and bounded, append-only hash-chained JSONL on disk. Fail closed on lost persistence. */
public final class RuntimeEventJournal {
    private final Path path;
    private final int capacity;
    private final long maxBytes;
    private final ArrayDeque<RuntimeVerificationResult> history = new ArrayDeque<>();
    private String chain = "";
    private long ordinal, bytes;
    private boolean gap;
    private String diagnostic = "";
    public RuntimeEventJournal(Path directory, int capacity, long maxBytes) {
        if (capacity < 1 || maxBytes < 32_768) throw new IllegalArgumentException("JOURNAL_LIMIT_INVALID");
        this.capacity = capacity; this.maxBytes = maxBytes;
        try {
            Files.createDirectories(directory);
            path = Files.createTempFile(directory, "runtime-", ".jsonl");
        } catch (java.io.IOException error) { throw new IllegalStateException("JOURNAL_CREATE_FAILED", error); }
    }
    public synchronized boolean append(String kind, Map<String, Object> payload, RuntimeVerificationResult result) {
        if (gap) return false;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ordinal", ordinal); body.put("previousHash", chain); body.put("kind", kind);
            body.put("payload", payload); body.put("result", result.toMap());
            String hash = ExternalOclConstraintService.sha256(CanonicalJson.encode(body));
            body.put("entryHash", hash);
            byte[] line = (new String(CanonicalJson.encode(body), StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8);
            if (bytes + line.length > maxBytes - 4096) { fail("JOURNAL_OVERFLOW"); return false; }
            Files.write(path, line, StandardOpenOption.APPEND);
            bytes += line.length; chain = hash; ordinal++; remember(result); return true;
        } catch (RuntimeException | java.io.IOException error) { fail("JOURNAL_IO_OR_PAYLOAD_ERROR:" + error.getMessage()); return false; }
    }
    private void fail(String reason) {
        gap = true; diagnostic = reason;
        try {
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("ordinal", ordinal); marker.put("previousHash", chain); marker.put("kind", "GAP");
            marker.put("diagnostic", reason);
            marker.put("entryHash", ExternalOclConstraintService.sha256(CanonicalJson.encode(marker)));
            Files.writeString(path, new String(CanonicalJson.encode(marker), StandardCharsets.UTF_8) + "\n", StandardOpenOption.APPEND);
        } catch (java.io.IOException ignored) { /* In-memory gap remains visible; export is explicitly rejected. */ }
    }
    public synchronized void remember(RuntimeVerificationResult result) {
        if (history.size() == capacity) history.removeFirst();
        history.addLast(result);
    }
    public synchronized List<RuntimeVerificationResult> history() { return List.copyOf(history); }
    public Path path() { return path; }
    public synchronized boolean hasGap() { return gap; }
    public synchronized String diagnostic() { return diagnostic; }
    public synchronized long persistedEntries() { return ordinal; }
    public synchronized long retainedFromVersion() { return history.isEmpty() ? 0 : history.getFirst().stateVersion(); }
}
