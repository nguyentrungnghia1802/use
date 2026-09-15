package org.tzi.use.plugins.jacamo.runtime;

import java.util.concurrent.atomic.AtomicReference;

/** Process-local handoff used by the Jason custom architecture hook. */
public final class JasonRuntimeConnectorRegistry {
    private static final AtomicReference<JasonRuntimeConnector> ACTIVE = new AtomicReference<>();

    private JasonRuntimeConnectorRegistry() { }

    public static void activate(JasonRuntimeConnector connector) {
        if (connector == null || !ACTIVE.compareAndSet(null, connector))
            throw new IllegalStateException("JASON_CONNECTOR_ALREADY_ACTIVE");
    }

    public static void deactivate(JasonRuntimeConnector connector) {
        if (!ACTIVE.compareAndSet(connector, null))
            throw new IllegalStateException("JASON_CONNECTOR_NOT_ACTIVE");
    }

    static boolean deactivateIfActive(JasonRuntimeConnector connector) {
        return ACTIVE.compareAndSet(connector, null);
    }

    static JasonRuntimeConnector requireActive() {
        JasonRuntimeConnector connector = ACTIVE.get();
        if (connector == null) throw new IllegalStateException("JASON_CONNECTOR_NOT_ACTIVE");
        return connector;
    }
}
