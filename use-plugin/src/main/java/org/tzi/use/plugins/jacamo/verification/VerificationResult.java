package org.tzi.use.plugins.jacamo.verification;

import java.util.List;

public record VerificationResult(String constraintId, VerificationOutcome outcome, String contextObject,
                                 String explanation, String oclSource, List<String> sourceTrace,
                                 String correlationId, List<String> runtimeEventIds) {
    public VerificationResult {
        sourceTrace = List.copyOf(sourceTrace);
        runtimeEventIds = List.copyOf(runtimeEventIds);
        explanation = explanation == null ? "" : explanation;
        oclSource = oclSource == null ? "" : oclSource;
    }
}
