package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.util.*;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Evaluation;

/** Presentation only: fixed M1 rows, conservative summary of the current admitted
 * occurrences. Original iStar marking/quantifiers remain the semantic authority. */
public final class LiveGoalSummary {
 public record Row(String id,String label,String status,String originalStatus,String detail){}
 private final Map<String,String> declarations=new LinkedHashMap<>();
 public LiveGoalSummary(GoalModel model){
  for(var actor:model.getActors())for(var e:actor.elements())if(e instanceof Goal||e instanceof Task)declarations.put(e.id(),actor.name()+"::"+e.id());
  // The original marking indexes actor elements, not social dependum declarations.
  // Dependencies stay visible in the model/evidence; do not invent extra occurrences.
 }
 public List<Row> rows(List<Evaluation> evaluations){
  var grouped=new HashMap<String,List<Evaluation>>();
  for(var row:evaluations)if(row.kind().equals("GOAL_TASK"))grouped.computeIfAbsent(row.element(),key->new ArrayList<>()).add(row);
  var rows=new ArrayList<Row>();
  for(var definition:declarations.entrySet()){
   var instances=grouped.getOrDefault(definition.getKey(),List.of());
   String status=instances.isEmpty()?"UNKNOWN":instances.stream().anyMatch(r->r.status().equals("VIOLATED")&&Set.of("KNOWN","KNOWN_DERIVED","KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(r.quality()))?"VIOLATED":instances.stream().anyMatch(r->r.status().equals("FAILED")&&Set.of("KNOWN","KNOWN_DERIVED","KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(r.quality()))?"FAILED":instances.stream().anyMatch(r->!Set.of("KNOWN","KNOWN_DERIVED","KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(r.quality())||!Set.of("FULFILLED","PENDING").contains(r.status()))?"UNKNOWN":instances.stream().allMatch(r->r.status().equals("FULFILLED"))?"FULFILLED":"PENDING";
   var originalStatuses=instances.stream().map(Evaluation::originalStatus).filter(x->!x.isBlank()).distinct().toList();
   String original=originalStatuses.isEmpty()?"UNKNOWN":originalStatuses.size()==1?originalStatuses.getFirst():"MIXED";
   var detail=new StringBuilder(definition.getValue()+"\nOriginal history is the engine aggregate. Certified execution may report FAILED separately from raw TaskMarking PENDING; evidence is scoped to the exact episode.\nExecution summary covers observed instances only, not an unobserved population.\nInstances: "+instances.size()+"\n");
   if(instances.isEmpty())detail.append("No admitted occurrence at this cut. Missing evidence is UNKNOWN.\n");
   for(var instance:instances)detail.append("\n").append(instance.status()).append(" | ").append(instance.quality()).append(" | original aggregate=").append(instance.originalStatus()).append(" | leaf raw=").append(instance.raw()).append("\n").append(instance.evidence()).append("\nLast certified history: ").append(instance.certifiedHistory()).append("\n");
   rows.add(new Row(definition.getKey(),definition.getValue(),status,original,detail.toString()));
  }
  return List.copyOf(rows);
 }
}
