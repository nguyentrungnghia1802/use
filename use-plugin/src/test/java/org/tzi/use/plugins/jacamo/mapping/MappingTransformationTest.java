package org.tzi.use.plugins.jacamo.mapping;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.uml.mm.ModelFactory;

class MappingTransformationTest {
    @TempDir Path temporary;

    @Test
    void canonicalMappingPassesSchemaFingerprintAndEcoreIdentityValidation() {
        MappingModel mapping = new MappingLoader().loadCanonical(Path.of("."));
        assertEquals("1.1.0", mapping.schemaVersion());
        assertEquals(37, mapping.classes().size());
        assertEquals(67, mapping.attributes().size());
        assertEquals(63, mapping.associations().size());
        assertEquals(14, mapping.inheritance().size());
        assertEquals(7, mapping.projections().size());
    }

    @Test
    void invalidSchemaAndFingerprintBlockLoading() throws Exception {
        Path invalid = temporary.resolve("invalid.json");
        Files.writeString(invalid, "{}");
        MappingException schema = assertThrows(MappingException.class, () -> new MappingLoader().load(
                invalid, Path.of("Core/Mapping/jacamo-use-mapping.schema.json"),
                Path.of("Core/Metamodel/JaCaMo-Metamodel.ecore"), Path.of("Core/Mapping/freeze-manifest.json")));
        assertEquals("MAPPING_SCHEMA_INVALID", schema.code());

        Path changedEcore = temporary.resolve("changed.ecore");
        Files.writeString(changedEcore, Files.readString(Path.of("Core/Metamodel/JaCaMo-Metamodel.ecore")) + "\n");
        MappingException fingerprint = assertThrows(MappingException.class, () -> new MappingLoader().load(
                Path.of("Core/Mapping/jacamo-use-mapping-v1.json"),
                Path.of("Core/Mapping/jacamo-use-mapping.schema.json"), changedEcore,
                Path.of("Core/Mapping/freeze-manifest.json")));
        assertEquals("MAPPING_ECORE_MISMATCH", fingerprint.code());

        Path invalidSource = temporary.resolve("invalid-source.json");
        Files.writeString(invalidSource, Files.readString(Path.of("Core/Mapping/jacamo-use-mapping-v1.json"))
                .replaceFirst("dSML4JaCaMo::MAS#Name", "dSML4JaCaMo::MAS#NotDeclared"));
        MappingException source = assertThrows(MappingException.class, () -> new MappingLoader().load(
                invalidSource, Path.of("Core/Mapping/jacamo-use-mapping.schema.json"),
                Path.of("Core/Metamodel/JaCaMo-Metamodel.ecore"), Path.of("Core/Mapping/freeze-manifest.json")));
        assertEquals("MAPPING_SOURCE_INVALID", source.code());
    }

    @Test
    void auctionPlanIsDeterministicAndGeneratedUseCompiles() {
        var semantic = new StaticProjectImporter().importProject(Path.of("src/test/resources/auction/auction.jcm")).model();
        MappingModel mapping = new MappingLoader().loadCanonical(Path.of("."));
        TransformationPlan first = new TransformationPlanner().plan(semantic, mapping);
        TransformationPlan second = new TransformationPlanner().plan(semantic, mapping);
        assertEquals(first, second);
        assertEquals(38, first.classes().size(), "37 baseline classes plus concrete Artifact type");
        TargetClassSpec concrete = first.classes().stream().filter(spec -> spec.name().startsWith("AuctionArtifact")).findFirst().orElseThrow();
        assertEquals("Artifact", concrete.superclasses().getFirst());
        assertTrue(first.attributes().stream().anyMatch(attribute -> attribute.owner().equals(concrete.name())
                && attribute.name().equals("open") && attribute.type().equals("Boolean") && attribute.ruleId().equals("VP002")));
        assertTrue(first.operations().stream().anyMatch(operation -> operation.owner().equals(concrete.name())
                && operation.name().equals("placeBid") && operation.ruleId().equals("VP003")));
        assertEquals(63, first.associations().size());
        assertTrue(first.associations().stream().anyMatch(association -> association.name().equals("ExternalAction_operation_AbsOperation")));
        assertTrue(first.associations().stream().anyMatch(association -> association.name().equals("ObsProperty_obsproperty_Belief")));
        assertTrue(first.associations().stream().anyMatch(association -> association.name().equals("OGoal_OGoalToGoal_Goal")));
        assertTrue(first.classes().stream().anyMatch(spec -> spec.name().equals("Norm")), "VP007 remains structural");
        assertTrue(first.classes().stream().allMatch(spec -> !spec.sourceIdentity().isBlank() && !spec.ruleId().isBlank()));

        String generated = new StructuralUseGenerator().generate("auction", first);
        StringWriter errors = new StringWriter();
        var model = USECompiler.compileSpecification(new ByteArrayInputStream(generated.getBytes(StandardCharsets.UTF_8)),
                "auction.use", temporary.resolve("auction.use").toUri(), new PrintWriter(errors), new ModelFactory());
        assertNotNull(model, () -> errors + "\n" + generated);
        assertEquals(38, model.classes().size());
    }

    @Test
    void namingEscapesReservedWordsAndResolvesCollisionsDeterministically() {
        UseNameAllocator allocator = new UseNameAllocator();
        assertEquals("ecore_class", allocator.allocate("class", "one"));
        String first = allocator.allocate("a-b", "source-one");
        String collision = allocator.allocate("a b", "source-two");
        assertEquals("a_b", first);
        assertTrue(collision.matches("a_b_[0-9a-f]{8}"));
        assertEquals(collision, new UseNameAllocator().allocateAll(
                java.util.Map.of("source-one", "a-b", "source-two", "a b")).get("source-two"));
    }

    @Test
    void artifactTypeCollisionsAreStableAndUnsupportedPropertyStaysStructural() throws Exception {
        Files.createDirectories(temporary.resolve("src/env/a"));
        Files.createDirectories(temporary.resolve("src/env/b"));
        Files.writeString(temporary.resolve("app.jcm"), "mas app { workspace w { artifact x:a.Same() artifact y:b.Same() } java-path:src/env }\n");
        Files.writeString(temporary.resolve("src/env/a/Same.java"),
                "package a; class Same extends Artifact { void init(){ defineObsProperty(\"dynamic\", new Object()); } }");
        Files.writeString(temporary.resolve("src/env/b/Same.java"), "package b; class Same extends Artifact { }");
        var semantic = new StaticProjectImporter().importProject(temporary.resolve("app.jcm")).model();
        TransformationPlan plan = new TransformationPlanner().plan(semantic, new MappingLoader().loadCanonical(Path.of(".")));
        var generated = plan.classes().stream().filter(spec -> spec.ruleId().equals("VP001")).toList();
        assertEquals(2, generated.size());
        assertTrue(generated.stream().anyMatch(spec -> spec.name().equals("Same")));
        assertTrue(generated.stream().anyMatch(spec -> spec.name().matches("Same_[0-9a-f]{8}")));
        assertTrue(plan.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("VP002_UNSUPPORTED")));
        assertTrue(plan.classes().stream().anyMatch(spec -> spec.name().equals("ObsProperty")),
                "VP002 fallback must retain the structural mapping");
    }
}
