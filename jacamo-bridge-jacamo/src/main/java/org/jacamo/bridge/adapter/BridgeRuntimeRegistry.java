package org.jacamo.bridge.adapter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.RuntimeEvent;

/** Process-local hook used only by official JaCaMo extension instances. */
public final class BridgeRuntimeRegistry {
    private static final AtomicReference<Consumer<RuntimeEvent>> ACTIVE = new AtomicReference<>();
    private BridgeRuntimeRegistry() { }
    public static AutoCloseable attach(Consumer<RuntimeEvent> observer) {
        Objects.requireNonNull(observer);
        if (!ACTIVE.compareAndSet(null, observer)) throw new IllegalStateException("BRIDGE_OBSERVER_ALREADY_ATTACHED");
        return () -> ACTIVE.compareAndSet(observer, null);
    }
    static Consumer<RuntimeEvent> require() {
        Consumer<RuntimeEvent> observer = ACTIVE.get();
        if (observer == null) throw new IllegalStateException("BRIDGE_OBSERVER_NOT_ATTACHED");
        return observer;
    }
}
