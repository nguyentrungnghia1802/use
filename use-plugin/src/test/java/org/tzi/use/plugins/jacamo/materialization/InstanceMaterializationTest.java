package org.tzi.use.plugins.jacamo.materialization;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;

class InstanceMaterializationTest {
    @Test
    void auctionPlanMaterializesEveryElementValueAndResolvedLinkDeterministically() {
        var semantic = new StaticProjectImporter().importProject(Path.of("src/test/resources/auction/auction.jcm")).model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var baseline = new TransformationPlanner().plan(semantic, mapping);
        var structure = new VerificationSemanticLayer().apply(baseline,
                new VerificationProfileLoader().loadV1()).transformation();
        InstancePlan first = new InstancePlanner().plan(semantic, mapping, structure);
        InstancePlan second = new InstancePlanner().plan(semantic, mapping, structure);
        assertEquals(first, second);
        assertEquals(semantic.elements().size(), first.objects().size());
        assertEquals(first.objects().size(), first.objects().stream().map(ObjectPlan::name).collect(java.util.stream.Collectors.toSet()).size());
        ObjectPlan artifact = first.objects().stream().filter(object -> object.className().startsWith("AuctionArtifact")).findFirst().orElseThrow();
        assertEquals(true, ((org.tzi.use.plugins.jacamo.semantic.AttributeValue.Bool) artifact.values().get("open")).value());
        assertTrue(first.links().stream().anyMatch(link -> link.association().equals("MAS_agent_Agent")));
        assertTrue(first.links().stream().anyMatch(link -> link.association().equals("Artifact_obsproperty_ObsProperty")));
        assertEquals(first.links().size(), new HashSet<>(first.links()).size(), "duplicate links are forbidden");
        ObjectPlan belief = first.objects().stream().filter(object -> object.className().equals("Belief")).findFirst().orElseThrow();
        assertFalse(belief.values().containsKey("unresolved"), "unset values must remain undefined");
    }

    @Test
    void textAndDirectBackendsShareOneVerificationPlanAndPassInitialValidation() {
        var semantic = new StaticProjectImporter().importProject(Path.of("src/test/resources/auction/auction.jcm")).model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var baseline = new TransformationPlanner().plan(semantic, mapping);
        var structure = new VerificationSemanticLayer().apply(baseline,
                new VerificationProfileLoader().loadV1()).transformation();
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        TextBackend.GeneratedArtifacts artifacts = new TextBackend().generate("auction", structure, instances);
        assertEquals("46454dd8816ff84283f0901decfce97bf328566ca06a4e24f28924d1d6795bb1", sha256(artifacts.useModel()));
        assertEquals("e0742bab87a53fa4e6529754bdedf114baf463540dfd7a610021a1f19d2294b4", sha256(artifacts.initialCommands()));
        DirectUseBackend.Result direct = new DirectUseBackend().materialize(artifacts, instances);
        assertEquals(instances.objects().size(), direct.system().state().numObjects());
        assertEquals(instances.links().size(), direct.system().state().allLinks().size());
        assertTrue(direct.structureValid(), direct.validationOutput());
        assertTrue(direct.invariantsValid(), direct.validationOutput());
        assertTrue(direct.diagnostics().stream().noneMatch(diagnostic ->
                diagnostic.code().equals("MATERIALIZATION_COMPOSITION_CONFLICT")
                        || diagnostic.code().equals("MATERIALIZATION_REQUIRED_LINK_MISSING")),
                () -> direct.diagnostics().toString());
        assertTrue(direct.diagnostics().stream().noneMatch(diagnostic -> diagnostic.severity() == Severity.ERROR),
                () -> direct.diagnostics().toString());
        var artifact = direct.system().state().allObjects().stream()
                .filter(object -> object.cls().name().startsWith("AuctionArtifact")).findFirst().orElseThrow();
        assertEquals("true", artifact.state(direct.system().state()).attributeValue("open").toString());
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
}
