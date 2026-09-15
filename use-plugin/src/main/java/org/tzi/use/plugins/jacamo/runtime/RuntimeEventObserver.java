package org.tzi.use.plugins.jacamo.runtime;

/** Headless hook around the ordered mutation boundary. Implementations must not mutate JaCaMo. */
public interface RuntimeEventObserver {
    RuntimeEventObserver NOOP = new RuntimeEventObserver() { };

    default void stateChanged(MirrorState state) { }
    default void snapshotApplied(RuntimeSnapshot snapshot) { }
    default void eventReceived(RuntimeEvent event) { }
    default void beforeMutation(RuntimeEvent event) { }
    default void afterMutation(RuntimeEvent event, MutationResult result) { }
    default void driftChecked(RuntimeDriftReport report) { }
}
