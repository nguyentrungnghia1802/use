package org.tzi.use.uml.sys.events;

import org.tzi.use.uml.sys.events.tags.EventContext;
import org.tzi.use.uml.sys.events.tags.SystemStateChangedEvent;
import org.tzi.use.util.soil.StateDifference;

/** One notification after an API transaction, never for its intermediate writes. */
public final class AtomicStateChangedEvent extends Event implements SystemStateChangedEvent {
    public record InvariantEvaluation(String outcome, String diagnostic) { }
    private final StateDifference changes;
    private final java.util.Map<String, InvariantEvaluation> evaluations;
    public AtomicStateChangedEvent(StateDifference changes) {
        this(changes, java.util.Map.of());
    }
    public AtomicStateChangedEvent(StateDifference changes, java.util.Map<String, InvariantEvaluation> evaluations) {
        super(EventContext.NORMAL_EXECUTION);
        this.changes = changes;
        this.evaluations = java.util.Map.copyOf(evaluations);
    }
    public java.util.Map<String, InvariantEvaluation> getInvariantEvaluations() { return evaluations; }
    public StateDifference getChanges() { return changes; }
    public java.util.Set<org.tzi.use.uml.sys.MObject> getNewObjects() { return java.util.Set.copyOf(changes.getNewObjects()); }
    public java.util.Set<org.tzi.use.uml.sys.MObject> getDeletedObjects() { return java.util.Set.copyOf(changes.getDeletedObjects()); }
    public java.util.Set<org.tzi.use.uml.sys.MObject> getModifiedObjects() { return java.util.Set.copyOf(changes.getModifiedObjects()); }
    public java.util.Set<org.tzi.use.uml.sys.MLink> getNewLinks() { return java.util.Set.copyOf(changes.getNewLinks()); }
    public java.util.Set<org.tzi.use.uml.sys.MLink> getDeletedLinks() { return java.util.Set.copyOf(changes.getDeletedLinks()); }
}
