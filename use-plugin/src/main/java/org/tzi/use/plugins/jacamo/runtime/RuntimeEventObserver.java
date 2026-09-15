package org.tzi.use.plugins.jacamo.runtime;

/** Headless hook around the ordered mutation boundary. Implementations must not mutate JaCaMo. */
public interface RuntimeEventObserver {
    RuntimeEventObserver NOOP = new RuntimeEventObserver() { };

    default void stateChanged(MirrorState state) { }
    default void snapshotApplied(RuntimeSnapshot snapshot) { }

    /** Called synchronously at the connector boundary; implementations must not block event submission. */
    default void eventReceived(RuntimeEvent event) { }

    /** Called when a received event cannot enter the ordered stream. */
    default void eventRejected(RuntimeEvent event, RuntimeException reason) { }

    default void beforeMutation(RuntimeEvent event) { }
    default void afterMutation(RuntimeEvent event, MutationResult result) { }

    /** Called after the ordered worker finishes an accepted event, including a failed mutation. */
    default void eventCompleted(RuntimeEvent event) { }

    /** Called once a stream is closed; observers must discard stream-scoped timing and correlation state. */
    default void eventStreamClosed() { }

    default void driftChecked(RuntimeDriftReport report) { }
}
