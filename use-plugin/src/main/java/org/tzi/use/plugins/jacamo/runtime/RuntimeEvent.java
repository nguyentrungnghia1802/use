package org.tzi.use.plugins.jacamo.runtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.tzi.use.plugins.jacamo.semantic.Dimension;

public record RuntimeEvent(String schemaVersion, String eventId, Instant timestamp, long sequence,
                           Dimension dimension, RuntimeEventKind kind, String runtimeSourceId,
                           String semanticSourceId, Map<String, Object> payload, String correlationId) {
    public RuntimeEvent {
        payload = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        RuntimeEventValidator.validate(eventId, timestamp, sequence, dimension, kind, runtimeSourceId,
                payload, correlationId);
        if (!"1.0.0".equals(schemaVersion)) throw new IllegalArgumentException("RUNTIME_EVENT_SCHEMA_VERSION");
    }

    public static RuntimeEvent create(String eventId, Instant timestamp, long sequence, Dimension dimension,
                                      RuntimeEventKind kind, String runtimeSourceId, String semanticSourceId,
                                      Map<String, Object> payload, String correlationId) {
        return new RuntimeEvent("1.0.0", eventId, timestamp, sequence, dimension, kind, runtimeSourceId,
                semanticSourceId, payload, correlationId);
    }
}
