package org.tzi.use.plugins.jacamo.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/** The only runtime component allowed to mutate the USE mirror. */
public final class RuntimeMutationEngine {
    public record RetentionMetrics(int activeOperationCorrelations, int completedOperationCorrelations,
                                   int operationHistory, int quarantinedEvents,
                                   long retiredCompletedCorrelations, long retiredOperationHistory,
                                   long retiredQuarantinedEvents, boolean correlationOverflow,
                                   RuntimeTrace.RetentionMetrics trace) { }
    private final Map<String, String> authorizedObjectClasses = new LinkedHashMap<>();
    private final LinkedHashSet<String> completedCorrelations = new LinkedHashSet<>();
    private final RuntimeMapping mapping;
    private final RuntimeTargetResolver targets;
    private final MSystem system;
    private final TraceIndex trace;
    private org.tzi.use.plugins.jacamo.mapping.TransformationPlan orderStructure;
    private org.tzi.use.plugins.jacamo.materialization.InstancePlan orderMembership;
    private final Object operationLifecycle = new Object();
    private final Map<String, String> dynamicRuntimeObjects = new LinkedHashMap<>();
    private final ConcurrentMap<String, RuntimeOperationState> operationCorrelations = new ConcurrentHashMap<>();
    private final LinkedHashMap<String, OperationRuntimeOutcome> operationHistory = new LinkedHashMap<>();
    private final List<RuntimeEvent> quarantined = new ArrayList<>();
    private final int operationCorrelationLimit;
    private final int operationHistoryLimit;
    private final int quarantineLimit;
    private long retiredCompletedCorrelations;
    private long retiredOperationHistory;
    private long retiredQuarantinedEvents;
    private boolean correlationOverflow;
    private long lastSequence = -1;
    private long completedThroughSequence = -1;
    private final RuntimeTrace runtimeTrace;

    public RuntimeMutationEngine(MSystem system, TraceIndex trace) {
        this(system, trace, new RuntimeMappingLoader().loadDefault(), new TraceRuntimeTargetAdapter(trace));
    }

    public RuntimeMutationEngine(MSystem system, TraceIndex trace, RuntimeMapping mapping, RuntimeTargetResolver targets) {
        this(system, trace, mapping, targets, RuntimeRetention.OPERATION_CORRELATIONS,
                RuntimeRetention.OPERATION_HISTORY, RuntimeRetention.QUARANTINED_EVENTS);
    }

    RuntimeMutationEngine(MSystem system, TraceIndex trace, RuntimeMapping mapping, RuntimeTargetResolver targets,
                          int operationCorrelationLimit, int operationHistoryLimit, int quarantineLimit) {
        if (!trace.runtimeEligible()) throw new IllegalArgumentException("RUNTIME_ARCHIVED_TRACE: rebuild from active source before mutation");
        this.mapping = java.util.Objects.requireNonNull(mapping);
        this.targets = java.util.Objects.requireNonNull(targets);
        this.system = system;
        this.trace = trace;
        this.operationCorrelationLimit = RuntimeRetention.requirePositive(operationCorrelationLimit, "operationCorrelations");
        this.operationHistoryLimit = RuntimeRetention.requirePositive(operationHistoryLimit, "operationHistory");
        this.quarantineLimit = RuntimeRetention.requirePositive(quarantineLimit, "quarantinedEvents");
        this.runtimeTrace = new RuntimeTrace();
        for (var record : trace.byTargetKind("OBJECT")) {
            var object = system.state().objectByName(record.targetUseId().substring("object:".length()));
            if (object != null) authorizedObjectClasses.put(object.name(), object.cls().name());
        }
        runtimeTrace.begin("INITIAL");
    }

    public RuntimeMutationEngine(MSystem system, TraceIndex trace, RuntimeMapping mapping, RuntimeTargetResolver targets,
            org.tzi.use.plugins.jacamo.mapping.TransformationPlan structure,
            org.tzi.use.plugins.jacamo.materialization.InstancePlan membership) {
        this(system, trace, mapping, targets);
        this.orderStructure = java.util.Objects.requireNonNull(structure);
        this.orderMembership = new org.tzi.use.plugins.jacamo.mapping.OrderProjectionPlanner()
                .membership(structure, java.util.Objects.requireNonNull(membership));
    }

