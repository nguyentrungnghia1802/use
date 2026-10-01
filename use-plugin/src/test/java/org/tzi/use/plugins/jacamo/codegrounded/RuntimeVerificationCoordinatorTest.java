package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.codegrounded.RuntimeVerificationFixtures.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.common.eventbus.Subscribe;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.uml.sys.events.AtomicStateChangedEvent;

class RuntimeVerificationCoordinatorTest {
    @TempDir java.nio.file.Path directory;
    static final String PROFILE = "context ObservablePropertySnapshot inv NoB: self.values <> '[\"B\"]'";
    @Test void propertyABACommitsViolatingStateAndKeepsImmutableHistoryWithOneCheckAndNotification() throws Exception {
        var projector = projector(); var coordinator = projector.coordinator(); var system = projector.system();
        var baseline = coordinator.loadProfileSource("constraints.ocl", PROFILE);
        assertEquals(VerificationOutcome.PASS, external(baseline));
        AtomicInteger notifications = new AtomicInteger();
        system.getEventBus().register(new Object() { @Subscribe public void onCommit(AtomicStateChangedEvent event) {
            assertTrue(javax.swing.SwingUtilities.isEventDispatchThread()); notifications.incrementAndGet();
            assertEquals(coordinator.latest().stateHash(), hash(coordinator));
        }});
        long checks = coordinator.verificationCount();
        projector.apply(delta("B", 1, "B")); var violation = coordinator.latest();
        assertEquals(VerificationOutcome.FAIL, external(violation));
        var property = system.state().allObjects().stream().filter(object -> object.cls().name().equals("ObservablePropertySnapshot")).findFirst().orElseThrow();
        assertEquals("[\"B\"]", ((org.tzi.use.uml.ocl.value.StringValue) property.state(system.state()).attributeValue("values")).value());
        projector.apply(delta("A", 2, "A"));
        assertEquals(VerificationOutcome.PASS, external(coordinator.latest()));
        assertEquals(violation.stateVersion() + 1, coordinator.latest().stateVersion());
        assertNotEquals(violation.stateHash(), coordinator.latest().stateHash());
        assertEquals(baseline.stateHash(), coordinator.latest().stateHash());
        assertEquals(checks + 2, coordinator.verificationCount()); assertEquals(2, notifications.get());
        assertTrue(coordinator.history().stream().anyMatch(value -> value.equals(violation)));
        assertTrue(Files.readString(coordinator.journal().path()).contains("EXTERNAL:ObservablePropertySnapshot::NoB"));
        assertSame(system, coordinator.constraints().system());
    }
    @Test void malformedBatchRollsBackAllWritesAndMarksCoverageLostWithoutIncrement() throws Exception {
        var projector = projector(); var coordinator = projector.coordinator(); coordinator.loadProfileSource("user.ocl", PROFILE);
        long version = coordinator.latest().stateVersion(); String before = hash(coordinator);
        var beforeObjects = projector.system().state().allObjects().stream().collect(java.util.stream.Collectors.toMap(
                org.tzi.use.uml.sys.MObject::name, object -> object));
        var beforeLinks = java.util.Set.copyOf(projector.system().state().allLinks());
        String commands = new NativeUseSoilExporter().export(projector.system()).commands();
        var broken = new java.util.LinkedHashMap<>(property("B")); broken.put("valueTypes", List.of());
        var rejected = event("malformed", 1, RuntimeEventKind.CHANGED, ID,
                Map.of("normalizedEventKind", "APPLY_CARTAGO_PROPERTY_DELTA", "semanticId", ARTIFACT,
                        "removedPropertySemanticIds", List.of(PROPERTY), "properties", List.of(broken)));
        var watermarks = projector.sourceWatermarks(); var aliases = projector.runtimeAliases(); var trace = projector.trace();
        assertThrows(RuntimeException.class, () -> projector.apply(rejected));
        assertTrue(projector.resyncRequired());
        assertEquals(watermarks, projector.sourceWatermarks()); assertEquals(aliases, projector.runtimeAliases());
        assertEquals(trace, projector.trace());
        beforeObjects.forEach((name, object) -> assertSame(object, projector.system().state().objectByName(name)));
        assertTrue(beforeLinks.stream().allMatch(original -> projector.system().state().allLinks().stream().anyMatch(link -> link == original)));
        // A rejected transaction is not in the applied-event idempotency ledger.
        assertThrows(RuntimeException.class, () -> projector.apply(rejected));
        assertEquals(commands, new NativeUseSoilExporter().export(projector.system()).commands());
        assertEquals(before, hash(coordinator)); assertEquals(version, coordinator.latest().stateVersion());
        assertEquals("STALE", coordinator.latest().freshness()); assertEquals(0, coordinator.latest().count(VerificationOutcome.PASS));
    }
    @Test void artifactDisposeRecreateUsesDifferentUuidAndRejectsResurrectedIncarnation() throws Exception {
        var projector = projector(); int objects = projector.system().state().numObjects();
        projector.apply(event("dispose", 1, RuntimeEventKind.DISPOSED, ID,
                Map.of("normalizedEventKind", "DELETE_CARTAGO_ARTIFACT", "semanticId", ARTIFACT)));
        assertEquals(objects - 2, projector.system().state().numObjects());
        var id = new BridgeEntityId("cartago", "environment", "artifact", "/main", "box", "uuid-2");
        projector.apply(event("create", 2, RuntimeEventKind.CREATED, id, artifact("cartago:artifact:env:/main:uuid-2", "uuid-2")));
        assertEquals(objects - 1, projector.system().state().numObjects());
        assertThrows(RuntimeException.class, () -> projector.apply(event("resurrect", 3, RuntimeEventKind.CREATED, ID, artifact(ARTIFACT, "uuid-1"))));
    }
    @Test void duplicateIsIdempotentWhileConflictRewindGapAndStaleIdentitiesCannotPass() throws Exception {
        for (String kind : List.of("conflict", "rewind", "gap", "generation", "revision")) {
            var projector = projector(); projector.coordinator().loadProfileSource("user.ocl", PROFILE);
            var first = delta("first", 1, "A"); projector.apply(first);
            long version = projector.coordinator().latest().stateVersion(); assertFalse(projector.apply(first));
            var next = delta(kind.equals("conflict") ? "first" : "next", kind.equals("gap") ? 3 : kind.equals("rewind") ? 1 : 2, "B");
            if (kind.equals("generation") || kind.equals("revision")) next = new RuntimeEvent(next.eventId(), next.sessionId(),
                    kind.equals("generation") ? 0 : next.generation(), kind.equals("revision") ? "old" : next.modelRevision(), next.subsystem(),
                    next.sourceId(), next.sourceSequence(), next.observedAt(), next.kind(), next.factKind(), next.projectionStatus(),
                    next.entityId(), next.relationId(), "", "", next.before(), next.after(), next.watermark(), next.completeness(), next.evidence());
            RuntimeEvent rejected = next;
            assertThrows(RuntimeException.class, () -> projector.apply(rejected), kind);
            assertEquals(version, projector.coordinator().latest().stateVersion());
            assertEquals(0, projector.coordinator().latest().count(VerificationOutcome.PASS));
            assertEquals("INCOMPLETE", projector.coordinator().latest().coverage());
        }
    }
    @Test void resyncKeepsProfileAndSameSystemAndRecoversCoverage() throws Exception {
        var projector = projector(); var system = projector.system(); var coordinator = projector.coordinator();
        coordinator.loadProfileSource("user.ocl", PROFILE); coordinator.coverageGap("TEST_GAP");
        projector.applySnapshot(snapshot("resync", 5), SESSION, 2);
        assertSame(system, projector.system()); assertEquals(2, coordinator.latest().generation());
        assertEquals(VerificationOutcome.PASS, external(coordinator.latest()));
        assertEquals(PROFILE, coordinator.constraints().profile().source());
        assertEquals(REVISION, coordinator.constraints().profile().modelRevision());
    }
    @Test void typedIdentityMismatchCannotRedirectAnArtifactMutation() throws Exception {
        var projector = projector(); projector.coordinator().loadProfileSource("user.ocl", PROFILE);
        String before = hash(projector.coordinator()); long version = projector.coordinator().latest().stateVersion();
        var wrong = new BridgeEntityId("cartago", "environment", "artifact", "/main", "box", "uuid-other");
        assertThrows(RuntimeException.class, () -> projector.apply(event("redirect", 1, RuntimeEventKind.CHANGED, wrong,
                Map.of("normalizedEventKind", "APPLY_CARTAGO_PROPERTY_DELTA", "semanticId", ARTIFACT,
                        "properties", List.of(property("B")), "removedPropertySemanticIds", List.of()))));
        assertEquals(before, hash(projector.coordinator())); assertEquals(version, projector.coordinator().latest().stateVersion());
        assertEquals(0, projector.coordinator().latest().count(VerificationOutcome.PASS));
    }
    @Test void evidenceOnlyObservationHasNoStateVersionOrOclEvaluation() throws Exception {
        var projector = projector(); var coordinator = projector.coordinator(); coordinator.loadProfileSource("user.ocl", PROFILE);
        var latest = coordinator.latest(); long checks = coordinator.verificationCount();
        var observation = new RuntimeEvent("intention-observed", SESSION, 1, REVISION, "jason", "jason", 1, java.time.Instant.EPOCH,
                RuntimeEventKind.CHANGED, RuntimeFactKind.INTENTION, ProjectionStatus.EVIDENCE_ONLY,
                new BridgeEntityId("jason", "agent", "intention", "project", "7", "incarnation"), null,
                "", "", Map.of(), Map.of("intentionId", 7), new SourceWatermark("jason", 1), Completeness.COMPLETE, List.of());
        assertFalse(projector.apply(observation)); assertSame(latest, coordinator.latest());
        assertEquals(latest.stateVersion(), coordinator.lastObservation().stateVersion()); assertEquals(checks, coordinator.verificationCount());
        assertTrue(coordinator.lastObservation().outcomes().stream().allMatch(item -> item.outcome() == VerificationOutcome.SKIPPED));
    }
    @Test void persistedOverflowAndBoundedHistoryAreExplicitlyStale() throws Exception {
        var pipeline = CodeGroundedTestFixtures.helloPipeline();
        var engine = new NativeRuntimeMutationEngine(pipeline.state().system(), pipeline.state().semanticObjectIndex(), new CodeGroundedRuntimeRuleRegistry());
        var coordinator = new RuntimeVerificationCoordinator(engine, SESSION, 1, REVISION, directory, 3, 32_768);
        for (int i = 0; i < 40; i++) coordinator.manualVerify();
        assertTrue(coordinator.journal().hasGap()); assertEquals(3, coordinator.history().size());
        assertEquals("STALE", coordinator.latest().freshness()); assertEquals(0, coordinator.latest().count(VerificationOutcome.PASS));
        assertTrue(Files.readString(coordinator.journal().path()).contains("JOURNAL_OVERFLOW"));
    }
    @Test void ephemeralRuntimeDirectoryIsRemovedOnExplicitClose() throws Exception {
        var pipeline = CodeGroundedTestFixtures.helloPipeline();
        var engine = new NativeRuntimeMutationEngine(pipeline.state().system(), pipeline.state().semanticObjectIndex(),
                new CodeGroundedRuntimeRuleRegistry());
        var root = directory.resolve("runtime-root");
        var run = RuntimeVerificationCoordinator.createEphemeralDirectory(root);
        var coordinator = new RuntimeVerificationCoordinator(engine, SESSION, 1, REVISION, run, 3, 32_768, true);
        assertTrue(Files.exists(run));
        coordinator.close();
        assertFalse(Files.exists(run));
    }
    @Test void concurrentVerifyAndExportObserveOnlyCommittedState() throws Exception {
        var projector = projector(); var coordinator = projector.coordinator(); coordinator.loadProfileSource("user.ocl", PROFILE);
        try (var pool = Executors.newFixedThreadPool(3)) {
            var writer = pool.submit(() -> { for (int i = 1; i <= 12; i++) projector.apply(delta("change-" + i, i, i % 2 == 0 ? "A" : "B")); });
            var verifier = pool.submit(() -> { for (int i = 0; i < 12; i++) coordinator.manualVerify(); });
            var exporter = pool.submit(() -> { for (int i = 0; i < 12; i++) coordinator.read(() -> {
                assertEquals(hash(coordinator), coordinator.latest().stateHash()); return null;
            }); });
            writer.get(20, java.util.concurrent.TimeUnit.SECONDS); verifier.get(20, java.util.concurrent.TimeUnit.SECONDS);
            exporter.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
    static String hash(RuntimeVerificationCoordinator coordinator) {
        return org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(
                new NativeUseSoilExporter().export(coordinator.system()).commands().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    static VerificationOutcome external(RuntimeVerificationResult result) {
        return result.outcomes().stream().filter(item -> item.constraintId().equals("EXTERNAL:ObservablePropertySnapshot::NoB"))
                .findFirst().orElseThrow().outcome();
    }
}
