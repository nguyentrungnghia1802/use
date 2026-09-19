package org.tzi.use.plugins.jacamo.runtime;

import cartago.AgentId;
import cartago.ArtifactId;
import cartago.ArtifactInfo;
import cartago.ArtifactObsProperty;
import cartago.CartagoException;
import cartago.CartagoLoggerAdapter;
import cartago.Op;
import cartago.OpId;
import cartago.Tuple;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.tzi.use.plugins.jacamo.semantic.Dimension;

/** Live CArtAgO connector using workspace controllers for snapshots and workspace loggers for deltas. */
public final class CartagoRuntimeConnector implements RuntimeConnector {
    private final String id;
    private final CartagoRuntimeAccess access;
    private final Map<String, CartagoArtifactBinding> bindings = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<Consumer<RuntimeEvent>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private WorkspaceLogger logger;
    private long generation;
    private final Map<String, ArtifactId> incarnations = new LinkedHashMap<>();
    private final List<String> quarantinedObservations = new CopyOnWriteArrayList<>();
    private final Set<String> registeredWorkspaces = new LinkedHashSet<>();
    private volatile ConnectorState state = ConnectorState.DISCONNECTED;

    public CartagoRuntimeConnector(String id, CartagoRuntimeAccess access, List<CartagoArtifactBinding> bindings) {
        if (id == null || id.isBlank() || access == null || bindings == null || bindings.isEmpty())
            throw new IllegalArgumentException("CARTAGO_CONNECTOR_INVALID");
        this.id = id;
        this.access = access;
        for (CartagoArtifactBinding binding : bindings) {
            if (this.bindings.putIfAbsent(binding.qualifiedName(), binding) != null)
                throw new IllegalArgumentException("CARTAGO_BINDING_DUPLICATE: " + binding.qualifiedName());
        }
    }

    @Override public String connectorId() { return id; }
    @Override public Set<ConnectorCapability> capabilities() {
        return Set.of(ConnectorCapability.FULL_SNAPSHOT, ConnectorCapability.EVENT_SUBSCRIPTION,
                ConnectorCapability.RECONNECT, ConnectorCapability.ARTIFACT_STATE, ConnectorCapability.OPERATION_EVENTS);
    }
    @Override public ConnectorState state() { return state; }

    @Override public synchronized void connect(URI endpoint) {
        if (endpoint == null || !"jacamo".equalsIgnoreCase(endpoint.getScheme()))
            throw new IllegalArgumentException("CARTAGO_ENDPOINT_INVALID");
        if (state == ConnectorState.CONNECTED) throw new IllegalStateException("CONNECTOR_ALREADY_CONNECTED");
        try {
            logger = new WorkspaceLogger(++generation);
            incarnations.clear();
            for (String workspace : bindings.values().stream().map(CartagoArtifactBinding::workspace).distinct().toList()) {
                access.registerLogger(workspace, logger);
                registeredWorkspaces.add(workspace);
            }
            state = ConnectorState.CONNECTED;
        } catch (CartagoException exception) {
            state = ConnectorState.ERROR;
            unregisterAll();
            throw new IllegalStateException("CARTAGO_CONNECT_FAILED", exception);
        }
    }

    public List<String> discoveredArtifacts() {
        requireConnected();
        try {
            List<String> result = new ArrayList<>();
            for (String workspace : registeredWorkspaces)
                for (ArtifactId artifact : access.controller(workspace).getCurrentArtifacts())
                    result.add(workspace + "/" + artifact.getName());
            return result.stream().sorted().toList();
        } catch (CartagoException exception) { throw new IllegalStateException("CARTAGO_DISCOVERY_FAILED", exception); }
    }

    @Override public synchronized RuntimeSnapshot fullSnapshot() {
        requireConnected();
        try {
            List<RuntimeEvent> events = new ArrayList<>();
            for (CartagoArtifactBinding binding : bindings.values()) {
                ArtifactInfo information = access.controller(binding.workspace()).getArtifactInfo(binding.artifact());
                if (information == null) throw new IllegalStateException("CARTAGO_ARTIFACT_MISSING: " + binding.qualifiedName());
                incarnations.put(binding.qualifiedName(), information.getId());
                Set<String> observed = new LinkedHashSet<>();
                if (information.getObsProperties() != null) {
                    for (ArtifactObsProperty property : information.getObsProperties()) {
                        observed.add(property.getName());
                        events.add(propertyEvent(binding, property, RuntimeEventKind.OBS_PROPERTY_ADDED,
                                Instant.now()));
                    }
                }
                binding.observableAttributes().keySet().stream().filter(property -> !observed.contains(property))
                        .sorted().forEach(property -> events.add(missingPropertyEvent(binding, property)));
            }
            String fingerprint = sha256(events.stream().map(value -> value.runtimeSourceId() + "|"
                    + value.payload()).sorted().toList().toString());
            long last = events.isEmpty() ? sequence.get() : events.getLast().sequence();
            return new RuntimeSnapshot("cartago-" + last, Instant.now(), last, events, fingerprint);
        } catch (CartagoException exception) { throw new IllegalStateException("CARTAGO_SNAPSHOT_FAILED", exception); }
    }

