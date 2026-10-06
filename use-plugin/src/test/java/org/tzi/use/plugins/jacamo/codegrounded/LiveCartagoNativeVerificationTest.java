package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import cartago.CartagoEnvironment;
import cartago.Op;
import cartago.util.agent.CartagoBasicContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.jacamo.bridge.adapter.CartagoSnapshotSource;
import org.jacamo.bridge.adapter.SnapshotCoordinator;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade;
import org.tzi.use.plugins.jacamo.PipelineMode;
import org.tzi.use.plugins.jacamo.SemanticAuthority;
import org.tzi.use.plugins.jacamo.bridge.*;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

class LiveCartagoNativeVerificationTest {
    @TempDir Path directory;
    @Test void officialLoggerBridgeFacadeNativeOclHistoryAndReplayObserveTransientViolation() throws Exception {
        var environment = CartagoEnvironment.getInstance(); environment.init();
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "");
        String name = "observed" + suffix;
        var context = new CartagoBasicContext("observer" + suffix);
        var artifact = context.makeArtifact(context.getJoinedWspId("main"), name, LiveRuntimePropertyArtifact.class.getName());
        var model = CodeGroundedTestFixtures.helloSnapshot();
        String oldRevision = System.getProperty("jacamo.bridge.modelRevision"), oldGeneration = System.getProperty("jacamo.bridge.generation");
        System.setProperty("jacamo.bridge.modelRevision", model.modelRevision()); System.setProperty("jacamo.bridge.generation", "1");
        var source = new CartagoSnapshotSource(environment, List.of("/main"), "live-native-session");
        var transport = new LiveTransport(model, source);
        var session = new Session();
        var configuration = new BridgeConnectionConfig(java.net.URI.create("tcp://127.0.0.1:6553"), directory.resolve("secret"),
                "1".repeat(64), Set.of("official.model", "runtime.snapshot"), 4 * 1024 * 1024, 5000, 8192);
        try (var facade = new DefaultJaCaMoFacade(Path.of("."), SemanticAuthority.BRIDGE, () -> configuration,
                ignored -> transport, PipelineMode.CODE_GROUNDED_NATIVE, session)) {
            facade.importProject(CodeGroundedTestFixtures.hello()); var system = session.system();
            Path ocl = directory.resolve("constraints.ocl");
            Files.writeString(ocl, RuntimeVerificationCoordinatorTest.PROFILE);
            facade.loadVerificationProfile(ocl);
            assertEquals(VerificationOutcome.PASS, RuntimeVerificationCoordinatorTest.external(facade.runtimeVerificationResult()));
            assertSame(system.model(), session.system().model());
            context.doAction(artifact, new Op("set", "B"));
            await(() -> facade.runtimeVerificationHistory().stream().anyMatch(result ->
                    result.failingConstraints().contains("EXTERNAL:LiveRuntimePropertyArtifact::NoB")));
            assertEquals(VerificationOutcome.FAIL, RuntimeVerificationCoordinatorTest.external(facade.runtimeVerificationResult()));
            StepReplayProof.onEdt(() -> {
                assertEquals("false", org.tzi.use.api.UseSystemApi.create(system,false).evaluate(
                        "LiveRuntimePropertyArtifact.allInstances()->forAll(a | a.status <> 'B')").toString());
                return null;
            });
            context.doAction(artifact, new Op("set", "A"));
            await(() -> RuntimeVerificationCoordinatorTest.external(facade.runtimeVerificationResult()) == VerificationOutcome.PASS);
            assertTrue(facade.runtimeVerificationHistory().stream().anyMatch(result -> result.count(VerificationOutcome.FAIL) > 0));
            var normalized = transport.observations.stream().filter(event -> "APPLY_CARTAGO_PROPERTY_DELTA".equals(event.after().get("normalizedEventKind"))).toList();
            assertTrue(normalized.size() >= 2); assertTrue(normalized.stream().allMatch(event -> event.projectionStatus() == ProjectionStatus.MATERIALIZED_FAITHFULLY));
            assertEquals(artifact.getId().toString(), normalized.getFirst().entityId().incarnation());
            context.doAction(artifact, new Op("remove"));
            context.doAction(artifact, new Op("add", "A"));
            await(() -> transport.observations.stream().filter(event -> "APPLY_CARTAGO_PROPERTY_DELTA".equals(event.after().get("normalizedEventKind"))).count() >= 4);
            assertTrue(transport.observations.stream().anyMatch(event -> event.after().get("removedPropertySemanticIds") instanceof List<?> ids && !ids.isEmpty()));
            // BasicContext's default-workspace overload has no workspace here. Use the
            // exact joined official WorkspaceId, just as makeArtifact does above.
            context.doAction(new Op("disposeArtifact", artifact), context.getJoinedWspId("main"));
            await(() -> transport.observations.stream().anyMatch(event -> event.kind() == RuntimeEventKind.DISPOSED));
            var recreated = context.makeArtifact(context.getJoinedWspId("main"), name, LiveRuntimePropertyArtifact.class.getName());
            assertNotEquals(artifact.getId(), recreated.getId());
            assertTrue(transport.observations.stream().anyMatch(event -> event.kind() == RuntimeEventKind.CREATED
                    && event.entityId().incarnation().equals(recreated.getId().toString())), transport.observations.stream()
                    .skip(Math.max(0, transport.observations.size() - 6)).map(event -> event.kind() + ":" + event.after()).toList().toString());
            var creation = transport.observations.stream().filter(event -> event.kind() == RuntimeEventKind.CREATED
                    && event.entityId().incarnation().equals(recreated.getId().toString())).findFirst().orElseThrow();
            assertFalse(((List<?>) creation.after().get("properties")).isEmpty(), "Initial properties must not be omitted");
            assertTrue(system.state().allObjects().stream().anyMatch(object -> object.cls().name().equals("LiveRuntimePropertyArtifact")
                    && facade.verificationSnapshot().image().objects().get(object.name()).semanticId().contains(recreated.getId().toString())
                    && object.state(system.state()).attributeValue("status").toString().equals("'A'")));
            Path bundle = directory.resolve("replay"); facade.exportRuntimeReplay(bundle);
            var replay = new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay().replay(bundle);
            assertTrue(replay.complete(), replay.diagnostics().toString());
            assertSame(system, session.system());
            assertEquals(0, facade.runtimeVerificationResult().count(VerificationOutcome.ERROR));
            transport.emitGap();
            await(() -> "STALE".equals(facade.runtimeVerificationResult().freshness()));
            assertEquals("live-gap", facade.runtimeVerificationResult().eventId());
            assertEquals("cartago", facade.runtimeVerificationResult().sourceId());
            assertEquals(0, facade.runtimeVerificationResult().count(VerificationOutcome.PASS));
            assertTrue(facade.latestVerification().results().stream().noneMatch(result -> result.outcome() == VerificationOutcome.PASS));
        } finally {
            environment.getController("/main").removeArtifact(name);
            restore("jacamo.bridge.modelRevision", oldRevision); restore("jacamo.bridge.generation", oldGeneration);
        }
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "Live callback evidence timed out");
    }
    private static void restore(String key, String value) { if (value == null) System.clearProperty(key); else System.setProperty(key, value); }
    /** Real official source and production wire codec/client; deterministic in-process byte transport. */
    static final class LiveTransport implements BridgeTransport {
        private final ModelSnapshot model;
        private final SnapshotCoordinator snapshots;
        final List<RuntimeEvent> observations = new CopyOnWriteArrayList<>();
        private volatile Consumer<byte[]> receiver;
        LiveTransport(ModelSnapshot model, CartagoSnapshotSource source) throws Exception {
            this.model = model;
            snapshots = new SnapshotCoordinator(List.of(source), 8192, event -> {
                observations.add(event); var next = receiver;
                if (next != null) next.accept(frame(MessageType.RUNTIME_EVENT, ContractPayloads.event(event)));
            });
        }
        @Override public byte[] handshake() { return frame(MessageType.HANDSHAKE, Map.of("readOnly", true)); }
        @Override public byte[] modelSnapshot() { return frame(MessageType.MODEL_SNAPSHOT, ContractPayloads.model(model)); }
        @Override public byte[] runtimeSnapshot() {
            try { return frame(MessageType.RUNTIME_SNAPSHOT, ContractPayloads.runtime(snapshots.capture(model.modelRevision(), 3).snapshot())); }
            catch (Exception error) { throw new IllegalStateException(error); }
        }
        @Override public Subscription subscribe(String token, Consumer<byte[]> next) { receiver = next; return () -> receiver = null; }
        @Override public void acknowledge(String token) { }
        void emitGap() {
            long sequence = observations.isEmpty() ? 1 : observations.getLast().sourceSequence() + 1;
            receiver.accept(frame(MessageType.RUNTIME_EVENT, ContractPayloads.event(new RuntimeEvent("live-gap", "live-native-session", 1,
                    model.modelRevision(), "cartago", "cartago", sequence, Instant.now(), RuntimeEventKind.GAP, null, null, null, null,
                    "", "", Map.of(), Map.of("diagnostic", "PRODUCER_QUEUE_OVERFLOW"), new SourceWatermark("cartago", sequence), Completeness.PARTIAL, List.of()))));
        }
        @Override public void close() { receiver = null; try { snapshots.close(); } catch (Exception error) { throw new IllegalStateException(error); } }
        private byte[] frame(MessageType type, Map<String, Object> payload) {
            return ContractCodec.encode(ContractEnvelope.create("1.0.0", type, "live-test",
                    new DistributionFingerprint("1.3.1", "1".repeat(64), Map.of()), model.sources().getFirst().id().scope(),
                    model.modelRevision(), "live-native-session", 1, "frame:" + type, Instant.now(),
                    List.of(new Capability("official.model", CapabilityStatus.COMPLETE, List.of(), ""),
                            new Capability("runtime.snapshot", CapabilityStatus.COMPLETE, List.of(), "")), Completeness.COMPLETE, Map.of(), List.of(), payload));
        }
    }
}
