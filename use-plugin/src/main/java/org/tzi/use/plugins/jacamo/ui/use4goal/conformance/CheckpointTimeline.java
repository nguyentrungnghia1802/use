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
        // A selected source Task may finish without changing any Entity value.
        // Include scoped terminal progress, not proof text, timestamps, row order
        // or another execution's source token. All raw cuts remain in audit.
        var progress=new TreeSet<String>();
        for(var row:cut.evaluations()) {
            if(row.kind().equals("GOAL_TASK")&&row.quality().startsWith("KNOWN")
                    &&Set.of("FULFILLED","VIOLATED","FAILED").contains(row.status()))
                progress.add(List.of(row.kind(),row.element(),row.player(),row.context(),row.occurrence(),row.status()).toString());
            if(Set.of("TASK_POST","TASK_INVOCATION").contains(row.kind())
                    &&Set.of("CONDITION_TRUE","CONDITION_FALSE","PASS","FAIL").contains(row.status()))
                progress.add(List.of(row.kind(),row.element(),row.context(),row.occurrence(),row.status()).toString());
        }
        return SemanticJson.hash(List.of(cut.businessState(),progress));
    }
}
