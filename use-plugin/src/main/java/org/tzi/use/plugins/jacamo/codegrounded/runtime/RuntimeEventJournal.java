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
    private final ArrayDeque<RuntimeHistoryPage.Entry> history = new ArrayDeque<>();
    private String interval = "CORE_ONLY";
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
        return append(kind,payload,result,null);
    }
    public synchronized boolean append(String kind, Map<String,Object> payload, RuntimeVerificationResult result, String installedInterval) {
        if (gap) return false;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ordinal", ordinal); body.put("previousHash", chain); body.put("kind", kind);
            body.put("payload", payload); body.put("result", result.toMap());
            if (kind.equals("PROFILE")) interval = result.sessionId() + ":" + result.generation() + ":" + result.modelRevision()
                    + ":profile:" + result.stateVersion() + ":" + result.constraintSetHash();
            if (installedInterval != null) interval = installedInterval;
            body.put("verificationInterval", interval);
            String hash = ExternalOclConstraintService.sha256(CanonicalJson.encode(body));
            body.put("entryHash", hash);
            byte[] line = (new String(CanonicalJson.encode(body), StandardCharsets.UTF_8) + "\n").getBytes(StandardCharsets.UTF_8);
            if (bytes + line.length > maxBytes - 4096) { fail("JOURNAL_OVERFLOW"); return false; }
            Files.write(path, line, StandardOpenOption.APPEND);
            bytes += line.length; chain = hash; add(new RuntimeHistoryPage.Entry(ordinal++, kind, interval, result, true)); return true;
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
    synchronized void markGap(String reason) {if(!gap)fail(reason);}
    public synchronized void remember(RuntimeVerificationResult result) {
        add(new RuntimeHistoryPage.Entry(ordinal, gap ? "GAP" : "NOT_PERSISTED", interval, result, false));
    }
    private void add(RuntimeHistoryPage.Entry entry) {
        if (history.size() == capacity) history.removeFirst();
        history.addLast(entry);
    }
    public synchronized List<RuntimeVerificationResult> history() { return history.stream().map(RuntimeHistoryPage.Entry::result).toList(); }
    public synchronized RuntimeHistoryPage tailPage(int limit) {
        checkLimit(limit);
        return new RuntimeHistoryPage(history.stream().skip(Math.max(0, history.size()-limit)).toList(), ordinal,
                ordinal, history.isEmpty() ? ordinal : history.getFirst().ordinal(), gap, diagnostic, false);
    }
    /** Disk scan is bounded by journal size and a fixed page; never hold the writer lock across disk IO. */
    public RuntimeHistoryPage page(long offset, int limit) {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("PERSISTED_HISTORY_REQUIRES_WORKER");
        checkLimit(limit);
        if (offset < 0) throw new IllegalArgumentException("HISTORY_OFFSET_INVALID");
        long end; long retained; boolean lost; String reason;
        synchronized (this) { end=ordinal; retained=history.isEmpty()?ordinal:history.getFirst().ordinal(); lost=gap; reason=diagnostic; }
        if (offset >= end) return new RuntimeHistoryPage(List.of(),end,end,retained,lost,reason,true);
        var entries = new java.util.ArrayList<RuntimeHistoryPage.Entry>(limit);
        String previous="", activeInterval="CORE_ONLY"; long position=0;
        try {
            if (Files.isSymbolicLink(path) || Files.size(path) > maxBytes) throw new IllegalStateException("HISTORY_FILE_UNSAFE");
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                while (position < end && position < offset + limit) {
                    String line=reader.readLine(); if (line == null) throw new IllegalStateException("HISTORY_TRUNCATED");
                    var body=new LinkedHashMap<>(CanonicalJson.object(CanonicalJson.decode(line.getBytes(StandardCharsets.UTF_8))));
                    String hash=(String)body.remove("entryHash");
                    if (((Number)body.get("ordinal")).longValue()!=position || !previous.equals(body.get("previousHash"))
                            || !ExternalOclConstraintService.sha256(CanonicalJson.encode(body)).equals(hash))
                        throw new IllegalStateException("HISTORY_CHAIN_CORRUPT:" + position);
                    previous=hash;
                    String kind=(String)body.get("kind");
                    var result=RuntimeVerificationResult.fromMap(CanonicalJson.object(body.get("result")));
                    if (body.containsKey("verificationInterval")) activeInterval=(String)body.get("verificationInterval");
                    else if (kind.equals("PROFILE")) activeInterval="profile:" + position;
                    if (position >= offset) entries.add(new RuntimeHistoryPage.Entry(position,kind,activeInterval,result,true));
                    position++;
                }
            }
            return new RuntimeHistoryPage(entries, position, end, retained, lost, reason, true);
        } catch (java.io.IOException error) { throw new IllegalStateException("HISTORY_READ_FAILED", error); }
    }
    private static void checkLimit(int limit) { if (limit < 1 || limit > 128) throw new IllegalArgumentException("HISTORY_PAGE_LIMIT_INVALID"); }
    public Path path() { return path; }
    public synchronized boolean hasGap() { return gap; }
    public synchronized String diagnostic() { return diagnostic; }
    public synchronized long persistedEntries() { return ordinal; }
    public synchronized long retainedFromVersion() { return history.isEmpty() ? 0 : history.getFirst().result().stateVersion(); }
}
