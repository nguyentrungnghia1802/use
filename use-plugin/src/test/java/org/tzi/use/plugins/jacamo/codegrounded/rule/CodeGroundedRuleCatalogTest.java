package org.tzi.use.plugins.jacamo.codegrounded.rule;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.junit.jupiter.api.Test;

class CodeGroundedRuleCatalogTest {
    private static final List<String> IMPLEMENTED_PHASE_1_TO_6 = List.of(
            "J01", "J02", "J03", "J04", "J05", "J06", "J07", "J08", "J09", "J10", "J11",
            "A01", "A02", "A03", "A04", "A05", "A06", "A07", "A08", "A09", "A10", "A11",
            "A16", "A17", "A18", "A19", "A20", "A21", "A22",
            "C01", "C02", "C03", "C04", "C05", "C07", "C09", "C10", "C11", "C12",
            "C13", "C14", "C15", "C16", "C17", "C18", "C19", "C20",
            "M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08", "M09", "M10", "M11",
            "M12", "M13", "M14", "M15", "M16", "M17", "M18", "M19", "M20", "M21", "M22",
            "M23", "M24", "M25", "M26", "M27", "M28", "M29", "M30", "M31", "M32", "M33",
            "M34", "M35", "M36", "M37", "M38", "M39", "M40", "M41", "M42", "M43",
            "X01", "X02", "X03", "X04", "X05", "X06", "X07", "X08", "X09");

    @Test void catalogIsCompleteUniqueAndDeterministic() {
        var first = new CodeGroundedRuleCatalog().rules();
        var second = new CodeGroundedRuleCatalog().rules();
        assertEquals(105, first.size());
        assertEquals(first, second);
        assertEquals(105, first.stream().map(CodeGroundedRule::ruleId).distinct().count());
        assertEquals(Map.of('J', 11L, 'A', 22L, 'C', 20L, 'M', 43L, 'X', 9L),
                first.stream().collect(Collectors.groupingBy(rule -> rule.ruleId().charAt(0), Collectors.counting())));
        assertEquals(expectedIds(), first.stream().map(CodeGroundedRule::ruleId).toList());
    }

    @Test void everyRuleCarriesAuthorityTargetFidelityCapabilityAndDiagnosticPolicy() {
        for (CodeGroundedRule rule : new CodeGroundedRuleCatalog().rules()) {
            assertNotNull(rule.sourceAuthority(), rule.ruleId());
            assertNotEquals(EvidenceAuthority.UNAVAILABLE, rule.sourceAuthority(), rule.ruleId());
            assertFalse(rule.sourceKindFqcn().isBlank(), rule.ruleId());
            assertFalse(rule.targetUseKind().isBlank(), rule.ruleId());
            assertNotNull(rule.fidelity(), rule.ruleId());
            assertNotNull(rule.capabilityStatus(), rule.ruleId());
            assertFalse(rule.diagnosticPolicy().isBlank(), rule.ruleId());
        }
        var catalog = new CodeGroundedRuleCatalog();
        assertEquals(ImplementationStatus.IMPLEMENTED, catalog.require("A20").implementationStatus());
        assertEquals(ImplementationStatus.UNAVAILABLE_IN_AUDITED_API,
                catalog.require("C08").implementationStatus());
        assertEquals(CapabilityStatus.UNAVAILABLE, catalog.require("C08").capabilityStatus());
        assertEquals(ImplementationStatus.EXPLICITLY_UNSUPPORTED, catalog.require("C06").implementationStatus());
        assertEquals(CapabilityStatus.PARTIAL, catalog.require("C06").capabilityStatus());
    }

    @Test void phase6ClosurePinsAllCrossRulesAndKeepsUnavailableRulesExplicit() {
        assertEquals(IMPLEMENTED_PHASE_1_TO_6, new CodeGroundedRuleCatalog().rules().stream()
                .filter(rule -> rule.implementationStatus() == ImplementationStatus.IMPLEMENTED)
                .map(CodeGroundedRule::ruleId).toList());
    }

