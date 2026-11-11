package org.jacamo.bridge.adapter;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit readiness replaces assumptions based on Platform.start timing. */
public final class AdapterReadinessRegistry {
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    public record State(AdapterReadiness readiness, String detail) { }
    public void update(String adapter, AdapterReadiness readiness, String detail) {
        if (adapter == null || adapter.isBlank() || readiness == null) throw new IllegalArgumentException("adapter/readiness");
        states.put(adapter, new State(readiness, detail == null ? "" : detail));
    }
    public State state(String adapter) { return states.getOrDefault(adapter, new State(AdapterReadiness.UNCONFIGURED, "")); }
    public Map<String, State> snapshot() { return java.util.Collections.unmodifiableMap(new TreeMap<>(states)); }
}
