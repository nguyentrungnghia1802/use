package org.vnu.sme.goal.dsl.istar.view;

import java.util.List;

/**
 * Adapter: wraps one iStar 2.0 MM element (actor or intentional element) as a
 * placeable node. Position/size are mutable — the View (drag) and the
 * LayoutBuilder are the only writers; painting only reads them.
 */
public final class IStarNode {
    enum ActorSymbol { ACTOR, ROLE, AGENT }

    public final String      id;
    public final String      label;
    public final IStarNodeKind kind;
    public final String      actorId;      // owning actor id; null for ACTOR nodes themselves
    final ActorSymbol actorSymbol;
    public final boolean     actorBoundary;
    public final boolean     dependum;
    /** Human-readable OCL contract lines belonging to this goal/task. */
    public final List<String> oclContracts;
    /** Display name of the temporal GoalType; null for tasks and untyped goals. */
    public final String goalType;

    public int x, y, w, h;

    IStarNode(String id, String label, IStarNodeKind kind, String actorId,
              ActorSymbol actorSymbol, boolean actorBoundary, boolean dependum,
              List<String> oclContracts, String goalType) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.actorId = actorId;
        this.actorSymbol = actorSymbol;
        this.actorBoundary = actorBoundary;
        this.dependum = dependum;
        this.oclContracts = List.copyOf(oclContracts);
        this.goalType = goalType;
    }
}
