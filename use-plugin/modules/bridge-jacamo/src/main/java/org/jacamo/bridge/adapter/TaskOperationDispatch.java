package org.jacamo.bridge.adapter;

import java.util.Map;

/** Synchronous local dispatch context, not a name/time heuristic. CArtAgO's
 * opRequested callback receives the same Op object subsequently started/finished. */
final class TaskOperationDispatch implements AutoCloseable {
    private static final ThreadLocal<Map<String,Object>> CURRENT = new ThreadLocal<>();
    private final Map<String,Object> previous;
    private TaskOperationDispatch(Map<String,Object> claim) {
        previous = CURRENT.get();
        CURRENT.set(claim);
    }
    static TaskOperationDispatch enter(Map<String,Object> claim) { return new TaskOperationDispatch(claim); }
    static Map<String,Object> current() { var claim = CURRENT.get(); return claim == null ? Map.of() : claim; }
    @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
}
