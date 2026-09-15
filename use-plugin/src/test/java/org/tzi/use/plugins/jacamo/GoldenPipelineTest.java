package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.constraint.ConstraintExtractor;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.trace.TraceStore;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;

class GoldenPipelineTest {
    @TempDir Path temporary;

    @Test
    void auctionArtifactsMatchReviewedGoldenDigests() throws Exception {
        Path fixture = Path.of("src/test/resources/auction");
        Path project = Files.createDirectory(temporary.resolve("auction")).toRealPath();
        // Fixed LF fixture bytes keep provenance hashes independent of Git autocrlf.
        try (var files = Files.walk(fixture)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path target = project.resolve(fixture.relativize(file));
                Files.createDirectories(target.getParent());
                Files.writeString(target, Files.readString(file).replace("\r\n", "\n"));
            }
        }
        var imported = new StaticProjectImporter().importProject(project.resolve("auction.jcm"));
        assertTrue(imported.success(), () -> imported.diagnostics().toString());
        var semantic = imported.model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var structure = new VerificationSemanticLayer().apply(new TransformationPlanner().plan(semantic, mapping),
                new VerificationProfileLoader().loadV1()).transformation();
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        var artifacts = new TextBackend().generate("auction", structure, instances);
        var constraints = new ConstraintExtractor().extract(semantic, structure, Map.of());
        var profiles = new OclProfileLoader();
        var ocl = new OclGenerator().generate("auction", structure, constraints,
                List.of(profiles.loadCore(), profiles.loadCase(project, Path.of("verification/auction.ocl"))));
        Path output = Files.createDirectories(Path.of("target/phase13-golden"));
        new TraceStore().write(output.resolve("trace.json"),
                new TraceBuilder().build(semantic, mapping, structure, instances));
        var golden = new Properties();
        try (var input = Files.newInputStream(Path.of("src/test/resources/golden/auction-sha256.properties"))) {
            golden.load(input);
        }
        Map<String, String> actual = Map.of(
                "auction.use", artifacts.useModel(), "auction.cmd", artifacts.initialCommands(),
                "auction-ocl.use", ocl.useModel(),
                "trace.json", Files.readString(output.resolve("trace.json")),
                "diagnostics.txt", imported.diagnostics().toString());
        for (var entry : actual.entrySet()) {
            String normalized = entry.getValue().replace("\\\\", "/").replace('\\', '/')
                    .replace(project.toString().replace('\\', '/'), "<auction>").replace("\r\n", "\n");
            Files.writeString(output.resolve(entry.getKey()), normalized);
        }
        assertAll(actual.keySet().stream().sorted().map(name -> () -> {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readString(output.resolve(name)).getBytes(StandardCharsets.UTF_8)));
            assertEquals(golden.getProperty(name), digest, name);
        }));
    }
}
