package org.tzi.use.plugins.jacamo.verification.profile;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;

class VerificationSemanticLayerTest {
    @Test
    void historicalProfileIsExplicitAndDoesNotMutateFrozenBaselinePlan() {
        var semantic = new StaticProjectImporter().importProject(Path.of("src/test/resources/auction/auction.jcm")).model();
        var mapping = historical();
        var baseline = new TransformationPlanner().plan(semantic, mapping);
        var profile = new VerificationProfileLoader().loadV1();
        assertEquals(mapping.mappingId(), profile.baselineMappingId());
        assertTrue(profile.decisions().stream().allMatch(decision ->
                decision.classification() == VerificationProfile.Classification.OUR_EXT
                        || decision.classification() == VerificationProfile.Classification.SEMANTIC_CLARIFICATION));

        var effective = new VerificationSemanticLayer().apply(baseline, profile);
        for (String name : java.util.List.of("Norm", "Group", "Role", "Scheme")) {
            assertTrue(classSpec(baseline, name).superclasses().contains("Organisation"));
            assertFalse(classSpec(effective.transformation(), name).superclasses().contains("Organisation"));
        }
        assertEquals("1", association(baseline, "R047").secondEnd().multiplicity());
        assertEquals("0..1", association(effective.transformation(), "R047").secondEnd().multiplicity());
        assertEquals(baseline.classes().size(), effective.transformation().classes().size());
        assertEquals(baseline.associations().size(), effective.transformation().associations().size());
    }

    @Test void activeV2ProfilePreservesTheWholePlanAndRejectsCrossBaselineUse() {
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var baseline = new TransformationPlanner().structuralPlan(mapping);
        var loader = new VerificationProfileLoader();
        var profile = loader.loadActive(mapping);
        assertEquals("JACAMO_VERIFICATION_PROFILE_V2", profile.profileId());
        assertTrue(profile.decisions().isEmpty());
        var layer = new VerificationSemanticLayer();
        assertEquals(baseline, layer.apply(baseline, mapping, profile).transformation());
        assertFalse(baseline.orderProjections().isEmpty());
        assertFalse(baseline.enums().isEmpty());
        assertFalse(baseline.classes().stream().anyMatch(c -> c.name().equals("Organisation")));
        assertTrue(baseline.classes().stream().filter(c -> java.util.List.of("Norm", "Group", "Role", "Scheme").contains(c.name()))
                .allMatch(c -> c.superclasses().isEmpty()));
        assertThrows(VerificationProfileException.class, () -> layer.apply(baseline, mapping, loader.loadV1()));
        assertThrows(VerificationProfileException.class, () -> loader.loadActive(historical()));
    }

    private org.tzi.use.plugins.jacamo.mapping.MappingModel historical() {
        return new MappingLoader().load(Path.of("Core/Mapping/version-1/jacamo-use-mapping-v1.json"),
                Path.of("Core/Mapping/version-1/jacamo-use-mapping.schema.json"),
                Path.of("Core/Metamodel/version-1/JaCaMo-Metamodel.ecore"), Path.of("Core/Mapping/version-1/freeze-manifest.json"));
    }

    private org.tzi.use.plugins.jacamo.mapping.TargetClassSpec classSpec(
            org.tzi.use.plugins.jacamo.mapping.TransformationPlan plan, String name) {
        return plan.classes().stream().filter(spec -> spec.name().equals(name)).findFirst().orElseThrow();
    }

    private org.tzi.use.plugins.jacamo.mapping.TargetAssociationSpec association(
            org.tzi.use.plugins.jacamo.mapping.TransformationPlan plan, String rule) {
        return plan.associations().stream().filter(spec -> spec.ruleId().equals(rule)).findFirst().orElseThrow();
    }
}
