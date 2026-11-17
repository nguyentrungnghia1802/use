package org.tzi.use.plugins.jacamo.verify.runtime;

import java.util.*;
import org.vnu.sme.goal.dsl.istar_marking.mm.*;
import org.vnu.sme.goal.dsl.istar_marking.semantics.*;

/** Raw original history, derived original aggregate and deferred inputs are deliberately separate. */
public record EngineStepResult(long boundary,EngineCheckpoint history,IStarMarking aggregate,Set<String> deferred,
        List<String> originalApiCalls) {
    public EngineStepResult {deferred=Set.copyOf(deferred);originalApiCalls=List.copyOf(originalApiCalls);}
}