    @Override public RuntimeSubscription subscribe(Consumer<RuntimeEvent> listener) {
        requireConnected();
        if (listener == null) throw new IllegalArgumentException("RUNTIME_LISTENER_REQUIRED");
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override public synchronized void disconnect() {
        generation++;
        unregisterAll();
        listeners.clear();
        state = ConnectorState.DISCONNECTED;
    }

    private void unregisterAll() {
        for (String workspace : List.copyOf(registeredWorkspaces)) {
            try { access.unregisterLogger(workspace, logger); }
            catch (CartagoException ignored) { state = ConnectorState.ERROR; }
        }
        registeredWorkspaces.clear();
    }

    private CartagoArtifactBinding binding(ArtifactId artifact) {
        String workspace = artifact.getWorkspaceId().getFullName();
        return bindings.get(workspace + "/" + artifact.getName());
    }

    public List<String> quarantinedObservations() { return List.copyOf(quarantinedObservations); }

    private RuntimeEvent propertyEvent(CartagoArtifactBinding binding, ArtifactObsProperty property,
                                       RuntimeEventKind kind, Instant timestamp) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("property", property.getName());
        payload.put("propertyId", String.valueOf(property.getId()));
        payload.put("propertyKey", binding.runtimeSourceId() + ":property:" + property.getName());
        payload.put("values", safeValues(property.getValues()));
        Object value = property.getValues().length == 1 ? safe(property.getValue()) : property.toString();
        payload.put("value", value);
        payload.put("valueType", valueType(value));
        String attribute = binding.observableAttributes().get(property.getName());
        if (attribute != null) payload.put("attribute", attribute);
        return event(binding, kind, payload, null, timestamp);
    }

    private RuntimeEvent missingPropertyEvent(CartagoArtifactBinding binding, String property) {
        return event(binding, RuntimeEventKind.OBS_PROPERTY_REMOVED,
                Map.of("property", property, "values", List.of(),
                        "attribute", binding.observableAttributes().get(property),
                        "value", "undefined", "valueType", "UNDEFINED"), null, Instant.now());
    }

    private synchronized RuntimeEvent event(CartagoArtifactBinding binding, RuntimeEventKind kind,
                                            Map<String, Object> payload, String correlation, Instant timestamp) {
        long next = sequence.incrementAndGet();
        return RuntimeEvent.create(id + "-" + next, timestamp, next, Dimension.ENVIRONMENT, kind,
                binding.runtimeSourceId(), binding.semanticId(), payload, correlation);
    }

