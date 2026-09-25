package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.constraint.ConstraintExtractor;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.runtime.QueueMetrics;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEvent;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEventCodec;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEventKind;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMirrorService;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.runtime.RuntimeSnapshot;
import org.tzi.use.plugins.jacamo.runtime.SyntheticRuntimeConnector;
import org.tzi.use.plugins.jacamo.semantic.Dimension;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.tzi.use.plugins.jacamo.verification.ConstraintRegistry;
import org.tzi.use.plugins.jacamo.verification.DefaultVerificationService;
import org.tzi.use.plugins.jacamo.verification.RuntimeVerificationEngine;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;
import org.tzi.use.uml.ocl.value.BooleanValue;

class Phase43PerformanceEvidenceTest {
    @TempDir Path temporary;

    @Test void recordsObservedPipelineAndRuntimeMeasurementsWithoutSemanticShortcuts() throws Exception {
        TreeMap<String, Long> durations = new TreeMap<>();
        Path project = Path.of("src/test/resources/auction");

        long started = System.nanoTime();
        var imported = new StaticProjectImporter().importProject(project.resolve("auction.jcm"));
        durations.put("import", elapsed(started));
        assertTrue(imported.success(), imported.diagnostics().toString());

        started = System.nanoTime();
        ActiveBaseline.Selection active = new ActiveBaseline().fromCheckout(Path.of("."));
        durations.put("ecoreAndMappingValidation", elapsed(started));

        started = System.nanoTime();
        var baseline = new TransformationPlanner().plan(imported.model(), active.mapping());
        var structure = new VerificationSemanticLayer().apply(baseline,
                new VerificationProfileLoader().loadActive(active.mapping())).transformation();
        var instances = new InstancePlanner().plan(imported.model(), active.mapping(), structure);
        var text = new TextBackend().generate("auction", structure, instances);
        durations.put("transformation", elapsed(started));

        started = System.nanoTime();
        var constraints = new ConstraintExtractor().extract(imported.model(), structure, Map.of());
        var profiles = new OclProfileLoader();
        var core = profiles.loadCore();
        var caseProfile = profiles.loadCase(project, Path.of("verification/auction.ocl"));
        var generated = new OclGenerator().generate("auction", structure, constraints,
                List.of(core, caseProfile));
        DirectUseBackend.Result direct = new DirectUseBackend().materialize(
                new TextBackend.GeneratedArtifacts(generated.useModel(), text.initialCommands()), instances);
        var trace = new TraceBuilder().build(imported.model(), active.mapping(), structure, instances);
        ConstraintRegistry registry = ConstraintRegistry.load(direct.system().model(), generated,
                List.of(ConstraintRegistry.profile(ConstraintOrigin.CORE, core),
                        ConstraintRegistry.profile(ConstraintOrigin.CASE, caseProfile)));
        durations.put("oclGenerationAndCompile", elapsed(started));
        assertTrue(direct.structureValid());

        started = System.nanoTime();
        var full = new DefaultVerificationService().runFullVerification(direct.system(), registry, trace);
        durations.put("fullVerification", elapsed(started));
        assertFalse(full.results().stream().anyMatch(result -> result.outcome() == VerificationOutcome.FAIL));

        String artifactSemanticId = instances.objects().stream()
                .filter(object -> object.className().equals("AuctionArtifact"))
                .findFirst().orElseThrow().semanticId();
        var artifactTrace = trace.bySemanticId(artifactSemanticId).stream()
                .filter(record -> record.targetKind().equals("OBJECT")).findFirst().orElseThrow();
        String runtimeKey = "cartago:artifact:market/auction1";
        trace.registerRuntimeKey(artifactTrace.traceId(), runtimeKey);
        RuntimeEvent snapshotEvent = event("phase43-snapshot", 1, runtimeKey, artifactSemanticId, true);
        RuntimeEvent delta = event("phase43-delta", 2, runtimeKey, artifactSemanticId, false);
        Path replay = temporary.resolve("runtime-events.json");
        RuntimeEventCodec codec = new RuntimeEventCodec();
        codec.writeEvents(replay, List.of(delta));
        SyntheticRuntimeConnector connector = new SyntheticRuntimeConnector("phase43-performance",
                new RuntimeSnapshot("phase43-open", Instant.EPOCH, 1, List.of(snapshotEvent), "phase43-open"),
                replay, codec);
        RuntimeVerificationEngine runtimeVerification = new RuntimeVerificationEngine(
                direct.system(), registry, trace);
        RuntimeMirrorService mirror = new RuntimeMirrorService(connector,
                new RuntimeMutationEngine(direct.system(), trace), 8, runtimeVerification);
        mirror.connect(URI.create("synthetic://phase43-performance"));
        connector.replayAll();
        mirror.awaitIdle(Duration.ofSeconds(5));
        QueueMetrics queue = mirror.metrics();
        var eventReport = runtimeVerification.reports().stream()
                .filter(report -> report.event() != null && report.event().eventId().equals(delta.eventId()))
                .findFirst().orElseThrow();
        durations.put("runtimeEventToResult", eventReport.latencyNanos());
        assertTrue(eventReport.latencyNanos() > 0);
        assertTrue(queue.highWatermark() > 0);
        assertEquals(0, queue.failed());
        assertEquals(0, queue.dropped());

        started = System.nanoTime();
        mirror.resync();
        durations.put("authoritativeResync", elapsed(started));
        var artifact = direct.system().state().objectByName(
                artifactTrace.targetUseId().substring("object:".length()));
        assertEquals(BooleanValue.TRUE,
                artifact.state(direct.system().state()).attributeValue(artifact.cls().attribute("open", true)));
        mirror.close();

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        durations.forEach((name, value) -> assertTrue(value > 0, name));
        assertTrue(usedMemory > 0);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schemaVersion", "1.0.0");
        evidence.put("status", "PASS");
        evidence.put("date", "2026-09-24");
        evidence.put("command", "mvn -B -pl use-plugin -Dtest=Phase43PerformanceEvidenceTest test");
        evidence.put("benchmarkPolicy", "OBSERVED_NO_SLA");
        evidence.put("semantics", "Full production pipeline; no checks disabled and no benchmark-specific optimization");
        evidence.put("durationsNanos", durations);
        evidence.put("queue", Map.of("capacity", 8, "depth", queue.depth(),
                "highWatermark", queue.highWatermark(), "processed", queue.processed(),
                "rejected", queue.rejected(), "failed", queue.failed(), "dropped", queue.dropped()));
        evidence.put("usedHeapBytes", usedMemory);
        evidence.put("counts", Map.of("semanticElements", imported.model().elements().size(),
                "targetClasses", structure.classes().size(), "targetObjects", instances.objects().size(),
                "constraints", registry.descriptors().size(), "traceRecords", trace.records().size()));
        evidence.put("activeInputHashes", active.hashes());
        evidence.put("runtimeMappingSha256", sha(Path.of(
                "src/main/resources/org/tzi/use/plugins/jacamo/runtime/jacamo-use-runtime-mapping-v2.json")));
        Path output = Path.of("target/phase43-performance.json");
        Files.createDirectories(output.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), evidence);
        System.out.printf("PHASE43_PERFORMANCE import=%dns validation=%dns transformation=%dns oclCompile=%dns "
                        + "full=%dns eventToResult=%dns highWatermark=%d memory=%d resync=%dns%n",
                durations.get("import"), durations.get("ecoreAndMappingValidation"),
                durations.get("transformation"), durations.get("oclGenerationAndCompile"),
                durations.get("fullVerification"), durations.get("runtimeEventToResult"),
                queue.highWatermark(), usedMemory, durations.get("authoritativeResync"));
    }

    private RuntimeEvent event(String id, long sequence, String runtimeKey, String semanticId, boolean value) {
        return RuntimeEvent.create(id, Instant.ofEpochSecond(sequence), sequence, Dimension.ENVIRONMENT,
                RuntimeEventKind.SET_ATTRIBUTE, runtimeKey, semanticId,
                Map.of("attribute", "open", "valueType", "BOOLEAN", "value", value), null);
    }

    private long elapsed(long started) { return Math.max(1, System.nanoTime() - started); }

    private String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
