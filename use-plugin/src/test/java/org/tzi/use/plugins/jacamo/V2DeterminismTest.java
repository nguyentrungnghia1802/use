package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.constraint.ConstraintExtractor;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.MappingModel;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;

class V2DeterminismTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void exactV2InputsProduceIdenticalSemanticAndGeneratedOutputs() throws Exception {
        Snapshot first = run();
        Snapshot second = run();

        assertEquals(first.inputFingerprint(), second.inputFingerprint());
        assertEquals(first.pluginVersion(), second.pluginVersion());
        assertEquals(first.semanticIds(), second.semanticIds(), "SemanticId order must be stable");
        assertEquals(first.outputHashes(), second.outputHashes(),
                ".use, .cmd, trace, generated OCL and provenance must be byte-identical");
        assertEquals(first.diagnostics(), second.diagnostics(), "diagnostic ordering must be stable");
        assertEquals(first.mappingDecisions(), second.mappingDecisions(), "mapping decisions must be stable");
        assertFalse(first.semanticIds().isEmpty());
        System.out.printf("PHASE43_DETERMINISM input=%s output=%s semanticIds=%d diagnostics=%d decisions=%d%n",
                first.inputFingerprint(), fingerprint(first.outputHashes()), first.semanticIds().size(),
                first.diagnostics().size(), first.mappingDecisions().size());
    }

    private Snapshot run() throws Exception {
        Path project = Path.of("src/test/resources/auction");
        var imported = new StaticProjectImporter().importProject(project.resolve("auction.jcm"));
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var structure = new VerificationSemanticLayer().apply(
                new TransformationPlanner().plan(imported.model(), mapping),
                new VerificationProfileLoader().loadActive(mapping)).transformation();
        var instances = new InstancePlanner().plan(imported.model(), mapping, structure);
        var generated = new TextBackend().generate("auction", structure, instances);
        var profiles = new OclProfileLoader();
        var constraints = new ConstraintExtractor().extract(imported.model(), structure, Map.of());
        var ocl = new OclGenerator().generate("auction", structure, constraints,
                List.of(profiles.loadCore(), profiles.loadCase(project, Path.of("verification/auction.ocl"))));
        var trace = new TraceBuilder().build(imported.model(), mapping, structure, instances);

        TreeMap<String, String> outputs = new TreeMap<>();
        outputs.put("cmd", sha(generated.initialCommands()));
        outputs.put("generatedOcl", sha(ocl.useModel()));
        outputs.put("oclProvenance", sha(ocl.provenanceManifest()));
        outputs.put("trace", sha(JSON.writeValueAsString(trace.records())));
        outputs.put("use", sha(generated.useModel()));

        List<String> semanticIds = imported.model().elements().stream()
                .map(element -> element.id().value()).toList();
        List<String> diagnostics = imported.diagnostics().stream().map(Object::toString).toList();
        List<String> decisions = mappingDecisions(mapping);
        decisions = new ArrayList<>(decisions);
        decisions.add("TARGET_CLASSES=" + structure.classes());
        decisions.add("TARGET_ATTRIBUTES=" + structure.attributes());
        decisions.add("TARGET_ASSOCIATIONS=" + structure.associations());
        decisions.add("TARGET_OPERATIONS=" + structure.operations());
        decisions.add("TARGET_ORDERS=" + structure.orderProjections());
        decisions = List.copyOf(decisions);

        String pluginVersion = JSON.readTree(Path.of("compatibility.json").toFile())
                .path("plugin").path("version").asText();
        return new Snapshot(inputFingerprint(project), pluginVersion, semanticIds,
                Collections.unmodifiableSortedMap(outputs), diagnostics, decisions);
    }

    private List<String> mappingDecisions(MappingModel mapping) {
        List<String> result = new ArrayList<>();
        mapping.classes().forEach(value -> result.add("CLASS=" + value));
        mapping.attributes().forEach(value -> result.add("ATTRIBUTE=" + value));
        mapping.associations().forEach(value -> result.add("ASSOCIATION=" + value));
        mapping.inheritance().forEach(value -> result.add("INHERITANCE=" + value));
        mapping.projections().forEach(value -> result.add("PROJECTION=" + value));
        mapping.enums().forEach(value -> result.add("ENUM=" + value.name() + "|" + value.literals()));
        mapping.orderProjections().forEach(value -> result.add("ORDER=" + value));
        return List.copyOf(result);
    }

    private String inputFingerprint(Path project) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        List<Path> roots = List.of(
                Path.of("Core/Metamodel/version-2/jacamo_v2_complete.ecore"),
                Path.of("Core/Mapping/version-2/jacamo-use-mapping-v2.json"),
                Path.of("Core/Mapping/version-2/jacamo-use-mapping-v2.schema.json"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core-v2.ocl"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/verification/jacamo-verification-profile-v2.json"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/runtime/jacamo-use-runtime-mapping-v2.json"),
                Path.of("pom.xml"));
        List<Path> files = new ArrayList<>(roots);
        try (var stream = Files.walk(project)) {
            files.addAll(stream.filter(Files::isRegularFile).toList());
        }
        for (Path file : files.stream().sorted(java.util.Comparator.comparing(Path::toString)).toList()) {
            digest.update(file.toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String fingerprint(Map<String, String> values) throws Exception {
        return sha(new TreeMap<>(values).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("\n")));
    }

    private record Snapshot(String inputFingerprint, String pluginVersion, List<String> semanticIds,
                            Map<String, String> outputHashes, List<String> diagnostics,
                            List<String> mappingDecisions) { }
}
