package org.tzi.use.plugins.jacamo.verification;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.tzi.use.plugins.jacamo.runtime.RuntimeEvent;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEventCodec;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEventKind;
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

        assertTrue(verifier.reports().stream().anyMatch(report -> report.event() != null
                && report.event().eventId().startsWith("event-2-") && report.hasViolation()
                && report.verification().results().stream().anyMatch(result -> result.sourceTrace().contains(semanticId))));
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
        RuntimeVerificationEngine verifier = new RuntimeVerificationEngine(fixture.direct().system(),
                fixture.registry(), fixture.trace());
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector,
                new RuntimeMutationEngine(fixture.direct().system(), fixture.trace()), 8, verifier);
        mirror.connect(URI.create("synthetic://drift"));
        setOpen(fixture, false);

        RuntimeDriftReport diagnostic = mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY);
        assertTrue(diagnostic.drifted());
        assertEquals("RUNTIME_MIRROR_DRIFT", diagnostic.differences().getFirst().diagnosticCode());
        assertFalse(open(fixture));

        mirror.enablePeriodicDriftChecks(Duration.ofMillis(20), DriftResyncPolicy.AUTO_RESYNC);
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!open(fixture) && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(open(fixture), "periodic authoritative check must trigger full resync");
        assertTrue(mirror.lastDriftReport().resyncTriggered());
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
}
