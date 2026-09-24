package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlan;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.LinkPlan;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.semantic.Dimension;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.plugins.jacamo.trace.TraceRecord;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;
import org.tzi.use.uml.ocl.value.BooleanValue;

class RuntimeFoundationTest {
    @TempDir Path temporary;

    @Test
    void eventCodecValidatesKindPayloadAndRoundTripsOrderingMetadata() {
        RuntimeEvent event = event(7, RuntimeEventKind.SET_ATTRIBUTE, "cartago:artifact:market/auction1",
                "jacamo:test:environment:Artifact:MAS/ws:auction1",
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), "bid-7");
        RuntimeEventCodec codec = new RuntimeEventCodec();

        String json = codec.write(event);
        RuntimeEvent loaded = codec.read(json);
        assertEquals(event, loaded);
        assertTrue(json.contains("\"sequence\" : 7"));
        assertThrows(IllegalArgumentException.class, () -> codec.read(json.replaceFirst("\\{", "{\"extra\":1,")));
        assertThrows(IllegalArgumentException.class, () -> event(8, RuntimeEventKind.SET_ATTRIBUTE,
                "runtime-1", null, Map.of("attribute", "open"), null));
    }

    @Test
    void mutationEngineAppliesAllMutationKindsAndQuarantinesUnknownTrace() {
        Fixture fixture = fixture();
        RuntimeMutationEngine engine = fixture.engine();
        String runtimeKey = fixture.runtimeKey();
        String semanticId = fixture.artifactTrace().sourceSemanticId();

        assertEquals(MutationStatus.APPLIED, engine.apply(event(1, RuntimeEventKind.SET_ATTRIBUTE, runtimeKey,
                semanticId, Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null)).status());
        assertFalse(((BooleanValue) fixture.attribute("open")).value());

        assertEquals(MutationStatus.QUARANTINED, engine.apply(event(2, RuntimeEventKind.CREATE_OBJECT, "runtime:new",
                semanticId, Map.of("useClass", "AuctionArtifact", "useObject", "runtimeAuction"), null)).status());
        assertNull(fixture.direct().system().state().objectByName("runtimeAuction"));

        LinkPlan link = fixture.instances().links().getFirst();
        Map<String, Object> linkPayload = Map.of("association", link.association(),
                "participants", List.of(link.sourceObject(), link.targetObject()));
        assertEquals(MutationStatus.APPLIED, engine.apply(event(4, RuntimeEventKind.DELETE_LINK, runtimeKey,
                semanticId, linkPayload, null)).status());
        var association = fixture.direct().system().model().getAssociation(link.association());
        var source = fixture.direct().system().state().objectByName(link.sourceObject());
        var target = fixture.direct().system().state().objectByName(link.targetObject());
        assertFalse(fixture.direct().system().state().hasLinkBetweenObjects(association, source, target));
        assertEquals(MutationStatus.APPLIED, engine.apply(event(5, RuntimeEventKind.INSERT_LINK, runtimeKey,
                semanticId, linkPayload, null)).status());
        assertTrue(fixture.direct().system().state().hasLinkBetweenObjects(association, source, target));

        Map<String, Object> operation = Map.of("operation", "placeBid", "arguments", List.of("item1", 10));
        assertEquals(MutationStatus.APPLIED, engine.apply(event(6, RuntimeEventKind.OP_ENTER, runtimeKey,
                semanticId, operation, "op-1")).status());
        assertEquals(MutationStatus.APPLIED, engine.apply(event(7, RuntimeEventKind.OP_EXIT, runtimeKey,
                semanticId, Map.of(), "op-1")).status());
        assertEquals(OperationRuntimeOutcome.EXITED, engine.operationHistory().get("op-1"));
        assertEquals(MutationStatus.APPLIED, engine.apply(event(8, RuntimeEventKind.OP_ENTER, runtimeKey,
                semanticId, operation, "op-2")).status());
        assertEquals(MutationStatus.APPLIED, engine.apply(event(9, RuntimeEventKind.OP_FAIL, runtimeKey,
                semanticId, Map.of("error", "synthetic failure"), "op-2")).status());
        assertEquals(OperationRuntimeOutcome.FAILED, engine.operationHistory().get("op-2"));

        MutationResult unresolved = engine.apply(event(10, RuntimeEventKind.SET_ATTRIBUTE, "unknown-runtime",
                null, Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null));
        assertEquals(MutationStatus.QUARANTINED, unresolved.status());
        assertEquals(2, engine.quarantinedEvents().size());
        assertFalse(((BooleanValue) fixture.attribute("open")).value(), "unknown trace must not corrupt state");
    }

    @Test
    void unrelatedSemanticTraceCannotAuthorizeCreationAndUnknownPropertyCannotDisappear() {
        Fixture f = fixture();
        var result = f.engine().apply(event(1, RuntimeEventKind.CREATE_OBJECT, "unknown-object",
            f.artifactTrace().sourceSemanticId(), Map.of("useClass", "AuctionArtifact", "useObject", "forged"), null));
        assertEquals(MutationStatus.QUARANTINED, result.status());
        assertNull(f.direct().system().state().objectByName("forged"));
        result = f.engine().apply(event(2, RuntimeEventKind.OBS_PROPERTY_CHANGED, f.runtimeKey(),
            f.artifactTrace().sourceSemanticId(), Map.of("property", "unbound", "values", List.of(1)), null));
        assertEquals(MutationStatus.QUARANTINED, result.status());
    }

    @Test
    void terminalCannotCloseAnotherTargetAndLinkReplayIsIdempotent() {
        Fixture f = fixture();
        assertEquals(MutationStatus.APPLIED, f.engine().apply(event(1, RuntimeEventKind.OP_ENTER, f.runtimeKey(),
            f.artifactTrace().sourceSemanticId(), Map.of("operation", "placeBid", "arguments", List.of("x", 1)), "same")).status());
        assertEquals(MutationStatus.QUARANTINED, f.engine().apply(event(2, RuntimeEventKind.OP_EXIT, f.runtimeKey(),
            "wrong-semantic-id", Map.of(), "same")).status());
        assertEquals(MutationStatus.APPLIED, f.engine().apply(event(3, RuntimeEventKind.OP_EXIT, f.runtimeKey(),
            f.artifactTrace().sourceSemanticId(), Map.of(), "same")).status());
        LinkPlan link = f.instances().links().getFirst();
        var payload = Map.<String,Object>of("association", link.association(), "participants", List.of(link.sourceObject(),link.targetObject()));
        assertEquals(MutationStatus.APPLIED, f.engine().apply(event(4, RuntimeEventKind.INSERT_LINK, f.runtimeKey(),
            f.artifactTrace().sourceSemanticId(),payload,null)).status());
    }

    @Test
    void tracedLifecycleRestoresExactObjectAndStrictValuesRejectLossyInput() throws Exception {
        Fixture f = fixture();
        // A standalone traced object exercises generic lifecycle. An object participating
        // in V2 ordered relations requires full projected resync (V2RuntimeOrderTest).
        String name = "detachedArtifact";
        f.direct().system().state().createObject(f.direct().system().model().getClass("AuctionArtifact"), name);
        var record = new org.tzi.use.plugins.jacamo.trace.TraceRecord("detached", "detached-semantic", "object:" + name,
                "Artifact", "OBJECT", "C018", "VP001", null, null, "detached-runtime",
                org.tzi.use.plugins.jacamo.trace.TraceRecord.Status.PROJECTED);
        var engine = new RuntimeMutationEngine(f.direct().system(), new org.tzi.use.plugins.jacamo.trace.TraceIndex(List.of(record)));
        assertEquals(MutationStatus.APPLIED, engine.apply(event(1, RuntimeEventKind.DESTROY_OBJECT,
            "detached-runtime", "detached-semantic", Map.of(), null)).status());
        assertNull(f.direct().system().state().objectByName(name));
        assertEquals(MutationStatus.APPLIED, engine.apply(event(2, RuntimeEventKind.CREATE_OBJECT,
            "detached-runtime", "detached-semantic", Map.of("useClass","AuctionArtifact","useObject",name),null)).status());
        assertNotNull(f.direct().system().state().objectByName(name));
        assertThrows(IllegalArgumentException.class, () -> RuntimeValues.convert("BOOLEAN", "maybe"));
        assertThrows(ArithmeticException.class, () -> RuntimeValues.convert("INTEGER", 1.5));
        assertThrows(ArithmeticException.class, () -> RuntimeValues.convert("INTEGER", Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> RuntimeValues.convert("REAL", "NaN"));
        assertThrows(IllegalArgumentException.class, () -> RuntimeValues.convert("STRING", 12));
        assertEquals(MutationStatus.FAILED, f.engine().apply(event(3, RuntimeEventKind.OP_ENTER,
            f.runtimeKey(), f.artifactTrace().sourceSemanticId(), Map.of("operation","placeBid","arguments",List.of("item",1.5)),"bad")).status());
        assertTrue(f.engine().operationHistory().isEmpty());
    }

    @Test
    void orderedQueuePreservesSequenceReportsBackpressureAndDrainsOnStop() {
        List<Long> applied = java.util.Collections.synchronizedList(new ArrayList<>());
        try (OrderedRuntimeEventQueue queue = new OrderedRuntimeEventQueue(2, event -> applied.add(event.sequence()))) {
            queue.start();
            queue.submit(event(1, RuntimeEventKind.OP_FAIL, "runtime", null, Map.of("error", "one"), "one"));
            queue.submit(event(2, RuntimeEventKind.OP_FAIL, "runtime", null, Map.of("error", "two"), "two"));
            assertThrows(IllegalArgumentException.class, () -> queue.submit(
                    event(2, RuntimeEventKind.OP_FAIL, "runtime", null, Map.of("error", "duplicate"), "duplicate")));
            queue.stopGracefully(Duration.ofSeconds(5));
            assertEquals(List.of(1L, 2L), applied);
            QueueMetrics metrics = queue.metrics();
            assertEquals(2, metrics.processed());
            assertEquals(1, metrics.rejected());
            assertEquals(0, metrics.dropped());
            assertTrue(metrics.highWatermark() >= 1);
        }
    }

    @Test
    void syntheticConnectorMirrorsJsonReplayThenReconnectResyncRepairsDrift() {
        Fixture fixture = fixture();
        String runtimeKey = fixture.runtimeKey();
        String semanticId = fixture.artifactTrace().sourceSemanticId();
        RuntimeEvent snapshotOpen = event(1, RuntimeEventKind.SET_ATTRIBUTE, runtimeKey, semanticId,
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null);
        RuntimeEvent replayClosed = event(2, RuntimeEventKind.SET_ATTRIBUTE, runtimeKey, semanticId,
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);
        RuntimeEventCodec codec = new RuntimeEventCodec();
        Path replay = temporary.resolve("events.json");
        codec.writeEvents(replay, List.of(replayClosed));
        SyntheticRuntimeConnector connector = new SyntheticRuntimeConnector("synthetic-auction",
                new RuntimeSnapshot("snapshot-1", Instant.parse("2026-09-15T00:00:00Z"), 1,
                        List.of(snapshotOpen), "snapshot-hash-1"), replay, codec);
        assertTrue(connector.capabilities().containsAll(List.of(ConnectorCapability.FULL_SNAPSHOT,
                ConnectorCapability.EVENT_SUBSCRIPTION, ConnectorCapability.RECONNECT)));
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector, fixture.engine(), 16);

        mirror.connect(URI.create("synthetic://auction"));
        assertEquals(MirrorState.LIVE, mirror.state());
        connector.replayAll();
        mirror.awaitIdle(Duration.ofSeconds(5));
        assertFalse(((BooleanValue) fixture.attribute("open")).value());

        mirror.disconnect();
        assertEquals(MirrorState.STALE, mirror.state());
        RuntimeEvent resyncOpen = event(10, RuntimeEventKind.SET_ATTRIBUTE, runtimeKey, semanticId,
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null);
        connector.replaceSnapshot(new RuntimeSnapshot("snapshot-2", Instant.parse("2026-09-15T00:01:00Z"),
                10, List.of(resyncOpen), "snapshot-hash-2"));
        mirror.reconnectAndResync();
        assertEquals(MirrorState.LIVE, mirror.state());
        assertTrue(((BooleanValue) fixture.attribute("open")).value());
        assertEquals("snapshot-hash-2", mirror.lastSnapshotFingerprint());
        assertEquals(0, mirror.metrics().dropped());
        mirror.close();
    }

    @Test
    void initialSynchronizationBuffersConcurrentDeltaAndDetectsLostConnection() {
        Fixture fixture = fixture();
        String runtimeKey = fixture.runtimeKey();
        String semanticId = fixture.artifactTrace().sourceSemanticId();
        RuntimeEvent snapshotOpen = event(1, RuntimeEventKind.SET_ATTRIBUTE, runtimeKey, semanticId,
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null);
        RuntimeEvent concurrentClosed = event(2, RuntimeEventKind.SET_ATTRIBUTE, runtimeKey, semanticId,
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);
        SnapshotRaceConnector connector = new SnapshotRaceConnector(snapshotOpen, concurrentClosed);
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector, fixture.engine(), 8);

        mirror.connect(URI.create("jacamo://local/auction"));
        mirror.awaitIdle(Duration.ofSeconds(5));
        assertEquals(MirrorState.LIVE, mirror.state());
        assertFalse(((BooleanValue) fixture.attribute("open")).value(),
                "delta observed while snapshotting must not be lost");

        connector.dropConnection();
        mirror.refreshConnectionState();
        assertEquals(MirrorState.STALE, mirror.state());
        mirror.close();
    }

    @Test
    void useMutationFailureMovesMirrorToErrorAndDoesNotCorruptExistingState() {
        Fixture fixture = fixture();
        String runtimeKey = fixture.runtimeKey();
        String semanticId = fixture.artifactTrace().sourceSemanticId();
        RuntimeEvent initial = event(1, RuntimeEventKind.SET_ATTRIBUTE, runtimeKey, semanticId,
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null);
        RuntimeEvent invalid = event(2, RuntimeEventKind.SET_ATTRIBUTE, runtimeKey, semanticId,
                Map.of("attribute", "missingAttribute", "valueType", "BOOLEAN", "value", false), null);
        RuntimeEventCodec codec = new RuntimeEventCodec();
        Path replay = temporary.resolve("invalid-mutation.json");
        codec.writeEvents(replay, List.of(invalid));
        SyntheticRuntimeConnector connector = new SyntheticRuntimeConnector("mutation-error",
                new RuntimeSnapshot("initial", Instant.now(), 1, List.of(initial), "initial-hash"), replay, codec);
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector, fixture.engine(), 8);

        mirror.connect(URI.create("synthetic://mutation-error"));
        connector.replayAll();
        mirror.awaitIdle(Duration.ofSeconds(5));

        assertEquals(MirrorState.ERROR, mirror.state());
        assertEquals(1, mirror.metrics().failed());
        assertTrue(((BooleanValue) fixture.attribute("open")).value());
        mirror.close();
    }

    @Test
    void lateCallbackAfterWorkspaceReplacementBelongsToTheOldObserver() {
        Fixture old = fixture();
        Fixture next = fixture();
        LateEventConnector connector = new LateEventConnector();
        TrackingObserver previousObserver = new TrackingObserver();
        TrackingObserver nextObserver = new TrackingObserver();
        try (RuntimeMirrorService mirror = new RuntimeMirrorService(connector, old.engine(), 8, previousObserver)) {
            mirror.connect(URI.create("synthetic://replacement"));
            Consumer<RuntimeEvent> oldListener = connector.listener;
            mirror.replaceWorkspace(next.engine(), nextObserver, () -> { });
            RuntimeEvent late = event(1, RuntimeEventKind.SET_ATTRIBUTE, old.runtimeKey(),
                    old.artifactTrace().sourceSemanticId(),
                    Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);
            oldListener.accept(late);
            assertEquals(List.of(late.eventId()), previousObserver.rejected);
            assertTrue(nextObserver.rejected.isEmpty());
            assertEquals(0, mirror.metrics().processed());
            connector.emit(late);
            mirror.awaitIdle(Duration.ofSeconds(5));
            assertEquals(1, mirror.metrics().processed());
            assertFalse(((BooleanValue) next.attribute("open")).value());
            assertTrue(((BooleanValue) old.attribute("open")).value());
        }
    }

    @Test
    void mirrorReportsOrderingAndShutdownRejectionsAndClosesTimingLifecycle() {
        Fixture fixture = fixture();
        LateEventConnector connector = new LateEventConnector();
        TrackingObserver observer = new TrackingObserver();
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector, fixture.engine(), 8, observer);
        RuntimeEvent accepted = event(2, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);
        RuntimeEvent outOfOrder = event(1, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null);
        RuntimeEvent afterShutdown = event(3, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null);

        mirror.connect(URI.create("synthetic://late-events"));
        connector.emit(accepted);
        mirror.awaitIdle(Duration.ofSeconds(5));
        assertThrows(IllegalArgumentException.class, () -> connector.emit(outOfOrder));
        mirror.disconnect();
        connector.emit(afterShutdown);

        assertEquals(List.of(outOfOrder.eventId(), afterShutdown.eventId()), observer.rejected);
        assertEquals(1, observer.streamClosed);
        mirror.close();
    }

    @Test
    void backpressureRejectionCannotResurrectAnInFlightOperationCorrelation() throws Exception {
        Fixture fixture = fixture();
        LateEventConnector connector = new LateEventConnector();
        BlockingMutationObserver observer = new BlockingMutationObserver();
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector, fixture.engine(), 1, observer);
        Map<String, Object> operation = Map.of("operation", "placeBid",
                "arguments", List.of("item1", 10));
        RuntimeEvent enter = event(1, RuntimeEventKind.OP_ENTER, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), operation, "reused-correlation");
        RuntimeEvent queued = event(2, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);
        RuntimeEvent rejectedExit = event(3, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), Map.of(), "reused-correlation");
        RuntimeEvent replacementEnter = event(4, RuntimeEventKind.OP_ENTER, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), operation, "reused-correlation");

        mirror.connect(URI.create("synthetic://rejected-operation-terminal"));
        try {
            connector.emit(enter);
            assertTrue(observer.started.await(5, TimeUnit.SECONDS));
            connector.emit(queued);
            assertThrows(IllegalStateException.class, () -> connector.emit(rejectedExit));
        } finally {
            observer.release.countDown();
        }
        mirror.awaitIdle(Duration.ofSeconds(5));

        connector.emit(replacementEnter);
        mirror.awaitIdle(Duration.ofSeconds(5));

        assertEquals(MirrorState.LIVE, mirror.state(),
                "a rejected terminal event must invalidate the abandoned mutation correlation");
        mirror.close();
    }

    @Test
    void staleRejectedTerminalDoesNotInvalidateANewerActiveOperation() {
        Fixture fixture = fixture();
        LateEventConnector connector = new LateEventConnector();
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector, fixture.engine(), 8);
        Map<String, Object> operation = Map.of("operation", "placeBid",
                "arguments", List.of("item1", 10));
        RuntimeEvent enter = event(2, RuntimeEventKind.OP_ENTER, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), operation, "ordered-correlation");
        RuntimeEvent staleExit = event(1, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), Map.of(), "ordered-correlation");
        RuntimeEvent validExit = event(3, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), Map.of(), "ordered-correlation");

        mirror.connect(URI.create("synthetic://stale-operation-terminal"));
        connector.emit(enter);
        mirror.awaitIdle(Duration.ofSeconds(5));
        assertThrows(IllegalArgumentException.class, () -> connector.emit(staleExit));
        connector.emit(validExit);
        mirror.awaitIdle(Duration.ofSeconds(5));

        assertEquals(MirrorState.LIVE, mirror.state());
        assertEquals(OperationRuntimeOutcome.EXITED,
                fixture.engine().operationHistory().get("ordered-correlation"));
        mirror.close();
    }

    @Test
    void outOfOrderTerminalCannotPoisonANewerAcceptedTerminal() throws Exception {
        Fixture fixture = fixture();
        LateEventConnector connector = new LateEventConnector();
        BlockingMutationObserver observer = new BlockingMutationObserver();
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector, fixture.engine(), 2, observer);
        Map<String, Object> operation = Map.of("operation", "placeBid",
                "arguments", List.of("item1", 10));
        RuntimeEvent enter = event(1, RuntimeEventKind.OP_ENTER, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), operation, "accepted-terminal");
        RuntimeEvent acceptedExit = event(4, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), Map.of(), "accepted-terminal");
        RuntimeEvent staleExit = event(3, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), Map.of(), "accepted-terminal");

        mirror.connect(URI.create("synthetic://accepted-terminal-ordering"));
        try {
            connector.emit(enter);
            assertTrue(observer.started.await(5, TimeUnit.SECONDS));
            connector.emit(acceptedExit);
            assertThrows(IllegalArgumentException.class, () -> connector.emit(staleExit));
        } finally {
            observer.release.countDown();
        }
        mirror.awaitIdle(Duration.ofSeconds(5));

        assertEquals(MirrorState.LIVE, mirror.state());
        assertEquals(OperationRuntimeOutcome.EXITED,
                fixture.engine().operationHistory().get("accepted-terminal"));
        mirror.close();
    }

    @Test
    void lateClosedStreamTerminalCannotPoisonMutationCorrelation() {
        Fixture fixture = fixture();
        RuntimeMutationEngine engine = fixture.engine();
        Map<String, Object> operation = Map.of("operation", "placeBid",
                "arguments", List.of("item1", 10));
        RuntimeEvent lateExit = event(10, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), Map.of(), "next-stream");
        RuntimeEvent nextEnter = event(1, RuntimeEventKind.OP_ENTER, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), operation, "next-stream");
        RuntimeEvent nextExit = event(2, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), Map.of(), "next-stream");

        engine.eventStreamClosed();
        engine.eventRejected(lateExit, new IllegalStateException("RUNTIME_EVENT_STREAM_CLOSED"));

        assertEquals(MutationStatus.APPLIED, engine.apply(nextEnter).status());
        assertEquals(MutationStatus.APPLIED, engine.apply(nextExit).status());
    }

    @Test
    void backpressureTombstonesRetireAfterTheAcceptedWatermarkCompletes() {
        Fixture fixture = fixture();
        RuntimeMutationEngine engine = fixture.engine();
        engine.eventCompleted(event(7, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null));
        for (int index = 0; index < 100; index++) {
            RuntimeEvent rejected = event(100 + index, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                    fixture.artifactTrace().sourceSemanticId(), Map.of(), "rejected-" + index);
            engine.eventRejected(rejected, new RuntimeQueueBackpressureException(7));
        }
        assertEquals(0, engine.pendingOperationRejections(),
                "a completion that wins the race must prevent later tombstone insertion");

        for (int index = 0; index < 100; index++) {
            RuntimeEvent rejected = event(300 + index, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                    fixture.artifactTrace().sourceSemanticId(), Map.of(), "pending-" + index);
            engine.eventRejected(rejected, new RuntimeQueueBackpressureException(8));
        }
        assertEquals(100, engine.pendingOperationRejections());
        engine.eventCompleted(event(8, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null));

        assertEquals(0, engine.pendingOperationRejections(),
                "unique rejected correlations must not accumulate for the life of the stream");
    }

    @Test
    void completionBeforeRejectedTerminalStillInvalidatesTheOlderMutationCorrelation() {
        Fixture fixture = fixture();
        RuntimeMutationEngine engine = fixture.engine();
        Map<String, Object> operation = Map.of("operation", "placeBid",
                "arguments", List.of("item1", 10));
        RuntimeEvent enter = event(1, RuntimeEventKind.OP_ENTER, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), operation, "completed-before-rejection");
        RuntimeEvent rejectedExit = event(2, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), Map.of(), "completed-before-rejection");
        RuntimeEvent laterExit = event(3, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(), Map.of(), "completed-before-rejection");

        assertEquals(MutationStatus.APPLIED, engine.apply(enter).status());
        engine.eventCompleted(enter);
        engine.eventRejected(rejectedExit, new RuntimeQueueBackpressureException(1));

        MutationResult result = engine.apply(laterExit);
        assertEquals(MutationStatus.FAILED, result.status());
        assertTrue(result.diagnostic().contains("OPERATION_CORRELATION_MISSING"),
                "the rejected terminal must abandon an older active correlation even after its watermark completed");
    }

    @Test
    void mirrorRejectsCallbackDeliveredAfterFailedInitialSynchronization() {
        Fixture fixture = fixture();
        LateEventConnector connector = new LateEventConnector(true);
        TrackingObserver observer = new TrackingObserver();
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector, fixture.engine(), 8, observer);
        RuntimeEvent late = event(1, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                fixture.artifactTrace().sourceSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);

        assertThrows(IllegalStateException.class, () -> mirror.connect(URI.create("synthetic://failed-sync")));
        connector.emit(late);

        assertEquals(List.of(late.eventId()), observer.rejected,
                "a callback after its stream closes must not retain observer timing state");
        assertEquals(1, observer.streamClosed);
    }

    @Test
    void observerFailureCannotLeaveMirrorLive() {
      for (String hook : List.of("before", "after", "completed")) {
        Fixture fixture = fixture();
        LateEventConnector connector = new LateEventConnector();
        RuntimeEventObserver observer = new RuntimeEventObserver() {
            private void check(String current) { if (hook.equals(current)) throw new IllegalStateException("observer unavailable: " + hook); }
            @Override public void beforeMutation(RuntimeEvent event) { check("before"); }
            @Override public void afterMutation(RuntimeEvent event, MutationResult result) { check("after"); }
            @Override public void eventCompleted(RuntimeEvent event) { check("completed"); }
        };
        try (var mirror = new RuntimeMirrorService(connector, fixture.engine(), 8, observer)) {
            mirror.connect(URI.create("synthetic://observer-failure"));
            var delta = event(1, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                    fixture.artifactTrace().sourceSemanticId(),
                    Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);
            connector.emit(delta);
            mirror.awaitIdle(Duration.ofSeconds(5));
            assertEquals(MirrorState.ERROR, mirror.state(), "An unverified/unapplied accepted event cannot leave LIVE truth");
            assertEquals(1, mirror.metrics().failed());
            assertTrue(mirror.lastFailure().contains(delta.eventId()));
        }
      }
    }

    @Test
    void receivingObserverFailureRequiresDisconnectAndAuthoritativeRecovery() {
        Fixture fixture = fixture();
        LateEventConnector connector = new LateEventConnector();
        var fail = new java.util.concurrent.atomic.AtomicBoolean(true);
        RuntimeEventObserver observer = new RuntimeEventObserver() {
            @Override public void eventReceived(RuntimeEvent event) {
                if (fail.get()) throw new IllegalStateException("receive unavailable");
            }
        };
        try (var mirror = new RuntimeMirrorService(connector, fixture.engine(), 8, observer)) {
            mirror.connect(URI.create("synthetic://receive-failure"));
            var delta = event(1, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                    fixture.artifactTrace().sourceSemanticId(),
                    Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);
            assertThrows(IllegalStateException.class, () -> connector.emit(delta));
            assertEquals(MirrorState.ERROR, mirror.state());
            assertTrue(mirror.lastFailure().contains(delta.eventId()));
            mirror.disconnect();
            fail.set(false);
            mirror.reconnectAndResync();
            assertEquals(MirrorState.LIVE, mirror.state());
        }
    }

    @Test
    void lifecycleObserverFailuresCannotRetainLiveTruth() {
        for (String hook : List.of("state", "snapshot", "drift", "closed")) {
            Fixture fixture = fixture();
            LateEventConnector connector = new LateEventConnector();
            var enabled = new java.util.concurrent.atomic.AtomicBoolean(true);
            RuntimeEventObserver observer = new RuntimeEventObserver() {
                private void check(String current) {
                    if (enabled.get() && hook.equals(current)) throw new IllegalStateException("injected " + hook);
                }
                public void stateChanged(MirrorState state) { if (state == MirrorState.LIVE) check("state"); }
                public void snapshotApplied(RuntimeSnapshot snapshot) { check("snapshot"); }
                public void driftChecked(RuntimeDriftReport report) { check("drift"); }
                public void eventStreamClosed() { check("closed"); }
            };
            var mirror = new RuntimeMirrorService(connector, fixture.engine(), 8, observer);
            try {
                if (hook.equals("state") || hook.equals("snapshot")) {
                    assertThrows(IllegalStateException.class, () -> mirror.connect(URI.create("synthetic://lifecycle")));
                } else {
                    mirror.connect(URI.create("synthetic://lifecycle"));
                    if (hook.equals("drift")) assertThrows(IllegalStateException.class,
                            () -> mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY));
                    else assertThrows(IllegalStateException.class,
                            () -> mirror.replaceWorkspace(fixture.engine(), observer, () -> { }));
                }
                assertEquals(MirrorState.ERROR, mirror.state(), hook);
                assertNotNull(mirror.lastFailure());
            } finally { enabled.set(false); mirror.close(); }
        }
    }

    @Test
    void recordsEventToMirrorLatencyAndQueueSampleWithoutAnSla() {
        Fixture fixture = fixture();
        LateEventConnector connector = new LateEventConnector();
        var started = new java.util.concurrent.atomic.AtomicLong();
        var elapsed = new java.util.concurrent.atomic.AtomicLong();
        RuntimeEventObserver observer = new RuntimeEventObserver() {
            public void eventReceived(RuntimeEvent event) { started.set(System.nanoTime()); }
            public void afterMutation(RuntimeEvent event, MutationResult result) {
                elapsed.set(System.nanoTime() - started.get());
                assertEquals(MutationStatus.APPLIED, result.status());
            }
        };
        try (var mirror = new RuntimeMirrorService(connector, fixture.engine(), 8, observer)) {
            mirror.connect(URI.create("synthetic://performance"));
            connector.emit(event(1, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                    fixture.artifactTrace().sourceSemanticId(),
                    Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null));
            mirror.awaitIdle(Duration.ofSeconds(5));
            assertEquals(BooleanValue.FALSE, fixture.attribute("open"));
            assertEquals(1, mirror.metrics().processed());
            System.out.printf("PHASE27_RUNTIME eventToMirror=%dns depth=%d highWatermark=%d%n",
                    elapsed.get(), mirror.metrics().depth(), mirror.metrics().highWatermark());
        }
    }

    @Test
    void unsubscribeFailureStillDrainsWorkerAndDisconnectsTransport() {
        Fixture fixture = fixture();
        var disconnected = new java.util.concurrent.atomic.AtomicBoolean();
        var closes = new java.util.concurrent.atomic.AtomicInteger();
        RuntimeConnector connector = new RuntimeConnector() {
            public String connectorId() { return "cleanup"; }
            public Set<ConnectorCapability> capabilities() { return Set.of(); }
            public ConnectorState state() { return ConnectorState.CONNECTED; }
            public void connect(URI endpoint) { }
            public void disconnect() { disconnected.set(true); }
            public RuntimeSnapshot fullSnapshot() { return new RuntimeSnapshot("empty", Instant.EPOCH, 0, List.of(), "empty"); }
            public RuntimeSubscription subscribe(Consumer<RuntimeEvent> listener) {
                return () -> { if (closes.incrementAndGet() == 1) throw new IllegalStateException("unsubscribe failed"); };
            }
        };
        var mirror = new RuntimeMirrorService(connector, fixture.engine(), 8);
        mirror.connect(URI.create("synthetic://cleanup"));
        assertThrows(IllegalStateException.class, mirror::disconnect);
        assertTrue(disconnected.get(), "subscription failure must not prevent transport cleanup");
        assertEquals(MirrorState.ERROR, mirror.state());
        mirror.close();
        assertEquals(MirrorState.OFFLINE, mirror.state());
        assertEquals(2, closes.get(), "failed unsubscribe remains retryable");
    }

    private RuntimeEvent event(long sequence, RuntimeEventKind kind, String runtimeSourceId,
                               String semanticSourceId, Map<String, Object> payload, String correlationId) {
        return RuntimeEvent.create("event-" + sequence + "-" + kind, Instant.ofEpochSecond(sequence), sequence,
                Dimension.ENVIRONMENT, kind, runtimeSourceId, semanticSourceId, payload, correlationId);
    }

    private Fixture fixture() {
        var semantic = new StaticProjectImporter().importProject(
                Path.of("src/test/resources/auction/auction.jcm")).model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var baseline = new TransformationPlanner().plan(semantic, mapping);
        var structure = new VerificationSemanticLayer().apply(baseline,
                new VerificationProfileLoader().loadActive(mapping)).transformation();
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        var generated = new TextBackend().generate("auction", structure, instances);
        DirectUseBackend.Result direct = new DirectUseBackend().materialize(generated, instances);
        TraceIndex trace = new TraceBuilder().build(semantic, mapping, structure, instances);
        String artifactSemanticId = instances.objects().stream().filter(object -> object.className().equals("AuctionArtifact"))
                .findFirst().orElseThrow().semanticId();
        TraceRecord artifactTrace = trace.bySemanticId(artifactSemanticId).stream()
                .filter(record -> record.targetKind().equals("OBJECT")).findFirst().orElseThrow();
        String runtimeKey = "cartago:artifact:market/auction1";
        trace.registerRuntimeKey(artifactTrace.traceId(), runtimeKey);
        return new Fixture(direct, instances, artifactTrace, runtimeKey,
                new RuntimeMutationEngine(direct.system(), trace));
    }

    private record Fixture(DirectUseBackend.Result direct, InstancePlan instances, TraceRecord artifactTrace,
                           String runtimeKey, RuntimeMutationEngine engine) {
        Object attribute(String name) {
            var object = direct.system().state().objectByName(
                    artifactTrace.targetUseId().substring("object:".length()));
            return object.state(direct.system().state()).attributeValue(object.cls().attribute(name, true));
        }
    }

    private static final class SnapshotRaceConnector implements RuntimeConnector {
        private final RuntimeEvent snapshot;
        private final RuntimeEvent concurrent;
        private Consumer<RuntimeEvent> listener;
        private ConnectorState state = ConnectorState.DISCONNECTED;

        private SnapshotRaceConnector(RuntimeEvent snapshot, RuntimeEvent concurrent) {
            this.snapshot = snapshot;
            this.concurrent = concurrent;
        }
        @Override public String connectorId() { return "snapshot-race"; }
        @Override public Set<ConnectorCapability> capabilities() {
            return Set.of(ConnectorCapability.FULL_SNAPSHOT, ConnectorCapability.EVENT_SUBSCRIPTION,
                    ConnectorCapability.RECONNECT);
        }
        @Override public ConnectorState state() { return state; }
        @Override public void connect(URI endpoint) { state = ConnectorState.CONNECTED; }
        @Override public RuntimeSnapshot fullSnapshot() {
            listener.accept(concurrent);
            return new RuntimeSnapshot("snapshot-race", Instant.now(), snapshot.sequence(),
                    List.of(snapshot), "snapshot-race-hash");
        }
        @Override public RuntimeSubscription subscribe(Consumer<RuntimeEvent> listener) {
            this.listener = listener;
            return () -> this.listener = null;
        }
        @Override public void disconnect() { state = ConnectorState.DISCONNECTED; }
        void dropConnection() { state = ConnectorState.DISCONNECTED; }
    }

    private static final class TrackingObserver implements RuntimeEventObserver {
        private final List<String> rejected = new ArrayList<>();
        private int streamClosed;

        @Override public void eventRejected(RuntimeEvent event, RuntimeException reason) {
            rejected.add(event.eventId());
        }
        @Override public void eventStreamClosed() { streamClosed++; }
    }

    private static final class BlockingMutationObserver implements RuntimeEventObserver {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override public void beforeMutation(RuntimeEvent event) {
            if (event.kind() != RuntimeEventKind.OP_ENTER) return;
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test gate timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }
    }

    private static final class LateEventConnector implements RuntimeConnector {
        private Consumer<RuntimeEvent> listener;
        private ConnectorState state = ConnectorState.DISCONNECTED;
        private final boolean failSnapshot;

        private LateEventConnector() { this(false); }

        private LateEventConnector(boolean failSnapshot) { this.failSnapshot = failSnapshot; }

        @Override public String connectorId() { return "late-events"; }
        @Override public Set<ConnectorCapability> capabilities() {
            return Set.of(ConnectorCapability.FULL_SNAPSHOT, ConnectorCapability.EVENT_SUBSCRIPTION);
        }
        @Override public ConnectorState state() { return state; }
        @Override public void connect(URI endpoint) { state = ConnectorState.CONNECTED; }
        @Override public RuntimeSnapshot fullSnapshot() {
            if (failSnapshot) throw new IllegalStateException("RUNTIME_SYNTHETIC_SNAPSHOT_FAILURE");
            return new RuntimeSnapshot("empty", Instant.EPOCH, 0, List.of(), "empty");
        }
        @Override public RuntimeSubscription subscribe(Consumer<RuntimeEvent> listener) {
            this.listener = listener;
            return () -> { };
        }
        @Override public void disconnect() { state = ConnectorState.DISCONNECTED; }
        private void emit(RuntimeEvent event) { listener.accept(event); }
    }
}