    public synchronized MutationResult apply(RuntimeEvent event) {
        try { runtimeTrace.accept(runtimeTrace.generation(), event); }
        catch (IllegalArgumentException exception) { return MutationResult.failed(exception.getMessage()); }
        MutationResult result = applyOrdered(event);
        runtimeTrace.result(runtimeTrace.generation(), event, result);
        return result;
    }

    public RuntimeTrace runtimeTrace() { return runtimeTrace; }

    private List<org.tzi.use.plugins.jacamo.mapping.OrderProjectionPlanner.SourceOrder> sourceOrders(RuntimeEvent event) {
        if (orderStructure == null || orderMembership == null) throw new IllegalArgumentException("RUNTIME_ORDER_BINDING_MISSING");
        List<org.tzi.use.plugins.jacamo.mapping.OrderProjectionPlanner.SourceOrder> result = new ArrayList<>();
        for (Object item : (List<?>) event.payload().get("orders")) {
            if (!(item instanceof Map<?, ?> order) || !(order.get("sourceIdentity") instanceof String feature) ||
                    !(order.get("ownerSemanticId") instanceof String owner) || !(order.get("targetSemanticIds") instanceof List<?> ids))
                throw new IllegalArgumentException("RUNTIME_ORDER_PAYLOAD_INVALID");
            List<String> names = new ArrayList<>();
            for (Object id : ids) {
                if (!(id instanceof String text)) throw new IllegalArgumentException("RUNTIME_ORDER_TARGET_INVALID");
                names.add(orderObject(text));
            }
            result.add(new org.tzi.use.plugins.jacamo.mapping.OrderProjectionPlanner.SourceOrder(feature, orderObject(owner), names));
        }
        return List.copyOf(result);
    }

    private String orderObject(String semanticId) {
        var matches = trace.bySemanticId(semanticId).stream().filter(r -> r.targetKind().equals("OBJECT") &&
                (r.status() == org.tzi.use.plugins.jacamo.trace.TraceRecord.Status.RESOLVED || r.status() == org.tzi.use.plugins.jacamo.trace.TraceRecord.Status.PROJECTED)).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("RUNTIME_TRACE_UNRESOLVED:" + semanticId);
        return matches.getFirst().targetUseId().substring("object:".length());
    }

    private MutationResult applyOrdered(RuntimeEvent event) {
        if (event.sequence() <= lastSequence)
            return MutationResult.failed("RUNTIME_EVENT_OUT_OF_ORDER: " + event.sequence() + " <= " + lastSequence);
        lastSequence = event.sequence();
        try {
            var rule = mapping.select(event);
            if (rule.action() == RuntimeSemanticAction.UNSUPPORTED)
                return quarantine(event, "RUNTIME_MAPPING_UNSUPPORTED:" + rule.id());
            for (String field : rule.payload())
                if (!event.payload().containsKey(field) || event.payload().get(field) == null)
                    return quarantine(event, "RUNTIME_MAPPING_PAYLOAD_UNBOUND:" + rule.id() + ":" + field);
            if (rule.action() == RuntimeSemanticAction.OBJECT_AVAILABLE) return create(event);
            if (!dynamicRuntimeObjects.containsKey(event.runtimeSourceId()))
                targets.resolve(new RuntimeTargetResolver.Request(event.runtimeSourceId(), event.semanticSourceId(), "OBJECT"));
            String objectName = resolveObject(event.runtimeSourceId());
            if (objectName == null) return quarantine(event, "RUNTIME_TRACE_UNRESOLVED:" + event.runtimeSourceId());
            if (event.semanticSourceId() != null && trace.byUseId("object:" + objectName).stream()
                    .noneMatch(record -> record.sourceSemanticId().equals(event.semanticSourceId())))
                return quarantine(event, "RUNTIME_TRACE_TARGET_MISMATCH:" + event.runtimeSourceId());
            return switch (rule.action()) {
                case OBJECT_AVAILABLE -> throw new IllegalStateException("handled above");
                case OBJECT_UNAVAILABLE -> destroy(event.runtimeSourceId(), objectName);
                case ATTRIBUTE_STATE_SET -> set(objectName, event.payload());
                case ATTRIBUTE_STATE_UNSET -> set(objectName, undefined(event.payload()));
                case RELATION_INSERT -> insert(event.payload());
                case RELATION_DELETE -> delete(event.payload());
                case RELATION_REORDER -> {
                    new OrderProjectionRuntimeBinding().reorder(system, orderStructure, orderMembership, sourceOrders(event), trace);
                    yield MutationResult.applied();
                }
                case OPERATION_ENTER -> enter(event, objectName);
                case OPERATION_EXIT -> exit(event, OperationRuntimeOutcome.EXITED);
                case OPERATION_FAIL -> exit(event, OperationRuntimeOutcome.FAILED);
                case TRACE_ONLY, NO_MUTATION -> new MutationResult(MutationStatus.APPLIED, "RUNTIME_MAPPING_TRACE_ONLY:" + rule.id());
                case UNSUPPORTED -> throw new IllegalStateException("handled above");
            };
        } catch (Exception exception) {
            String diagnostic = String.valueOf(exception.getMessage());
            if (diagnostic.contains("TRACE_UNRESOLVED") || diagnostic.startsWith("RUNTIME_TRACE_TARGET_MISMATCH") || diagnostic.startsWith("USE_OBJECT_MISSING")
                    || diagnostic.startsWith("USE_ATTRIBUTE_MISSING") || diagnostic.startsWith("USE_ASSOCIATION_MISSING")
                    || diagnostic.startsWith("USE_OPERATION_MISSING") || diagnostic.startsWith("OPERATION_TARGET_MISMATCH"))
                return quarantine(event, diagnostic);
            return MutationResult.failed("RUNTIME_MUTATION_FAILED[" + event.kind() + "]: " + exception.getMessage());
        }
    }

