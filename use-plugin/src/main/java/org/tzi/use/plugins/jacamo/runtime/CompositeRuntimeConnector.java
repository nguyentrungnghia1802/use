package org.tzi.use.plugins.jacamo.runtime;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** One ordered JaCaMo connection composed from the Jason, CArtAgO and Moise connectors. */
public final class CompositeRuntimeConnector implements RuntimeConnector {
    private final String id;
    private final List<RuntimeConnector> connectors;
    private final CopyOnWriteArrayList<Consumer<RuntimeEvent>> listeners = new CopyOnWriteArrayList<>();
    private final List<RuntimeSubscription> childSubscriptions = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private volatile ConnectorState state = ConnectorState.DISCONNECTED;
    private long subscriptionGeneration;
    private final List<RuntimeEvent> retiredEvents = new CopyOnWriteArrayList<>();

    public CompositeRuntimeConnector(String id, List<RuntimeConnector> connectors) {
        if (id == null || id.isBlank() || connectors == null || connectors.isEmpty()
                || connectors.stream().anyMatch(value -> value == null)
                || connectors.stream().map(RuntimeConnector::connectorId).distinct().count() != connectors.size())
            throw new IllegalArgumentException("COMPOSITE_CONNECTOR_INVALID");
        this.id = id;
        this.connectors = List.copyOf(connectors);
    }

    @Override public String connectorId() { return id; }
    @Override public Set<ConnectorCapability> capabilities() {
        Set<ConnectorCapability> result = new LinkedHashSet<>();
        for (ConnectorCapability capability : ConnectorCapability.values()) {
            boolean transport = capability == ConnectorCapability.FULL_SNAPSHOT
                    || capability == ConnectorCapability.EVENT_SUBSCRIPTION
                    || capability == ConnectorCapability.RECONNECT;
            boolean supported = transport
                    ? connectors.stream().allMatch(value -> value.capabilities().contains(capability))
                    : connectors.stream().anyMatch(value -> value.capabilities().contains(capability));
            if (supported) result.add(capability);
        }
        return Set.copyOf(result);
    }
    @Override public ConnectorState state() {
        if (state == ConnectorState.CONNECTED
                && connectors.stream().anyMatch(connector -> connector.state() != ConnectorState.CONNECTED))
            state = ConnectorState.ERROR;
        return state;
    }

    @Override public synchronized void connect(URI endpoint) {
        if (state == ConnectorState.CONNECTED) throw new IllegalStateException("CONNECTOR_ALREADY_CONNECTED");
        List<RuntimeConnector> connected = new ArrayList<>();
        try {
            for (RuntimeConnector connector : connectors) {
                connector.connect(endpoint);
                connected.add(connector);
            }
            state = ConnectorState.CONNECTED;
        } catch (RuntimeException exception) {
            state = ConnectorState.ERROR;
            for (int i = connected.size() - 1; i >= 0; i--) {
                try { connected.get(i).disconnect(); }
                catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
            }
            throw new IllegalStateException("COMPOSITE_CONNECT_FAILED: " + exception.getMessage(), exception);
        }
    }

    @Override public synchronized RuntimeSnapshot fullSnapshot() {
        requireConnected();
        List<RuntimeEvent> events = new ArrayList<>();
        List<String> fingerprints = new ArrayList<>();
        for (RuntimeConnector connector : connectors) {
            RuntimeSnapshot snapshot = connector.fullSnapshot();
            fingerprints.add(connector.connectorId() + "=" + snapshot.fingerprint());
            snapshot.mutations().forEach(event -> events.add(normalize(connector, event)));
        }
        String fingerprint = sha256(fingerprints.toString());
        long last = events.isEmpty() ? sequence.get() : events.getLast().sequence();
        return new RuntimeSnapshot("composite-" + last, Instant.now(), last, events, fingerprint);
    }

    @Override public synchronized RuntimeSubscription subscribe(Consumer<RuntimeEvent> listener) {
        requireConnected();
        if (listener == null) throw new IllegalArgumentException("RUNTIME_LISTENER_REQUIRED");
        listeners.add(listener);
        if (childSubscriptions.isEmpty()) {
            long owner = ++subscriptionGeneration;
            try {
                for (RuntimeConnector connector : connectors)
                    childSubscriptions.add(connector.subscribe(event -> forward(owner, connector, event)));
            } catch (RuntimeException exception) {
                listeners.remove(listener);
                try { closeChildSubscriptions(); }
                catch (RuntimeException cleanup) { exception.addSuppressed(cleanup); }
                throw exception;
            }
        }
        return () -> {
            listeners.remove(listener);
            synchronized (CompositeRuntimeConnector.this) {
                if (listeners.isEmpty()) closeChildSubscriptions();
            }
        };
    }

    @Override public synchronized void disconnect() {
        listeners.clear();
        RuntimeException failure = null;
        try { closeChildSubscriptions(); } catch (RuntimeException exception) { failure = exception; }
        for (int i = connectors.size() - 1; i >= 0; i--) {
            try { connectors.get(i).disconnect(); }
            catch (RuntimeException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        state = failure == null ? ConnectorState.DISCONNECTED : ConnectorState.ERROR;
        if (failure != null) throw new IllegalStateException("COMPOSITE_DISCONNECT_FAILED", failure);
    }

    public List<RuntimeEvent> retiredEvents() { return List.copyOf(retiredEvents); }

    private synchronized void forward(long owner, RuntimeConnector connector, RuntimeEvent event) {
        if (owner != subscriptionGeneration) { retiredEvents.add(event); return; }
        RuntimeEvent normalized = normalize(connector, event);
        listeners.forEach(listener -> listener.accept(normalized));
    }

    private RuntimeEvent normalize(RuntimeConnector connector, RuntimeEvent source) {
        long next = sequence.incrementAndGet();
        Map<String, Object> payload = new LinkedHashMap<>(source.payload());
        payload.put("sourceConnector", connector.connectorId());
        payload.put("sourceEventId", source.eventId());
        String correlation = source.correlationId() == null ? null
                : connector.connectorId() + ":" + source.correlationId();
        return RuntimeEvent.create(id + "-" + next, source.timestamp(), next, source.dimension(), source.kind(),
                source.runtimeSourceId(), source.semanticSourceId(), payload, correlation);
    }

    private void closeChildSubscriptions() {
        subscriptionGeneration++;
        RuntimeException failure = null;
        for (RuntimeSubscription subscription : List.copyOf(childSubscriptions)) {
            try { subscription.close(); childSubscriptions.remove(subscription); }
            catch (RuntimeException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) { state = ConnectorState.ERROR; throw failure; }
    }
    private void requireConnected() {
        if (state != ConnectorState.CONNECTED || connectors.stream().anyMatch(
                connector -> connector.state() != ConnectorState.CONNECTED)) {
            state = ConnectorState.ERROR;
            throw new IllegalStateException("COMPOSITE_CHILD_DISCONNECTED");
        }
    }
    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception exception) { throw new IllegalStateException("SHA256_UNAVAILABLE", exception); }
    }
}
