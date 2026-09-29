package org.tzi.use.plugins.jacamo.codegrounded.rule;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.junit.jupiter.api.Test;

class CodeGroundedRuleCatalogTest {
    private static final List<String> IMPLEMENTED_PHASE_1_TO_3 = List.of(
            "J01", "J02", "J03", "J04", "J05", "J06", "J07", "J08", "J09", "J10", "J11",
            "A01", "A02", "A03", "A04", "A05", "A06", "A07", "A08", "A09", "A10", "A11",
            "A16", "A17", "A18", "A19", "A20", "A21", "A22");

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
    }

    @Test void phase3ClosurePinsExactlyTwentyNineImplementedRules() {
        assertEquals(IMPLEMENTED_PHASE_1_TO_3, new CodeGroundedRuleCatalog().rules().stream()
                .filter(rule -> rule.implementationStatus() == ImplementationStatus.IMPLEMENTED)
                .map(CodeGroundedRule::ruleId).toList());
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
