package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.runtime.*;
import org.tzi.use.plugins.jacamo.semantic.Dimension;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class HotfixLifecycleTest {
    @TempDir Path temporary;
    @ParameterizedTest
    @ValueSource(strings = {"rebuild", "profile", "reimport"})
    void liveWorkspaceReplacementKeepsRuntimeAndFullVerificationTogether(String action) throws Exception {
            try (var facade = new DefaultJaCaMoFacade(Path.of("."))) {
                Path entry = Path.of("src/test/resources/auction/auction.jcm");
                facade.importProject(entry);
                String semantic = facade.traces().stream().filter(t -> t.sourceKind().equals("Artifact"))
                        .findFirst().orElseThrow().semanticId();
                var field = DefaultJaCaMoFacade.class.getDeclaredField("workspace"); field.setAccessible(true);
                Object workspace = field.get(facade);
                var tf = workspace.getClass().getDeclaredField("trace"); tf.setAccessible(true);
                var trace = (org.tzi.use.plugins.jacamo.trace.TraceIndex) tf.get(workspace);
                trace.registerRuntimeKey(trace.bySemanticId(semantic).stream().filter(t -> t.targetKind().equals("OBJECT"))
                        .findFirst().orElseThrow().traceId(), "cartago:artifact:market/auction1");
                trace.registerRuntimeKey(trace.bySemanticId(semantic).stream().filter(t -> t.targetKind().equals("OBJECT"))
                        .findFirst().orElseThrow().traceId(), "alias:auction1");
                var event = RuntimeEvent.create("close-1", Instant.now(), 1, Dimension.ENVIRONMENT,
                        RuntimeEventKind.SET_ATTRIBUTE, "cartago:artifact:market/auction1", semantic,
                        Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);
                Path replay = temporary.resolve(action + ".json");
                var codec = new RuntimeEventCodec(); codec.writeEvents(replay, List.of(event));
                var snapshot = new RuntimeSnapshot("initial", Instant.now(), 0, List.of(), "initial");
                var connector = new SyntheticRuntimeConnector(action, snapshot, replay, codec);
                facade.configureRuntime(connector, URI.create("synthetic://auction"), 8);
                facade.connectRuntime(); connector.replayAll(); await(facade);
                assertTrue(facade.runFullVerification().results().stream().anyMatch(r -> r.outcome() == VerificationOutcome.FAIL));
                connector.replaceSnapshot(new RuntimeSnapshot("closed", Instant.now(), 1, List.of(event), "closed"));
                switch (action) {
                    case "rebuild" -> facade.rebuild();
                    case "profile" -> facade.loadVerificationProfile(Files.writeString(temporary.resolve("extra.ocl"), "context AuctionArtifact inv HotfixOpen: self.open = true"));
                    default -> facade.importProject(entry);
                }
                assertEquals(MirrorState.LIVE, facade.runtimeStatus().state());
                assertTrue(facade.runFullVerification().results().stream().anyMatch(r -> r.outcome() == VerificationOutcome.FAIL), action);
                var second = RuntimeEvent.create("close-2", Instant.now(), 2, Dimension.ENVIRONMENT,
                        RuntimeEventKind.SET_ATTRIBUTE, "alias:auction1", semantic,
                        Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);
                codec.writeEvents(replay, List.of(second)); connector.replayAll(); await(facade);
                assertEquals(0, facade.runtimeStatus().failed());
                assertTrue(facade.runtimeStatus().violationCount() > 0);
                assertTrue(facade.runFullVerification().results().stream().anyMatch(r -> r.outcome() == VerificationOutcome.FAIL));
                var rf = DefaultJaCaMoFacade.class.getDeclaredField("runtime"); rf.setAccessible(true);
                var mirror = (RuntimeMirrorService) rf.get(facade);
                assertTrue(mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY).differences().isEmpty());
                var listeners = SyntheticRuntimeConnector.class.getDeclaredField("listeners"); listeners.setAccessible(true);
                assertEquals(1, ((List<?>) listeners.get(connector)).size(), "exactly one active subscription");
                facade.resyncRuntime(); facade.disconnectRuntime(); facade.connectRuntime();
                mirror = (RuntimeMirrorService) rf.get(facade);
                assertTrue(mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY).differences().isEmpty());
                assertEquals(1, ((List<?>) listeners.get(connector)).size());
                assertTrue(facade.runFullVerification().results().stream().anyMatch(r -> r.outcome() == VerificationOutcome.FAIL));
            }
    }
    @Test void failedRebuildLeavesTheLiveWorkspaceIntact() throws Exception {
        Path entry = temporary.resolve("app.jcm");
        Files.writeString(entry, "mas app { agent a:a.asl }");
        Files.writeString(temporary.resolve("a.asl"), "ready.");
        try (var facade = new DefaultJaCaMoFacade(Path.of("."))) {
            var summary = facade.importProject(entry);
            Path replay = Files.writeString(temporary.resolve("empty.json"), "[]");
            var connector = new SyntheticRuntimeConnector("failed-build",
                    new RuntimeSnapshot("empty", Instant.now(), 0, List.of(), "empty"), replay, new RuntimeEventCodec());
            facade.configureRuntime(connector, URI.create("synthetic://app"), 8);
            facade.connectRuntime();
            Files.writeString(entry, "malformed project");
            assertThrows(IllegalArgumentException.class, facade::rebuild);
            assertEquals(summary, facade.projectSummary());
            assertEquals(MirrorState.LIVE, facade.runtimeStatus().state());
            facade.resyncRuntime();
        }
    }

    private void await(DefaultJaCaMoFacade facade) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (facade.runtimeStatus().processed() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(facade.runtimeStatus().processed() > 0);
    }
}

