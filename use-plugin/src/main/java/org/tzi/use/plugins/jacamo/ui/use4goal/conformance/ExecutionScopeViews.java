package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.*;

/** Read-only projections of frozen execution evidence. No evaluation or identity inference. */
public final class ExecutionScopeViews {
 private ExecutionScopeViews(){}
 public static Map<String,Object> object(Object value){return value instanceof Map<?,?>?CanonicalJson.object(value):Map.of();}
 public static List<Map<String,Object>> contexts(Cut cut){
  Object rows=object(cut.visualEvidence().get("originalEngine")).get("contexts");
  return rows instanceof List<?> list?list.stream().map(ExecutionScopeViews::object).toList():List.of();
 }
 public static Map<String,String> choices(List<Cut> cuts){
  var contexts=new TreeMap<String,Map<String,Object>>();
  for(var cut:cuts)for(var context:contexts(cut)){
   String id=Objects.toString(context.get("context"),"");
   if(!id.isBlank()&&(!contexts.containsKey(id)||!object(object(context.get("scopeView")).get("entities")).isEmpty()))contexts.put(id,context);
  }
  var result=new LinkedHashMap<String,String>();int ordinal=0;
  for(var entry:contexts.entrySet())result.put(entry.getKey(),label(entry.getValue(),++ordinal));
  return Collections.unmodifiableMap(result);
 }
 public static String label(Map<String,Object> context,int ordinal){
  var entities=object(object(context.get("scopeView")).get("entities"));
  var names=new ArrayList<String>();
  for(var entry:new TreeMap<>(entities).entrySet()){
   String type=entry.getKey().split(":",2)[0];var attributes=object(object(entry.getValue()).get("attributes"));
   // This is a display label only; exact context and native IDs remain authoritative.
   String id=Objects.toString(attributes.get("id"),"");names.add(type+(id.isBlank()?"":" · id="+id));
  }
  return "Execution "+ordinal+(names.isEmpty()?" · entity binding unavailable":" — "+String.join(", ",names));
 }
 /** Short actor caption avoids expanding the original circular actor over its goals.
  * The full Entity/context identity remains in the selector and evidence. */
 public static String actorLabel(Map<String,Object> context,int ordinal){
  var entities=object(object(context.get("scopeView")).get("entities"));
  if(entities.size()==1){String id=Objects.toString(object(object(entities.values().iterator().next()).get("attributes")).get("id"),"");
   if(id.startsWith("'")&&id.endsWith("'")&&id.length()>1)id=id.substring(1,id.length()-1);
   if(!id.isBlank())return id.length()>8?id.substring(0,8)+"…":id;
  }
  return "Exec "+ordinal;
 }
 public static String selected(Cut cut){return Objects.toString(cut.visualEvidence().get("selectedExecution"),"");}
 public static Cut project(Cut cut,String id){
  if(id.isBlank())return cut;
  var context=contexts(cut).stream().filter(c->id.equals(c.get("context"))).findFirst().orElse(Map.of());
  var rows=new ArrayList<Evaluation>();
  var inventory=object(cut.visualEvidence().get("requirementInventory"));
  for(var row:cut.evaluations())if(row.kind().equals("GOAL_TASK")&&row.context().equals(id))rows.add(row);
  Object checks=context.get("executionConstraints");
  if(checks instanceof List<?> list)for(Object raw:list){var check=object(raw);String kind=Objects.toString(check.get("kind")),name=Objects.toString(check.get("id"));
   if(kind.equals("INVARIANT_BODY"))continue; // Bodies remain frozen proof details, not duplicate independent requirements.
   var bodyProofs=list.stream().map(ExecutionScopeViews::object).filter(b->name.equals(b.get("id"))&&"INVARIANT_BODY".equals(b.get("kind"))).toList();
   boolean condition=Set.of("GOAL_CONDITION","TASK_PRE","TASK_POST").contains(kind),execution=kind.equals("EXECUTION_OUTCOME");
   String status=Objects.toString(check.get("verdict"));
   if(condition)status=status.equals("PASS")?"CONDITION_TRUE":status.equals("FAIL")?"CONDITION_FALSE":status;
   if(execution)status="EXECUTION_FAILED";
   rows.add(new Evaluation(name,Objects.toString(check.get("backendValue"),""),status,Objects.toString(check.get("diagnostic"),""),
    Objects.toString(check.get("contextObject"),"")+" / "+Objects.toString(check.get("invocation"),"")+" / expression="+check.get("expression")+" / witness="+check.get("witness")+" / selected bodies="+bodyProofs,"",
    !condition&&!execution&&(!kind.equals("INVARIANT")||((List<?>)inventory.getOrDefault("requiredInvariants",List.of())).contains(name)||!((List<?>)inventory.getOrDefault("supportingInvariants",List.of())).contains(name)),kind,"","",id+" / "+Objects.toString(check.get("contextObject"),""),Objects.toString(check.get("invocation"),"")));
  }
  else rows.add(new Evaluation("Execution OCL","UNAVAILABLE","UNKNOWN","EXECUTION_OCL_RESULT_UNAVAILABLE","No scoped native checks at this exact cut","",true,"RUNTIME_BINDING"));
  for(Object target:(List<?>)inventory.getOrDefault("requiredInvariants",List.of()))
   if(rows.stream().noneMatch(r->r.kind().equals("INVARIANT")&&r.element().equals(target)))
    rows.add(new Evaluation(target.toString(),"UNAVAILABLE","UNKNOWN","REQUIRED_SCOPED_CONTRACT_RESULT_MISSING","No native aggregate result for this execution","",true,"INVARIANT","","",id,""));
  for(Object target:(List<?>)inventory.getOrDefault("selectedIntentionalElements",List.of()))
   if(rows.stream().noneMatch(r->r.kind().equals("GOAL_TASK")&&r.element().equals(target)))
    rows.add(new Evaluation(target.toString(),"UNAVAILABLE","UNKNOWN","EXECUTION_RESULT_UNAVAILABLE",
      "No admitted result for this exact execution at this cut","",true,"GOAL_TASK","", "",id,""));
  var evidence=new TreeMap<String,Object>(cut.visualEvidence());evidence.put("selectedExecution",id);
  var projection=object(evidence.get("presentationState"));var selected=new TreeMap<String,Object>();
  var entityKeys=object(object(context.get("scopeView")).get("entities")).keySet();
  var objects=object(projection.get("objects"));var objectQuality=object(projection.get("objectQuality"));
  for(String key:entityKeys)if(Boolean.TRUE.equals(objectQuality.getOrDefault(key,projection.get("qualifiedValues")))&&objects.containsKey(key))selected.put(key,objects.get(key));
  evidence.put("selectedEntityState",selected);
  // Native structural membership remains intact. The additional links are explicit
  // execution-cast witnesses, never inferred from group membership.
  var owners=object(object(context.get("scopeView")).get("owners"));var scopedObjects=new TreeMap<String,Object>();
  objects.forEach((key,value)->{int colon=key.indexOf(':');if(colon>=0&&owners.containsKey(key.substring(colon+1)))scopedObjects.put(key,value);});
  var nativeIds=new HashSet<String>();scopedObjects.keySet().forEach(k->nativeIds.add(k.substring(k.indexOf(':')+1)));
  var links=new ArrayList<Object>();for(Object raw:(List<?>)projection.getOrDefault("links",List.of())){
   var link=object(raw);if(((List<?>)link.getOrDefault("participants",List.of())).stream().allMatch(nativeIds::contains))links.add(link);
  }
  Object participants=object(context.get("scopeView")).get("participation");
  if(Boolean.TRUE.equals(object(context.get("scopeView")).get("complete"))&&participants instanceof List<?> list)
   for(Object raw:list){var participant=object(raw);String role=Objects.toString(participant.get("role"),"");
    if(!nativeIds.contains(role))continue;
    for(String entityKey:entityKeys){String entity=entityKey.substring(entityKey.indexOf(':')+1);
     if(nativeIds.contains(entity))links.add(Map.of("association","participates (execution)","participants",List.of(role,entity),"witness",participant));
    }
   }
  var scopedProjection=new TreeMap<String,Object>(projection);scopedProjection.put("objects",scopedObjects);scopedProjection.put("links",links);
  scopedProjection.put("qualifiedValues",!scopedObjects.isEmpty()&&!context.isEmpty()&&scopedObjects.keySet().stream().allMatch(k->Boolean.TRUE.equals(objectQuality.getOrDefault(k,projection.get("qualifiedValues")))));
  evidence.put("presentationState",scopedProjection);
  // Missing bindings/values are diagnostic gaps, not Entity changes. Never turn
  // alternating qualified/unavailable source cuts into hundreds of milestones.
  var scopeView=object(context.get("scopeView"));
  boolean domainBound=!scopeView.isEmpty()&&!Boolean.FALSE.equals(scopeView.get("domainBound"));
  if(!domainBound&&selected.isEmpty())scopedObjects.forEach((k,v)->{if(Boolean.TRUE.equals(objectQuality.getOrDefault(k,projection.get("qualifiedValues"))))selected.put(k,v);});
  var text=new StringBuilder("Selected execution business state\n");
  selected.forEach((key,value)->{text.append(key).append('\n');object(value).forEach((field,v)->text.append("  ").append(field).append(" = ").append(v).append('\n'));});
  if(selected.isEmpty())text.append("No qualified bound Entity state at this cut. No other execution is substituted.\n");
  String key="PINNED_NATIVE_CUT".equals(object(cut.visualEvidence().get("originalEngine")).get("evaluated"))&&(!domainBound||!selected.isEmpty())?SemanticJson.hash(List.of(id,selected,context.isEmpty())):"";
  boolean failed=rows.stream().anyMatch(r->!Set.of("GOAL_TASK","EXECUTION_OUTCOME").contains(r.kind())&&r.status().equals("FAIL")||r.kind().equals("GOAL_TASK")&&r.required()&&r.status().equals("VIOLATED"));
  long missing=rows.stream().filter(Evaluation::required).filter(r->Set.of("UNKNOWN","SKIPPED","UNSUPPORTED","INCONCLUSIVE","UNDEFINED","ERROR").contains(r.status())).count();
  String requirements="Selected execution requirements: "+(failed?"VIOLATION":missing>0?"INCOMPLETE ("+missing+" unavailable)":"KNOWN")+" | Shared group rules apply to this execution too";
  return new Cut(cut.id(),cut.trace(),cut.state(),rows,cut.coverage(),cut.freshness(),failed,key,
    requirements,text.toString(),!domainBound&&cut.executionMilestones(),cut.selectedRequirements(),selected.isEmpty()&&context.isEmpty(),evidence);
 }
 public static String goalVerdict(Cut cut){
  var rows=cut.evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")&&r.required()).toList();
  if(rows.isEmpty())return "INCOMPLETE";
  if(rows.stream().anyMatch(r->r.status().equals("VIOLATED")&&known(r)))return "VIOLATION";
  if(!cut.freshness().equals("CURRENT_OBSERVED")||rows.stream().anyMatch(r->!known(r)||!Set.of("FULFILLED","PENDING","FAILED").contains(r.status())))return "INCOMPLETE";
  if(rows.stream().anyMatch(r->!r.status().equals("FULFILLED")))return "PENDING";
  return "COMPLETE_OBSERVED";
 }
 private static boolean known(Evaluation row){return Set.of("KNOWN","KNOWN_DERIVED","KNOWN_HISTORY","KNOWN_DERIVED_HISTORY").contains(row.quality());}
}
