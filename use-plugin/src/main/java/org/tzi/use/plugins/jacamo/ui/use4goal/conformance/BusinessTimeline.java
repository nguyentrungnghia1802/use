package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;
import java.util.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;
/** Immutable UI grouping only. Every input boundary remains available in detail/replay. */
public final class BusinessTimeline {
 private BusinessTimeline(){}
 public static List<Cut> summarize(List<Cut> input) {
  var output=new ArrayList<Cut>();var reached=new HashSet<String>();String priorBusiness="",priorHealth="",priorFailure="";
  var eligible=new ArrayList<Cut>();boolean observedDomain=false;
  for(var cut:input) {
   if(!cut.executionMilestones()) {
    if(cut.businessKey().isBlank()||!observedDomain&&cut.businessPopulationEmpty())continue;
    observedDomain=true;
   }
   eligible.add(cut);
  }
  for(Cut cut:eligible) {
   boolean progress=false;
   for(var row:cut.evaluations())if(cut.executionMilestones()&&Set.of("KNOWN","KNOWN_DERIVED","KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(row.quality())&&Set.of("FULFILLED","VIOLATED","FAILED").contains(row.status())) {
    // Goal/Task evidence carries exact occurrence/player identity, not a display label alone.
    String identity=row.evidence().split("; raw propagated=",2)[0];
    progress|=reached.add(row.element()+":"+identity+":"+row.status());
   }
   String health=cut.freshness().contains("STALE")?"STALE":cut.freshness().contains("ERROR")?"ERROR":"CURRENT";
   if(cut.evaluations().stream().anyMatch(r->Set.of("HISTORY_GAP","COVERAGE_LOST").contains(r.quality())))health="GAP";
   String failure=cut.evaluations().stream().filter(r->r.status().equals("FAIL")||r.status().equals("FAILED")||r.status().equals("VIOLATED")||r.status().equals("ERROR")).map(r->r.element()+":"+r.status()+":"+r.evidence()).sorted().toList().toString();
   boolean change=output.isEmpty()||progress||!cut.businessKey().isEmpty()&&!cut.businessKey().equals(priorBusiness)||cut.executionMilestones()&&(!health.equals(priorHealth)||!failure.equals(priorFailure));
   if(change)output.add(cut);
   else if(output.size()>1)output.set(output.size()-1,cut);
   if(!cut.businessKey().isEmpty())priorBusiness=cut.businessKey();priorHealth=health;priorFailure=failure;
  }
  // Preserve an initial boundary and the exact latest cut even without a domain change.
  if(!eligible.isEmpty()&&!output.getLast().id().equals(eligible.getLast().id()))output.add(eligible.getLast());
  return List.copyOf(output);
 }
}
