package org.tzi.use.plugins.jacamo.ui.use4goal.istar;

import java.util.List;

/**
 * Adapter: wraps one iStar 2.0 MM element (actor or intentional element) as a
 * placeable node. Position/size are mutable — the View (drag) and the
 * LayoutBuilder are the only writers; painting only reads them.
 */
public final class IStarNode {

    public final String      id;
    public final String      label;
    public final IStarNodeKind kind;
    public final String      actorId;      // owning actor id; null for ACTOR nodes themselves
    public final boolean     actorIsRole;  // meaningful only when kind == ACTOR (Role vs Agent glyph)
    public final boolean     actorBoundary;
    public final boolean     dependencyMarker;
    /** Human-readable OCL contract lines belonging to this goal/task. */
    public final List<String> oclContracts;
    /** Display name of the temporal GoalType; null for tasks and untyped goals. */
    public final String goalType;

    public int x, y, w, h;

    public IStarNode(String id, String label, IStarNodeKind kind, String actorId, boolean actorIsRole) {
        this(id, label, kind, actorId, actorIsRole, false, false);
    }

    public IStarNode(String id, String label, IStarNodeKind kind, String actorId,
                     boolean actorIsRole, boolean actorBoundary, boolean dependencyMarker) {
        this(id, label, kind, actorId, actorIsRole, actorBoundary, dependencyMarker, List.of());
    }

    public IStarNode(String id, String label, IStarNodeKind kind, String actorId,
                     boolean actorIsRole, boolean actorBoundary, boolean dependencyMarker,
                     List<String> oclContracts) {
        this(id, label, kind, actorId, actorIsRole, actorBoundary, dependencyMarker, oclContracts, null);
    }

    public IStarNode(String id, String label, IStarNodeKind kind, String actorId,
                     boolean actorIsRole, boolean actorBoundary, boolean dependencyMarker,
                     List<String> oclContracts, String goalType) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.actorId = actorId;
        this.actorIsRole = actorIsRole;
        this.actorBoundary = actorBoundary;
        this.dependencyMarker = dependencyMarker;
        this.oclContracts = List.copyOf(oclContracts);
        this.goalType = goalType;
    }
}
