package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.util.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut;

/** Read-only coverage of the selected native requirements, independent of truth. */
public final class VerificationDataCoverage {
    public record Assessment(String status,List<String> unavailable){public Assessment{unavailable=List.copyOf(unavailable);}}
    private static final Set<String> KNOWN_TRUTH=Set.of("PASS","FAIL","CONDITION_TRUE","CONDITION_FALSE");
    private static final Set<String> KNOWN_GOAL=Set.of("KNOWN","KNOWN_DERIVED","KNOWN_HISTORY","KNOWN_DERIVED_HISTORY");
    private VerificationDataCoverage(){ }
    public static Assessment assess(Cut cut) {
        if(cut==null)return new Assessment("UNAVAILABLE",List.of("No accepted verification state"));
        var admission=ExecutionScopeViews.object(cut.visualEvidence().get("nativeAdmissionChecks"));
        // The existing admission engine already distinguishes applicable checks
        // from future Goal/Task conditions. Do not invent another applicability rule.
        var required=cut.evaluations().stream().filter(r->(r.required()||r.kind().equals("CHECKPOINT_CHECK"))
                &&!r.status().equals("NOT_ASSIGNED")&&(admission.isEmpty()||!r.kind().equals("GOAL_TASK"))).toList();
        if(required.isEmpty()&&admission.isEmpty())return new Assessment("UNAVAILABLE",List.of("No admitted requirement evidence"));
        var missing=new TreeSet<String>();
        if(!cut.freshness().equals("CURRENT_OBSERVED"))missing.add("Accepted evidence is not current: "+cut.freshness());
        admission.forEach((id,value)->{var check=ExecutionScopeViews.object(value);if(!Set.of("FUTURE","NOT_APPLICABLE").contains(check.getOrDefault("applicability",""))
                &&(!check.getOrDefault("applicability","").equals("APPLICABLE")||!Set.of("PASS","FAIL").contains(check.get("truth"))))missing.add(id+" / "+check);});
        for(var row:required) {
            boolean known=row.kind().equals("GOAL_TASK")?KNOWN_GOAL.contains(row.quality())&&Set.of("FULFILLED","PENDING","FAILED","VIOLATED").contains(row.status()):KNOWN_TRUTH.contains(row.status());
            if(!known)missing.add(row.element()+" / "+row.context()+" / "+row.status()+" / "+row.quality());
        }
        return new Assessment(missing.isEmpty()?"COMPLETE":"INCOMPLETE",List.copyOf(missing));
    }
    public static String diagnostics(Cut cut,Assessment assessed) {
        return "Data coverage policy: SELECTED_NATIVE_REQUIREMENTS/1.0\n"
                +"Coverage describes available evidence, not whether the predicates are True.\n"
                +"Overall source coverage (includes historical/global observation): "+(cut==null?"UNAVAILABLE":cut.coverage())
                +"\nRequired evidence gaps: "+assessed.unavailable()+"\nApplicable native inventory: "+(cut==null?Map.of():cut.visualEvidence().getOrDefault("nativeAdmissionChecks",Map.of()))+"\n"+(cut==null?"":cut.trace());
    }
}
