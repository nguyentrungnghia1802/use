package org.tzi.use.plugins.jacamo.codegrounded.runtime;

/** Fail-closed protocol error for native runtime synchronization. */
public final class NativeRuntimeProtocolException extends IllegalStateException {
    public NativeRuntimeProtocolException(String message) { super(message); }
    public NativeRuntimeProtocolException(String message, Throwable cause) { super(message, cause); }
}
