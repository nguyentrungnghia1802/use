package org.tzi.use.semantic.engine.runtime;

import java.util.*;
import org.vnu.sme.goal.verify.conformance.semantics.*;

/** Raw original history, derived original aggregate and deferred inputs are deliberately separate. */
public record EngineStepResult(long boundary,EngineCheckpoint history,IStarMarking aggregate,Set<String> deferred,
        List<String> originalApiCalls) {
    public EngineStepResult {deferred=Set.copyOf(deferred);originalApiCalls=List.copyOf(originalApiCalls);}
}
