package org.tzi.use.plugins.jacamo.codegrounded;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionStatus;

/** Acceptance evidence for the verification-oriented native projection profile. */
class NativeProjectionProfileTest {
    @Test
    void autoReducesSchemaAndStateWhileFullRemainsAvailable() throws Exception {
        var full = new CodeGroundedNativePipeline().build(CodeGroundedTestFixtures.helloSnapshot(),
                NativeProjectionMode.FULL);
        var auto = new CodeGroundedNativePipeline().build(CodeGroundedTestFixtures.helloSnapshot(),
                NativeProjectionMode.AUTO);
        var defaultResult = new CodeGroundedNativePipeline().build(CodeGroundedTestFixtures.helloSnapshot());

        assertEquals(NativeProjectionMode.AUTO, auto.projectionMode());
        assertEquals(NativeProjectionMode.AUTO, defaultResult.projectionMode());
        assertEquals(full.source().revision(), auto.source().revision(),
                "projection profile must not change the extracted semantic source model");
        System.out.printf("NATIVE_PROJECTION_COUNTS AUTO classes=%d objects=%d FULL classes=%d objects=%d%n",
                auto.model().model().classes().size(), auto.state().system().state().numObjects(),
                full.model().model().classes().size(), full.state().system().state().numObjects());
        assertTrue(auto.model().model().classes().size() < full.model().model().classes().size());
        assertTrue(auto.state().system().state().numObjects() < full.state().system().state().numObjects());
        assertFalse(auto.model().model().classes().stream()
                .anyMatch(value -> value.name().equals("A17PlanOrderEntry")));
        assertFalse(auto.model().model().classes().stream()
                .anyMatch(value -> value.name().equals("A19BodyOrderEntry")));
        assertEquals(NativeProjectionStatus.EVIDENCE_ONLY,
                auto.projectionProfile().conceptStatuses().get("ExactBindingEvidence"));
        for (String concept : List.of("WorkspaceDeclaration", "ArtifactDeclaration", "OrganizationDeployment",
                "GroupDeployment", "SchemeDeployment", "InstitutionDeployment", "AgentProgram", "Trigger",
                "Action", "Belief", "AgentGoal", "BeliefRule", "Signal", "Guard", "Operation"))
            assertEquals(NativeProjectionStatus.EVIDENCE_ONLY,
                    auto.projectionProfile().conceptStatuses().get(concept), concept);
        assertEquals(NativeProjectionStatus.PROFILE_EXCLUDED,
                auto.projectionProfile().conceptStatuses().get("A17PlanOrderEntry"));
        assertTrue(auto.trace().records().stream().anyMatch(value -> value.targetKind().equals("ProjectionStatus")
                && value.targetIdentity().equals("projection:ExactBindingEvidence")
                && value.diagnostics().contains("STATUS=EVIDENCE_ONLY")));
        assertTrue(full.model().model().classes().stream()
                .anyMatch(value -> value.name().equals("A17PlanOrderEntry")));
        assertTrue(full.model().model().classes().stream()
                .anyMatch(value -> value.name().equals("A19BodyOrderEntry")));
    }
}
