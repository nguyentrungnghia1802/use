package org.tzi.use.plugins.jacamo.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.RealValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/** The only runtime component allowed to mutate the USE mirror. */
public final class RuntimeMutationEngine {
    private final MSystem system;
    private final TraceIndex trace;
    private final Map<String, String> dynamicRuntimeObjects = new LinkedHashMap<>();
    private final Map<String, String> activeOperations = new LinkedHashMap<>();
    private final Map<String, OperationRuntimeOutcome> operationHistory = new LinkedHashMap<>();
    private final List<RuntimeEvent> quarantined = new ArrayList<>();
    private long lastSequence = -1;

    public RuntimeMutationEngine(MSystem system, TraceIndex trace) {
        this.system = system;
        this.trace = trace;
    }

    public synchronized MutationResult apply(RuntimeEvent event) {
        if (event.sequence() <= lastSequence)
            return MutationResult.failed("RUNTIME_EVENT_OUT_OF_ORDER: " + event.sequence() + " <= " + lastSequence);
        lastSequence = event.sequence();
        try {
            if (event.kind() == RuntimeEventKind.CREATE_OBJECT) return create(event);
            String objectName = resolveObject(event.runtimeSourceId());
            if (objectName == null) {
                quarantined.add(event);
                return MutationResult.quarantined("RUNTIME_TRACE_UNRESOLVED: " + event.runtimeSourceId());
            }
            return switch (event.kind()) {
                case DESTROY_OBJECT -> destroy(event.runtimeSourceId(), objectName);
                case SET_ATTRIBUTE -> set(objectName, event.payload());
                case INSERT_LINK -> insert(event.payload());
                case DELETE_LINK -> delete(event.payload());
                case OP_ENTER -> enter(event, objectName);
                case OP_EXIT -> exit(event, OperationRuntimeOutcome.EXITED);
                case OP_FAIL -> exit(event, OperationRuntimeOutcome.FAILED);
                case CREATE_OBJECT -> throw new IllegalStateException("handled above");
                case OBS_PROPERTY_ADDED, OBS_PROPERTY_CHANGED ->
                        event.payload().containsKey("attribute") ? set(objectName, event.payload()) : MutationResult.applied();
                case OBS_PROPERTY_REMOVED -> event.payload().containsKey("attribute")
                        ? set(objectName, undefined(event.payload())) : MutationResult.applied();
                case BELIEF_ADDED, BELIEF_REMOVED,
                        GOAL_ADOPTED, GOAL_REMOVED, GOAL_ACHIEVED, GOAL_FAILED,
                        ACTION_STARTED, ACTION_SUCCEEDED, ACTION_FAILED,
                        MESSAGE_SENT, MESSAGE_RECEIVED,
                        ARTIFACT_CREATED, ARTIFACT_DISPOSED,
                        SIGNAL,
                        ORGANISATION_DISCOVERED, GROUP_CREATED, GROUP_DISPOSED,
                        SCHEME_CREATED, SCHEME_DISPOSED,
                        ROLE_ADOPTED, ROLE_REMOVED, MISSION_COMMITTED, MISSION_REMOVED,
                        SCHEME_STATE_CHANGED, NORM_STATE_CHANGED -> MutationResult.applied();
            };
        } catch (Exception exception) {
            return MutationResult.failed("RUNTIME_MUTATION_FAILED[" + event.kind() + "]: " + exception.getMessage());
        }
    }

    public synchronized void applySnapshot(RuntimeSnapshot snapshot) {
        lastSequence = -1;
        for (RuntimeEvent event : snapshot.mutations()) {
            MutationResult result = apply(event);
            if (result.status() != MutationStatus.APPLIED)
                throw new IllegalStateException("RUNTIME_SNAPSHOT_APPLY_FAILED: " + result.diagnostic());
        }
        lastSequence = Math.max(lastSequence, snapshot.sequence());
    }

    public synchronized List<RuntimeEvent> quarantinedEvents() { return List.copyOf(quarantined); }
    public synchronized Map<String, OperationRuntimeOutcome> operationHistory() { return Map.copyOf(operationHistory); }
    public synchronized long lastSequence() { return lastSequence; }

    private MutationResult create(RuntimeEvent event) throws Exception {
        if (event.semanticSourceId() == null || trace.bySemanticId(event.semanticSourceId()).isEmpty()) {
            quarantined.add(event);
            return MutationResult.quarantined("RUNTIME_CREATE_TRACE_UNRESOLVED: " + event.semanticSourceId());
        }
        String className = text(event.payload(), "useClass");
        String objectName = text(event.payload(), "useObject");
        var cls = system.model().getClass(className);
        if (cls == null) throw new IllegalArgumentException("USE_CLASS_MISSING: " + className);
        MObject existing = system.state().objectByName(objectName);
        if (existing == null) system.state().createObject(cls, objectName);
        else if (!existing.cls().name().equals(className))
            throw new IllegalArgumentException("USE_OBJECT_CLASS_MISMATCH: " + objectName);
        dynamicRuntimeObjects.put(event.runtimeSourceId(), objectName);
        return MutationResult.applied();
    }