    private void emit(RuntimeEvent event) { listeners.forEach(listener -> listener.accept(event)); }
    private void requireConnected() {
        if (state != ConnectorState.CONNECTED) throw new IllegalStateException("CONNECTOR_NOT_CONNECTED");
    }
    private String correlation(CartagoArtifactBinding binding, OpId operation) {
        return binding.runtimeSourceId() + ":generation:" + generation + ":artifact:"
                + operation.getArtifactId().getId() + ":op:" + operation.getId() + ":"
                + operation.getOpName() + ":agent:" + operation.getAgentBodyId().getGlobalId();
    }
    private List<Object> safeValues(Object[] values) { return Arrays.stream(values).map(this::safe).toList(); }
    private Object safe(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                ? value : value.toString();
    }
    private String valueType(Object value) {
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return "INTEGER";
        if (value instanceof Number) return "REAL";
        return "STRING";
    }
    private List<Object> operationArguments(Op operation) { return safeValues(operation.getParamValues()); }
    private Instant instant(long when) { return when > 0 ? Instant.ofEpochMilli(when) : Instant.now(); }
    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception exception) { throw new IllegalStateException("SHA256_UNAVAILABLE", exception); }
    }

    private final class WorkspaceLogger extends CartagoLoggerAdapter {
        private final long owner;
        private WorkspaceLogger(long owner) { this.owner = owner; }
        private CartagoArtifactBinding resolve(ArtifactId artifact) {
            synchronized (CartagoRuntimeConnector.this) {
                if (owner != generation) {
                    quarantinedObservations.add("CARTAGO_RETIRED_GENERATION:" + owner + ":" + artifact.getId());
                    return null;
                }
                CartagoArtifactBinding result = binding(artifact);
                if (result == null) {
                    quarantinedObservations.add("CARTAGO_UNBOUND_ARTIFACT:" + artifact.getWorkspaceId().getFullName()
                            + "/" + artifact.getName() + ":" + artifact.getId());
                    return null;
                }
                ArtifactId previous = incarnations.putIfAbsent(result.qualifiedName(), artifact);
                if (previous != null && !previous.equals(artifact)) {
                    quarantinedObservations.add("CARTAGO_STALE_ARTIFACT:" + artifact.getId());
                    return null;
                }
                return result;
            }
        }
        @Override public void artifactCreated(long when, ArtifactId artifact, AgentId creator) {
            CartagoArtifactBinding binding = resolve(artifact);
            if (binding != null) emit(event(binding, RuntimeEventKind.ARTIFACT_CREATED,
                    Map.of("artifact", artifact.getName(), "type", artifact.getArtifactType(),
                            "creator", creator == null ? "unknown" : creator.getAgentName()), null, instant(when)));
        }
        @Override public void artifactDisposed(long when, ArtifactId artifact, AgentId disposer) {
            CartagoArtifactBinding binding = resolve(artifact);
            if (binding != null) emit(event(binding, RuntimeEventKind.ARTIFACT_DISPOSED,
                    Map.of("artifact", artifact.getName(), "disposer",
                            disposer == null ? "unknown" : disposer.getAgentName()), null, instant(when)));
        }
        @Override public void newPercept(long when, ArtifactId artifact, Tuple signal,
                                         ArtifactObsProperty[] added, ArtifactObsProperty[] removed,
                                         ArtifactObsProperty[] changed) {
            CartagoArtifactBinding binding = resolve(artifact);
            if (binding == null) return;
            if (added != null) for (ArtifactObsProperty property : added)
                emit(propertyEvent(binding, property, RuntimeEventKind.OBS_PROPERTY_ADDED, instant(when)));
            if (changed != null) for (ArtifactObsProperty property : changed)
                emit(propertyEvent(binding, property, RuntimeEventKind.OBS_PROPERTY_CHANGED, instant(when)));
            if (removed != null) for (ArtifactObsProperty property : removed)
                emit(propertyEvent(binding, property, RuntimeEventKind.OBS_PROPERTY_REMOVED, instant(when)));
            if (signal != null) emit(event(binding, RuntimeEventKind.SIGNAL,
                    Map.of("signal", signal.getLabel(), "values", safeValues(signal.getContents())), null, instant(when)));
        }
        @Override public void opStarted(long when, OpId operationId, ArtifactId artifact, Op operation) {
            CartagoArtifactBinding binding = resolve(artifact);
            if (binding == null || !binding.operations().containsKey(operation.getName())) return;
            emit(event(binding, RuntimeEventKind.OP_ENTER,
                    Map.of("operation", binding.operations().get(operation.getName()),
                            "runtimeOperation", operation.getName(), "arguments", operationArguments(operation),
                            "agent", operationId.getAgentBodyId().getAgentName()),
                    correlation(binding, operationId), instant(when)));
        }
        @Override public void opCompleted(long when, OpId operationId, ArtifactId artifact, Op operation) {
            CartagoArtifactBinding binding = resolve(artifact);
            if (binding == null || !binding.operations().containsKey(operation.getName())) return;
            emit(event(binding, RuntimeEventKind.OP_EXIT, Map.of(
                            "operation", binding.operations().get(operation.getName()),
                            "runtimeOperation", operation.getName(),
                            "agent", operationId.getAgentBodyId().getAgentName()),
                    correlation(binding, operationId), instant(when)));
        }
        @Override public void opFailed(long when, OpId operationId, ArtifactId artifact, Op operation,
                                       String message, Tuple description) {
            CartagoArtifactBinding binding = resolve(artifact);
            if (binding == null || !binding.operations().containsKey(operation.getName())) return;
            emit(event(binding, RuntimeEventKind.OP_FAIL,
                    Map.of("operation", binding.operations().get(operation.getName()),
                            "runtimeOperation", operation.getName(),
                            "agent", operationId.getAgentBodyId().getAgentName(),
                            "error", message == null ? "CArtAgO operation failed" : message,
                            "description", description == null ? "" : description.toString()),
                    correlation(binding, operationId), instant(when)));
        }
    }
}
