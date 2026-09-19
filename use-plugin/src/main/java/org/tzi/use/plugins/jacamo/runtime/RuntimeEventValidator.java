package org.tzi.use.plugins.jacamo.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.tzi.use.plugins.jacamo.semantic.Dimension;

final class RuntimeEventValidator {
    private RuntimeEventValidator() { }

    static void validate(String eventId, Instant timestamp, long sequence, Dimension dimension,
                         RuntimeEventKind kind, String runtimeSourceId, Map<String, Object> payload,
                         String correlationId) {
        if (eventId == null || eventId.isBlank() || timestamp == null || sequence < 0 || dimension == null
                || kind == null || runtimeSourceId == null || runtimeSourceId.isBlank() || payload == null)
            throw new IllegalArgumentException("RUNTIME_EVENT_INVALID");
        switch (kind) {
            case CREATE_OBJECT -> required(payload, "useClass", "useObject");
            case DESTROY_OBJECT -> { }
            case SET_ATTRIBUTE -> required(payload, "attribute", "valueType", "value");
            case INSERT_LINK, DELETE_LINK -> {
                required(payload, "association", "participants");
                if (!(payload.get("participants") instanceof List<?> participants) || participants.size() < 2)
                    throw new IllegalArgumentException("RUNTIME_EVENT_PARTICIPANTS_INVALID");
            }
            case OP_ENTER -> {
                required(payload, "operation", "arguments");
                correlation(correlationId);
            }
            case OP_EXIT -> correlation(correlationId);
            case OP_FAIL -> {
                required(payload, "error");
                correlation(correlationId);
            }
        }
    }

    private static void required(Map<String, Object> payload, String... fields) {
        for (String field : fields)
            if (!payload.containsKey(field) || payload.get(field) == null)
                throw new IllegalArgumentException("RUNTIME_EVENT_PAYLOAD_MISSING: " + field);
    }
    private static void correlation(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("RUNTIME_EVENT_CORRELATION_REQUIRED");
    }
}
