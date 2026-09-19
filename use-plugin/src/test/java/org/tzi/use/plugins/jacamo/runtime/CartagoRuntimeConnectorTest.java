package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cartago.ArtifactId;
import cartago.CartagoEnvironment;
import cartago.Op;
import cartago.util.agent.CartagoBasicContext;
import cartago.util.agent.ActionFailedException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CartagoRuntimeConnectorTest {
    @Test
    void failedUnregisterRemainsAnExplicitRetryableCleanupFailure() {
        var fail = new java.util.concurrent.atomic.AtomicBoolean(true);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        CartagoRuntimeAccess access = new CartagoRuntimeAccess() {
            public cartago.ICartagoController controller(String workspace) { return null; }
            public void registerLogger(String workspace, cartago.ICartagoLogger logger) { }
            public void unregisterLogger(String workspace, cartago.ICartagoLogger logger) throws cartago.CartagoException {
                calls.incrementAndGet();
                if (fail.get()) throw new cartago.CartagoException("injected cleanup failure");
            }
        };
        var binding = new CartagoArtifactBinding("/main", "x", "semantic", java.util.Map.of(), java.util.Map.of());
        var connector = new CartagoRuntimeConnector("cleanup", access, java.util.List.of(binding));
        connector.connect(java.net.URI.create("jacamo://cleanup"));
        var error = assertThrows(IllegalStateException.class, connector::disconnect);
        assertTrue(error.getMessage().contains("CARTAGO_UNREGISTER_FAILED"));
        assertEquals(ConnectorState.ERROR, connector.state());
        fail.set(false);
        connector.disconnect();
        assertEquals(ConnectorState.DISCONNECTED, connector.state());
        assertEquals(2, calls.get(), "failed registration must remain available for retry");
    }

    @Test
    void discoversRealArtifactSnapshotsPropertiesAndStreamsOperationLifecycle() throws Exception {
        CartagoEnvironment environment = CartagoEnvironment.getInstance();
        environment.init();
        cartago.utils.BasicLogger basicLogger = new cartago.utils.BasicLogger();
        environment.registerLogger("/main", basicLogger);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String artifactName = "auction" + suffix;
        CartagoBasicContext context = new CartagoBasicContext("useMonitor" + suffix);
        String semanticId = "jacamo:test:environment:Artifact:MAS/ws:" + artifactName;
        CartagoArtifactBinding binding = new CartagoArtifactBinding("/main", artifactName, semanticId,
                Map.of("open", "open"), Map.of("closeAuction", "closeAuction", "placeBid", "placeBid",
                "removeOpen", "removeOpen"));
        CartagoRuntimeConnector connector = new CartagoRuntimeConnector("cartago-live",
                new OfficialCartagoRuntimeAccess(environment), List.of(binding));
        List<RuntimeEvent> events = new ArrayList<>();
        ArtifactId artifact = null;

        try {
            connector.connect(URI.create("jacamo://local/auction"));
            connector.subscribe(events::add);
            artifact = context.makeArtifact(
                    context.getJoinedWspId("main"), artifactName, TestAuctionArtifact.class.getName());
            assertTrue(events.stream().anyMatch(event -> event.kind() == RuntimeEventKind.ARTIFACT_CREATED
                    && semanticId.equals(event.semanticSourceId())
                    && binding.runtimeSourceId().equals(event.runtimeSourceId())));
            assertTrue(connector.discoveredArtifacts().stream().anyMatch(value -> value.endsWith("/" + artifactName)));
            RuntimeEvent initial = connector.fullSnapshot().mutations().stream()
                    .filter(event -> "open".equals(event.payload().get("property"))).findFirst().orElseThrow();
            assertEquals(RuntimeEventKind.OBS_PROPERTY_ADDED, initial.kind());
            assertEquals(true, initial.payload().get("value"));
            assertEquals("open", initial.payload().get("attribute"));

            ArtifactId liveArtifact = artifact;
            context.doAction(liveArtifact, new Op("closeAuction"));
            context.doAction(liveArtifact, new Op("placeBid", "item1", 10));
            assertThrows(ActionFailedException.class,
                    () -> context.doAction(liveArtifact, new Op("placeBid", "item1", 0)));
            context.doAction(liveArtifact, new Op("removeOpen"));

            RuntimeEvent closeEnter = events.stream().filter(event -> event.kind() == RuntimeEventKind.OP_ENTER
                    && "closeAuction".equals(event.payload().get("operation"))).findFirst().orElseThrow();
            assertTrue(closeEnter.correlationId().contains(artifact.getId().toString()));
            assertTrue(closeEnter.correlationId().contains(":generation:"));
            assertTrue(initial.payload().containsKey("propertyId"));
            assertTrue(initial.payload().get("propertyKey").toString().contains(artifactName));
            assertTrue(events.stream().anyMatch(event -> event.kind() == RuntimeEventKind.OBS_PROPERTY_CHANGED
                    && event.payload().get("property").equals("open") && Boolean.FALSE.equals(event.payload().get("value"))));
            assertTrue(events.stream().anyMatch(event -> event.kind() == RuntimeEventKind.OP_EXIT
                    && "closeAuction".equals(event.payload().get("operation"))
                    && context.getName().equals(event.payload().get("agent"))
                    && closeEnter.correlationId().equals(event.correlationId())));
            assertTrue(events.stream().anyMatch(event -> event.kind() == RuntimeEventKind.SIGNAL
                    && event.payload().get("signal").equals("bid")));
            assertTrue(events.stream().anyMatch(event -> event.kind() == RuntimeEventKind.OP_FAIL
                    && "placeBid".equals(event.payload().get("operation"))
                    && context.getName().equals(event.payload().get("agent"))
                    && String.valueOf(event.payload().get("error")).contains("positive")));
            assertTrue(events.stream().anyMatch(event -> event.kind() == RuntimeEventKind.OBS_PROPERTY_REMOVED
                    && event.payload().get("property").equals("open")));
            assertBalancedOperationLifecycles(events);
            assertTrue(connector.fullSnapshot().mutations().stream().anyMatch(
                    event -> event.kind() == RuntimeEventKind.OBS_PROPERTY_REMOVED
                            && "open".equals(event.payload().get("property"))
                            && "open".equals(event.payload().get("attribute"))));
            environment.getController("/main").removeArtifact(artifactName);
            artifact = null;
            assertTrue(events.stream().anyMatch(event -> event.kind() == RuntimeEventKind.ARTIFACT_DISPOSED));
            ArtifactId unknown = context.makeArtifact(context.getJoinedWspId("main"), "unbound" + suffix,
                    TestAuctionArtifact.class.getName());
            try {
                assertTrue(connector.quarantinedObservations().stream().anyMatch(value ->
                        value.contains("CARTAGO_UNBOUND_ARTIFACT") && value.contains(unknown.getId().toString())));
            } finally { environment.getController("/main").removeArtifact(unknown.getName()); }
        } finally {
            connector.disconnect();
            if (artifact != null) environment.getController("/main").removeArtifact(artifactName);
            environment.unregisterLogger("/main", basicLogger);
        }
        assertEquals(ConnectorState.DISCONNECTED, connector.state());
    }

    private void assertBalancedOperationLifecycles(List<RuntimeEvent> events) {
        Map<String, List<RuntimeEvent>> byCorrelation = new LinkedHashMap<>();
        events.stream().filter(event -> event.kind() == RuntimeEventKind.OP_ENTER
                        || event.kind() == RuntimeEventKind.OP_EXIT
                        || event.kind() == RuntimeEventKind.OP_FAIL)
                .forEach(event -> byCorrelation.computeIfAbsent(event.correlationId(), ignored -> new ArrayList<>())
                        .add(event));
        assertTrue(!byCorrelation.isEmpty(), "the real Artifact run must expose operation lifecycles");
        byCorrelation.forEach((correlation, lifecycle) -> {
            assertEquals(1, lifecycle.stream().filter(event -> event.kind() == RuntimeEventKind.OP_ENTER).count(),
                    () -> "exactly one operation start is required for " + correlation + ": " + lifecycle);
            assertEquals(1, lifecycle.stream().filter(event -> event.kind() == RuntimeEventKind.OP_EXIT
                            || event.kind() == RuntimeEventKind.OP_FAIL).count(),
                    () -> "exactly one terminal event is required for " + correlation + ": " + lifecycle);
        });
    }

}