    public synchronized void applySnapshot(RuntimeSnapshot snapshot) {
        dynamicRuntimeObjects.clear();
        runtimeTrace.begin("AUTHORITATIVE_SNAPSHOT:" + snapshot.snapshotId());
        resetOperationLifecycle();
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
    public synchronized RetentionMetrics retentionMetrics() {
        synchronized (operationLifecycle) {
            return new RetentionMetrics(operationCorrelations.size(), completedCorrelations.size(),
                    operationHistory.size(), quarantined.size(), retiredCompletedCorrelations,
                    retiredOperationHistory, retiredQuarantinedEvents, correlationOverflow,
                    runtimeTrace.retentionMetrics());
        }
    }

    /** A rejected event cannot participate in a later operation lifecycle. */
    public void eventRejected(RuntimeEvent event, RuntimeException reason) {
        runtimeTrace.reject(runtimeTrace.generation(), event, reason.getMessage());
        if (!isOperationTerminal(event) || event.correlationId() == null
                || !(reason instanceof RuntimeQueueBackpressureException backpressure)) return;
        synchronized (operationLifecycle) {
            RuntimeOperationState current = operationCorrelations.get(event.correlationId());
            if (current != null && current.sequence() > event.sequence()) return;
            if (backpressure.acceptedThroughSequence() <= completedThroughSequence) {
                operationCorrelations.remove(event.correlationId());
                return;
            }
            if (current == null && operationCorrelations.size() >= operationCorrelationLimit) {
                correlationOverflow = true;
                return;
            }
            operationCorrelations.put(event.correlationId(),
                    RuntimeOperationState.rejected(event.sequence(), backpressure.acceptedThroughSequence()));
        }
    }

    /** Retires rejection tombstones once all events accepted before that rejection have completed. */
    public void eventCompleted(RuntimeEvent event) {
        synchronized (operationLifecycle) {
            completedThroughSequence = Math.max(completedThroughSequence, event.sequence());
            operationCorrelations.entrySet().removeIf(entry -> entry.getValue().rejected()
                    && entry.getValue().retireAfterSequence() <= completedThroughSequence);
        }
    }

    /** Operation correlations are scoped to one connector event stream. */
    public synchronized void eventStreamClosed() {
        dynamicRuntimeObjects.clear();
        resetOperationLifecycle();
        runtimeTrace.close();
        // Direct engine clients may open their next stream without a transport snapshot.
        runtimeTrace.begin("STREAM_BOUNDARY");
    }

    int pendingOperationRejections() {
        synchronized (operationLifecycle) {
            return (int) operationCorrelations.values().stream().filter(RuntimeOperationState::rejected).count();
        }
    }

    /** Compares authoritative snapshot mutations with the current USE mirror without changing either side. */
    public synchronized List<RuntimeDriftDifference> compareSnapshot(RuntimeSnapshot snapshot) {
        List<RuntimeDriftDifference> differences = new ArrayList<>();
        for (RuntimeEvent event : snapshot.mutations()) {
            try {
                compare(event, differences);
            } catch (RuntimeException exception) {
                differences.add(new RuntimeDriftDifference("RUNTIME_DRIFT_COMPARE_ERROR", event.eventId(),
                        event.runtimeSourceId(), event.kind().name(), "comparable authoritative value",
                        exception.getMessage()));
            }
        }
        return List.copyOf(differences);
    }

    private MutationResult quarantine(RuntimeEvent event, String diagnostic) {
        if (RuntimeRetention.append(quarantined, event, quarantineLimit)) retiredQuarantinedEvents++;
        return MutationResult.quarantined(diagnostic);
    }

    private List<MObject> tracedParticipants(Map<String, Object> payload, String association) {
        if (trace.byUseId("association:" + association).stream().noneMatch(record ->
            record.status() == org.tzi.use.plugins.jacamo.trace.TraceRecord.Status.RESOLVED))
            throw new IllegalArgumentException("RUNTIME_ASSOCIATION_TRACE_UNRESOLVED:" + association);
        List<MObject> objects = participants(payload);
        for (MObject object : objects)
            if (trace.byUseId("object:" + object.name()).stream().noneMatch(record ->
                record.status() == org.tzi.use.plugins.jacamo.trace.TraceRecord.Status.RESOLVED
                || record.status() == org.tzi.use.plugins.jacamo.trace.TraceRecord.Status.PROJECTED))
                throw new IllegalArgumentException("RUNTIME_PARTICIPANT_TRACE_UNRESOLVED:" + object.name());
        return objects;
    }

    private MutationResult create(RuntimeEvent event) throws Exception {
        String className = text(event.payload(), "useClass");
        String objectName = text(event.payload(), "useObject");
        var exact = trace.byUseId("object:" + objectName).stream()
            .filter(record -> record.sourceSemanticId().equals(event.semanticSourceId()))
            .filter(record -> record.status() == org.tzi.use.plugins.jacamo.trace.TraceRecord.Status.RESOLVED
                || record.status() == org.tzi.use.plugins.jacamo.trace.TraceRecord.Status.PROJECTED).toList();
        if (exact.size() != 1) return quarantine(event, "RUNTIME_CREATE_TRACE_UNRESOLVED:" + objectName);
        if (!className.equals(authorizedObjectClasses.get(objectName)))
            return quarantine(event, "RUNTIME_DYNAMIC_INSTANCE_POLICY_UNSUPPORTED:" + objectName);
        String oldTarget = resolveObject(event.runtimeSourceId());
        if (oldTarget != null && !oldTarget.equals(objectName))
            return quarantine(event, "RUNTIME_ALIAS_TARGET_CONFLICT:" + event.runtimeSourceId());
        var cls = system.model().getClass(className);
        if (cls == null) throw new IllegalArgumentException("USE_CLASS_MISSING: " + className);
        MObject existing = system.state().objectByName(objectName);
        if (existing == null) system.state().createObject(cls, objectName);
        else if (!existing.cls().name().equals(className))
            throw new IllegalArgumentException("USE_OBJECT_CLASS_MISMATCH: " + objectName);
        dynamicRuntimeObjects.put(event.runtimeSourceId(), objectName);
        return MutationResult.applied();
    }

    private void compare(RuntimeEvent event, List<RuntimeDriftDifference> differences) {
        var action = mapping.select(event).action();
        if (action == RuntimeSemanticAction.RELATION_REORDER) {
            var desired = new org.tzi.use.plugins.jacamo.mapping.OrderProjectionPlanner().project(orderStructure, orderMembership, sourceOrders(event));
            for (var link : desired.links()) {
                var association = system.model().getAssociation(link.association());
                var source = system.state().objectByName(link.sourceObject());
                var target = system.state().objectByName(link.targetObject());
                if (association == null || source == null || target == null ||
                        !system.state().hasLinkBetweenObjects(association, List.of(source, target), null))
                    difference(differences, event, "association:" + link.association() + ":" + link.sourceObject() + ":" + link.targetObject(), "present", "missing");
            }
            for (var association : orderStructure.associations()) {
                long expected = desired.links().stream().filter(l -> l.association().equals(association.name())).count();
                long actual = system.state().linksOfAssociation(system.model().getAssociation(association.name())).size();
                if (expected != actual) difference(differences, event, "association:" + association.name(), Long.toString(expected), Long.toString(actual));
            }
            for (var spec : orderStructure.orderProjections()) {
                var expected = desired.objects().stream().filter(o -> o.className().equals(spec.entryClass())).map(o -> o.name()).collect(java.util.stream.Collectors.toSet());
                for (var object : system.state().allObjects()) if (object.cls().name().equals(spec.entryClass()) && !expected.contains(object.name()))
                    difference(differences, event, "object:" + object.name(), "absent", spec.entryClass());
            }
            for (var row : desired.objects()) if (orderStructure.orderProjections().stream().anyMatch(p -> p.entryClass().equals(row.className()))) {
                var object = system.state().objectByName(row.name());
                if (object != null && !object.cls().name().equals(row.className())) {
                    difference(differences, event, "object:" + row.name(), row.className(), object.cls().name());
                    continue;
                }
                String expected = Long.toString(((org.tzi.use.plugins.jacamo.semantic.AttributeValue.IntegerNumber) row.values().get("rank")).value());
                String actual = object == null ? "missing" : object.state(system.state()).attributeValue("rank").toString();
                if (!expected.equals(actual)) difference(differences, event, "object:" + row.name() + ".rank", expected, actual);
            }
            return;
        }
        if (action == RuntimeSemanticAction.OBJECT_AVAILABLE) {
            String objectName = text(event.payload(), "useObject");
            String className = text(event.payload(), "useClass");
            MObject object = system.state().objectByName(objectName);
            if (object == null || !object.cls().name().equals(className))
                difference(differences, event, "object:" + objectName, className,
                        object == null ? "<missing>" : object.cls().name());
            return;
        }
        String objectName = resolveObject(event.runtimeSourceId());
        if (objectName == null) {
            difference(differences, event, "runtime:" + event.runtimeSourceId(), "resolved trace", "<unresolved>");
            return;
        }
        if (action == RuntimeSemanticAction.OBJECT_UNAVAILABLE) {
            MObject object = system.state().objectByName(objectName);
            if (object != null) difference(differences, event, "object:" + objectName, "<absent>", object.cls().name());
            return;
        }
        if (action == RuntimeSemanticAction.ATTRIBUTE_STATE_SET || action == RuntimeSemanticAction.ATTRIBUTE_STATE_UNSET) {
            if (!event.payload().containsKey("attribute")) throw new IllegalArgumentException("RUNTIME_PROPERTY_UNBOUND");
            MObject object = requireObject(objectName);
            String attributeName = text(event.payload(), "attribute");
            var attribute = object.cls().attribute(attributeName, true);
            if (attribute == null) throw new IllegalArgumentException("USE_ATTRIBUTE_MISSING: " + attributeName);
            Value expected = action == RuntimeSemanticAction.ATTRIBUTE_STATE_UNSET
                    ? UndefinedValue.instance : value(event.payload());
            Value actual = object.state(system.state()).attributeValue(attribute);
            if (!expected.equals(actual)) difference(differences, event, "object:" + objectName + "." + attributeName,
                    expected.toString(), actual.toString());
            return;
        }
        if (action == RuntimeSemanticAction.RELATION_INSERT || action == RuntimeSemanticAction.RELATION_DELETE) {
            var association = system.model().getAssociation(text(event.payload(), "association"));
            if (association == null) throw new IllegalArgumentException("USE_ASSOCIATION_MISSING");
            List<MObject> participants = participants(event.payload());
            boolean present = system.state().hasLinkBetweenObjects(association, participants.toArray(MObject[]::new));
            boolean expected = action == RuntimeSemanticAction.RELATION_INSERT;
            if (present != expected) difference(differences, event, "association:" + association.name(),
                    Boolean.toString(expected), Boolean.toString(present));
        }
    }

    private void difference(List<RuntimeDriftDifference> differences, RuntimeEvent event, String target,
                            String expected, String actual) {
        differences.add(new RuntimeDriftDifference("RUNTIME_MIRROR_DRIFT", event.eventId(),
                event.runtimeSourceId(), target, expected, actual));
    }

    private MutationResult destroy(String runtimeSourceId, String objectName) {
        MObject object = system.state().objectByName(objectName);
        if (object != null && trace.byTargetKind("ORDER_LINK").stream().anyMatch(record -> {
            String[] parts = record.targetUseId().split(":", 4);
            return parts.length == 4 && (parts[2].equals(objectName) || parts[3].equals(objectName));
        })) throw new IllegalArgumentException("RUNTIME_ORDER_MEMBERSHIP_REQUIRES_RESYNC:" + objectName);
        if (object != null) system.state().deleteObject(object);
        operationCorrelations.entrySet().removeIf(entry -> !entry.getValue().rejected()
            && entry.getValue().target().startsWith(objectName + "::"));
        dynamicRuntimeObjects.remove(runtimeSourceId);
        return MutationResult.applied();
    }

    private MutationResult set(String objectName, Map<String, Object> payload) {
        MObject object = requireObject(objectName);
        String attributeName = text(payload, "attribute");
        var attribute = object.cls().attribute(attributeName, true);
        if (attribute == null) throw new IllegalArgumentException("USE_ATTRIBUTE_MISSING: " + attributeName);
        if (trace.byUseId("attribute:" + attribute.owner().name() + "." + attributeName).stream().noneMatch(record ->
            record.status() == org.tzi.use.plugins.jacamo.trace.TraceRecord.Status.RESOLVED
            || record.status() == org.tzi.use.plugins.jacamo.trace.TraceRecord.Status.PROJECTED))
            throw new IllegalArgumentException("RUNTIME_ATTRIBUTE_TRACE_UNRESOLVED:" + attributeName);
        Value converted = value(payload);
        if (!converted.isUndefined() && !converted.type().conformsTo(attribute.type()))
            throw new IllegalArgumentException("RUNTIME_ATTRIBUTE_TYPE_MISMATCH:" + attributeName);
        object.state(system.state()).setAttributeValue(attribute, converted);
        return MutationResult.applied();
    }

    private MutationResult insert(Map<String, Object> payload) throws Exception {
        var association = system.model().getAssociation(text(payload, "association"));
        if (association == null) throw new IllegalArgumentException("USE_ASSOCIATION_MISSING");
        List<MObject> objects = tracedParticipants(payload, association.name());
        if (!system.state().hasLinkBetweenObjects(association, objects.toArray(MObject[]::new))) {
            requireUnorderedMembershipChange(association.name());
            system.state().createLink(association, objects, null);
        }
        return MutationResult.applied();
    }

    private MutationResult delete(Map<String, Object> payload) throws Exception {
        var association = system.model().getAssociation(text(payload, "association"));
        if (association == null) throw new IllegalArgumentException("USE_ASSOCIATION_MISSING");
        List<MObject> objects = tracedParticipants(payload, association.name());
        if (system.state().hasLinkBetweenObjects(association, objects.toArray(MObject[]::new))) {
            requireUnorderedMembershipChange(association.name());
            system.state().deleteLink(association, objects, null);
        }
        return MutationResult.applied();
    }

    private void requireUnorderedMembershipChange(String association) {
        boolean ordered = trace.byUseId("association:" + association).stream().anyMatch(reference ->
                trace.bySemanticId(reference.sourceSemanticId()).stream().anyMatch(t -> t.targetKind().equals("ORDER_NAVIGATION")));
        if (ordered) throw new IllegalArgumentException("RUNTIME_ORDER_MEMBERSHIP_REQUIRES_RESYNC:" + association);
    }

    private MutationResult enter(RuntimeEvent event, String objectName) {
        String correlation = event.correlationId();
        MObject object = requireObject(objectName);
        String operation = text(event.payload(), "operation");
        if (object.cls().operation(operation, true) == null)
            throw new IllegalArgumentException("USE_OPERATION_MISSING: " + operation);
        if (trace.byUseId("operation:" + object.cls().operation(operation, true).cls().name() + "." + operation).stream()
                .noneMatch(record -> record.status() == org.tzi.use.plugins.jacamo.trace.TraceRecord.Status.PROJECTED))
            throw new IllegalArgumentException("RUNTIME_OPERATION_TRACE_UNRESOLVED:" + operation);
        var signature = object.cls().operation(operation, true);
        if (!(event.payload().get("arguments") instanceof List<?> arguments)
                || arguments.size() != signature.paramList().size())
            throw new IllegalArgumentException("OPERATION_ARGUMENT_COUNT");
        for (int index = 0; index < arguments.size(); index++)
            RuntimeValues.convert(signature.paramList().varDecl(index).type().toString(), arguments.get(index));
        synchronized (operationLifecycle) {
            if (correlationOverflow)
                throw new IllegalArgumentException("OPERATION_CORRELATION_CAPACITY_REQUIRES_RESYNC");
            if (completedCorrelations.contains(correlation))
                throw new IllegalArgumentException("OPERATION_CORRELATION_COMPLETED:" + correlation);
            RuntimeOperationState current = operationCorrelations.get(correlation);
            if (current != null && !current.rejected())
                throw new IllegalArgumentException("OPERATION_CORRELATION_ACTIVE: " + correlation);
            if (current == null && operationCorrelations.size() >= operationCorrelationLimit)
                throw new IllegalArgumentException("OPERATION_CORRELATION_CAPACITY:" + operationCorrelationLimit);
            // A newer rejected terminal tombstone deliberately suppresses this older enter.
            // The event itself remains applied because OP_ENTER has no direct USE state mutation.
            if (current == null || current.sequence() < event.sequence())
                operationCorrelations.put(correlation,
                        RuntimeOperationState.active(event.sequence(), objectName + "::" + operation));
        }
        rememberHistory(correlation, OperationRuntimeOutcome.ENTERED);
        return MutationResult.applied();
    }

    private MutationResult exit(RuntimeEvent event, OperationRuntimeOutcome outcome) {
        RuntimeOperationState[] matched = { null };
        synchronized (operationLifecycle) {
            operationCorrelations.compute(event.correlationId(), (ignored, current) -> {
                if (current == null || current.sequence() > event.sequence()) return current;
                if (!current.rejected()) {
                    String object = resolveObject(event.runtimeSourceId());
                    String operation = (String) event.payload().get("operation");
                    if (!current.target().startsWith(object + "::")
                            || operation != null && !current.target().equals(object + "::" + operation))
                        throw new IllegalArgumentException("OPERATION_TARGET_MISMATCH:" + event.correlationId());
                    matched[0] = current;
                }
                return null;
            });
        }
        if (matched[0] == null)
            throw new IllegalArgumentException("OPERATION_CORRELATION_MISSING: " + event.correlationId());
        synchronized (operationLifecycle) {
            if (RuntimeRetention.remember(completedCorrelations, event.correlationId(), operationCorrelationLimit))
                retiredCompletedCorrelations++;
        }
        rememberHistory(event.correlationId(), outcome);
        return MutationResult.applied();
    }

    private boolean isOperationTerminal(RuntimeEvent event) {
        return event.kind() == RuntimeEventKind.OP_EXIT || event.kind() == RuntimeEventKind.OP_FAIL;
    }

    private void resetOperationLifecycle() {
        synchronized (operationLifecycle) {
            completedCorrelations.clear();
            operationCorrelations.clear();
            completedThroughSequence = -1;
            correlationOverflow = false;
        }
    }

    private void rememberHistory(String correlation, OperationRuntimeOutcome outcome) {
        if (RuntimeRetention.putLatest(operationHistory, correlation, outcome, operationHistoryLimit))
            retiredOperationHistory++;
    }

    private String resolveObject(String runtimeSourceId) {
        String dynamic = dynamicRuntimeObjects.get(runtimeSourceId);
        if (dynamic != null) return dynamic;
        if (trace.byRuntimeKey(runtimeSourceId).isEmpty()) return null;
        String target = targets.resolve(new RuntimeTargetResolver.Request(runtimeSourceId, null, "OBJECT")).useId();
        return target.startsWith("object:") ? target.substring("object:".length()) : null;
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
        return RuntimeValues.convert(text(payload, "valueType"), payload.get("value"));
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

    private record RuntimeOperationState(long sequence, String target, long retireAfterSequence) {
        private static RuntimeOperationState active(long sequence, String target) {
            return new RuntimeOperationState(sequence, target, -1);
        }

        private static RuntimeOperationState rejected(long sequence, long retireAfterSequence) {
            return new RuntimeOperationState(sequence, null, retireAfterSequence);
        }

        private boolean rejected() { return target == null; }
    }
}
