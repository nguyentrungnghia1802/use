package org.tzi.use.plugins.jacamo.codegrounded.rule;

import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;

/** One deterministic, auditable mapping-rule declaration. */
public record CodeGroundedRule(
        String ruleId,
        RuleDimension dimension,
        EvidenceAuthority sourceAuthority,
        String sourceKindFqcn,
        String targetUseKind,
        Fidelity fidelity,
        CapabilityStatus capabilityStatus,
        ImplementationStatus implementationStatus,
        String diagnosticPolicy) {
    public CodeGroundedRule {
        if (ruleId == null || !ruleId.matches("[JACMX][0-9]{2}"))
            throw new IllegalArgumentException("CODE_GROUNDED_RULE_ID_INVALID: " + ruleId);
        if (dimension == null || sourceAuthority == null || fidelity == null || capabilityStatus == null
                || implementationStatus == null)
            throw new IllegalArgumentException("CODE_GROUNDED_RULE_METADATA_REQUIRED: " + ruleId);
        sourceKindFqcn = required(sourceKindFqcn, "sourceKindFqcn", ruleId);
        targetUseKind = required(targetUseKind, "targetUseKind", ruleId);
        diagnosticPolicy = required(diagnosticPolicy, "diagnosticPolicy", ruleId);
    }

    private static String required(String value, String field, String ruleId) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("CODE_GROUNDED_RULE_" + field + "_REQUIRED: " + ruleId);
        return value;
    }
}
