package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.Path;import java.util.*;
import org.junit.jupiter.api.*;import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.semantics.runtime.ExecutionConstraintScope;
import org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.*;
import org.tzi.use.semantic.engine.use75.*;
import static org.junit.jupiter.api.Assertions.*;
class ExecutionConstraintScopeTest {
 @TempDir Path temp;
 @Test void sameNativePredicateUsesExactOwnersAndPreservesNegationAndUnavailableEvidence()throws Exception {
  var helper=new XmlRuntimeMappingTest();helper.temp=temp;var f=helper.fixture();var engine=f.engine();
  engine.applyMappingCut(helper.cut(f,1,Map.of("A",3,"B",-2),Completeness.COMPLETE),"run",1,List.of());
  var a=engine.objectForSemanticId(XmlRuntimeMappingTest.id("A").canonical());var b=engine.objectForSemanticId(XmlRuntimeMappingTest.id("B").canonical());
  try(var backend=new NativeOclBackend(engine.system().model(),"test","execution-scope")){
   var expression=backend.compile(new OclContractSpec("positive",OclContractSpec.Kind.GOAL,a.cls(),null,"self.p >= 0")).expression();
   var bodies=List.of(new SemanticInvariantEvaluator.PreparedBody(a.name(),"sourceA",a.value(),"",Map.of()),new SemanticInvariantEvaluator.PreparedBody(b.name(),"sourceB",b.value(),"",Map.of()));
   for(boolean negated:List.of(false,true)){
    var global=new SemanticInvariantEvaluator.Prepared("Rule","Counter","self.p >= 0","self",expression,bodies,true,false,negated,"",Map.of());
    var nativeChecks=new PreparedEvaluation(engine.system().model(),List.of(new PreparedInvariant("Rule","Counter",expression,expression,null,"","",global)));
    for(var object:List.of(a,b)){
     var view=Map.<String,Object>of("owners",Map.of(object.name(),Map.of()),"complete",true);
     var prepared=ExecutionConstraintScope.prepare(nativeChecks,view);
     assertEquals(1,prepared.getFirst().bodies().size());assertEquals(object.name(),prepared.getFirst().bodies().getFirst().object());
     var rows=ExecutionConstraintScope.evaluate(prepared,engine.system().state(),List.of(),view,"scope:"+object.name(),1);
     assertEquals((object==a)^negated?SemanticVerdict.PASS:SemanticVerdict.FAIL,rows.getLast().verdict());
    }
    var partial=Map.<String,Object>of("owners",Map.of(a.name(),Map.of()),"complete",false);
    var missing=ExecutionConstraintScope.evaluate(ExecutionConstraintScope.prepare(nativeChecks,partial),engine.system().state(),List.of(),partial,"scopeA",1);
    assertFalse(Set.of(SemanticVerdict.PASS,SemanticVerdict.FAIL).contains(missing.getLast().verdict()),"A qualified positive body cannot prove the quantified invariant while its owner population is incomplete");
    var counterexample=Map.<String,Object>of("owners",Map.of(b.name(),Map.of()),"complete",false);
    assertEquals(negated?SemanticVerdict.PASS:SemanticVerdict.FAIL,ExecutionConstraintScope.evaluate(ExecutionConstraintScope.prepare(nativeChecks,counterexample),engine.system().state(),List.of(),counterexample,"partial-with-counterexample",1).getLast().verdict(),"A qualified false body remains a valid counterexample despite incomplete owner enumeration");
    var unavailableBody=new SemanticInvariantEvaluator.PreparedBody(a.name(),"sourceA",a.value(),"XML_FIELD_UNAVAILABLE",Map.of());
    var noValue=new SemanticInvariantEvaluator.Prepared("Rule","Counter","self.p >= 0","self",expression,List.of(unavailableBody),true,false,negated,"",Map.of());
    var unavailableChecks=new PreparedEvaluation(engine.system().model(),List.of(new PreparedInvariant("Rule","Counter",expression,expression,null,"","",noValue)));
    var bound=Map.<String,Object>of("owners",Map.of(a.name(),Map.of()),"complete",true);
    assertTrue(ExecutionConstraintScope.evaluate(ExecutionConstraintScope.prepare(unavailableChecks,bound),engine.system().state(),List.of(),bound,"scopeA",1).stream().noneMatch(r->Set.of(SemanticVerdict.PASS,SemanticVerdict.FAIL).contains(r.verdict())),"Missing scalar evidence cannot be read as either truth value");
    var absent=Map.<String,Object>of("owners",Map.of(),"complete",true);
    assertNotEquals(SemanticVerdict.PASS,ExecutionConstraintScope.evaluate(ExecutionConstraintScope.prepare(nativeChecks,absent),engine.system().state(),List.of(),absent,"missing",1).getLast().verdict(),"No vacuous completion for an unmapped owner");
   }
  }
 }
 @Test void explicitScopeAndOccurrenceProofsCannotLeakThroughASharedOwner(){
  var own=new org.tzi.use.plugins.jacamo.semantics.evidence.SemanticConstraintResult("Post","TASK_POST","Shared","obj","","true",SemanticVerdict.PASS,"true","",1,"op1",Map.of("scope","here","occurrence","own"));
  var foreign=new org.tzi.use.plugins.jacamo.semantics.evidence.SemanticConstraintResult("Post","TASK_POST","Shared","obj","","false",SemanticVerdict.FAIL,"false","",1,"op2",Map.of("scope","there","occurrence","foreign"));
  var view=Map.<String,Object>of("owners",Map.of("obj",Map.of()),"occurrenceIds",List.of("own"));
  var rows=ExecutionConstraintScope.evaluate(List.of(),null,List.of(own,foreign),view,"here",1);
  assertEquals(1,rows.size());assertEquals("op1",rows.getFirst().invocation());
 }

}
