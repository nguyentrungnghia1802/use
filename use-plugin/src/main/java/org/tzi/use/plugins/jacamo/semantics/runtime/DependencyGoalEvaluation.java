package org.tzi.use.plugins.jacamo.semantics.runtime;
import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.Value;
/** A bounded pure-predicate memo on one ordered engine owner. Coverage is checked
 * by the caller on EVERY cut. Temporal marking and invocation evaluation are never cached. */
public final class DependencyGoalEvaluation {
 private record Entry(String fingerprint,NativeOclOutcome result){}
 private final Map<NativeOclBackend.Compiled,Entry> entries=new LinkedHashMap<>();
 private long hits,executed;
 public NativeOclOutcome evaluate(NativeOclBackend backend,NativeOclBackend.Compiled compiled,UseStateReadView view,Map<String,Value> variables,String unavailable) {
  if(!unavailable.isBlank()||view==null||compiled.specification().requiresBefore()||!safe(compiled.expression()))return backend.evaluate(compiled,view,variables,unavailable);
  var visitor=ExternalOclConstraintService.dependencyVisitor();compiled.expression().processWithVisitor(visitor);
  // User operations / derived attributes may have hidden dependencies. Preserve full evaluation.
  if(!visitor.getCoverageData().getOperationCoverage().isEmpty()||visitor.attributeReads().stream().anyMatch(r->r.attribute().isDerived()))return backend.evaluate(compiled,view,variables,unavailable);
  var state=view.after();var values=new TreeMap<String,Object>();
  // Population changes, object incarnation and navigation are fingerprinted even
  // when the predicate reads only one attribute. No model metadata/watermark noise.
  for(var object:state.allObjects()) {
   var fields=new TreeMap<String,String>();
   for(var read:visitor.attributeReads())if(object.cls().isSubClassifierOf(read.attribute().owner()))fields.put(read.attribute().name(),object.state(state).attributeValue(read.attribute()).toString());
   values.put(object.name(),List.of(object.cls().name(),fields));
  }
  values.put("$links",state.allLinks().stream().map(Object::toString).sorted().toList());
  values.put("$variables",new TreeMap<>(variables).entrySet().stream().map(e->List.of(e.getKey(),e.getValue().type().toString(),e.getValue().toString())).toList());
  String fingerprint=SemanticJson.hash(values);var prior=entries.get(compiled);
  if(prior!=null&&prior.fingerprint().equals(fingerprint)){hits++;return prior.result();}
  var result=backend.evaluate(compiled,view,variables,unavailable);executed++;
  if(entries.size()>=4096&&!entries.containsKey(compiled))entries.clear();
  entries.put(compiled,new Entry(fingerprint,result));return result;
 }
 private static boolean safe(org.tzi.use.uml.ocl.expr.Expression expression) {
  if(expression.isPre())return false;
  if(expression instanceof org.tzi.use.uml.ocl.expr.ExpStdOp operation)return Set.of("and","or","not","implies","=","<>",">",">=","<","<=","+","-","*","/").contains(operation.opname())&&Arrays.stream(operation.args()).allMatch(DependencyGoalEvaluation::safe);
  if(expression instanceof org.tzi.use.uml.ocl.expr.ExpAttrOp attribute)return !attribute.attr().isDerived()&&safe(attribute.objExp());
  if(expression instanceof org.tzi.use.uml.ocl.expr.ExpExists||expression instanceof org.tzi.use.uml.ocl.expr.ExpForAll) {
   var query=(org.tzi.use.uml.ocl.expr.ExpQuery)expression;return safe(query.getRangeExpression())&&safe(query.getQueryExpression());
  }
  return expression instanceof org.tzi.use.uml.ocl.expr.ExpAllInstances||expression instanceof org.tzi.use.uml.ocl.expr.ExpVariable||expression instanceof org.tzi.use.uml.ocl.expr.ExpConstBoolean||expression instanceof org.tzi.use.uml.ocl.expr.ExpConstInteger||expression instanceof org.tzi.use.uml.ocl.expr.ExpConstReal||expression instanceof org.tzi.use.uml.ocl.expr.ExpConstString||expression instanceof org.tzi.use.uml.ocl.expr.ExpConstEnum;
 }
 public void clear(){entries.clear();}
 public Map<String,Object> metrics(){return Map.of("cacheHits",hits,"executed",executed,"entries",entries.size());}
}
