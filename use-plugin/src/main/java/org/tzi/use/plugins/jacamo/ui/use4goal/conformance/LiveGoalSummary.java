package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.util.*;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Evaluation;

/** Presentation only: fixed M1 rows, conservative summary of the current admitted
 * occurrences. Original iStar marking/quantifiers remain the semantic authority. */
public final class LiveGoalSummary {
 public record Row(String id,String label,String status,String originalStatus,String detail,String resultRole,String counts,List<Evaluation> instances){
  public Row{instances=List.copyOf(instances);}
  public boolean isGoal(){return label!=null&&label.startsWith("Goal: ");}
  public boolean isTask(){return label!=null&&label.startsWith("Task: ");}
  public String ownerLabel(){if(label==null)return id;if(label.startsWith("Goal: "))return label.substring("Goal: ".length());if(label.startsWith("Task: "))return label.substring("Task: ".length());return label;}
  public String resultText(){return PresentationResults.goal(status,false);}
  public List<Evaluation> displayInstances(){return instances.stream().filter(i->!PopulationOverview.notAssigned(i)).toList();}
 }
 public List<Row> goalRows(List<Evaluation> evaluations){return rows(evaluations).stream().filter(Row::isGoal).toList();}
 public List<Row> taskRows(List<Evaluation> evaluations){return rows(evaluations).stream().filter(Row::isTask).toList();}
 private final Map<String,String> declarations=new LinkedHashMap<>();
 private final PopulationOverview overview;
 public LiveGoalSummary(GoalModel model){this(model,Map.of());}
 public LiveGoalSummary(GoalModel model,Map<String,Object> mapping){
  overview=new PopulationOverview(model,mapping);
  for(var actor:model.getActors())for(var e:actor.elements())if(e instanceof Goal||e instanceof Task)declarations.put(e.id(),(e instanceof Goal?"Goal: ":"Task: ")+actor.name()+"::"+e.id());
  // The original marking indexes actor elements, not social dependum declarations.
  // Dependencies stay visible in the model/evidence; do not invent extra occurrences.
 }
 public List<Row> rows(List<Evaluation> evaluations){
  var grouped=new HashMap<String,List<Evaluation>>();
  for(var row:evaluations)if(row.kind().equals("GOAL_TASK"))grouped.computeIfAbsent(row.element(),key->new ArrayList<>()).add(row);
  var rows=new ArrayList<Row>();
  for(var definition:declarations.entrySet()){
   var instances=grouped.getOrDefault(definition.getKey(),List.of());
   var assigned=instances.stream().filter(r->!PopulationOverview.notAssigned(r)).toList();
   String status=overview.status(definition.getKey(),instances);
   var originalStatuses=instances.stream().map(Evaluation::originalStatus).filter(x->!x.isBlank()).distinct().toList();
   String original=originalStatuses.isEmpty()?"UNKNOWN":originalStatuses.size()==1?originalStatuses.getFirst():"MIXED";
   String resultRole=instances.isEmpty()?"No admitted evidence":instances.stream().anyMatch(Evaluation::required)?"Selected requirement":"Supporting";
   var counts=new TreeMap<String,Long>();assigned.forEach(i->counts.merge(i.status(),1L,Long::sum));
   String countText=counts.isEmpty()?"0 observed":counts.entrySet().stream().map(e->e.getValue()+" "+e.getKey()).collect(java.util.stream.Collectors.joining(", "));
   var detail=new StringBuilder(definition.getValue()+"\nResult role: "+resultRole+"\nOverview population policy: "+overview.description(definition.getKey())+" within each execution; ALL across executions.\nOriginal native markings and execution evidence are unchanged. Individual results are shown below.\nInstances: "+assigned.size()+"\n");
   if(instances.isEmpty())detail.append("No admitted occurrence at this cut. Missing evidence is UNKNOWN.\n");
   for(var instance:assigned)detail.append("\n").append(instance.status()).append(" | ").append(instance.quality()).append(" | original aggregate=").append(instance.originalStatus()).append(" | leaf raw=").append(instance.raw()).append("\n").append(instance.evidence()).append("\nLast certified history: ").append(instance.certifiedHistory()).append("\n");
   rows.add(new Row(definition.getKey(),definition.getValue(),status,original,detail.toString(),resultRole,countText,instances));
  }
  return List.copyOf(rows);
 }
}
