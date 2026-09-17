package org.tzi.use.plugins.jacamo.runtime;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Coordinates lifecycle, full synchronization and the ordered delta path. */
public final class RuntimeMirrorService implements RuntimeService {
    private final RuntimeConnector connector;
    private RuntimeMutationEngine mutations;
    private final int queueCapacity;
    private RuntimeEventObserver observer;
    private volatile MirrorState state = MirrorState.OFFLINE;
    private URI endpoint;
    private RuntimeSubscription subscription;
    private OrderedRuntimeEventQueue queue;
    private String lastSnapshotFingerprint;
    private RuntimeSnapshot lastSnapshot;
    private RuntimeDriftReport lastDriftReport;
    private ScheduledExecutorService driftMonitor;

    public RuntimeMirrorService(RuntimeConnector connector, RuntimeMutationEngine mutations, int queueCapacity) {
        this(connector, mutations, queueCapacity, RuntimeEventObserver.NOOP);
    }

    public RuntimeMirrorService(RuntimeConnector connector, RuntimeMutationEngine mutations, int queueCapacity,
                                RuntimeEventObserver observer) {
        if (connector == null || mutations == null || queueCapacity < 1)
            throw new IllegalArgumentException("RUNTIME_MIRROR_INVALID");
        this.connector = connector;
        this.mutations = mutations;
        this.queueCapacity = queueCapacity;
        this.observer = observer == null ? RuntimeEventObserver.NOOP : observer;
    }

    @Override public synchronized void connect(URI endpoint) {
        if (state == MirrorState.LIVE || state == MirrorState.CONNECTING || state == MirrorState.SYNCING)
            throw new IllegalStateException("RUNTIME_MIRROR_ALREADY_CONNECTED");
        this.endpoint = endpoint;
        transition(MirrorState.CONNECTING);
        try {
            connector.connect(endpoint);
            transition(MirrorState.SYNCING);
            synchronizeSnapshot();
            transition(MirrorState.LIVE);
            observer.snapshotApplied(lastSnapshot);
        } catch (RuntimeException exception) {
            transition(MirrorState.ERROR);
            stopEvents();
            connector.disconnect();
            throw exception;
        }
    }

    /** Drain the old workspace before replacing its consumers; reuse the connected transport. */
    public synchronized void replaceWorkspace(RuntimeMutationEngine nextMutations, RuntimeEventObserver nextObserver,
                                               Runnable transferBindings) {
        boolean live = state == MirrorState.LIVE;
        stopEvents();
        if (live) transition(MirrorState.SYNCING);
        transferBindings.run();
        mutations = nextMutations;
        observer = nextObserver;
        lastSnapshot = null;
        lastSnapshotFingerprint = null;
        lastDriftReport = null;
        if (!live) return;
        transition(MirrorState.SYNCING);
        try {
            synchronizeSnapshot();
            transition(MirrorState.LIVE);
            observer.snapshotApplied(lastSnapshot);
        } catch (RuntimeException exception) {
            transition(MirrorState.ERROR);
            stopEvents();
            connector.disconnect();
            throw exception;
        }
    }

    @Override public synchronized void disconnect() {
        stopEvents();
        connector.disconnect();
        if (state != MirrorState.OFFLINE) transition(MirrorState.STALE);
    }

    public synchronized void reconnectAndResync() {
        if (endpoint == null) throw new IllegalStateException("RUNTIME_ENDPOINT_MISSING");
        connect(endpoint);
    }

