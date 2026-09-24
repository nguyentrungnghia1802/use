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
    void auctionPlanIsDeterministicAndGeneratedUseCompiles() {
        var semantic = new StaticProjectImporter().importProject(Path.of("src/test/resources/auction/auction.jcm")).model();
        MappingModel mapping = new MappingLoader().loadCanonical(Path.of("."));
        TransformationPlan first = new TransformationPlanner().plan(semantic, mapping);
        TransformationPlan second = new TransformationPlanner().plan(semantic, mapping);
        assertEquals(first, second);
        assertEquals(mapping.classes().size() + 1, first.classes().size(), "V2 structural classes plus concrete Artifact type");
        TargetClassSpec concrete = first.classes().stream().filter(spec -> spec.name().startsWith("AuctionArtifact")).findFirst().orElseThrow();
        assertEquals("Artifact", concrete.superclasses().getFirst());
        assertTrue(first.attributes().stream().anyMatch(attribute -> attribute.owner().equals(concrete.name())
                && attribute.name().equals("open") && attribute.type().equals("Boolean") && attribute.ruleId().equals("VP002")));
        assertTrue(first.operations().stream().anyMatch(operation -> operation.owner().equals(concrete.name())
                && operation.name().equals("placeBid") && operation.ruleId().equals("VP003")));
        assertEquals(mapping.associations().stream().filter(a -> !a.reverse()).count(), first.associations().size());
        assertTrue(first.associations().stream().anyMatch(association -> association.name().equals("Action_operation_Operation")));
        assertTrue(first.associations().stream().anyMatch(association -> association.name().equals("Belief_property_Property")));
        assertTrue(first.associations().stream().anyMatch(association -> association.name().equals("AGoal_organizationalGoal_OGoal")));
        assertTrue(first.classes().stream().anyMatch(spec -> spec.name().equals("Norm")), "VP007 remains structural");
        assertTrue(first.classes().stream().allMatch(spec -> !spec.sourceIdentity().isBlank() && !spec.ruleId().isBlank()));

        String generated = new StructuralUseGenerator().generate("auction", first);
        StringWriter errors = new StringWriter();
        var model = USECompiler.compileSpecification(new ByteArrayInputStream(generated.getBytes(StandardCharsets.UTF_8)),
                "auction.use", temporary.resolve("auction.use").toUri(), new PrintWriter(errors), new ModelFactory());
        assertNotNull(model, () -> errors + "\n" + generated);
        assertEquals(first.classes().size() + first.orderProjections().size(), model.classes().size());
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
        assertTrue(plan.classes().stream().anyMatch(spec -> spec.name().equals("Property")),
                "VP002 fallback must retain the structural mapping");
    }
}