    @Test void auditedMetadataUsesSnapshotAndOfficialGuardCardinalityAndSignalTypes() {
        var catalog = new CodeGroundedRuleCatalog();
        assertEquals("jason.asSyntax.Literal+cartago.ArtifactObsProperty (C09 snapshot)", catalog.require("X02").sourceKindFqcn());
        assertEquals("cartago.IArtifactGuard", catalog.require("C07").sourceKindFqcn());
        assertEquals("cartago.Tuple", catalog.require("C11").sourceKindFqcn());
        for (String id : List.of("M15", "M16", "M17"))
            assertTrue(catalog.require(id).sourceKindFqcn().startsWith("moise.os.Cardinality"));
        assertEquals(org.jacamo.bridge.contract.semantic.Fidelity.UNKNOWN, catalog.require("C08").fidelity());
    }

    @Test void relationMetadataNamesActualOfficialApisAndNativeOperationsRemainConditional() throws Exception {
        var catalog = new CodeGroundedRuleCatalog();
        var methods = Map.of(
                "C13", cartago.CartagoEnvironment.class.getMethod("getRootWSP"),
                "C14", cartago.ArtifactId.class.getMethod("getWorkspaceId"),
                "C18", cartago.OpDescriptor.class.getMethod("getGuard"),
                "C19", cartago.ICartagoController.class.getMethod("getCurrentAgents"),
                "C20", cartago.ICartagoLogger.class.getMethod("artifactFocussed", long.class,
                        cartago.AgentId.class, cartago.ArtifactId.class, cartago.IEventFilter.class));
        methods.forEach((id, method) -> assertEquals(method.getDeclaringClass().getName() + "." + method.getName(),
                catalog.require(id).sourceKindFqcn(), id));
        assertEquals(org.jacamo.bridge.contract.semantic.Fidelity.CONDITIONAL, catalog.require("C06").fidelity());
        assertEquals(CapabilityStatus.PARTIAL, catalog.require("C06").capabilityStatus());
    }

    @Test void runtimeEvidenceIsNotAdvertisedAsMaterializedJasonState() throws Exception {
        var catalog = new CodeGroundedRuleCatalog();
        var registry = new org.tzi.use.plugins.jacamo.codegrounded.runtime.CodeGroundedRuntimeRuleRegistry();
        var facts = Map.of("A12", org.jacamo.bridge.contract.RuntimeFactKind.ACTION_EXECUTION,
                "A13", org.jacamo.bridge.contract.RuntimeFactKind.INTENTION,
                "A14", org.jacamo.bridge.contract.RuntimeFactKind.RUNTIME_EVENT,
                "A15", org.jacamo.bridge.contract.RuntimeFactKind.TRANSITION_SYSTEM);
        facts.forEach((id, kind) -> {
            assertEquals(org.tzi.use.plugins.jacamo.codegrounded.runtime.CodeGroundedRuntimeRuleRegistry.Action.EVIDENCE_ONLY,
                    registry.evidenceRule(kind).action());
            assertTrue(catalog.require(id).targetUseKind().startsWith("Trace runtime"), id);
        });
        var goals = jason.asSemantics.Agent.class.getMethod("getInitialGoals");
        assertEquals(goals.getDeclaringClass().getName() + "." + goals.getName(), catalog.require("A09").sourceKindFqcn());
    }

    private static List<String> expectedIds() {
        var ids = new java.util.ArrayList<String>();
        for (var family : List.of(Map.entry('J', 11), Map.entry('A', 22), Map.entry('C', 20),
                Map.entry('M', 43), Map.entry('X', 9)))
            for (int index = 1; index <= family.getValue(); index++)
                ids.add("%c%02d".formatted(family.getKey(), index));
        return List.copyOf(ids);
    }
}
