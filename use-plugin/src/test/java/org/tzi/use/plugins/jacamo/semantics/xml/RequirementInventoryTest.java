package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;import java.util.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.AuthoredOclSpecificationLoader;
import org.tzi.use.plugins.jacamo.semantics.runtime.RequirementInventory;
import org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import static org.junit.jupiter.api.Assertions.*;
class RequirementInventoryTest {
 @TempDir Path temp;
 @Test void actualNativeAuthoredHelperInvariantIsMandatoryEvenWhenPopulationUnavailable()throws Exception {
  var built=XmlNativePreparationTest.operationFixture(temp);
  var loader=new AuthoredOclSpecificationLoader();
  var source=loader.parse("context AtomicAgent inv ExplicitRequirement: false","authored.ocl");
  var prepared=loader.compile(source,built.binding(),source.sourceHash(),Map.of("ExplicitRequirement",AuthoredOclSpecificationLoader.parseContext("context AtomicAgent")));
  prepared.install(new org.tzi.use.api.UseModelApi(built.binding().model()));
  var inventory=RequirementInventory.from(built.program(),built.binding().model());
  String id="NATIVE:AtomicAgent::ExplicitRequirement";
  assertTrue(((List<?>)inventory.get("requiredInvariants")).contains(id));
  assertFalse(((List<?>)inventory.get("supportingInvariants")).contains(id));
  assertTrue(((List<?>)inventory.get("requiredInvariants")).contains("NATIVE:Counter::Nonnegative"));
  var engine=new NativeRuntimeMutationEngine(built.nativeResult().state().system(),built.nativeResult().state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry(),built.program());
  var invariant=prepared.clauses().getFirst().invariant();
  var cut=new SemanticInvariantEvaluator().prepareScoped(id,invariant,engine,Map.of("jason",Completeness.PARTIAL,"cartago",Completeness.COMPLETE),false,Set.of(),"");
  var rows=SemanticInvariantEvaluator.evaluateScoped(cut,engine.system().state());
  assertEquals(SemanticVerdict.SKIPPED,rows.getLast().verdict());
  assertEquals("UNPROVEN",rows.getLast().witness().get("population"));
  assertSame(built.nativeResult().state().system(),engine.system());
 }
}
