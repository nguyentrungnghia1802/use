package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.OccurrenceProjection;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables;
import org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.uml.sys.MObject;

/** Exact scope ownership captured on the writer barrier; native predicates are unchanged. */
public final class ExecutionConstraintScope {
 private ExecutionConstraintScope(){}
 public static Map<String,Object> capture(SemanticProgram program,NativeRuntimeMutationEngine engine,OccurrenceProjection.Scope scope){
  var state=engine.system().state();var owners=new TreeMap<String,Object>();var entities=new TreeMap<String,Object>();
  var ids=new TreeSet<String>(scope.resources().values());ids.add(scope.anchor());ids.add(scope.context());
  var participants=new ArrayList<Map<String,Object>>();
  for(var member:scope.members()){
   ids.add(member.player());ids.add(member.role());ids.add(member.context());ids.addAll(member.selves().values());
   var role=engine.objectForSemanticId(member.role());var player=engine.objectForSemanticId(member.player());
   if(role!=null&&player!=null&&!member.interval().isBlank())participants.add(Map.of("role",role.name(),"player",player.name(),"interval",member.interval(),"actor",member.actor(),"scope",scope.id()));
  }
  var selected=new HashSet<MObject>();for(String id:ids){var object=engine.objectForSemanticId(id);if(object!=null&&state.allObjects().contains(object))selected.add(object);}
  // Follow only structural parent/context and exact represented-player relations.
  boolean changed=true;while(changed){changed=false;for(var link:state.allLinks()){
   var objects=link.linkedObjects();if(objects.size()!=2)continue;String relation=link.association().name();
   if(Set.of("CslContextScopes","InContext").contains(relation)&&selected.contains(objects.get(1)))changed|=selected.add(objects.getFirst());
   if(relation.equals("CslAgentContexts")&&selected.contains(objects.get(1)))changed|=selected.add(objects.getFirst());
  }}
  var issues=new ArrayList<String>();
  var specification=program.xmlPlan().inputs().specification();
  var declaration=specification.requireId(scope.definition());
  var table=specification.requireId(declaration.attr("tableRef"));var columns=RuntimeTables.outputColumns(table);
  var anchor=engine.objectForSemanticId(scope.anchor());String epoch=anchor==null?"":engine.metadata(anchor,"xml:occurrence:epoch");
  for(var binding:declaration.children("observer")){
   var project=specification.requireId(binding.attr("projectRef"));
   if(!project.name().equals("project")||!columns.contains(binding.attr("subjectColumn")))throw binding.location().error("EXECUTION_OBSERVER_BINDING_INVALID");
   var view=specification.requireId(project.attr("viewRef"));String subject=Objects.toString(scope.bindings().get(binding.attr("subjectColumn")),"");
   var matches=state.allObjects().stream().filter(o->o.cls().name().equals(view.attr("name"))&&program.hash().equals(engine.metadata(o,"xml:observer:program"))
      &&epoch.equals(engine.metadata(o,"xml:observer:epoch"))&&project.attr("id").equals(engine.metadata(o,"xml:observer:project"))&&subject.equals(engine.metadata(o,"xml:observer:subject"))).toList();
   if(subject.isBlank()||matches.size()!=1)issues.add("EXECUTION_OBSERVER_NOT_UNIQUE:"+project.attr("id"));else selected.add(matches.getFirst());
  }
  for(var object:selected){
   owners.put(object.name(),Map.of("type",object.cls().name(),"source",engine.metadata(object,"semanticId")));
   if(program.cslModel().findEntity(object.cls().name()).isPresent()){
    var attributes=new TreeMap<String,Object>();object.state(state).attributeValueMap().forEach((a,v)->attributes.put(a.name(),v.toString()));
    entities.put(object.cls().name()+":"+object.name(),Map.of("identity",engine.metadata(object,"semanticId"),"attributes",attributes));
   }
  }
  var domainType=state.system().model().getClass("DomainEntity");
  boolean domainBound=declaration.children("resource").stream().anyMatch(r->state.system().model().getClass(r.attr("type")).isSubClassifierOf(domainType))
    ||declaration.children("self").stream().anyMatch(r->specification.root().child("bindings").children("elementRuntime").stream().anyMatch(e->e.attr("target").equals(r.attr("target"))&&state.system().model().getClass(e.attr("self")).isSubClassifierOf(domainType)));
  return Map.of("domainBound",domainBound,"version","BOUND_EXECUTION_VIEW/2.0","entities",entities,"owners",owners,"participation",participants,"bindings",scope.bindings(),"definition",scope.definition(),"complete",scope.complete()&&issues.isEmpty(),"unavailable",issues);
 }
 public static List<SemanticInvariantEvaluator.Prepared> prepare(ExternalOclConstraintService.PreparedEvaluation nativeChecks,Map<String,Object> view){
  var owners=CanonicalJson.object(view.get("owners"));var result=new ArrayList<SemanticInvariantEvaluator.Prepared>();
  for(var invariant:nativeChecks.invariants()){
   var original=invariant.scoped();
   if(original==null){result.add(new SemanticInvariantEvaluator.Prepared(invariant.id(),invariant.context(),invariant.body()==null?"":invariant.body().toString(),"self",invariant.body(),List.of(),false,false,false,"UNSUPPORTED:EXECUTION_NATIVE_COVERAGE_BINDING_REQUIRED",Map.of()));continue;}
   boolean qualified=Boolean.TRUE.equals(view.get("complete"));
   var bodies=original.bodies().stream().filter(b->owners.containsKey(b.object())).toList();
   String missing=!original.unavailable().isBlank()?original.unavailable():bodies.isEmpty()?"EXECUTION_CONSTRAINT_OWNER_UNAVAILABLE":"";
   var witness=new TreeMap<String,String>(original.witness());witness.put("scopePolicy","EXACT_BOUND_OWNER_POPULATION/1.0");
   witness.put("globalBodyCount",Integer.toString(original.bodies().size()));witness.put("bodyCount",Integer.toString(bodies.size()));
   result.add(new SemanticInvariantEvaluator.Prepared(original.id(),original.context(),original.text(),original.variable(),original.expression(),bodies,qualified&&missing.isBlank(),original.existential(),original.negated(),missing,witness));
  }
  return List.copyOf(result);
 }
 public static List<SemanticConstraintResult> evaluate(List<SemanticInvariantEvaluator.Prepared> checks,org.tzi.use.uml.sys.MSystemState state,List<SemanticConstraintResult> existing,Map<String,Object> view,String context,long version){
  var result=new ArrayList<SemanticConstraintResult>();
  for(var check:checks)result.addAll(SemanticInvariantEvaluator.evaluateScoped(check,state));
  var owners=CanonicalJson.object(view.get("owners"));
  for(var row:existing){
   if(Set.of("INVARIANT","INVARIANT_BODY").contains(row.kind()))continue;
   String scope=row.witness().getOrDefault("scope","");if(!scope.isBlank()&&!scope.equals(context))continue;
   String occurrence=row.witness().getOrDefault("occurrence","");
   if(!occurrence.isBlank()&&!((List<?>)view.getOrDefault("occurrenceIds",List.of())).contains(occurrence))continue;
   if(!row.contextObject().isBlank()&&owners.containsKey(row.contextObject())||scope.equals(context)||context.equals(row.witness().get("context")))result.add(row);
  }
  return result.stream().map(r->{var proof=new TreeMap<>(r.witness());proof.put("executionScope",context);return new SemanticConstraintResult(r.id(),r.kind(),r.contextClass(),r.contextObject(),r.sourceIdentity(),r.expression(),r.verdict(),r.backendValue(),r.diagnostic(),version,r.invocation(),proof);}).toList();
 }
}
