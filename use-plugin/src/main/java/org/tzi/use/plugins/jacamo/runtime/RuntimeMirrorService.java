package org.tzi.use.plugins.jacamo.runtime;

import java.net.URI;
import java.time.Duration;

/** Coordinates lifecycle, full synchronization and the ordered delta path. */
public final class RuntimeMirrorService implements RuntimeService {
    private final RuntimeConnector connector;
    private final RuntimeMutationEngine mutations;
    private final int queueCapacity;
    private volatile MirrorState state = MirrorState.OFFLINE;
    private URI endpoint;
    private RuntimeSubscription subscription;
    private OrderedRuntimeEventQueue queue;
    private String lastSnapshotFingerprint;

    public RuntimeMirrorService(RuntimeConnector connector, RuntimeMutationEngine mutations, int queueCapacity) {
        if (connector == null || mutations == null || queueCapacity < 1)
            throw new IllegalArgumentException("RUNTIME_MIRROR_INVALID");
        this.connector = connector;
        this.mutations = mutations;
        this.queueCapacity = queueCapacity;
    }

    @Override public synchronized void connect(URI endpoint) {
        if (state == MirrorState.LIVE || state == MirrorState.CONNECTING || state == MirrorState.SYNCING)
            throw new IllegalStateException("RUNTIME_MIRROR_ALREADY_CONNECTED");
        this.endpoint = endpoint;
        state = MirrorState.CONNECTING;
        try {
            connector.connect(endpoint);
            state = MirrorState.SYNCING;
            applySnapshot(connector.fullSnapshot());
            startEvents();
            state = MirrorState.LIVE;
        } catch (RuntimeException exception) {
            state = MirrorState.ERROR;
            connector.disconnect();
            throw exception;
        }
    }

    @Override public synchronized void disconnect() {
        stopEvents();
        connector.disconnect();
        if (state != MirrorState.OFFLINE) state = MirrorState.STALE;
    }

    public synchronized void reconnectAndResync() {
        if (endpoint == null) throw new IllegalStateException("RUNTIME_ENDPOINT_MISSING");
        connect(endpoint);
    }

    @Override public synchronized void resync() {
        if (state != MirrorState.LIVE) throw new IllegalStateException("RUNTIME_MIRROR_NOT_LIVE");
        state = MirrorState.SYNCING;
        stopEvents();
        try {
            applySnapshot(connector.fullSnapshot());
            startEvents();
            state = MirrorState.LIVE;
        } catch (RuntimeException exception) {
            state = MirrorState.ERROR;
            throw exception;
        }
    }

    public void awaitIdle(Duration timeout) {
        OrderedRuntimeEventQueue current = queue;
        if (current != null) current.awaitIdle(timeout);
    }

    @Override public MirrorState state() { return state; }
    @Override public QueueMetrics metrics() {
        OrderedRuntimeEventQueue current = queue;
        return current == null ? new QueueMetrics(0, 0, 0, 0, 0, 0) : current.metrics();
    }
    public String lastSnapshotFingerprint() { return lastSnapshotFingerprint; }

    @Override public synchronized void close() {
        disconnect();
        state = MirrorState.OFFLINE;
    }

    private void applySnapshot(RuntimeSnapshot snapshot) {
        mutations.applySnapshot(snapshot);
        lastSnapshotFingerprint = snapshot.fingerprint();
    }

    private void startEvents() {
        queue = new OrderedRuntimeEventQueue(queueCapacity, event -> {
            MutationResult result = mutations.apply(event);
            if (result.status() != MutationStatus.APPLIED) {
                state = MirrorState.ERROR;
                throw new IllegalStateException(result.diagnostic());
            }
        });
        queue.start();
        subscription = connector.subscribe(queue::submit);
    }

    private void stopEvents() {
        if (subscription != null) { subscription.close(); subscription = null; }
        if (queue != null) queue.stopGracefully(Duration.ofSeconds(5));
    }
}
