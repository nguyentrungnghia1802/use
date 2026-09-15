package org.tzi.use.plugins.jacamo.runtime;

import java.net.URI;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class SyntheticRuntimeConnector implements RuntimeConnector {
    private final String id;
    private final Path replayFile;
    private final RuntimeEventCodec codec;
    private final CopyOnWriteArrayList<Consumer<RuntimeEvent>> listeners = new CopyOnWriteArrayList<>();
    private volatile RuntimeSnapshot snapshot;
    private volatile ConnectorState state = ConnectorState.DISCONNECTED;

    public SyntheticRuntimeConnector(String id, RuntimeSnapshot snapshot, Path replayFile, RuntimeEventCodec codec) {
        if (id == null || id.isBlank() || snapshot == null || replayFile == null || codec == null)
            throw new IllegalArgumentException("SYNTHETIC_CONNECTOR_INVALID");
        this.id = id;
        this.snapshot = snapshot;
        this.replayFile = replayFile.toAbsolutePath().normalize();
        this.codec = codec;
    }

    @Override public String connectorId() { return id; }
    @Override public Set<ConnectorCapability> capabilities() {
        return Set.of(ConnectorCapability.FULL_SNAPSHOT, ConnectorCapability.EVENT_SUBSCRIPTION,
                ConnectorCapability.RECONNECT);
    }
    @Override public ConnectorState state() { return state; }
    @Override public void connect(URI endpoint) {
        if (endpoint == null || !"synthetic".equalsIgnoreCase(endpoint.getScheme()))
            throw new IllegalArgumentException("SYNTHETIC_ENDPOINT_INVALID");
        state = ConnectorState.CONNECTED;
    }
    @Override public RuntimeSnapshot fullSnapshot() {
        requireConnected();
        return snapshot;
    }
    @Override public RuntimeSubscription subscribe(Consumer<RuntimeEvent> listener) {
        requireConnected();
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
    @Override public void disconnect() {
        listeners.clear();
        state = ConnectorState.DISCONNECTED;
    }
    public void replayAll() {
        requireConnected();
        for (RuntimeEvent event : codec.readEvents(replayFile))
            for (Consumer<RuntimeEvent> listener : listeners) listener.accept(event);
    }
    public void replaceSnapshot(RuntimeSnapshot snapshot) { this.snapshot = snapshot; }
    private void requireConnected() {
        if (state != ConnectorState.CONNECTED) throw new IllegalStateException("CONNECTOR_NOT_CONNECTED");
    }
}
