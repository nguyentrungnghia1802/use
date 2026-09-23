package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.trace.TraceStore;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;

/** Explicit historical capture only; never changes production's default selection. */
class PreMigrationBaselineTest {
    @Test void auctionHistoricalStaticBaseline() throws Exception { capture("auction", "auction.jcm"); }
    @Test void counterHistoricalStaticBaseline() throws Exception { capture("counter-team", "counter-team.jcm"); }

    private void capture(String fixture, String entry) throws Exception {
        Path core = Path.of("Core");
        var mapping = new MappingLoader().load(
                core.resolve("Mapping/version-1/jacamo-use-mapping-v1.json"),
                core.resolve("Mapping/version-1/jacamo-use-mapping.schema.json"),
                core.resolve("Metamodel/version-1/JaCaMo-Metamodel.ecore"),
                core.resolve("Mapping/version-1/freeze-manifest.json"));
        var imported = new StaticProjectImporter().importProject(Path.of("src/test/resources", fixture, entry));
        assertTrue(imported.success(), () -> imported.diagnostics().toString());
        var model = imported.model();
        var plan = new VerificationSemanticLayer().apply(new TransformationPlanner().plan(model, mapping),
                new VerificationProfileLoader().loadV1()).transformation();
        var instances = new InstancePlanner().plan(model, mapping, plan);
        var artifacts = new TextBackend().generate(fixture, plan, instances);
        var direct = new DirectUseBackend().materialize(artifacts, instances);
        assertTrue(direct.structureValid(), direct.validationOutput());
        assertTrue(direct.invariantsValid(), direct.validationOutput());
        var repeated = new TextBackend().generate(fixture, plan, instances);
        assertEquals(artifacts, repeated);
        Path output = Files.createDirectories(Path.of("target/phase29-historical-baseline", fixture));
        Files.writeString(output.resolve("model.use"), artifacts.useModel());
        Files.writeString(output.resolve("initial-state.cmd"), artifacts.initialCommands());
        new TraceStore().write(output.resolve("trace.json"), new TraceBuilder().build(model, mapping, plan, instances));
        var hashes = new java.util.TreeMap<String, String>();
        for (String name : java.util.List.of("model.use", "initial-state.cmd", "trace.json"))
            hashes.put(name, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(output.resolve(name)))));
        Files.writeString(output.resolve("manifest.json"), new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValueAsString(Map.of("status", "HISTORICAL_V1_EXPLICIT_PATH_STATIC_ONLY",
                        "fixture", fixture, "mappingId", mapping.mappingId(), "sha256", hashes,
                        "scope", "USE compilation, initial structure/invariants, deterministic text, source trace; no V2 or runtime claim")) + "\n");
    }
}