    private MutationResult destroy(String runtimeSourceId, String objectName) {
        MObject object = requireObject(objectName);
        system.state().deleteObject(object);
        dynamicRuntimeObjects.remove(runtimeSourceId);
        return MutationResult.applied();
    }

    private MutationResult set(String objectName, Map<String, Object> payload) {
        MObject object = requireObject(objectName);
        String attributeName = text(payload, "attribute");
        var attribute = object.cls().attribute(attributeName, true);
        if (attribute == null) throw new IllegalArgumentException("USE_ATTRIBUTE_MISSING: " + attributeName);
        object.state(system.state()).setAttributeValue(attribute, value(payload));
        return MutationResult.applied();
    }

    private MutationResult insert(Map<String, Object> payload) throws Exception {
        var association = system.model().getAssociation(text(payload, "association"));
        if (association == null) throw new IllegalArgumentException("USE_ASSOCIATION_MISSING");
        system.state().createLink(association, participants(payload), null);
        return MutationResult.applied();
    }

    private MutationResult delete(Map<String, Object> payload) throws Exception {
        var association = system.model().getAssociation(text(payload, "association"));
        if (association == null) throw new IllegalArgumentException("USE_ASSOCIATION_MISSING");
        system.state().deleteLink(association, participants(payload), null);
        return MutationResult.applied();
    }

    private MutationResult enter(RuntimeEvent event, String objectName) {
        String correlation = event.correlationId();
        if (activeOperations.containsKey(correlation))
            throw new IllegalArgumentException("OPERATION_CORRELATION_ACTIVE: " + correlation);
        MObject object = requireObject(objectName);
        String operation = text(event.payload(), "operation");
        if (object.cls().operation(operation, true) == null)
            throw new IllegalArgumentException("USE_OPERATION_MISSING: " + operation);
        activeOperations.put(correlation, objectName + "::" + operation);
        operationHistory.put(correlation, OperationRuntimeOutcome.ENTERED);
        return MutationResult.applied();
    }

    private MutationResult exit(RuntimeEvent event, OperationRuntimeOutcome outcome) {
        if (activeOperations.remove(event.correlationId()) == null)
            throw new IllegalArgumentException("OPERATION_CORRELATION_MISSING: " + event.correlationId());
        operationHistory.put(event.correlationId(), outcome);
        return MutationResult.applied();
    }

    private String resolveObject(String runtimeSourceId) {
        String dynamic = dynamicRuntimeObjects.get(runtimeSourceId);
        if (dynamic != null) return dynamic;
        return trace.byRuntimeKey(runtimeSourceId).map(record -> record.targetUseId())
                .filter(value -> value.startsWith("object:"))
                .map(value -> value.substring("object:".length())).orElse(null);
    }

    private MObject requireObject(String name) {
        MObject object = system.state().objectByName(name);
        if (object == null) throw new IllegalArgumentException("USE_OBJECT_MISSING: " + name);
        return object;
    }

    private List<MObject> participants(Map<String, Object> payload) {
        Object raw = payload.get("participants");
        if (!(raw instanceof List<?> names)) throw new IllegalArgumentException("LINK_PARTICIPANTS_INVALID");
        return names.stream().map(String::valueOf).map(this::requireObject).toList();
    }

    private Value value(Map<String, Object> payload) {
        String type = text(payload, "valueType");
        Object raw = payload.get("value");
        return switch (type) {
            case "BOOLEAN" -> BooleanValue.get(raw instanceof Boolean value ? value : Boolean.parseBoolean(String.valueOf(raw)));
            case "INTEGER" -> IntegerValue.valueOf(raw instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(raw)));
            case "REAL" -> new RealValue(raw instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(raw)));
            case "STRING" -> new StringValue(String.valueOf(raw));
            case "UNDEFINED" -> UndefinedValue.instance;
            default -> throw new IllegalArgumentException("RUNTIME_VALUE_TYPE_UNSUPPORTED: " + type);
        };
    }

    private Map<String, Object> undefined(Map<String, Object> payload) {
        Map<String, Object> result = new LinkedHashMap<>(payload);
        result.put("valueType", "UNDEFINED");
        result.put("value", "undefined");
        return result;
    }

    private String text(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("RUNTIME_PAYLOAD_INVALID: " + key);
        return value.toString();
    }
}
