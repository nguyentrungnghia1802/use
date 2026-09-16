package org.tzi.use.plugins.jacamo.runtime;

/** Queue rejection that records the accepted watermark needed for ordered cleanup. */
public final class RuntimeQueueBackpressureException extends IllegalStateException {
    private final long acceptedThroughSequence;

    public RuntimeQueueBackpressureException(long acceptedThroughSequence) {
        super("RUNTIME_QUEUE_BACKPRESSURE");
        this.acceptedThroughSequence = acceptedThroughSequence;
    }

    public long acceptedThroughSequence() { return acceptedThroughSequence; }
}
