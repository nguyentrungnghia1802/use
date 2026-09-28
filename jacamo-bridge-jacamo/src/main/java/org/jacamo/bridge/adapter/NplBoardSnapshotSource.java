package org.jacamo.bridge.adapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import npl.NPLInterpreter;
import npl.NormInstance;
import npl.NormativeListener;
import ora4mas.nopl.NormativeBoard;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.SourceWatermark;

/** Dynamic official NPL view discovered from the live Moise normative boards. */
public final class NplBoardSnapshotSource implements SnapshotSource {
    private final Supplier<? extends Collection<? extends NormativeBoard>> boards;
    private final String sessionId;
    private final long generation;
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, Attached> attached = new HashMap<>();
    private volatile Consumer<RuntimeEvent> observer;
    private volatile Completeness completeness = Completeness.PARTIAL;

    public NplBoardSnapshotSource(String sessionId,
                                  Supplier<? extends Collection<? extends NormativeBoard>> boards) {
        this.sessionId = java.util.Objects.requireNonNull(sessionId);
        this.boards = java.util.Objects.requireNonNull(boards);
        this.generation = Long.parseLong(System.getProperty("jacamo.bridge.generation", "0"));
    }

    @Override public String sourceId() { return "npl"; }

    @Override public synchronized void attach(Consumer<RuntimeEvent> observer) {
        if (this.observer != null) throw new IllegalStateException("NPL_ALREADY_ATTACHED");
        this.observer = java.util.Objects.requireNonNull(observer);
        refreshListeners();
    }

    @Override public SourceWatermark watermark() { return new SourceWatermark(sourceId(), sequence.get()); }

    @Override public synchronized String topologyFingerprint() {
        refreshListeners();
        var topology = new TreeSet<String>();
        for (NormativeBoard board : currentBoards()) {
            NPLInterpreter interpreter = board.getNormativeEngine();
            topology.add(board.getArtId() + ":" + (interpreter == null ? "unavailable" : interpreter.getActivatedNorms()));
        }
        if (topology.isEmpty()) completeness = Completeness.PARTIAL;
        return AdapterEvidence.digest(String.join("\n", topology)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override public synchronized List<RuntimeFact> capture() {
        refreshListeners();
        var facts = new ArrayList<RuntimeFact>();
        boolean complete = !currentBoards().isEmpty();
        for (NormativeBoard board : currentBoards()) {
            String scope = board.getArtId();
            NPLInterpreter interpreter = board.getNormativeEngine();
            if (interpreter == null) { complete = false; continue; }
            add(facts, scope, "ACTIVE", interpreter.getActive());
            add(facts, scope, "FULFILLED", interpreter.getFulfilled());
            add(facts, scope, "UNFULFILLED", interpreter.getUnFulfilled());
            add(facts, scope, "INACTIVE", interpreter.getInactive());
        }
        completeness = complete ? Completeness.COMPLETE : Completeness.PARTIAL;
        facts.sort(Comparator.comparing(fact -> fact.id().canonical()));
        return facts;
    }

    private void add(List<RuntimeFact> facts, String scope, String state, List<NormInstance> values) {
        for (NormInstance value : values) {
            facts.add(new RuntimeFact(id(scope, value), RuntimeFactKind.NORM_INSTANCE,
                    Map.of("state", state, "norm", value.toString(), "modality", modality(value),
                            "deadline", value.getTimeDeadline()), List.of(), ProjectionStatus.EVIDENCE_ONLY,
                    Completeness.COMPLETE, List.of()));
        }
    }

    private String modality(NormInstance value) {
        return value.isObligation() ? "OBLIGATION" : value.isPermission() ? "PERMISSION"
                : value.isProhibition() ? "PROHIBITION" : "UNKNOWN";
    }

    private BridgeEntityId id(String scope, NormInstance value) {
        String local = AdapterEvidence.digest((value.getNorm() + "|" + value.getAg() + "|"
                + value.getUnifier()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 24);
        return new BridgeEntityId("npl", "organisation", "norm-instance", scope, local, sessionId + ":" + scope);
    }

    private synchronized List<NormativeBoard> currentBoards() {
        return boards.get().stream().filter(java.util.Objects::nonNull)
                .map(value -> (NormativeBoard) value)
                .sorted(Comparator.comparing(NormativeBoard::getArtId)).toList();
    }

    private void refreshListeners() {
        for (NormativeBoard board : currentBoards()) {
            NPLInterpreter interpreter = board.getNormativeEngine();
            if (interpreter == null || attached.containsKey(board.getArtId())) continue;
            Attached listener = new Attached(board.getArtId(), interpreter);
            interpreter.addListener(listener);
            attached.put(board.getArtId(), listener);
        }
    }

    @Override public Completeness completeness() { return completeness; }

    @Override public synchronized void close() {
        attached.values().forEach(value -> value.interpreter.removeListener(value));
        attached.clear();
        observer = null;
    }

    private final class Attached implements NormativeListener {
        private final String scope;
        private final NPLInterpreter interpreter;
        private Attached(String scope, NPLInterpreter interpreter) { this.scope = scope; this.interpreter = interpreter; }
        @Override public void created(NormInstance value) { emit(scope, value, RuntimeEventKind.NORM_ACTIVATED); }
        @Override public void fulfilled(NormInstance value) { emit(scope, value, RuntimeEventKind.NORM_FULFILLED); }
        @Override public void unfulfilled(NormInstance value) { emit(scope, value, RuntimeEventKind.NORM_UNFULFILLED); }
        @Override public void inactive(NormInstance value) { emit(scope, value, RuntimeEventKind.NORM_INACTIVATED); }
    }

    private synchronized void emit(String scope, NormInstance value, RuntimeEventKind kind) {
        Consumer<RuntimeEvent> sink = observer;
        if (sink == null) return;
        long next = sequence.incrementAndGet();
        sink.accept(new RuntimeEvent(sessionId + ":" + sourceId() + ":" + next, sessionId, generation,
                System.getProperty("jacamo.bridge.modelRevision", "unnegotiated"), "npl", sourceId(), next,
                Instant.now(), kind, RuntimeFactKind.NORM_INSTANCE, ProjectionStatus.EVIDENCE_ONLY,
                id(scope, value), null, "", "", Map.of(),
                Map.of("state", String.valueOf(value.getState()), "norm", value.toString()),
                new SourceWatermark(sourceId(), next), Completeness.COMPLETE, List.of()));
    }
}
