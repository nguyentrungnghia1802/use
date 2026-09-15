package org.tzi.use.plugins.jacamo.runtime;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
            synchronizeSnapshot();
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
            synchronizeSnapshot();
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

    /** Refreshes externally observable connector health without claiming stale data as live. */
    public synchronized void refreshConnectionState() {
        if (state == MirrorState.LIVE && connector.state() != ConnectorState.CONNECTED) {
            stopEvents();
            state = MirrorState.STALE;
        }
    }

    @Override public synchronized void close() {
        disconnect();
        state = MirrorState.OFFLINE;
    }

    private void applySnapshot(RuntimeSnapshot snapshot) {
        mutations.applySnapshot(snapshot);
        lastSnapshotFingerprint = snapshot.fingerprint();
    }

    private void synchronizeSnapshot() {
        Object gate = new Object();
        List<RuntimeEvent> buffered = new ArrayList<>();
        OrderedRuntimeEventQueue[] ready = new OrderedRuntimeEventQueue[1];
        RuntimeSubscription nextSubscription = connector.subscribe(event -> {
            synchronized (gate) {
                if (ready[0] == null) buffered.add(event);
                else ready[0].submit(event);
            }
        });
        RuntimeSnapshot snapshot;
        try {
            snapshot = connector.fullSnapshot();
            applySnapshot(snapshot);
        } catch (RuntimeException exception) {
            nextSubscription.close();
            throw exception;
        }
        OrderedRuntimeEventQueue nextQueue = new OrderedRuntimeEventQueue(queueCapacity, event -> {
            MutationResult result = mutations.apply(event);
            if (result.status() != MutationStatus.APPLIED) {
                state = MirrorState.ERROR;
                throw new IllegalStateException(result.diagnostic());
            }
        });
        nextQueue.start();
        synchronized (gate) {
            ready[0] = nextQueue;
            buffered.stream().filter(event -> event.sequence() > snapshot.sequence()).forEach(nextQueue::submit);
            buffered.clear();
        }
        queue = nextQueue;
        subscription = nextSubscription;
    }

    private void stopEvents() {
        if (subscription != null) { subscription.close(); subscription = null; }
        if (queue != null) queue.stopGracefully(Duration.ofSeconds(5));
    }
}