    @Override public synchronized void resync() {
        if (state != MirrorState.LIVE) throw new IllegalStateException("RUNTIME_MIRROR_NOT_LIVE");
        transition(MirrorState.SYNCING);
        stopEvents();
        try {
            synchronizeSnapshot();
            transition(MirrorState.LIVE);
            observer.snapshotApplied(lastSnapshot);
        } catch (RuntimeException exception) {
            transition(MirrorState.ERROR);
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
    public Instant lastSyncAt() { return lastSnapshot == null ? null : lastSnapshot.capturedAt(); }
    public RuntimeDriftReport lastDriftReport() { return lastDriftReport; }

    /** Executes one authoritative comparison and optionally performs a correctness-first full resync. */
    public synchronized RuntimeDriftReport checkDrift(DriftResyncPolicy policy) {
        if (state != MirrorState.LIVE) throw new IllegalStateException("RUNTIME_MIRROR_NOT_LIVE");
        RuntimeSnapshot authoritative = connector.fullSnapshot();
        List<RuntimeDriftDifference> differences = mutations.compareSnapshot(authoritative);
        boolean resync = !differences.isEmpty() && policy == DriftResyncPolicy.AUTO_RESYNC;
        RuntimeDriftReport report = new RuntimeDriftReport(authoritative.snapshotId(), authoritative.fingerprint(),
                Instant.now(), differences, policy, resync);
        lastDriftReport = report;
        observer.driftChecked(report);
        if (resync) resync();
        return report;
    }

    /** Starts periodic authoritative drift checks. Repeated calls replace the previous schedule. */
    public synchronized void enablePeriodicDriftChecks(Duration interval, DriftResyncPolicy policy) {
        if (interval == null || interval.isZero() || interval.isNegative() || policy == null)
            throw new IllegalArgumentException("RUNTIME_DRIFT_INTERVAL_INVALID");
        stopDriftMonitor();
        driftMonitor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "jacamo-runtime-drift");
            thread.setDaemon(true);
            return thread;
        });
        driftMonitor.scheduleWithFixedDelay(() -> {
            try {
                if (state == MirrorState.LIVE) checkDrift(policy);
            } catch (RuntimeException exception) {
                synchronized (RuntimeMirrorService.this) {
                    if (state == MirrorState.LIVE) transition(MirrorState.STALE);
                }
            }
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Refreshes externally observable connector health without claiming stale data as live. */
    public synchronized void refreshConnectionState() {
        if (state == MirrorState.LIVE && connector.state() != ConnectorState.CONNECTED) {
            stopEvents();
            transition(MirrorState.STALE);
        }
    }

    @Override public synchronized void close() {
        stopDriftMonitor();
        disconnect();
        transition(MirrorState.OFFLINE);
    }

    private void applySnapshot(RuntimeSnapshot snapshot) {
        mutations.applySnapshot(snapshot);
        lastSnapshotFingerprint = snapshot.fingerprint();
        lastSnapshot = snapshot;
    }

    private void synchronizeSnapshot() {
        // Late callbacks belong to the stream that subscribed them, even after workspace replacement.
        RuntimeMutationEngine streamMutations = mutations;
        RuntimeEventObserver streamObserver = observer;
        Object gate = new Object();
        List<RuntimeEvent> buffered = new ArrayList<>();
        OrderedRuntimeEventQueue[] ready = new OrderedRuntimeEventQueue[1];
        boolean[] streamOpen = new boolean[] { true };
        RuntimeSubscription connectorSubscription = connector.subscribe(event -> {
            streamObserver.eventReceived(event);
            synchronized (gate) {
                if (!streamOpen[0]) {
                    var closed = new IllegalStateException("RUNTIME_EVENT_STREAM_CLOSED");
                    streamMutations.eventRejected(event, closed);
                    streamObserver.eventRejected(event, closed);
                } else if (ready[0] == null) buffered.add(event);
                else submitReceived(ready[0], event);
            }
        });
        RuntimeSubscription nextSubscription = () -> {
            synchronized (gate) { streamOpen[0] = false; }
            connectorSubscription.close();
        };
        RuntimeSnapshot snapshot;
        try {
            snapshot = connector.fullSnapshot();
            applySnapshot(snapshot);
        } catch (RuntimeException exception) {
            nextSubscription.close();
            streamMutations.eventStreamClosed();
            streamObserver.eventStreamClosed();
            throw exception;
        }
        OrderedRuntimeEventQueue nextQueue = new OrderedRuntimeEventQueue(queueCapacity, event -> {
            try {
                streamObserver.beforeMutation(event);
                MutationResult result = streamMutations.apply(event);
                streamObserver.afterMutation(event, result);
                if (result.status() != MutationStatus.APPLIED) {
                    transition(MirrorState.ERROR);
                    throw new IllegalStateException(result.diagnostic());
                }
            } finally {
                streamMutations.eventCompleted(event);
                streamObserver.eventCompleted(event);
            }
        });
        nextQueue.start();
        queue = nextQueue;
        subscription = nextSubscription;
        synchronized (gate) {
            ready[0] = nextQueue;
            for (RuntimeEvent event : buffered) {
                if (event.sequence() > snapshot.sequence()) {
                    submitReceived(nextQueue, event);
                } else {
                    rejectReceived(event, new IllegalArgumentException("RUNTIME_EVENT_COVERED_BY_SNAPSHOT"));
                }
            }
            buffered.clear();
        }
    }

    private void stopEvents() {
        boolean hadStream = subscription != null || queue != null;
        try {
            if (subscription != null) { subscription.close(); subscription = null; }
            if (queue != null) { queue.stopGracefully(Duration.ofSeconds(5)); queue = null; }
        } finally {
            if (hadStream) {
                mutations.eventStreamClosed();
                observer.eventStreamClosed();
            }
        }
    }

    private void submitReceived(OrderedRuntimeEventQueue target, RuntimeEvent event) {
        try {
            target.submit(event);
        } catch (RuntimeException exception) {
            rejectReceived(event, exception);
            throw exception;
        }
    }

    private void rejectReceived(RuntimeEvent event, RuntimeException exception) {
        mutations.eventRejected(event, exception);
        observer.eventRejected(event, exception);
    }

    private void stopDriftMonitor() {
        if (driftMonitor != null) {
            driftMonitor.shutdownNow();
            driftMonitor = null;
        }
    }

    private void transition(MirrorState next) {
        state = next;
        observer.stateChanged(next);
    }
}
