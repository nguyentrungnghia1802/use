package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystemState;
import static org.junit.jupiter.api.Assertions.*;
public class XmlNativePreparationTest {
    @TempDir Path temp;
    private MappingInputs input(boolean operation)throws Exception {
        var fixture=new GenericMappingEngineTest();fixture.temp=temp;var original=fixture.input();
        String xml=Files.readString(temp.resolve("mapping.xml"));
        String extra="<oclClause name=\"Nonnegative\" context=\"context Counter\"/>";
        if(operation) {
            String ocl="context Counter inv Nonnegative: self.p >= 0\ncontext Counter::increment()\npre Allowed: self.p >= 0\npost Increased: self.p = self.p@pre + 1\n";
            Files.writeString(temp.resolve("requirements.ocl"),ocl);
            xml=xml.replace(original.ocl().sha256(),GenericMappingEngineTest.hash(temp.resolve("requirements.ocl")));
            String fqcn=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM).semanticContract().artifactDeclarations().getFirst().javaClass();
            extra+="<oclClause name=\"Allowed\" context=\"context Counter::increment()\"/><oclClause name=\"Increased\" context=\"context Counter::increment()\"/>";
            extra+="<operation id=\"increment-interface\" owner=\"Counter\" signature=\"[&quot;"+fqcn+"&quot;,&quot;increment&quot;,[],&quot;void&quot;]\" clauses=\"Allowed Increased\"/>";
        }
        Files.writeString(temp.resolve("mapping.xml"),xml.replace("</bindings>",extra+"</bindings>"));
        return read();
    }
    private MappingInputs read(){return MappingInputs.read(GenericMappingEngineTest.JCM,temp.resolve("mapping.xml"),temp.resolve("requirements.ocl"));}
    public static CodeGroundedNativePipeline.CanonicalBuildResult operationFixture(Path directory)throws Exception {
        var fixture=new XmlNativePreparationTest();fixture.temp=Files.createDirectories(directory);
        var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);
        return new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,new GenericMappingEngine().map(source,fixture.input(true)));
    }
    @Test void genericProgramBuildsOneNativeCandidateWithAuthoredInvariantAndGeneratedGoalCondition()throws Exception {
        var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);var program=new GenericMappingEngine().map(source,input(false));
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        assertNotNull(built.xmlPreparation());assertFalse(java.util.Arrays.stream(org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram.class.getDeclaredFields()).anyMatch(field->field.getName().equals("casePlan")));assertSame(built.binding().model(),built.nativeResult().state().system().model());
        assertEquals(1,built.xmlPreparation().authored().clauses().size());assertEquals(1,built.contracts().contracts().size());
        assertEquals("GOAL:Positive",built.contracts().contracts().getFirst().id());assertEquals(0,built.nativeResult().state().system().state().numObjects());
    }
    @Test void exactProducerOperationAndNativeAtPreUseTheSameSystem()throws Exception {
        var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);var program=new GenericMappingEngine().map(source,input(true));
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        assertEquals(1,built.xmlPreparation().operations().size());assertEquals(2,built.binding().model().prePostConditions().size());
        var system=built.nativeResult().state().system();var api=UseSystemApi.create(system,false);var self=api.createObject("Counter","counter");api.setAttributeValue("counter","p","4");
        var before=new MSystemState("before",system.state());api.setAttributeValue("counter","p","5");var variables=new VarBindings();variables.push("self",self.value());
        var post=built.xmlPreparation().authored().clauses().stream().filter(c->c.specification().name().equals("Increased")).findFirst().orElseThrow();
        assertEquals(BooleanValue.TRUE,new Evaluator().eval(post.expression(),before,system.state(),variables));
        assertEquals(BooleanValue.FALSE,new Evaluator().eval(post.expression(),system.state(),system.state(),variables));assertSame(system,before.system());
    }
    @Test void unprovedOperationAndWrongGoalTypeCannotPublishOrFallback()throws Exception {
        var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);input(true);
        Path file=temp.resolve("mapping.xml");String valid=Files.readString(file);
        for(String xml:List.of(valid.replace("&quot;increment&quot;","&quot;nonexistent&quot;"),valid.replace("<condition>self.p &gt;= 0</condition>","<condition>self.p + 1</condition>").replace("<condition>self.p >= 0</condition>","<condition>self.p + 1</condition>"))) {
            Files.writeString(file,xml);var program=new GenericMappingEngine().map(source,read());
            assertThrows(IllegalArgumentException.class,()->new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program));
        }
    }
    @Test void observerTupleTaskPredicatesAndExternalInvariantAreNativeHelpersNotCslEntities()throws Exception {
        var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);input(false);
        Path file=temp.resolve("mapping.xml");String xml=Files.readString(file)
            .replace("<goal id=\"positive\" name=\"Positive\" type=\"Achieve\"><condition>self.p >= 0</condition></goal>",
                "<task id=\"positive\" name=\"Positive\"><pre>step.enabled</pre><post>step.succeeded and self.p >= self.p@pre</post></task>")
            .replace("reason=\"Authored world condition, not an agent mental goal\"/>","reason=\"Explicit observed execution abstraction\"><variable name=\"step\" viewRef=\"step-view\" origin=\"exact-invocation-callback\"/></elementRuntime>")
            .replace("<observers/>","<observers><view id=\"step-view\" name=\"step\" category=\"TUPLE\" keyFields=\"run invocation\"><field name=\"enabled\" type=\"Boolean\" origin=\"exact-enabling\"/><field name=\"succeeded\" type=\"Boolean\" origin=\"exact-terminal-success\"/></view><view id=\"monitor\" name=\"DispatchMonitor\" category=\"CLASS\" keyFields=\"run invocation\"><field name=\"valid\" type=\"Boolean\" origin=\"accepted-observer-evidence\"/></view></observers>");
        String ocl=Files.readString(temp.resolve("requirements.ocl"))+"context DispatchMonitor inv Coherent: self.valid\n";
        var original=read();Files.writeString(temp.resolve("requirements.ocl"),ocl);
        xml=xml.replace(original.ocl().sha256(),GenericMappingEngineTest.hash(temp.resolve("requirements.ocl")))
            .replace("</bindings>","<oclClause name=\"Coherent\" context=\"context DispatchMonitor\"/></bindings>");
        Files.writeString(file,xml);var program=new GenericMappingEngine().map(source,read());
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        assertEquals(1,program.cslModel().entities().size());assertNull(built.binding().model().getClass("step"));
        assertNotNull(built.binding().model().getClass("DispatchMonitor"));assertEquals(2,built.contracts().contracts().size());
        assertEquals(2,built.xmlPreparation().authored().clauses().size());
        var post=built.contracts().contracts().stream().filter(c->c.id().equals("TASK:Positive:post")).findFirst().orElseThrow();
        assertTrue(post.specification().requiresBefore());assertEquals(built.xmlPreparation().observers().valueType("step-view"),post.specification().bindings().get("step"));
        try(var backend=new org.tzi.use.semantic.engine.use75.NativeOclBackend(built.binding().model(),program.hash(),program.profile().engine().name())) {
            var compiled=backend.compile(post.specification());var system=built.nativeResult().state().system();
            var outcome=backend.evaluate(compiled,org.tzi.use.semantic.engine.use75.UseStateReadView.pin(null,system.state()),Map.of(),"PARTIAL");
            assertEquals(org.tzi.use.semantic.engine.use75.NativeOclOutcome.Status.SKIPPED,outcome.status());assertNull(outcome.knownBoolean());
        }
    }
}
