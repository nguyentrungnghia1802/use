package org.tzi.use.plugins.jacamo.runtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.tzi.use.plugins.jacamo.semantic.Dimension;

public record RuntimeEvent(String schemaVersion, String eventId, Instant timestamp, long sequence,
                           Dimension dimension, RuntimeEventKind kind, String runtimeSourceId,
                           String semanticSourceId, Map<String, Object> payload, String correlationId) {
    public RuntimeEvent {
        if (payload == null) throw new IllegalArgumentException("RUNTIME_EVENT_INVALID");
        payload = immutableMap(payload);
        RuntimeEventValidator.validate(eventId, timestamp, sequence, dimension, kind, runtimeSourceId,
                payload, correlationId);
        if (!"1.0.0".equals(schemaVersion)) throw new IllegalArgumentException("RUNTIME_EVENT_SCHEMA_VERSION");
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, immutable(value)));
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static Object immutable(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String text)) throw new IllegalArgumentException("RUNTIME_PAYLOAD_KEY_INVALID");
                copy.put(text, immutable(item));
            });
            return java.util.Collections.unmodifiableMap(copy);
        }
        if (value instanceof java.util.List<?> list)
            return java.util.Collections.unmodifiableList(list.stream().map(RuntimeEvent::immutable).toList());
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof java.math.BigInteger
                || value instanceof java.math.BigDecimal) return value;
        if (value instanceof Double number && Double.isFinite(number)) return value;
        if (value instanceof Float number && Float.isFinite(number)) return value;
        throw new IllegalArgumentException("RUNTIME_PAYLOAD_VALUE_INVALID: " + value.getClass().getName());
    }

    public static RuntimeEvent create(String eventId, Instant timestamp, long sequence, Dimension dimension,
                                      RuntimeEventKind kind, String runtimeSourceId, String semanticSourceId,
                                      Map<String, Object> payload, String correlationId) {
        return new RuntimeEvent("1.0.0", eventId, timestamp, sequence, dimension, kind, runtimeSourceId,
                semanticSourceId, payload, correlationId);
    }
}
