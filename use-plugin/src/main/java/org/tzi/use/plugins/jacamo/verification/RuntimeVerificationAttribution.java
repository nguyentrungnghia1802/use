package org.tzi.use.plugins.jacamo.verification;

import java.util.List;
import org.tzi.use.plugins.jacamo.project.SourceSpan;

/** Exact constraint, source, structural mapping and runtime-rule attribution for one result. */
public record RuntimeVerificationAttribution(String constraintId, String constraintName, String origin,
        String context, String operation, String sourcePath, SourceSpan sourceSpan, String runtimeRuleId,
        List<String> mappingRuleIds, List<String> projectionRuleIds) {
    public RuntimeVerificationAttribution {
        if (constraintId == null || constraintId.isBlank() || constraintName == null || constraintName.isBlank()
                || origin == null || origin.isBlank())
            throw new IllegalArgumentException("RUNTIME_VERIFICATION_ATTRIBUTION_INVALID");
        context = context == null ? "" : context;
        operation = operation == null ? "" : operation;
        sourcePath = sourcePath == null ? "" : sourcePath;
        runtimeRuleId = runtimeRuleId == null ? "" : runtimeRuleId;
        mappingRuleIds = List.copyOf(mappingRuleIds);
        projectionRuleIds = List.copyOf(projectionRuleIds);
    }
}
