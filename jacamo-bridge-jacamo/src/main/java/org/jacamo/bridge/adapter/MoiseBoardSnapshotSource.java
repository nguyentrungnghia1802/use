package org.jacamo.bridge.adapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import ora4mas.nopl.GroupBoard;
import ora4mas.nopl.SchemeBoard;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.SourceWatermark;

/** Exact official board snapshots. Polling completeness is explicitly PARTIAL for intermediate events. */
public final class MoiseBoardSnapshotSource implements SnapshotSource {
    private final String sourceId;
    private final String organisation;
    private final Set<String> excludedOrganisations;
    private final String sessionId;
    private final Supplier<? extends Collection<? extends GroupBoard>> groups;
    private final Supplier<? extends Collection<? extends SchemeBoard>> schemes;
    private final AtomicLong sequence = new AtomicLong();

    /** Captures one organization declared statically by the JCM project. */
    public MoiseBoardSnapshotSource(String organisation, String sessionId,
                                    Supplier<? extends Collection<? extends GroupBoard>> groups,
                                    Supplier<? extends Collection<? extends SchemeBoard>> schemes) {
        if (organisation == null || organisation.isBlank()) throw new IllegalArgumentException("organisation");
        this.sourceId = "moise:" + organisation;
        this.organisation = organisation;
        this.excludedOrganisations = Set.of();
        this.sessionId = java.util.Objects.requireNonNull(sessionId);
        this.groups = java.util.Objects.requireNonNull(groups);
        this.schemes = java.util.Objects.requireNonNull(schemes);
    }

    /** Captures organizations created dynamically at runtime, excluding every statically declared organization. */
    public MoiseBoardSnapshotSource(Set<String> excludedOrganisations, String sessionId,
                                    Supplier<? extends Collection<? extends GroupBoard>> groups,
                                    Supplier<? extends Collection<? extends SchemeBoard>> schemes) {
        this.sourceId = "moise:dynamic";
        this.organisation = null;
        this.excludedOrganisations = Set.copyOf(excludedOrganisations);
        this.sessionId = java.util.Objects.requireNonNull(sessionId);
        this.groups = java.util.Objects.requireNonNull(groups);
        this.schemes = java.util.Objects.requireNonNull(schemes);
    }

    @Override public String sourceId() { return sourceId; }

    @Override public void attach(Consumer<RuntimeEvent> observer) {
        // Board API has no complete lifecycle hook; authoritative reconciliations are snapshots.
    }

    @Override public SourceWatermark watermark() { return new SourceWatermark(sourceId(), sequence.get()); }

    @Override public String topologyFingerprint() {
        var values = new TreeSet<String>();
        groups.get().stream().filter(java.util.Objects::nonNull)
                .filter(board -> acceptsOrganisation(board.getOEId()))
                .forEach(board -> values.add("g:" + board.getOEId() + ":" + board.getArtId()));
        schemes.get().stream().filter(java.util.Objects::nonNull)
                .filter(board -> acceptsOrganisation(board.getOEId()))
                .forEach(board -> values.add("s:" + board.getOEId() + ":" + board.getArtId()));
        return AdapterEvidence.digest(String.join("\n", values).getBytes());
    }

    @Override public List<RuntimeFact> capture() {
        var facts = new ArrayList<RuntimeFact>();
        for (GroupBoard board : List.copyOf(groups.get())) {
            if (board == null) throw new IllegalStateException("MOISE_GROUP_BOARD_INVALID");
            String scope = board.getOEId();
            if (!acceptsOrganisation(scope)) continue;
            if (board.getGrpState() == null || board.getSpec() == null)
                throw new IllegalStateException("MOISE_GROUP_BOARD_INVALID");
            var state = board.getGrpState().clone();
            if (!board.getArtId().equals(state.getId()))
                throw new IllegalStateException("MOISE_BOARD_IDENTITY_MISMATCH");
            var boardId = id(scope, "group-board", state.getId(), board.getArtId());
            facts.add(new RuntimeFact(boardId, RuntimeFactKind.GROUP_BOARD,
                    Map.of("spec", board.getSpec().getId(), "wellFormed", board.isWellFormed()), List.of(),
                    ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of()));
            state.getPlayers().forEach(player -> facts.add(new RuntimeFact(
                    id(scope, "role-player", player.getAg() + "/" + player.getTarget() + "/" + state.getId(),
                            board.getArtId()), RuntimeFactKind.ROLE_PLAYER,
                    Map.of("agent", player.getAg(), "role", player.getTarget(), "group", state.getId()),
                    List.of(), ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of())));
        }
        for (SchemeBoard board : List.copyOf(schemes.get())) {
            if (board == null) throw new IllegalStateException("MOISE_SCHEME_BOARD_INVALID");
            String scope = board.getOEId();
            if (!acceptsOrganisation(scope)) continue;
            if (board.getSchState() == null || board.getSpec() == null)
                throw new IllegalStateException("MOISE_SCHEME_BOARD_INVALID");
            var state = board.getSchState().clone();
            if (!board.getArtId().equals(state.getId()))
                throw new IllegalStateException("MOISE_BOARD_IDENTITY_MISMATCH");
            facts.add(new RuntimeFact(id(scope, "scheme-board", state.getId(), board.getArtId()),
                    RuntimeFactKind.SCHEME_BOARD,
                    Map.of("spec", board.getSpec().getId(), "wellFormed", board.isWellFormed()), List.of(),
                    ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of()));
            state.getPlayers().forEach(player -> facts.add(new RuntimeFact(
                    id(scope, "mission-commitment", player.getAg() + "/" + player.getTarget() + "/" + state.getId(),
                            board.getArtId()), RuntimeFactKind.MISSION_COMMITMENT,
                    Map.of("agent", player.getAg(), "mission", player.getTarget(), "scheme", state.getId()),
                    List.of(), ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of())));
            board.getSpec().getGoals().forEach(goal -> facts.add(new RuntimeFact(
                    id(scope, "organisational-goal-state", state.getId() + "/" + goal.getId(), board.getArtId()),
                    RuntimeFactKind.ORGANISATIONAL_GOAL_STATE,
                    Map.of("scheme", state.getId(), "goal", goal.getId(),
                            "state", state.isSatisfied(goal) ? "SATISFIED" : "NOT_SATISFIED"),
                    List.of(), ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of())));
        }
        sequence.incrementAndGet();
        facts.sort(Comparator.comparing(fact -> fact.id().canonical()));
        return facts;
    }

    boolean acceptsOrganisation(String value) {
        if (value == null || value.isBlank()) return false;
        return organisation == null ? !excludedOrganisations.contains(value) : organisation.equals(value);
    }

    private BridgeEntityId id(String scope, String kind, String local, String incarnation) {
        return new BridgeEntityId("moise", "organisation", kind, scope, local, sessionId + ":" + incarnation);
    }

    @Override public Completeness completeness() { return Completeness.PARTIAL; }
    @Override public void close() { }
}
