package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import static org.junit.jupiter.api.Assertions.*;
class ModelNativeContractsTest {
 @TempDir Path temp;
 MappingInputs fixture(String body,String optional)throws Exception{
  var helper=new GenericMappingEngineTest();helper.temp=temp;var base=helper.input();var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);var generated=new GenericMappingEngine().map(source,base);
  Path csl=Files.writeString(temp.resolve("case.csl"),generated.rules().cslSource()+"\n/* @use-native-ocl 1\ncontext Counter inv Embedded: "+body+"\n*/\n");
  Path goal=Files.writeString(temp.resolve("case.istar"),generated.rules().iStarSource());
  Path ocl=Files.writeString(temp.resolve("requirements.ocl"),optional);
  String xml=Files.readString(temp.resolve("mapping.xml")).replaceFirst("(?s)<modelRules .*?</modelRules>","<modelRules targetVersion=\"v4.0\" targetModel=\"CounterView\"/>").replaceFirst("(?s)<istar .*?</istar>","<istar name=\"Requirements\"/>").replace(base.ocl().sha256(),GenericMappingEngineTest.hash(ocl)).replace("<selectors>","<models mode=\"AUTHORED\" cslSha256=\""+GenericMappingEngineTest.hash(csl)+"\" istarSha256=\""+GenericMappingEngineTest.hash(goal)+"\"/><selectors>").replace("</bindings>","<oclClause name=\"Embedded\" context=\"context Counter\"/></bindings>");
  Path mapping=Files.writeString(temp.resolve("mapping.xml"),xml);
  return MappingInputs.read(GenericMappingEngineTest.JCM,mapping,ocl,csl,goal);
 }
 @Test void embeddedContractRunsOnNativeUseAndEmptyOptionalFileIsValid()throws Exception {
  var inputs=fixture("false","-- Optional additional constraints only\n");var spec=ModelNativeContracts.read(inputs);assertEquals(1,spec.clauses().size());
  var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);var program=new GenericMappingEngine().map(source,inputs);var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
  var system=built.nativeResult().state().system();var api=org.tzi.use.api.UseSystemApi.create(system,false);var self=api.createObject("Counter","actual");
  var expression=built.xmlPreparation().authored().clauses().getFirst().expression();var variables=new org.tzi.use.uml.ocl.value.VarBindings(system.state());variables.push("self",self.value());assertEquals("false",new org.tzi.use.uml.ocl.expr.Evaluator().eval(expression,system.state(),variables).toString());
  var recording=new org.tzi.use.plugins.jacamo.semantics.evidence.SemanticProgramManifest().encode(program);assertEquals(program.hash(),new org.tzi.use.plugins.jacamo.semantics.evidence.SemanticProgramManifest().decode(recording).program().hash());
 }
 @Test void unknownContextNonBooleanAndDuplicateExternalContractFailClosed()throws Exception {
  var inputs=fixture("1 + 2","-- none\n");var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);var program=new GenericMappingEngine().map(source,inputs);assertThrows(IllegalArgumentException.class,()->new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program));
  inputs=fixture("true","context Counter inv Embedded: true\n");var duplicate=inputs;assertThrows(IllegalArgumentException.class,()->ModelNativeContracts.read(duplicate));
 }
 @Test void markerIsExplicitVersionedAndNotDetectedInsideStringsOrLineComments(){
  for(String text:List.of("/* @use-native-ocl 2\ncontext X inv A: true\n*/","/* @use-native-ocl 1\n*/","/* @use-native-ocl 1"))assertThrows(IllegalArgumentException.class,()->ModelNativeContracts.blocks(text.getBytes(StandardCharsets.UTF_8)));
  assertTrue(ModelNativeContracts.blocks("// /* @use-native-ocl 1\n'/* @use-native-ocl 1 */'".getBytes(StandardCharsets.UTF_8)).isEmpty());
 }
}
