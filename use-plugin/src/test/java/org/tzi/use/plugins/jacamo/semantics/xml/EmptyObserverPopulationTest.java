package org.tzi.use.plugins.jacamo.semantics.xml;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.semantics.ocl.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import static org.junit.jupiter.api.Assertions.*;
class EmptyObserverPopulationTest {
 @TempDir java.nio.file.Path temp;
 @Test void completeEmptyTableHasACurrentCertificateAndPartialSourceRevokesIt()throws Exception{
  var helper=new XmlObserverProjectionTest();helper.temp=temp;
  var fixture=helper.helper().fixture(XmlObserverProjectionTest.RUNTIME,XmlObserverProjectionTest.OBSERVERS);var engine=fixture.engine();
  engine.applyMappingCut(helper.cut(helper.helper(),fixture,1,3,null,false),"run",1,List.of());
  var type=engine.system().model().getClass("SignalView");assertTrue(engine.system().state().objectsOfClass(type).isEmpty());
  var resolver=new ConstraintCoverageResolver();assertTrue(resolver.populationComplete(type,Map.of("cartago",Completeness.COMPLETE),engine));
  var invariant=new UseModelApi(engine.system().model()).createInvariant("NoPresentViolation","SignalView","self.current",false);
  var prepared=new SemanticInvariantEvaluator().prepareScoped("empty",invariant,engine,Map.of("cartago",Completeness.COMPLETE),false,Set.of(),"");
  assertTrue(prepared.complete());assertTrue(prepared.bodies().isEmpty());
  assertEquals(SemanticVerdict.PASS,SemanticInvariantEvaluator.evaluateScoped(prepared,engine.system().state()).getLast().verdict(),"Native universal quantification over a proved empty set");
  var exists=new SemanticInvariantEvaluator.Prepared(prepared.id(),prepared.context(),prepared.text(),prepared.variable(),prepared.expression(),List.of(),true,true,false,"",prepared.witness());
  assertEquals(SemanticVerdict.FAIL,SemanticInvariantEvaluator.evaluateScoped(exists,engine.system().state()).getLast().verdict(),"Existence/goal completion is never fabricated from an empty population");
  var missing=new SemanticInvariantEvaluator.Prepared(prepared.id(),prepared.context(),prepared.text(),prepared.variable(),prepared.expression(),List.of(),false,false,false,"",prepared.witness());
  assertNotEquals(SemanticVerdict.PASS,SemanticInvariantEvaluator.evaluateScoped(missing,engine.system().state()).getLast().verdict());
  engine.applyMappingCut(helper.helper().cut(fixture,2,Map.of("A",3),Completeness.PARTIAL),"run",1,List.of());
  assertFalse(resolver.populationComplete(type,Map.of("cartago",Completeness.COMPLETE),engine),"Old certificate cannot survive a newer partial cut");
 }
}
