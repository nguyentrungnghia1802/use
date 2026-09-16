package org.tzi.use.plugins.jacamo.verification;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.constraint.ConstraintExtractor;
import org.tzi.use.plugins.jacamo.constraint.ConstraintSpec;
import org.tzi.use.plugins.jacamo.constraint.Expression;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.runtime.DriftResyncPolicy;
import org.tzi.use.plugins.jacamo.runtime.RuntimeDriftReport;
import org.tzi.use.plugins.jacamo.runtime.RuntimeQueueBackpressureException;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEvent;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEventCodec;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEventKind;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEventObserver;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMirrorService;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.runtime.RuntimeSnapshot;
import org.tzi.use.plugins.jacamo.runtime.SyntheticRuntimeConnector;
import org.tzi.use.plugins.jacamo.semantic.Dimension;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.plugins.jacamo.trace.TraceRecord;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;
import org.tzi.use.uml.ocl.value.BooleanValue;

class RuntimeVerificationEngineTest {
    @TempDir Path temporary;

    @Test
    void eventDrivenChecksCorrelateViolationAndOperationPrePostUsingCapturedPreState() {
        Fixture fixture = fixture(true);
        String runtimeKey = fixture.runtimeKey();
        String semanticId = fixture.artifactSemanticId();
        List<RuntimeEvent> replay = List.of(
                event(2, RuntimeEventKind.SET_ATTRIBUTE, runtimeKey, semanticId,
                        Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null),
                event(3, RuntimeEventKind.OP_ENTER, runtimeKey, semanticId,
                        Map.of("operation", "placeBid", "arguments", List.of("item1", -1)), "bid-invalid"),
                event(4, RuntimeEventKind.OP_FAIL, runtimeKey, semanticId,
                        Map.of("operation", "placeBid", "error", "amount must be positive"), "bid-invalid"),
                event(5, RuntimeEventKind.SET_ATTRIBUTE, runtimeKey, semanticId,
                        Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null),
                event(6, RuntimeEventKind.OP_ENTER, runtimeKey, semanticId,
                        Map.of("operation", "placeBid", "arguments", List.of("item1", 10)), "bid-post"),
                event(7, RuntimeEventKind.SET_ATTRIBUTE, runtimeKey, semanticId,
                        Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), "bid-post"),
                event(8, RuntimeEventKind.OP_EXIT, runtimeKey, semanticId,
                        Map.of("operation", "placeBid"), "bid-post"));
        Path events = temporary.resolve("runtime-events.json");
        RuntimeEventCodec codec = new RuntimeEventCodec();
        codec.writeEvents(events, replay);
        RuntimeSnapshot snapshot = new RuntimeSnapshot("initial", Instant.now(), 1,
                List.of(event(1, RuntimeEventKind.SET_ATTRIBUTE, runtimeKey, semanticId,
                        Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null)), "initial-open");
        SyntheticRuntimeConnector connector = new SyntheticRuntimeConnector("runtime-verification", snapshot,
                events, codec);
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(fixture.direct().system(),
                fixture.registry(), fixture.trace());
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector,
                new RuntimeMutationEngine(fixture.direct().system(), fixture.trace()), 32, verifier);

        mirror.connect(URI.create("synthetic://runtime-verification"));
        connector.replayAll();
        mirror.awaitIdle(Duration.ofSeconds(5));

        RuntimeVerificationReport stateChange = verifier.reports().stream().filter(report -> report.event() != null
                && report.event().eventId().startsWith("event-2-")).findFirst().orElseThrow();
        assertTrue(stateChange.hasViolation());
        assertTrue(stateChange.verification().results().stream()
                .anyMatch(result -> result.sourceTrace().contains(semanticId)));
        assertTrue(stateChange.latencyNanos() > 0);
        System.out.printf("PHASE13_RUNTIME eventToResult=%dns%n", stateChange.latencyNanos());
        assertTrue(verifier.reports().stream().anyMatch(report -> report.event() != null
                && report.event().eventId().startsWith("event-3-")
                && report.verification().results().stream().anyMatch(result -> result.outcome() == VerificationOutcome.FAIL
                && result.correlationId().equals("bid-invalid"))));
        assertTrue(verifier.reports().stream().anyMatch(report -> report.diagnostics().contains("RUNTIME_OPERATION_ABORTED")));
        VerificationResult post = verifier.reports().stream().flatMap(report -> report.verification().results().stream())
                .filter(result -> result.constraintId().equals("POST-AUCTION-OPEN")).findFirst().orElseThrow();
        assertEquals(VerificationOutcome.FAIL, post.outcome(), "@pre must use the state captured at operation enter");
        assertEquals(List.of("event-6-OP_ENTER", "event-8-OP_EXIT"), post.runtimeEventIds());
        assertEquals(4, verifier.snapshotVersion(), "initial snapshot plus three state-changing deltas");
        String json = new RuntimeVerificationReportExporter().toJson(verifier.latestReport());
        assertTrue(json.contains("\"connectionState\" : \"LIVE\""));
        assertTrue(json.contains("\"latencyNanos\""));
        mirror.close();
    }

    @Test
    void dependencyIndexTargetsDeclaredAndGlobalConstraintsAndFallsBackWhenNoDependencyIsKnown() {
        Path source = Path.of("fixture.ocl");
        var span = new org.tzi.use.plugins.jacamo.project.SourceSpan(source, 1, 1, 1, 10);
        ConstraintDescriptor targeted = new ConstraintDescriptor("targeted", "Targeted", "Artifact", null,
                ConstraintKind.INV, ConstraintOrigin.TRANSLATED, source, span, List.of("semantic:artifact"), true, "true");
        ConstraintDescriptor global = new ConstraintDescriptor("global", "Global", "Artifact", null,
                ConstraintKind.INV, ConstraintOrigin.CASE, source, span, List.of(), true, "true");
        ConstraintDependencyIndex index = new ConstraintDependencyIndex(List.of(targeted, global));

        var selection = index.select(List.of("semantic:artifact"));
        assertFalse(selection.fullCheckFallback());
        assertEquals(java.util.Set.of("targeted", "global"), selection.constraintIds());
        assertEquals(java.util.Set.of("global"), index.select(List.of("semantic:other")).constraintIds());
        assertTrue(new ConstraintDependencyIndex(List.of(targeted)).select(List.of("semantic:other"))
                .fullCheckFallback());
    }

    @Test
    void targetedInvariantResultIsEquivalentToTheSameConstraintInAFullCheck() {
        Fixture fixture = fixture(false);
        setOpen(fixture, false);
        VerificationService service = new DefaultVerificationService();
        VerificationReport full = service.runFullVerification(fixture.direct().system(), fixture.registry(),
                fixture.trace());
        String constraintId = fixture.registry().descriptors().stream()
                .filter(descriptor -> descriptor.name().equals("AuctionInitiallyOpen"))
                .findFirst().orElseThrow().id();
        VerificationReport targeted = service.runTargetedVerification(fixture.direct().system(), fixture.registry(),
                fixture.trace(), java.util.Set.of(constraintId), "equivalence");
        VerificationResult fullResult = full.results().stream()
                .filter(result -> result.constraintId().equals(constraintId)).findFirst().orElseThrow();
        VerificationResult targetedResult = targeted.results().getFirst();
        assertEquals(fullResult.outcome(), targetedResult.outcome());
        assertEquals(fullResult.contextObject(), targetedResult.contextObject());
        assertEquals(fullResult.sourceTrace(), targetedResult.sourceTrace());
    }

    @Test
    void stateChangeLatencyCoversMutationThroughVerificationResult() {
        Fixture fixture = fixture(false);
        AtomicLong clock = new AtomicLong(1_000);
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(fixture.direct().system(),
                fixture.registry(), fixture.trace(), new DefaultVerificationService(), clock::get);
        RuntimeEvent event = event(1, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                fixture.artifactSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);

        verifier.eventReceived(event);
        clock.addAndGet(20);
        verifier.beforeMutation(event);
        clock.addAndGet(30);
        var mutation = new RuntimeMutationEngine(fixture.direct().system(), fixture.trace()).apply(event);
        clock.addAndGet(50);
        verifier.afterMutation(event, mutation);

        assertEquals(100, verifier.latestReport().latencyNanos(),
                "event-to-result latency must include USE mutation and verification");
    }

    @Test
    void connectorReceiptIsNotBlockedByAnOngoingVerification() throws Exception {
        Fixture fixture = fixture(false);
        BlockingVerificationService verification = new BlockingVerificationService();
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(fixture.direct().system(),
                fixture.registry(), fixture.trace(), verification);
        RuntimeEvent checking = event(1, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                fixture.artifactSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);
        RuntimeEvent arriving = event(2, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                fixture.artifactSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null);
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            verifier.eventReceived(checking);
            Future<?> ongoing = threads.submit(() -> verifier.afterMutation(checking,
                    org.tzi.use.plugins.jacamo.runtime.MutationResult.applied()));
            assertTrue(verification.started.await(5, TimeUnit.SECONDS), "verification did not reach controlled gate");

            Future<?> receipt = threads.submit(() -> verifier.eventReceived(arriving));
            boolean receivedWhileCheckWasBlocked;
            try {
                receipt.get(500, TimeUnit.MILLISECONDS);
                receivedWhileCheckWasBlocked = true;
            } catch (TimeoutException expected) {
                receivedWhileCheckWasBlocked = false;
            } finally {
                verification.release.countDown();
            }
            ongoing.get(5, TimeUnit.SECONDS);
            receipt.get(5, TimeUnit.SECONDS);

            assertTrue(receivedWhileCheckWasBlocked,
                    "connector receipt must not contend on the long-running verification monitor");
        } finally {
            verification.release.countDown();
            threads.shutdownNow();
        }
    }

    @Test
    void operationReportsUseTheSameReceiptToResultLatencyContract() {
        Fixture fixture = fixture(true);
        AtomicLong clock = new AtomicLong(1_000);
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(fixture.direct().system(),
                fixture.registry(), fixture.trace(), new DefaultVerificationService(), clock::get);
        RuntimeMutationEngine mutations = new RuntimeMutationEngine(fixture.direct().system(), fixture.trace());
        RuntimeEvent enter = event(1, RuntimeEventKind.OP_ENTER, fixture.runtimeKey(), fixture.artifactSemanticId(),
                Map.of("operation", "placeBid", "arguments", List.of("item1", 10)), "latency-op");

        verifier.eventReceived(enter);
        clock.set(1_060);
        verifier.beforeMutation(enter);
        assertEquals(60, verifier.latestReport().latencyNanos());
        verifier.afterMutation(enter, mutations.apply(enter));

        RuntimeEvent failure = event(2, RuntimeEventKind.OP_FAIL, fixture.runtimeKey(), fixture.artifactSemanticId(),
                Map.of("operation", "placeBid", "error", "synthetic"), "latency-op");
        clock.set(2_000);
        verifier.eventReceived(failure);
        clock.set(2_080);
        verifier.beforeMutation(failure);
        verifier.afterMutation(failure, mutations.apply(failure));

        assertEquals(80, verifier.latestReport().latencyNanos());
    }

    @Test
    void rejectedAndClosedEventsCannotLeaveReusableReceiptTimestamps() {
        Fixture fixture = fixture(false);
        AtomicLong clock = new AtomicLong(100);
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(fixture.direct().system(),
                fixture.registry(), fixture.trace(), new DefaultVerificationService(), clock::get);
        RuntimeMutationEngine mutations = new RuntimeMutationEngine(fixture.direct().system(), fixture.trace());
        RuntimeEvent rejected = eventWithId("reused-after-rejection", 1, fixture, false);

        verifier.eventReceived(rejected);
        verifier.eventRejected(rejected, new IllegalArgumentException("RUNTIME_QUEUE_OUT_OF_ORDER"));
        clock.set(1_000);
        RuntimeEvent accepted = eventWithId("reused-after-rejection", 2, fixture, false);
        verifier.eventReceived(accepted);
        clock.set(1_060);
        verifier.beforeMutation(accepted);
        verifier.afterMutation(accepted, mutations.apply(accepted));
        verifier.eventCompleted(accepted);
        assertEquals(60, verifier.latestReport().latencyNanos());

        clock.set(2_000);
        RuntimeEvent abandoned = eventWithId("reused-after-close", 3, fixture, true);
        verifier.eventReceived(abandoned);
        verifier.eventStreamClosed();
        clock.set(3_000);
        RuntimeEvent afterReconnect = eventWithId("reused-after-close", 4, fixture, true);
        verifier.eventReceived(afterReconnect);
        clock.set(3_040);
        verifier.beforeMutation(afterReconnect);
        verifier.afterMutation(afterReconnect, mutations.apply(afterReconnect));
        verifier.eventCompleted(afterReconnect);
        assertEquals(40, verifier.latestReport().latencyNanos());
    }

    @Test
    void rejectedOperationTerminalCannotBeResurrectedByBlockedPreStateCapture() throws Exception {
        Fixture fixture = fixture(true);
        BlockingVerificationService verification = new BlockingVerificationService(true);
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(fixture.direct().system(),
                fixture.registry(), fixture.trace(), verification);
        RuntimeEvent enter = event(1, RuntimeEventKind.OP_ENTER, fixture.runtimeKey(), fixture.artifactSemanticId(),
                Map.of("operation", "placeBid", "arguments", List.of("item1", 10)), "rejected-terminal");
        RuntimeEvent rejectedExit = event(2, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactSemanticId(), Map.of(), "rejected-terminal");
        RuntimeEvent laterExit = event(3, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactSemanticId(), Map.of(), "rejected-terminal");

        verifier.eventReceived(enter);
        ExecutorService thread = Executors.newSingleThreadExecutor();
        Future<?> capture = thread.submit(() -> verifier.beforeMutation(enter));
        assertTrue(verification.started.await(5, TimeUnit.SECONDS));
        verifier.eventReceived(rejectedExit);
        verifier.eventRejected(rejectedExit, new RuntimeQueueBackpressureException(1));
        verification.release.countDown();
        capture.get(5, TimeUnit.SECONDS);
        verifier.eventCompleted(enter);
        thread.shutdownNow();

        verifier.eventReceived(laterExit);
        verifier.beforeMutation(laterExit);
        verifier.afterMutation(laterExit, org.tzi.use.plugins.jacamo.runtime.MutationResult.applied());
        verifier.eventCompleted(laterExit);

        assertEquals("RUNTIME_OPERATION_EXIT_UNMATCHED",
                verifier.latestReport().verification().results().getFirst().constraintId(),
                "a later exit must not complete pre-state abandoned by a rejected terminal event");
    }

    @Test
    void lateClosedStreamTerminalCannotPoisonNextStreamPreState() {
        Fixture fixture = fixture(true);
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(fixture.direct().system(),
                fixture.registry(), fixture.trace(), new DefaultVerificationService());
        RuntimeEvent lateExit = event(10, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactSemanticId(), Map.of(), "next-stream");
        RuntimeEvent nextEnter = event(1, RuntimeEventKind.OP_ENTER, fixture.runtimeKey(),
                fixture.artifactSemanticId(),
                Map.of("operation", "placeBid", "arguments", List.of("item1", 10)), "next-stream");
        RuntimeEvent nextExit = event(2, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactSemanticId(), Map.of(), "next-stream");

        verifier.eventStreamClosed();
        verifier.eventReceived(lateExit);
        verifier.eventRejected(lateExit, new IllegalStateException("RUNTIME_EVENT_STREAM_CLOSED"));
        verifier.eventReceived(nextEnter);
        verifier.beforeMutation(nextEnter);
        verifier.afterMutation(nextEnter, org.tzi.use.plugins.jacamo.runtime.MutationResult.applied());
        verifier.eventCompleted(nextEnter);
        verifier.eventReceived(nextExit);
        verifier.beforeMutation(nextExit);
        verifier.afterMutation(nextExit, org.tzi.use.plugins.jacamo.runtime.MutationResult.applied());
        verifier.eventCompleted(nextExit);

        assertTrue(verifier.latestReport().verification().results().stream()
                .anyMatch(result -> result.constraintId().equals("POST-AUCTION-OPEN")),
                "late callbacks from a closed stream must not block the next stream's operation capture");
    }

    @Test
    void backpressureTombstonesRetireAfterTheAcceptedWatermarkCompletes() {
        Fixture fixture = fixture(false);
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(fixture.direct().system(),
                fixture.registry(), fixture.trace(), new DefaultVerificationService());
        verifier.eventCompleted(eventWithId("accepted-watermark-7", 7, fixture, true));
        for (int index = 0; index < 100; index++) {
            RuntimeEvent rejected = event(100 + index, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                    fixture.artifactSemanticId(), Map.of(), "rejected-" + index);
            verifier.eventReceived(rejected);
            verifier.eventRejected(rejected, new RuntimeQueueBackpressureException(7));
        }
        assertEquals(0, verifier.pendingOperationRejections(),
                "a completion that wins the race must prevent later tombstone insertion");

        for (int index = 0; index < 100; index++) {
            RuntimeEvent rejected = event(300 + index, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                    fixture.artifactSemanticId(), Map.of(), "pending-" + index);
            verifier.eventReceived(rejected);
            verifier.eventRejected(rejected, new RuntimeQueueBackpressureException(8));
        }
        assertEquals(100, verifier.pendingOperationRejections());
        verifier.eventCompleted(eventWithId("accepted-watermark-8", 8, fixture, true));

        assertEquals(0, verifier.pendingOperationRejections(),
                "unique rejected correlations must not accumulate for the life of the stream");
    }

    @Test
    void completionBeforeRejectedTerminalStillInvalidatesTheOlderVerificationCorrelation() {
        Fixture fixture = fixture(true);
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(fixture.direct().system(),
                fixture.registry(), fixture.trace(), new DefaultVerificationService());
        RuntimeEvent enter = event(1, RuntimeEventKind.OP_ENTER, fixture.runtimeKey(), fixture.artifactSemanticId(),
                Map.of("operation", "placeBid", "arguments", List.of("item1", 10)),
                "completed-before-rejection");
        RuntimeEvent rejectedExit = event(2, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactSemanticId(), Map.of(), "completed-before-rejection");
        RuntimeEvent laterExit = event(3, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(),
                fixture.artifactSemanticId(), Map.of(), "completed-before-rejection");

        verifier.eventReceived(enter);
        verifier.beforeMutation(enter);
        verifier.afterMutation(enter, org.tzi.use.plugins.jacamo.runtime.MutationResult.applied());
        verifier.eventCompleted(enter);
        verifier.eventReceived(rejectedExit);
        verifier.eventRejected(rejectedExit, new RuntimeQueueBackpressureException(1));
        verifier.eventReceived(laterExit);
        verifier.beforeMutation(laterExit);
        verifier.afterMutation(laterExit, org.tzi.use.plugins.jacamo.runtime.MutationResult.applied());
        verifier.eventCompleted(laterExit);

        assertEquals("RUNTIME_OPERATION_EXIT_UNMATCHED",
                verifier.latestReport().verification().results().getFirst().constraintId(),
                "the rejected terminal must abandon an older pre-state even after its watermark completed");
    }

    @Test
    void streamCloseDiscardsOperationCorrelationBeforeTheNextStream() {
        Fixture fixture = fixture(true);
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(fixture.direct().system(),
                fixture.registry(), fixture.trace(), new DefaultVerificationService());
        RuntimeMutationEngine mutations = new RuntimeMutationEngine(fixture.direct().system(), fixture.trace());
        RuntimeEvent enter = event(1, RuntimeEventKind.OP_ENTER, fixture.runtimeKey(), fixture.artifactSemanticId(),
                Map.of("operation", "placeBid", "arguments", List.of("item1", 10)), "closed-operation");
        RuntimeEvent exit = event(2, RuntimeEventKind.OP_EXIT, fixture.runtimeKey(), fixture.artifactSemanticId(),
                Map.of(), "closed-operation");

        verifier.eventReceived(enter);
        verifier.beforeMutation(enter);
        verifier.afterMutation(enter, mutations.apply(enter));
        verifier.eventCompleted(enter);
        verifier.eventStreamClosed();

        verifier.eventReceived(exit);
        verifier.beforeMutation(exit);
        verifier.afterMutation(exit, mutations.apply(exit));
        verifier.eventCompleted(exit);

        assertEquals("RUNTIME_OPERATION_EXIT_UNMATCHED",
                verifier.latestReport().verification().results().getFirst().constraintId(),
                "an exit after reconnect must not complete an operation from a closed stream");
    }

    @Test
    void bufferedEventLatencyStartsAtOriginalConnectorReceipt() {
        Fixture fixture = fixture(false);
        AtomicLong clock = new AtomicLong(100);
        RuntimeEvent buffered = event(1, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(),
                fixture.artifactSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", false), null);
        BufferedTimingConnector connector = new BufferedTimingConnector(buffered, clock);
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(fixture.direct().system(),
                fixture.registry(), fixture.trace(), new DefaultVerificationService(), clock::get);
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector,
                new RuntimeMutationEngine(fixture.direct().system(), fixture.trace()), 8, verifier);

        mirror.connect(URI.create("synthetic://buffered-timing"));
        mirror.awaitIdle(Duration.ofSeconds(5));

        RuntimeVerificationReport report = verifier.reports().stream()
                .filter(value -> value.event() != null && value.event().eventId().equals(buffered.eventId()))
                .findFirst().orElseThrow();
        assertEquals(100, report.latencyNanos());
        mirror.close();
    }

    @Test
    void authoritativeDriftIsDiagnosedAndPeriodicAutoResyncRepairsMirror() throws Exception {
        Fixture fixture = fixture(false);
        RuntimeEvent open = event(1, RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(), fixture.artifactSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", true), null);
        Path empty = temporary.resolve("empty-events.json");
        RuntimeEventCodec codec = new RuntimeEventCodec();
        codec.writeEvents(empty, List.of());
        SyntheticRuntimeConnector connector = new SyntheticRuntimeConnector("drift",
                new RuntimeSnapshot("authoritative", Instant.now(), 1, List.of(open), "authoritative-open"),
                empty, codec);
        AtomicReference<RuntimeDriftReport> resyncReport = new AtomicReference<>();
        RuntimeEventObserver observer = new RuntimeEventObserver() {
            @Override public void driftChecked(RuntimeDriftReport report) {
                if (report.resyncTriggered()) resyncReport.compareAndSet(null, report);
            }
        };
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector,
                new RuntimeMutationEngine(fixture.direct().system(), fixture.trace()), 8, observer);
        mirror.connect(URI.create("synthetic://drift"));
        setOpen(fixture, false);

        RuntimeDriftReport diagnostic = mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY);
        assertTrue(diagnostic.drifted());
        assertEquals("RUNTIME_MIRROR_DRIFT", diagnostic.differences().getFirst().diagnosticCode());
        assertFalse(open(fixture));

        mirror.enablePeriodicDriftChecks(Duration.ofMillis(20), DriftResyncPolicy.AUTO_RESYNC);
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while ((!open(fixture) || resyncReport.get() == null) && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(open(fixture), "periodic authoritative check must trigger full resync");
        assertNotNull(resyncReport.get(), "the triggering drift report must not be lost when a later clean check runs");
        mirror.close();
    }

    private Fixture fixture(boolean postcondition) {
        Path project = Path.of("src/test/resources/auction");
        var semantic = new StaticProjectImporter().importProject(project.resolve("auction.jcm")).model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var baseline = new TransformationPlanner().plan(semantic, mapping);
        var structure = new VerificationSemanticLayer().apply(baseline,
                new VerificationProfileLoader().loadV1()).transformation();
        var constraints = new ArrayList<>(new ConstraintExtractor().extract(semantic, structure, Map.of()));
        if (postcondition) {
            var operation = semantic.elements().stream().filter(element -> element.kind() == MetamodelKind.Operation)
                    .findFirst().orElseThrow();
            Expression self = new Expression.VariableRef("self", Expression.ValueType.OBJECT);
            constraints.add(new ConstraintExtractor().explicitPostcondition("POST-AUCTION-OPEN", "AuctionArtifact",
                    "placeBid", "OpenUnchanged", new Expression.BinaryOp(
                    new Expression.PropertyRef(self, "open", Expression.ValueType.BOOLEAN, false), "=",
                    new Expression.PropertyRef(self, "open", Expression.ValueType.BOOLEAN, true),
                    Expression.ValueType.BOOLEAN), operation, List.of("source-backed fixture"),
                    List.of(operation.id().value())));
        }
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        OclProfileLoader loader = new OclProfileLoader();
        var caseProfile = loader.loadCase(project, Path.of("verification/auction.ocl"));
        var generated = new OclGenerator().generate("auction", structure, constraints,
                List.of(loader.loadCore(), caseProfile));
        String commands = new TextBackend().generate("auction", structure, instances).initialCommands();
        DirectUseBackend.Result direct = new DirectUseBackend().materialize(
                new TextBackend.GeneratedArtifacts(generated.useModel(), commands), instances);
        TraceIndex trace = new TraceBuilder().build(semantic, mapping, structure, instances);
        String semanticId = instances.objects().stream().filter(object -> object.className().equals("AuctionArtifact"))
                .findFirst().orElseThrow().semanticId();
        TraceRecord record = trace.bySemanticId(semanticId).stream()
                .filter(value -> value.targetKind().equals("OBJECT")).findFirst().orElseThrow();
        String runtimeKey = "cartago:artifact:market/auction1";
        trace.registerRuntimeKey(record.traceId(), runtimeKey);
        ConstraintRegistry registry = ConstraintRegistry.load(direct.system().model(), generated,
                List.of(ConstraintRegistry.profile(ConstraintOrigin.CORE, loader.loadCore()),
                        ConstraintRegistry.profile(ConstraintOrigin.CASE, caseProfile)));
        return new Fixture(direct, trace, registry, record, runtimeKey, semanticId);
    }

    private RuntimeEvent event(long sequence, RuntimeEventKind kind, String runtimeKey, String semanticId,
                               Map<String, Object> payload, String correlation) {
        return RuntimeEvent.create("event-" + sequence + "-" + kind, Instant.ofEpochSecond(sequence), sequence,
                Dimension.ENVIRONMENT, kind, runtimeKey, semanticId, payload, correlation);
    }

    private RuntimeEvent eventWithId(String eventId, long sequence, Fixture fixture, boolean open) {
        return RuntimeEvent.create(eventId, Instant.ofEpochSecond(sequence), sequence, Dimension.ENVIRONMENT,
                RuntimeEventKind.SET_ATTRIBUTE, fixture.runtimeKey(), fixture.artifactSemanticId(),
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", open), null);
    }

    private boolean open(Fixture fixture) {
        var object = fixture.direct().system().state().objectByName(
                fixture.artifactTrace().targetUseId().substring("object:".length()));
        return ((BooleanValue) object.state(fixture.direct().system().state())
                .attributeValue(object.cls().attribute("open", true))).value();
    }

    private void setOpen(Fixture fixture, boolean value) {
        var object = fixture.direct().system().state().objectByName(
                fixture.artifactTrace().targetUseId().substring("object:".length()));
        object.state(fixture.direct().system().state()).setAttributeValue(object.cls().attribute("open", true),
                BooleanValue.get(value));
    }

    private record Fixture(DirectUseBackend.Result direct, TraceIndex trace, ConstraintRegistry registry,
                           TraceRecord artifactTrace, String runtimeKey, String artifactSemanticId) { }

    private static final class BlockingVerificationService implements VerificationService {
        private final VerificationService delegate = new DefaultVerificationService();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final boolean blockBeginOperation;

        private BlockingVerificationService() { this(false); }

        private BlockingVerificationService(boolean blockBeginOperation) {
            this.blockBeginOperation = blockBeginOperation;
        }

        @Override public VerificationReport runFullVerification(org.tzi.use.uml.sys.MSystem system,
                                                                 ConstraintRegistry registry, TraceIndex trace) {
            return delegate.runFullVerification(system, registry, trace);
        }

        @Override public VerificationReport runTargetedVerification(org.tzi.use.uml.sys.MSystem system,
                ConstraintRegistry registry, TraceIndex trace, Set<String> constraintIds, String runId) {
            if (!blockBeginOperation) block();
            return delegate.runTargetedVerification(system, registry, trace, constraintIds, runId);
        }

        @Override public OperationCheck beginOperation(org.tzi.use.uml.sys.MSystem system,
                ConstraintRegistry registry, TraceIndex trace, OperationRequest request) {
            if (blockBeginOperation) block();
            return delegate.beginOperation(system, registry, trace, request);
        }

        private void block() {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test gate timed out");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }

        @Override public VerificationReport completeOperation(OperationCheck check,
                org.tzi.use.uml.sys.MSystemState postState, org.tzi.use.uml.ocl.value.Value result,
                List<String> exitRuntimeEventIds) {
            return delegate.completeOperation(check, postState, result, exitRuntimeEventIds);
        }
    }

    private static final class BufferedTimingConnector implements org.tzi.use.plugins.jacamo.runtime.RuntimeConnector {
        private final RuntimeEvent buffered;
        private final AtomicLong clock;
        private Consumer<RuntimeEvent> listener;
        private org.tzi.use.plugins.jacamo.runtime.ConnectorState state =
                org.tzi.use.plugins.jacamo.runtime.ConnectorState.DISCONNECTED;

        private BufferedTimingConnector(RuntimeEvent buffered, AtomicLong clock) {
            this.buffered = buffered;
            this.clock = clock;
        }

        @Override public String connectorId() { return "buffered-timing"; }
        @Override public Set<org.tzi.use.plugins.jacamo.runtime.ConnectorCapability> capabilities() {
            return Set.of(org.tzi.use.plugins.jacamo.runtime.ConnectorCapability.FULL_SNAPSHOT,
                    org.tzi.use.plugins.jacamo.runtime.ConnectorCapability.EVENT_SUBSCRIPTION);
        }
        @Override public org.tzi.use.plugins.jacamo.runtime.ConnectorState state() { return state; }
        @Override public void connect(URI endpoint) {
            state = org.tzi.use.plugins.jacamo.runtime.ConnectorState.CONNECTED;
        }
        @Override public RuntimeSnapshot fullSnapshot() {
            listener.accept(buffered);
            clock.set(200);
            return new RuntimeSnapshot("buffered-base", Instant.EPOCH, 0, List.of(), "buffered-base");
        }
        @Override public org.tzi.use.plugins.jacamo.runtime.RuntimeSubscription subscribe(
                Consumer<RuntimeEvent> listener) {
            this.listener = listener;
            return () -> this.listener = null;
        }
        @Override public void disconnect() {
            state = org.tzi.use.plugins.jacamo.runtime.ConnectorState.DISCONNECTED;
        }
    }
}
