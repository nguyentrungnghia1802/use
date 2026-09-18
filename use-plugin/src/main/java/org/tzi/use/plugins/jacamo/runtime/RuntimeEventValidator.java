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
        Dimension authority = authority(kind);
        if (authority != null && dimension != authority)
            throw new IllegalArgumentException("RUNTIME_AUTHORITY_CONFLICT: " + kind + " requires " + authority);
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
            case BELIEF_ADDED, BELIEF_REMOVED -> required(payload, "belief");
            case GOAL_ADOPTED, GOAL_REMOVED, GOAL_ACHIEVED, GOAL_FAILED -> required(payload, "goal");
            case ACTION_STARTED -> {
                required(payload, "action", "arguments");
                correlation(correlationId);
            }
            case ACTION_SUCCEEDED -> correlation(correlationId);
            case ACTION_FAILED -> {
                required(payload, "error");
                correlation(correlationId);
            }
            case MESSAGE_SENT -> required(payload, "receiver", "performative", "content");
            case MESSAGE_RECEIVED -> required(payload, "sender", "performative", "content");
            case ARTIFACT_CREATED, ARTIFACT_DISPOSED -> required(payload, "artifact");
            case OBS_PROPERTY_ADDED, OBS_PROPERTY_CHANGED, OBS_PROPERTY_REMOVED ->
                    required(payload, "property", "values");
            case SIGNAL -> required(payload, "signal", "values");
            case ORGANISATION_DISCOVERED -> required(payload, "organisation");
            case GROUP_CREATED, GROUP_DISPOSED -> required(payload, "group", "specification");
            case SCHEME_CREATED, SCHEME_DISPOSED -> required(payload, "scheme", "specification");
            case ROLE_ADOPTED, ROLE_REMOVED -> required(payload, "agent", "role", "group");
            case MISSION_COMMITTED, MISSION_REMOVED -> required(payload, "agent", "mission", "scheme");
            case SCHEME_STATE_CHANGED -> required(payload, "scheme", "goal", "state");
            case NORM_STATE_CHANGED -> required(payload, "norm", "state");
        }
    }

    static Dimension authority(RuntimeEventKind kind) {
        return switch (kind) {
            case BELIEF_ADDED, BELIEF_REMOVED, GOAL_ADOPTED, GOAL_REMOVED, GOAL_ACHIEVED, GOAL_FAILED,
                    ACTION_STARTED, ACTION_SUCCEEDED, ACTION_FAILED, MESSAGE_SENT, MESSAGE_RECEIVED -> Dimension.AGENT;
            case ARTIFACT_CREATED, ARTIFACT_DISPOSED, OBS_PROPERTY_ADDED, OBS_PROPERTY_CHANGED,
                    OBS_PROPERTY_REMOVED, SIGNAL -> Dimension.ENVIRONMENT;
            case ORGANISATION_DISCOVERED, GROUP_CREATED, GROUP_DISPOSED, SCHEME_CREATED, SCHEME_DISPOSED,
                    ROLE_ADOPTED, ROLE_REMOVED, MISSION_COMMITTED, MISSION_REMOVED,
                    SCHEME_STATE_CHANGED, NORM_STATE_CHANGED -> Dimension.ORGANISATION;
            default -> null; // Generic mutation events retain the baseline synthetic adapter contract.
        };
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
