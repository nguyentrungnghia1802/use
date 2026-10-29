package org.tzi.use.plugins.jacamo.semantics;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.CslScopeModelAdapter;
import org.tzi.use.plugins.jacamo.semantics.runtime.DependencyGoalEvaluation;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.ocl.value.Value;
import static org.junit.jupiter.api.Assertions.*;
class DependencyGoalEvaluationTest {
 @Test void dependencyMemoAvoidsUnrelatedAttributeEvaluationsButRechecksChangedValueAndCoverage()throws Exception {
  var csl=org.vnu.sme.goal.dsl.csl.parser.CslCompiler.compile("csl v4.0 CacheCase { entity Item { attribute p : Integer required mutable; attribute noise : Integer required mutable; } }").model();
  var model=new CslScopeModelAdapter().compile(csl).model();var system=new MSystem(model);var api=UseSystemApi.create(system,false);var self=api.createObject("Item","item");api.setAttributeValue("item","p","1");api.setAttributeValue("item","noise","0");
  var memo=new DependencyGoalEvaluation();try(var backend=new NativeOclBackend(model,"test","memo")) {
   var expression=backend.compile(new OclContractSpec("positive",OclContractSpec.Kind.GOAL,self.cls(),null,"self.p >= 0"));var vars=Map.<String,Value>of("self",self.value());
   assertEquals(NativeOclOutcome.Status.PASS,memo.evaluate(backend,expression,UseStateReadView.pin(null,system.state()),vars,"").status());long evaluations=backend.metrics().evaluations();
   api.setAttributeValue("item","noise","2");assertEquals(NativeOclOutcome.Status.PASS,memo.evaluate(backend,expression,UseStateReadView.pin(null,system.state()),vars,"").status());assertEquals(evaluations,backend.metrics().evaluations());
   api.setAttributeValue("item","p","-1");assertEquals(NativeOclOutcome.Status.FAIL,memo.evaluate(backend,expression,UseStateReadView.pin(null,system.state()),vars,"").status());
   assertEquals(NativeOclOutcome.Status.SKIPPED,memo.evaluate(backend,expression,UseStateReadView.pin(null,system.state()),vars,"COVERAGE_LOST").status());
   memo.clear();memo.evaluate(backend,expression,UseStateReadView.pin(null,system.state()),vars,"");assertEquals(evaluations+2,backend.metrics().evaluations());
  }
 }
 @Test void unsupportedCollectionDependencyFallsBackToNativeEvaluationOnEveryCut()throws Exception {
  var csl=org.vnu.sme.goal.dsl.csl.parser.CslCompiler.compile("csl v4.0 FallbackCase { entity Item { attribute p : Integer required mutable; } }").model();
  var model=new CslScopeModelAdapter().compile(csl).model();var system=new MSystem(model);var api=UseSystemApi.create(system,false);var self=api.createObject("Item","item");api.setAttributeValue("item","p","1");
  var memo=new DependencyGoalEvaluation();try(var backend=new NativeOclBackend(model,"fallback","memo")) {
   var expression=backend.compile(new OclContractSpec("selected",OclContractSpec.Kind.GOAL,model.getClass("Item"),null,"Item.allInstances()->select(x | x.p >= 0)->size() = 1"));
   for(int i=0;i<2;i++)assertEquals(NativeOclOutcome.Status.PASS,memo.evaluate(backend,expression,UseStateReadView.pin(null,system.state()),Map.of("self",self.value()),"").status());
   assertEquals(2,backend.metrics().evaluations());assertEquals(0L,memo.metrics().get("cacheHits"));
  }
 }

}
