package org.tzi.use.plugins.jacamo.verification.profile;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;

class VerificationSemanticLayerTest {
    @Test
    void profileIsExplicitAndDoesNotMutateFrozenBaselinePlan() {
        var semantic = new StaticProjectImporter().importProject(Path.of("src/test/resources/auction/auction.jcm")).model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
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

    private org.tzi.use.plugins.jacamo.mapping.TargetClassSpec classSpec(
            org.tzi.use.plugins.jacamo.mapping.TransformationPlan plan, String name) {
        return plan.classes().stream().filter(spec -> spec.name().equals(name)).findFirst().orElseThrow();
    }

    private org.tzi.use.plugins.jacamo.mapping.TargetAssociationSpec association(
            org.tzi.use.plugins.jacamo.mapping.TransformationPlan plan, String rule) {
        return plan.associations().stream().filter(spec -> spec.ruleId().equals(rule)).findFirst().orElseThrow();
    }
}
