package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import static org.junit.jupiter.api.Assertions.*;

class XmlObserverReferenceTest {
    @TempDir java.nio.file.Path temp;
    static final String VIEWS="""
        <observers>
        <view id="monitor" name="ShipmentMonitor" category="CLASS" keyFields="record"><field name="ready" type="Boolean" origin="current source"/></view>
        <view id="activation" name="ShipmentDuty" category="CLASS" keyFields="record"><field name="monitor" type="ShipmentMonitor" origin="exact board and domain occurrence"/></view>
        <project id="monitors" viewRef="monitor" tableRef="flags" keyFields="record"><write field="ready" column="flag" mode="current"/></project>
        <project id="duties" viewRef="activation" tableRef="flags" keyFields="record">
          <reference field="monitor" targetProject="monitors" subjectColumn="target" mode="current"><key field="record" column="targetKey"/></reference>
        </project></observers>
        """;
    record Fixture(XmlRuntimeMappingTest.Fixture nativeState,ObserverProjection projection,Port port) { }
    Fixture fixture(String observers)throws Exception {
        var helper=new XmlRuntimeMappingTest();helper.temp=temp;
        String runtime=XmlObserverProjectionTest.RUNTIME.replace("<column name=\"flag\"", "<column name=\"record\" path=\"identity\" required=\"true\"/><column name=\"target\" path=\"identity\" required=\"true\"/><column name=\"targetKey\" path=\"identity\" required=\"true\"/><column name=\"flag\"");
        var f=helper.fixture(runtime,observers);var access=f.engine().getClass().getDeclaredMethod("cslPort");assertTrue(access.trySetAccessible());
        return new Fixture(f,new ObserverProjection(f.built().program().xmlPlan().inputs().specification(),f.engine().system().model(),f.engine().canonicalProgramHash()),(Port)access.invoke(f.engine()));
    }
    static RuntimeTables.Result rows(String target,String key,boolean includeSecond) {
        var rows=new ArrayList<RuntimeTables.Row>();rows.add(new RuntimeTables.Row("physical-one",Map.of("record","one","target",target,"targetKey",key,"flag",true),Completeness.COMPLETE,Map.of("component","qualified source fixture"),Set.of("physical-one")));
        if(includeSecond)rows.add(new RuntimeTables.Row("physical-two",Map.of("record","two","target","physical-two","targetKey","two","flag",false),Completeness.COMPLETE,Map.of(),Set.of("physical-two")));
        return new RuntimeTables.Result(rows,Completeness.COMPLETE,List.of());
    }
    static List<org.tzi.use.uml.sys.MLink> links(Fixture f){return f.nativeState().engine().system().state().allLinks().stream().filter(l->l.association().name().equals("Observer_activation_monitor")).toList();}
    @Test void exactSubjectAndTupleReferenceUsesSameNativeAssociationAndUnknownDisconnectsAndRollbackRestores()throws Exception {
        var f=fixture(VIEWS);var engine=f.nativeState().engine();var system=engine.system();
        f.projection().apply(f.port(),"epoch","cut",Map.of("flags",rows("physical-one","one",true)));assertEquals(2,links(f).size());
        var saved=engine.savepoint();var first=links(f).stream().filter(l->"physical-one".equals(f.port().metadata(l.linkedObjects().getFirst(),"xml:observer:subject"))).findFirst().orElseThrow();
        assertEquals("physical-one",f.port().metadata(first.linkedObjects().get(1),"xml:observer:subject"));
        f.projection().apply(f.port(),"epoch","bad-key",Map.of("flags",rows("physical-one","two",true)));assertEquals(1,links(f).size());
        assertEquals("INCOMPLETE",f.port().metadata(first.linkedObjects().getFirst(),"xml:observer:quality:monitor"));
        engine.rollback(saved);assertEquals(2,links(f).size());assertSame(system,engine.system());
        f.projection().apply(f.port(),"epoch","missing",Map.of("flags",new RuntimeTables.Result(List.of(),Completeness.PARTIAL,List.of())));assertTrue(links(f).isEmpty());
    }
    @Test void retainedBindingCannotMoveToDifferentPhysicalOccurrenceAndHistoricalReferenceIsExplicit()throws Exception {
        var f=fixture(VIEWS.replace("mode=\"current\"><key","mode=\"retain-bound\"><key"));
        f.projection().apply(f.port(),"epoch","cut",Map.of("flags",rows("physical-one","one",true)));assertEquals(2,links(f).size());
        f.projection().apply(f.port(),"epoch","missing",Map.of("flags",new RuntimeTables.Result(List.of(),Completeness.PARTIAL,List.of())));assertEquals(2,links(f).size());
        assertTrue(links(f).stream().allMatch(l->f.port().metadata(l.linkedObjects().getFirst(),"xml:observer:quality:monitor").equals("HISTORICAL_COMPLETE")));
        f.projection().apply(f.port(),"epoch","replacement",Map.of("flags",rows("physical-two","two",true)));assertEquals(1,links(f).size());
        var first=f.nativeState().engine().system().state().allObjects().stream().filter(o->o.cls().name().equals("ShipmentDuty")&&f.port().metadata(o,"xml:observer:subject").equals("physical-one")).findFirst().orElseThrow();
        assertEquals("ERROR",f.port().metadata(first,"xml:observer:quality:monitor"));
        f.projection().apply(f.port(),"epoch","repeated-original",Map.of("flags",rows("physical-one","one",true)));
        assertEquals("ERROR",f.port().metadata(first,"xml:observer:quality:monitor"),"Conflicting retained identity must not silently recover in the same epoch");assertEquals(1,links(f).size());
    }
    @Test void unresolvedForwardOrWrongTypeReferenceRejectsAtCompile() {
        for(String invalid:List.of(VIEWS.replace("targetProject=\"monitors\"","targetProject=\"duties\""),VIEWS.replace("field=\"record\" column=\"targetKey\"","field=\"wrong\" column=\"targetKey\""),VIEWS.replace("mode=\"current\"><key","mode=\"guess\"><key")))assertThrows(IllegalArgumentException.class,()->fixture(invalid));
    }
}
