package org.tzi.use.plugins.jacamo.runtime;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;

/** Single-consumer correctness queue. Rejections are explicit and never counted as silent drops. */
public final class OrderedRuntimeEventQueue implements AutoCloseable {
    private final ArrayBlockingQueue<RuntimeEvent> queue;
    private final Consumer<RuntimeEvent> handler;
    private Thread worker;
    private boolean accepting;
    private boolean running;
    private long lastAcceptedSequence = -1;
    private int highWatermark;
    private long accepted;
    private long processed;
    private long rejected;
    private long failed;

    public OrderedRuntimeEventQueue(int capacity, Consumer<RuntimeEvent> handler) {
        if (capacity < 1 || handler == null) throw new IllegalArgumentException("RUNTIME_QUEUE_INVALID");
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.handler = handler;
    }

    public synchronized void start() {
        if (running) return;
        accepting = true;
        running = true;
        worker = new Thread(this::run, "jacamo-runtime-events");
        worker.setDaemon(true);
        worker.start();
    }

    public synchronized void submit(RuntimeEvent event) {
        if (!accepting) {
            rejected++;
            throw new IllegalStateException("RUNTIME_QUEUE_NOT_ACCEPTING");
        }
        if (event.sequence() <= lastAcceptedSequence) {
            rejected++;
            throw new IllegalArgumentException("RUNTIME_QUEUE_OUT_OF_ORDER");
        }
        if (!queue.offer(event)) {
            rejected++;
            throw new IllegalStateException("RUNTIME_QUEUE_BACKPRESSURE");
        }
        lastAcceptedSequence = event.sequence();
        accepted++;
        highWatermark = Math.max(highWatermark, queue.size());
        notifyAll();
    }

    public synchronized void awaitIdle(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (processed < accepted && System.nanoTime() < deadline) {
            long millis = Math.max(1, Math.min(100, Duration.ofNanos(deadline - System.nanoTime()).toMillis()));
            try { wait(millis); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
        }
        if (processed < accepted) throw new IllegalStateException("RUNTIME_QUEUE_DRAIN_TIMEOUT");
    }

    public void stopGracefully(Duration timeout) {
        synchronized (this) { accepting = false; }
        awaitIdle(timeout);
        synchronized (this) { running = false; }
        if (worker != null) {
            worker.interrupt();
            try { worker.join(Math.max(1, timeout.toMillis())); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            if (worker.isAlive()) throw new IllegalStateException("RUNTIME_QUEUE_STOP_TIMEOUT");
        }
    }

    public synchronized QueueMetrics metrics() {
        return new QueueMetrics(queue.size(), highWatermark, processed, rejected, failed, 0);
    }

    private void run() {
        while (isRunning() || !queue.isEmpty()) {
            RuntimeEvent event;
            try { event = queue.take(); }
            catch (InterruptedException exception) {
                if (!isRunning() && queue.isEmpty()) break;
                continue;
            }
            try { handler.accept(event); }
            catch (RuntimeException exception) { synchronized (this) { failed++; } }
            finally {
                synchronized (this) { processed++; notifyAll(); }
            }
        }
    }

    private synchronized boolean isRunning() { return running; }

    @Override public void close() {
        if (isRunning()) stopGracefully(Duration.ofSeconds(5));
    }
}
