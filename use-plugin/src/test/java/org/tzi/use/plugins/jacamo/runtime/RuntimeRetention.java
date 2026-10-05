package org.tzi.use.plugins.jacamo.runtime;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/** Central deterministic bounds for runtime-only evidence and lifecycle bookkeeping. */
public final class RuntimeRetention {
    public static final int OPERATION_CORRELATIONS = 4_096;
    public static final int OPERATION_HISTORY = 4_096;
    public static final int QUARANTINED_EVENTS = 1_024;
    public static final int TRACE_ENTRIES = 16_384;
    public static final int TRACE_BOUNDARIES = 256;
    public static final int TRACE_EVENT_IDS = 8_192;
    public static final int VERIFICATION_REPORTS = 4_096;
    public static final int CONNECTOR_DIAGNOSTICS = 1_024;

    private RuntimeRetention() { }

    public static int requirePositive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException("RUNTIME_RETENTION_INVALID:" + name);
        return value;
    }

    /** Appends evidence and returns whether the oldest entry was retired. */
    public static <T> boolean append(List<T> values, T value, int limit) {
        requirePositive(limit, "list");
        values.add(value);
        if (values.size() <= limit) return false;
        values.removeFirst();
        return true;
    }

    /** Remembers a unique value in insertion order and retires the oldest value at the bound. */
    public static <T> boolean remember(LinkedHashSet<T> values, T value, int limit) {
        requirePositive(limit, "set");
        if (!values.add(value) || values.size() <= limit) return false;
        Iterator<T> iterator = values.iterator();
        iterator.next();
        iterator.remove();
        return true;
    }

    /** Stores the latest outcome and returns whether a different oldest key was retired. */
    public static <K, V> boolean putLatest(LinkedHashMap<K, V> values, K key, V value, int limit) {
        requirePositive(limit, "map");
        boolean existing = values.containsKey(key);
        if (existing) values.remove(key);
        values.put(key, value);
        if (values.size() <= limit) return false;
        Iterator<K> iterator = values.keySet().iterator();
        iterator.next();
        iterator.remove();
        return !existing;
    }
}
