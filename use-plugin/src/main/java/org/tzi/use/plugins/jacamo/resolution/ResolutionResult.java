package org.tzi.use.plugins.jacamo.resolution;

import java.util.List;
import org.tzi.use.plugins.jacamo.semantic.SemanticElement;

public record ResolutionResult(Status status, Strategy strategy, SemanticElement target,
                               List<SemanticElement> candidates, String diagnostic) {
    public ResolutionResult { candidates = List.copyOf(candidates); }
    public enum Status { RESOLVED, AMBIGUOUS, UNRESOLVED, INVALID_BINDING }
    public enum Strategy { EXACT_ID, EXPLICIT_REFERENCE, OWNER_QUALIFIED, UNIQUE_TYPED_SCOPE, EXPLICIT_BINDING, FAILURE }
}
