package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.tzi.use.plugins.jacamo.constraint.ConstraintExtractor;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMappingLoader;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.tzi.use.plugins.jacamo.verification.ConstraintRegistry;
import org.tzi.use.plugins.jacamo.verification.DefaultVerificationService;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;

class MultiCaseV2PipelineTest {
    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("auction", Path.of("src/test/resources/auction/auction.jcm"),
                        Path.of("verification/auction.ocl"), "AuctionArtifact"),
                Arguments.of("counter-team", Path.of("src/test/resources/counter-team/counter-team.jcm"),
                        Path.of("verification/counter.ocl"), "Counter"));
    }

    @ParameterizedTest(name = "{0} uses the same V2 production pipeline")
    @MethodSource("cases")
    void sameProductionPipelineAcceptsBothCases(String modelName, Path entry, Path profile,
                                                String expectedConcreteClass) {
        var semantic = new StaticProjectImporter().importProject(entry).model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var base = new TransformationPlanner().plan(semantic, mapping);
        var structure = new VerificationSemanticLayer().apply(base,
                new VerificationProfileLoader().loadActive(mapping)).transformation();
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        var profiles = new OclProfileLoader();
        var core = profiles.loadCore();
        var caseProfile = profiles.loadCase(entry.toAbsolutePath().getParent(), profile);
        var constraints = new ConstraintExtractor().extract(semantic, structure, Map.of());
        var generatedOcl = new OclGenerator().generate(modelName, structure, constraints,
                List.of(core, caseProfile));
        var generated = new TextBackend().generate(modelName, structure, instances);
        var direct = new DirectUseBackend().materialize(
                new TextBackend.GeneratedArtifacts(generatedOcl.useModel(), generated.initialCommands()), instances);
        var trace = new TraceBuilder().build(semantic, mapping, structure, instances);
        var registry = ConstraintRegistry.load(direct.system().model(), generatedOcl,
                List.of(ConstraintRegistry.profile(ConstraintOrigin.CORE, core),
                        ConstraintRegistry.profile(ConstraintOrigin.CASE, caseProfile)));

        assertTrue(direct.structureValid(), direct.validationOutput());
        assertTrue(direct.invariantsValid(), direct.validationOutput());
        assertTrue(structure.classes().stream().anyMatch(value -> value.name().equals(expectedConcreteClass)));
        assertEquals(generatedOcl, new OclGenerator().generate(modelName, structure, constraints,
                List.of(core, caseProfile)));
        assertFalse(trace.records().isEmpty());
        assertTrue(registry.descriptors().stream().anyMatch(value -> value.origin() == ConstraintOrigin.CASE));
        var report = new DefaultVerificationService().runFullVerification(direct.system(), registry, trace);
        assertFalse(report.results().stream().anyMatch(value -> value.outcome() == VerificationOutcome.ERROR
                || value.outcome() == VerificationOutcome.FAIL), report.results().toString());

        var runtimeMapping = new RuntimeMappingLoader().loadDefault();
        assertEquals("STRUCTURAL_MAPPING_V2", runtimeMapping.targetBaseline());
        assertDoesNotThrow(() -> new RuntimeMutationEngine(direct.system(), trace));
    }

    @Test
    void activeProductionResourcesContainNoCaseDispatchOrCaseRuntimeIdentity() throws Exception {
        List<Path> inputs = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            inputs.addAll(files.filter(Files::isRegularFile).toList());
        }
        inputs.addAll(List.of(
                Path.of("Core/Metamodel/version-2/jacamo_v2_complete.ecore"),
                Path.of("Core/Mapping/version-2/jacamo-use-mapping-v2.json"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core-v2.ocl"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/runtime/jacamo-use-runtime-mapping-v2.json"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/verification/jacamo-verification-profile-v2.json")));
        List<String> forbidden = List.of("auction1", "auctioneer", "placebid", "closeauction",
                "counter-team", "counterteam", "counter1", "guardedset", "setvalue");
        List<String> findings = new ArrayList<>();
        for (Path input : inputs) {
            String text = Files.readString(input, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
            for (String token : forbidden) if (text.contains(token)) findings.add(input + ":" + token);
        }
        assertTrue(findings.isEmpty(), "case-specific production content: " + findings);
    }
}
