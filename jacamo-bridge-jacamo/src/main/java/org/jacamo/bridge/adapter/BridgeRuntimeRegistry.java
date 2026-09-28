package org.jacamo.bridge.adapter;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.RuntimeEvent;

/** Process-local hook used only by official JaCaMo extension instances. */
public final class BridgeRuntimeRegistry {
    private static final AtomicReference<Consumer<RuntimeEvent>> ACTIVE = new AtomicReference<>();
    private static final ConcurrentMap<String, BridgeEntityId> AGENT_IDENTITIES = new ConcurrentHashMap<>();
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

    static void registerAgent(String agentName, BridgeEntityId identity) {
        AGENT_IDENTITIES.put(Objects.requireNonNull(agentName), Objects.requireNonNull(identity));
    }

    static void unregisterAgent(String agentName, BridgeEntityId identity) {
        AGENT_IDENTITIES.remove(agentName, identity);
    }

    static Optional<BridgeEntityId> agentIdentity(String agentName) {
        return Optional.ofNullable(AGENT_IDENTITIES.get(agentName));
    }

    static void clearAgents() {
        AGENT_IDENTITIES.clear();
    }
}
