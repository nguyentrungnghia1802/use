package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantic.Dimension;

class CompositeRuntimeConnectorTest {
    @TempDir Path temporary;

    @Test void cleanupFailuresDoNotSkipOtherChildrenAndRemainRetryable() {
        var fail = new java.util.concurrent.atomic.AtomicBoolean(true);
        var closed = new java.util.concurrent.atomic.AtomicInteger();
        var disconnected = new java.util.concurrent.atomic.AtomicInteger();
        List<RuntimeConnector> children = new ArrayList<>();
        for (String name : List.of("a", "b")) children.add(new RuntimeConnector() {
            public String connectorId() { return name; }
            public java.util.Set<ConnectorCapability> capabilities() { return java.util.Set.of(); }
            public ConnectorState state() { return ConnectorState.CONNECTED; }
            public void connect(URI endpoint) { }
            public void disconnect() {
                disconnected.incrementAndGet();
                if (fail.get()) throw new IllegalStateException("disconnect-" + name);
            }
            public RuntimeSnapshot fullSnapshot() { return new RuntimeSnapshot("empty", Instant.EPOCH, 0, List.of(), "empty"); }
            public RuntimeSubscription subscribe(java.util.function.Consumer<RuntimeEvent> listener) {
                return () -> {
                    closed.incrementAndGet();
                    if (fail.get()) throw new IllegalStateException("unsubscribe-" + name);
                };
            }
        });
        var composite = new CompositeRuntimeConnector("cleanup", children);
        composite.connect(URI.create("jacamo://cleanup"));
        composite.subscribe(event -> { });
        var error = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, composite::disconnect);
        assertEquals(ConnectorState.ERROR, composite.state());
        assertEquals(2, closed.get());
        assertEquals(2, disconnected.get());
        assertEquals(3, error.getCause().getSuppressed().length);
        fail.set(false);
        composite.disconnect();
        assertEquals(4, closed.get());
        assertEquals(ConnectorState.DISCONNECTED, composite.state());
    }

    @Test void retiredChildCallbackCannotEnterReplacementSubscription() {
        List<java.util.function.Consumer<RuntimeEvent>> callbacks = new ArrayList<>();
        RuntimeConnector child = new RuntimeConnector() {
            public String connectorId() { return "child"; }
            public java.util.Set<ConnectorCapability> capabilities() { return java.util.Set.of(); }
            public ConnectorState state() { return ConnectorState.CONNECTED; }
            public void connect(URI endpoint) { }
            public void disconnect() { }
            public RuntimeSnapshot fullSnapshot() { return new RuntimeSnapshot("empty", Instant.EPOCH, 0, List.of(), "empty"); }
            public RuntimeSubscription subscribe(java.util.function.Consumer<RuntimeEvent> listener) {
                callbacks.add(listener); return () -> { };
            }
        };
        CompositeRuntimeConnector composite = new CompositeRuntimeConnector("parent", List.of(child));
        composite.connect(URI.create("jacamo://local/test"));
        List<RuntimeEvent> received = new ArrayList<>();
        RuntimeSubscription old = composite.subscribe(received::add);
        old.close(); composite.subscribe(received::add);
        RuntimeEvent event = event("late", 1, Dimension.AGENT, RuntimeEventKind.BELIEF_ADDED,
                "jason:agent:a", Map.of("belief", "ready"), null);
        callbacks.getFirst().accept(event);
        assertTrue(received.isEmpty()); assertEquals(List.of(event), composite.retiredEvents());
        callbacks.getLast().accept(event);
        assertEquals(1, received.size());
        composite.disconnect();
    }

    @Test
    void combinesSnapshotsAndRenumbersChildEventsIntoOneMonotonicStream() {
        RuntimeEvent agentEvent = event("agent-event", 4, Dimension.AGENT, RuntimeEventKind.BELIEF_ADDED,
                "jason:agent:a", Map.of("belief", "ready"), null);
        RuntimeEvent artifactEvent = event("artifact-event", 4, Dimension.ENVIRONMENT, RuntimeEventKind.OP_FAIL,
                "cartago:artifact:main/a", Map.of("error", "failed"), "op-1");
        SyntheticRuntimeConnector agent = synthetic("agent", agentEvent, temporary.resolve("agent.json"));
        SyntheticRuntimeConnector artifact = synthetic("artifact", artifactEvent, temporary.resolve("artifact.json"));
        CompositeRuntimeConnector composite = new CompositeRuntimeConnector("jacamo-live", List.of(agent, artifact));

        composite.connect(URI.create("synthetic://auction"));
        RuntimeSnapshot snapshot = composite.fullSnapshot();
        assertEquals(List.of(1L, 2L), snapshot.mutations().stream().map(RuntimeEvent::sequence).toList());
        assertEquals(List.of("agent", "artifact"), snapshot.mutations().stream()
                .map(event -> event.payload().get("sourceConnector")).toList());
        assertTrue(composite.capabilities().containsAll(List.of(ConnectorCapability.FULL_SNAPSHOT,
                ConnectorCapability.EVENT_SUBSCRIPTION, ConnectorCapability.RECONNECT)));

        List<RuntimeEvent> events = new ArrayList<>();
        composite.subscribe(events::add);
        agent.replayAll();
        assertEquals(3, events.getFirst().sequence());
        assertEquals("agent:agent-event", events.getFirst().payload().get("sourceConnector") + ":"
                + events.getFirst().payload().get("sourceEventId"));
        agent.disconnect();
        assertEquals(ConnectorState.ERROR, composite.state());
        composite.disconnect();
        assertEquals(ConnectorState.DISCONNECTED, composite.state());
    }

    private SyntheticRuntimeConnector synthetic(String id, RuntimeEvent event, Path replay) {
        RuntimeEventCodec codec = new RuntimeEventCodec();
        codec.writeEvents(replay, List.of(event));
        return new SyntheticRuntimeConnector(id,
                new RuntimeSnapshot(id + "-snapshot", Instant.now(), event.sequence(), List.of(event), id + "-hash"),
                replay, codec);
    }

    private RuntimeEvent event(String id, long sequence, Dimension dimension, RuntimeEventKind kind,
                               String runtimeId, Map<String, Object> payload, String correlation) {
        return RuntimeEvent.create(id, Instant.now(), sequence, dimension, kind, runtimeId, "semantic:" + id,
                payload, correlation);
    }
}
