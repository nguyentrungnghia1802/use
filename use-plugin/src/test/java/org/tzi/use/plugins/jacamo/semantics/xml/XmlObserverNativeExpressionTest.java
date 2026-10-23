package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;

/** Typed observer transformation uses the host compiler/evaluator, never a substitute OCL implementation. */
class XmlObserverNativeExpressionTest {
    @TempDir java.nio.file.Path temp;
    static final String OBSERVERS="""
      <observers><view id="summary" name="ShipmentSummary" category="CLASS" keyFields="uuid">
       <field name="reported" type="Boolean" origin="qualified successful invocation set"/>
       <field name="count" type="Integer" origin="qualified successful invocation set"/>
      </view><project id="summaries" viewRef="summary" tableRef="flags" keyFields="uuid">
       <derive field="reported" expression="calls->notEmpty()" completeColumn="history"><arg name="calls" column="calls" type="Set(String)"/></derive>
       <derive field="count" expression="calls->size()" completeColumn="history"><arg name="calls" column="calls" type="Set(String)"/></derive>
      </project></observers>
      """;
    record Fixture(XmlRuntimeMappingTest.Fixture nativeState,ObserverProjection projection,Port port) { }
    Fixture fixture(String observers)throws Exception {
        var helper=new XmlRuntimeMappingTest();helper.temp=temp;
        var runtime=XmlObserverProjectionTest.RUNTIME.replace("<column name=\"flag\"","<column name=\"calls\" path=\"values.calls\" required=\"false\"/><column name=\"flag\"");
        var f=helper.fixture(runtime,observers);var access=f.engine().getClass().getDeclaredMethod("cslPort");assertTrue(access.trySetAccessible());
        return new Fixture(f,new ObserverProjection(f.built().program().xmlPlan().inputs().specification(),f.engine().system().model(),f.engine().canonicalProgramHash()),(Port)access.invoke(f.engine()));
    }
    static RuntimeTables.Result row(Map<String,Object> values,Completeness quality){return new RuntimeTables.Result(List.of(new RuntimeTables.Row("physical",values,quality,Map.of("component","qualified collection fixture"),Set.of("physical"))),Completeness.PARTIAL,List.of());}
    @Test void explicitUndefinedArgumentIsPassedToNativeOclWithoutInventingATerminalTime()throws Exception {
        String schema=OBSERVERS.replace("calls->size()","if calls->isEmpty() then -1 else terminal endif")
            .replace("</derive>\n</project>","<arg name=\"terminal\" column=\"flag\" type=\"Integer\" onMissing=\"undefined\"/></derive>\n</project>");
        var f=fixture(schema);var engine=f.nativeState().engine();
        f.projection().apply(f.port(),"epoch","active",Map.of("flags",row(Map.of("uuid","one","calls",List.of(),"history",true),Completeness.COMPLETE)));
        var object=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("ShipmentSummary")).findFirst().orElseThrow();
        assertEquals(IntegerValue.valueOf(-1),object.state(engine.system().state()).attributeValue("count"));
        f.projection().apply(f.port(),"epoch","terminal-missing",Map.of("flags",row(Map.of("uuid","one","calls",List.of("terminal"),"history",true),Completeness.COMPLETE)));
        assertTrue(object.state(engine.system().state()).attributeValue("count").isUndefined());assertEquals("UNDEFINED",f.port().metadata(object,"xml:observer:quality:count"));
        final String invalid=schema.replace("onMissing=\"undefined\"","onMissing=\"zero\"");assertThrows(IllegalArgumentException.class,()->fixture(invalid));
    }
    @Test void nativeUndefinedTestCannotTurnMissingInputIntoAQualifiedDefault()throws Exception {
        String schema=OBSERVERS.replace("calls->size()","if terminal.oclIsUndefined() then 0 else terminal endif")
            .replace("</derive>\n</project>","<arg name=\"terminal\" column=\"flag\" type=\"Integer\" onMissing=\"undefined\"/></derive>\n</project>");
        var f=fixture(schema);var engine=f.nativeState().engine();
        f.projection().apply(f.port(),"epoch","missing-terminal",Map.of("flags",row(Map.of("uuid","one","calls",List.of(),"history",true),Completeness.COMPLETE)));
        var object=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("ShipmentSummary")).findFirst().orElseThrow();
        assertTrue(object.state(engine.system().state()).attributeValue("count").isUndefined(),"Native OCL may evaluate the expression, but missing source knowledge must remain unavailable");
        assertEquals("INCOMPLETE",f.port().metadata(object,"xml:observer:quality:count"));
    }
    @Test void nativeCollectionExpressionsStayTypedOnSameStateAndMissingCoverageIsNeverFalse()throws Exception {
        var f=fixture(OBSERVERS);var engine=f.nativeState().engine();var system=engine.system();
        f.projection().apply(f.port(),"epoch","known-empty",Map.of("flags",row(Map.of("uuid","one","calls",List.of(),"history",true),Completeness.COMPLETE)));
        var object=system.state().allObjects().stream().filter(o->o.cls().name().equals("ShipmentSummary")).findFirst().orElseThrow();
        assertEquals(BooleanValue.FALSE,object.state(system.state()).attributeValue("reported"));assertEquals(IntegerValue.valueOf(0),object.state(system.state()).attributeValue("count"));
        f.projection().apply(f.port(),"epoch","positive",Map.of("flags",row(Map.of("uuid","one","calls",List.of("actor-A","actor-B","actor-A"),"history",true),Completeness.COMPLETE)));
        assertEquals(BooleanValue.TRUE,object.state(system.state()).attributeValue("reported"));assertEquals(IntegerValue.valueOf(2),object.state(system.state()).attributeValue("count"));var saved=engine.savepoint();
        for(var data:List.of(Map.<String,Object>of("uuid","one","history",true),Map.<String,Object>of("uuid","one","calls",List.of(),"history",false))) {
            f.projection().apply(f.port(),"epoch","unknown",Map.of("flags",row(data,Completeness.COMPLETE)));assertTrue(object.state(system.state()).attributeValue("reported").isUndefined());assertEquals("INCOMPLETE",f.port().metadata(object,"xml:observer:quality:reported"));
        }
        f.projection().apply(f.port(),"epoch","bad-type",Map.of("flags",row(Map.of("uuid","one","calls",List.of(false),"history",true),Completeness.COMPLETE)));assertTrue(object.state(system.state()).attributeValue("reported").isUndefined());assertEquals("ERROR",f.port().metadata(object,"xml:observer:quality:reported"));
        engine.rollback(saved);assertEquals(BooleanValue.TRUE,object.state(system.state()).attributeValue("reported"));assertSame(system,engine.system());
    }
    @Test void nativeCompilerRejectsWrongResultTypeUnknownVariableAndGlobalStateQueriesInsteadOfGuessingCoverage() {
        for(String body:List.of("1 + 2","unknownVariable","Counter.allInstances()->size() > 0","if true then Counter.allInstances()->size() > 0 else true endif","Counter.byUseId('x').oclIsUndefined()"))
            assertThrows(IllegalArgumentException.class,()->fixture(OBSERVERS.replace("calls->notEmpty()",body)));
    }
}
