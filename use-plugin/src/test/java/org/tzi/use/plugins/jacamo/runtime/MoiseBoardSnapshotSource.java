package org.tzi.use.plugins.jacamo.runtime;

import java.util.*;
import java.util.function.Supplier;
import ora4mas.nopl.GroupBoard;
import ora4mas.nopl.SchemeBoard;
import static org.tzi.use.plugins.jacamo.runtime.MoiseRuntimeConnector.*;

/** Read-only adapter over pinned Moise 1.1 launcher boards, not the separate moise.oe.OE API.
 * Suppliers must return the current exact boards of one organisation at a quiescent checkpoint.
 * No global registry search, new OE, event listener, scheduler, or normative inference is used.
 * Polling records net changes only; it cannot establish every intermediate transition.
 */
public final class MoiseBoardSnapshotSource {
    private final String organisation;
    private final Supplier<? extends Collection<? extends GroupBoard>> groups;
    private final Supplier<? extends Collection<? extends SchemeBoard>> schemes;

    public MoiseBoardSnapshotSource(String organisation,
            Supplier<? extends Collection<? extends GroupBoard>> groups,
            Supplier<? extends Collection<? extends SchemeBoard>> schemes) {
        if (organisation == null || organisation.isBlank() || groups == null || schemes == null)
            throw new IllegalArgumentException("MOISE_BOARD_SOURCE_INVALID");
        this.organisation = organisation; this.groups = groups; this.schemes = schemes;
    }

    public String organisation() { return organisation; }

    OrganisationState capture() {
        Map<String, GroupState> groupStates = new TreeMap<>();
        Map<String, SchemeState> schemeStates = new TreeMap<>();
        Set<RoleKey> roles = new TreeSet<>();
        Set<MissionKey> missions = new TreeSet<>();
        Map<GoalKey, String> goals = new TreeMap<>();
        for (GroupBoard board : List.copyOf(groups.get())) {
            if (board == null || !organisation.equals(board.getOEId())
                    || board.getGrpState() == null || board.getSpec() == null)
                throw new IllegalStateException("MOISE_BOARD_OWNER_OR_STATE_INVALID");
            var value = board.getGrpState().clone();
            requireIdentity(board.getArtId(), value.getId());
            if (groupStates.putIfAbsent(value.getId(), new GroupState(value.getId(),
                    board.getSpec().getId(), board.isWellFormed())) != null)
                throw new IllegalStateException("MOISE_BOARD_IDENTITY_DUPLICATE: " + value.getId());
            value.getPlayers().forEach(p -> roles.add(new RoleKey(p.getAg(), p.getTarget(), value.getId())));
        }
        for (SchemeBoard board : List.copyOf(schemes.get())) {
            if (board == null || !organisation.equals(board.getOEId())
                    || board.getSchState() == null || board.getSpec() == null)
                throw new IllegalStateException("MOISE_BOARD_OWNER_OR_STATE_INVALID");
            var value = board.getSchState().clone();
            requireIdentity(board.getArtId(), value.getId());
            if (schemeStates.putIfAbsent(value.getId(), new SchemeState(value.getId(),
                    board.getSpec().getId(), board.isWellFormed())) != null)
                throw new IllegalStateException("MOISE_BOARD_IDENTITY_DUPLICATE: " + value.getId());
            value.getPlayers().forEach(p -> missions.add(new MissionKey(p.getAg(), p.getTarget(), value.getId())));
            board.getSpec().getGoals().forEach(goal -> goals.put(new GoalKey(value.getId(), goal.getId()),
                    value.isSatisfied(goal) ? "satisfied" : "not_satisfied"));
        }
        return new OrganisationState(Map.copyOf(groupStates), Map.copyOf(schemeStates),
                Set.copyOf(roles), Set.copyOf(missions), Map.copyOf(goals));
    }

    private static void requireIdentity(String artifact, String state) {
        if (artifact == null || artifact.isBlank() || !artifact.equals(state))
            throw new IllegalStateException("MOISE_BOARD_IDENTITY_MISMATCH");
    }
}
