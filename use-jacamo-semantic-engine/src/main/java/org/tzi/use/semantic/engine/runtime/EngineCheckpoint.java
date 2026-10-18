package org.tzi.use.semantic.engine.runtime;

import java.util.*;
import org.vnu.sme.goal.verify.conformance.semantics.*;

/** Immutable staged history only. No native model/state, socket, or UI ownership. */
public record EngineCheckpoint(String ownerToken,long epoch,String definitionHash,String castRevision,long boundary,
        Map<String,GoalMarking> goals,Map<String,TaskMarking> tasks,Map<String,QualityStatus> qualities,
        Map<String,EngineOccurrenceKey> keys) {
    public EngineCheckpoint {goals=Map.copyOf(goals);tasks=Map.copyOf(tasks);qualities=Map.copyOf(qualities);keys=Map.copyOf(keys);}
}
