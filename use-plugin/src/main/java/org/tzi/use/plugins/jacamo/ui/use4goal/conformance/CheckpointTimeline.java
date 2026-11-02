package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;

/** Presentation grouping of already scoped, immutable cuts. Does not verify or release them. */
final class CheckpointTimeline {
    private CheckpointTimeline() { }
    record Grouping(List<Cut> milestones, Map<String,Integer> numbers) { }

    static Grouping group(List<Cut> audit) {
        var milestones=new ArrayList<Cut>();var numbers=new LinkedHashMap<String,Integer>();
        String previous="";
        for(var cut:audit) {
            String signature=signature(cut);
            if(!signature.equals(previous))milestones.add(cut);
            else milestones.set(milestones.size()-1,cut);
            // Display the latest exact cut even when only its diagnostic metadata changed.
            // Every original token/evaluation remains in audit with the same milestone number.
            numbers.put(cut.id(),milestones.size());previous=signature;
        }
        return new Grouping(List.copyOf(milestones),Map.copyOf(numbers));
    }

    private static String signature(Cut cut) {
        // A state milestone represents the frozen scoped CSL graph. Native checks,
        // Task progress and failures remain exact in every audit cut and in the
        // latest representative, without fabricating another identical CSL state.
        // businessState is the canonical, ordered scoped object/attribute/link
        // projection shown in the state panel; proof witnesses are audit metadata.
        return SemanticJson.hash(cut.businessState());
    }
}
