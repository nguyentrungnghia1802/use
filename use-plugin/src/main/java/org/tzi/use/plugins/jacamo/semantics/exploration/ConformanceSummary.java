package org.tzi.use.plugins.jacamo.semantics.exploration;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.istar.IntentionalMarking;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;

public record ConformanceSummary(Status status,String scope,String profileHash,ExplorationBounds bounds,List<String> assumptions,
        long exploredNodes,long completeScenarios,long conformantScenarios,long nonConformantScenarios,long unknownScenarios,
        boolean exhaustive,Risk risk,List<Frontier> frontier,List<Witness> witnesses,List<String> diagnostics) {
    public enum Status{CONFORMANT_WITHIN_BOUNDS,WEAK,NON_CONFORMANT,INCONCLUSIVE,UNSUPPORTED}
    public enum Risk{VIOLATED_ROOT_WITNESS,NO_VIOLATED_ROOT_WITHIN_EXHAUSTIVE_BOUNDS,UNKNOWN}
    public record Frontier(List<String> transitions,String state,String reason){public Frontier{transitions=List.copyOf(transitions);}}
    public record Check(String sourceId,String expression,SemanticVerdict verdict,String backendValue,String diagnostic){}
    public record Witness(List<String> transitions,String state,TransitionProfile.State values,Map<String,IntentionalMarking> markings,List<Check> checks,String concretization) {
        public Witness{transitions=List.copyOf(transitions);markings=Map.copyOf(markings);checks=List.copyOf(checks);}
    }
    public ConformanceSummary{assumptions=List.copyOf(assumptions);frontier=List.copyOf(frontier);witnesses=List.copyOf(witnesses);diagnostics=List.copyOf(diagnostics);}
    public byte[] portableBytes(){return org.jacamo.bridge.contract.CanonicalJson.encode(SemanticJson.tree(this));}
}
