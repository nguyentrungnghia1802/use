package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticResultAdapter;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ExecutionScopeViews;
import static org.junit.jupiter.api.Assertions.*;
class AuthoredPresentationTest {
 @TempDir Path temp;
 @Test void authoredTypesProduceAnExactFrozenGraphWithoutCasePresentationXml()throws Exception{
  var helper=new XmlRuntimeMappingTest();helper.temp=temp;var f=helper.fixture();var engine=f.engine();
  assertTrue(f.built().program().xmlPlan().inputs().specification().root().children("presentation").isEmpty());
  engine.applyMappingCut(helper.cut(f,1,Map.of("A",3),Completeness.COMPLETE),"run",1,List.of());
  var method=SemanticResultAdapter.class.getDeclaredMethod("presentation",SemanticProgram.class,org.tzi.use.uml.sys.MSystemState.class);method.setAccessible(true);
  var before=new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().export(engine.system()).commands();
  var view=ExecutionScopeViews.object(method.invoke(null,f.built().program(),engine.system().state()));
  assertEquals("AUTHORED_DECLARATIONS/1.0",view.get("selectionPolicy"));assertEquals(Boolean.TRUE,view.get("qualifiedValues"));
  assertEquals(1,ExecutionScopeViews.object(view.get("objects")).size());assertTrue(view.toString().contains("p=3"));
  assertEquals(before,new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().export(engine.system()).commands(),"Projection cannot modify native state");
  engine.applyMappingCut(helper.cut(f,2,Map.of("A",4),Completeness.PARTIAL),"run",1,List.of());
  var partial=ExecutionScopeViews.object(method.invoke(null,f.built().program(),engine.system().state()));assertEquals(Boolean.FALSE,partial.get("qualifiedValues"));
 }
}
