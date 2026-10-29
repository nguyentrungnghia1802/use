package org.tzi.use.plugins.jacamo.semantics.xml;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;
/** Snapshot component with declared source fields; no claim of source-born history or live Auction acceptance. */
class XmlObserverProjectionTest {
    @TempDir java.nio.file.Path temp;
    static final String RUNTIME=XmlRuntimeMappingTest.PLAN.replace("</tables>","""
       <table id="prefix" dataset="raw-properties"><where path="values.name" type="String" equals="continuous"/>
        <column name="artifact" path="values.artifactSemanticId" required="true"/><column name="history" path="scalar.value" required="true"/>
       </table><table id="flags" dataset="raw-properties"><where path="values.name" type="String" equals="ready"/>
        <join tableRef="prefix" leftPath="values.artifactSemanticId" rightPath="artifact" as="prefix" cardinality="ONE" required="true"/>
        <join tableRef="properties" leftPath="values.artifactSemanticId" rightPath="artifact" as="state" cardinality="ONE" required="true"/>
        <column name="uuid" path="identity" required="true"/><column name="flag" path="scalar.value" required="true"/>
        <column name="history" path="prefix.history" required="true"/><column name="p" path="state.value" required="true"/><identity mode="PHYSICAL" path="identity"/>
       </table></tables>
      """);
    static final String OBSERVERS="""
      <observers><view id="signal-view" name="SignalView" category="CLASS" keyFields="uuid">
       <field name="current" type="Boolean" origin="current qualified property"/>
       <field name="ever" type="Boolean" origin="declared observed-prefix latch, not current domain state"/>
       <field name="frozen" type="Integer" origin="first accepted qualifying row, not internal framework transition time"/>
      </view><project id="signals" viewRef="signal-view" tableRef="flags" keyFields="uuid">
       <write field="current" column="flag" mode="current"/>
       <write field="ever" column="flag" mode="latch-true" historyColumn="history"/>
       <write field="frozen" column="p" mode="freeze-first-qualified" historyColumn="history" whenColumn="flag"/>
      </project></observers>
      """;
    XmlRuntimeMappingTest helper(){var helper=new XmlRuntimeMappingTest();helper.temp=temp;return helper;}
    RuntimeSnapshot cut(XmlRuntimeMappingTest helper,XmlRuntimeMappingTest.Fixture f,int sequence,int p,Boolean flag,boolean history) {
        var base=helper.cut(f,sequence,Map.of("A",p),Completeness.COMPLETE);var facts=new ArrayList<>(base.facts());
        if(flag!=null)for(var entry:Map.of("ready",flag,"continuous",history).entrySet()) {
            var id=new BridgeEntityId("cartago","environment","observable-property-snapshot","physical:A",entry.getKey(),"snapshot");
            facts.add(new RuntimeFact(id,RuntimeFactKind.PROPERTY,Map.of("artifactSemanticId","physical:A","name",entry.getKey(),"values",List.of(entry.getValue().toString()),"valueTypes",List.of("java.lang.Boolean")),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
        }
        return new RuntimeSnapshot(base.snapshotId(),base.modelRevision(),base.captureStartedAt(),base.captureEndedAt(),base.startWatermarks(),base.endWatermarks(),1,facts,base.sourceCompleteness(),base.stateFingerprint());
    }
    @Test void sameNativeObserverViewHasExplicitCurrentLatchAndFreezeSemanticsWithNoMissingFalse()throws Exception {
        var helper=helper();var f=helper.fixture(RUNTIME,OBSERVERS);var engine=f.engine();var system=engine.system();
        engine.applyMappingCut(cut(helper,f,1,1,false,false),"run",1,List.of());
        var observer=system.state().allObjects().stream().filter(o->o.cls().name().equals("SignalView")).findFirst().orElseThrow();
        assertFalse(observer.cls().isSubClassifierOf(system.model().getClass("DomainEntity")));
        assertEquals(BooleanValue.FALSE,observer.state(system.state()).attributeValue("current"));
        assertTrue(observer.state(system.state()).attributeValue("ever").isUndefined());assertTrue(observer.state(system.state()).attributeValue("frozen").isUndefined());
        engine.applyMappingCut(cut(helper,f,2,2,false,true),"run",1,List.of());assertEquals(BooleanValue.FALSE,observer.state(system.state()).attributeValue("ever"));
        engine.applyMappingCut(cut(helper,f,3,5,true,true),"run",1,List.of());
        assertEquals(BooleanValue.TRUE,observer.state(system.state()).attributeValue("ever"));assertEquals(IntegerValue.valueOf(5),observer.state(system.state()).attributeValue("frozen"));
        var saved=engine.savepoint();engine.applyMappingCut(cut(helper,f,4,9,true,true),"run",1,List.of());assertEquals(IntegerValue.valueOf(5),observer.state(system.state()).attributeValue("frozen"));
        engine.applyMappingCut(cut(helper,f,5,9,null,false),"run",1,List.of());
        assertTrue(observer.state(system.state()).attributeValue("current").isUndefined());assertEquals(BooleanValue.TRUE,observer.state(system.state()).attributeValue("ever"));
        assertEquals(IntegerValue.valueOf(5),observer.state(system.state()).attributeValue("frozen"));
        engine.rollback(saved);assertSame(observer,system.state().objectByName(observer.name()));assertEquals(BooleanValue.TRUE,observer.state(system.state()).attributeValue("current"));
        assertEquals(saved.bindings().toMap(),engine.savepoint().bindings().toMap());assertSame(system,engine.system());
    }
    @Test void explicitlyCurrentUntilFirstQualifiedFreezeDoesNotLatchEarlyOrFallbackOnUnknown()throws Exception {
        var helper=helper();String xml=OBSERVERS.replace("mode=\"freeze-first-qualified\"","mode=\"freeze-first-qualified\" beforeFreeze=\"current\"");var f=helper.fixture(RUNTIME,xml);var engine=f.engine();
        engine.applyMappingCut(cut(helper,f,1,3,false,true),"run",1,List.of());
        var object=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("SignalView")).findFirst().orElseThrow();
        assertEquals(IntegerValue.valueOf(3),object.state(engine.system().state()).attributeValue("frozen"));
        engine.applyMappingCut(cut(helper,f,2,4,false,false),"run",1,List.of());assertTrue(object.state(engine.system().state()).attributeValue("frozen").isUndefined(),"Unknown prefix cannot certify that the freeze condition never held");
        engine.applyMappingCut(cut(helper,f,3,5,true,true),"run",1,List.of());assertEquals(IntegerValue.valueOf(5),object.state(engine.system().state()).attributeValue("frozen"));
        engine.applyMappingCut(cut(helper,f,4,9,false,true),"run",1,List.of());assertEquals(IntegerValue.valueOf(5),object.state(engine.system().state()).attributeValue("frozen"),"The pre-freeze current value must not have been stored as history");
        assertThrows(IllegalArgumentException.class,()->helper.fixture(RUNTIME,xml.replace("beforeFreeze=\"current\"","beforeFreeze=\"zero\"")));
        assertThrows(IllegalArgumentException.class,()->helper.fixture(RUNTIME,OBSERVERS.replace("mode=\"current\"","mode=\"current\" beforeFreeze=\"current\"")));
    }
    @Test void invalidPolicyAndFieldOrTableBindingRejectBeforeAnyNativeObject()throws Exception {
        var helper=helper();for(String observer:new String[]{OBSERVERS.replace("mode=\"latch-true\"","mode=\"default-false\""),OBSERVERS.replace("column=\"flag\"","column=\"not-a-column\""),OBSERVERS.replace("viewRef=\"signal-view\"","viewRef=\"missing-view\"")})
            assertThrows(IllegalArgumentException.class,()->helper.fixture(RUNTIME,observer));
    }
    @Test void typedErrorIsNotFalseAndXmlRenamingAndRestartDoNotNeedCaseJavaOrCarryHistory()throws Exception {
        var helper=helper();var f=helper.fixture(RUNTIME,OBSERVERS.replace("SignalView","ShippingEvidence"));
        try(var projector=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector(f.built(),"run",1,f.revision(),temp.resolve("observer-runtime"))) {
            var system=projector.system();projector.applySnapshot(cut(helper,f,1,3,true,true),"run",1);
            var old=system.state().allObjects().stream().filter(o->o.cls().name().equals("ShippingEvidence")).findFirst().orElseThrow();
            assertEquals(BooleanValue.TRUE,old.state(system.state()).attributeValue("ever"));
            var typed=cut(helper,f,2,4,false,true);var facts=typed.facts().stream().map(fact->{
                if(!"ready".equals(fact.values().get("name")))return fact;var values=new LinkedHashMap<>(fact.values());values.put("values",List.of("1"));values.put("valueTypes",List.of("java.lang.Integer"));
                return new RuntimeFact(fact.id(),fact.kind(),values,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence());
            }).toList();
            var invalid=new RuntimeSnapshot(typed.snapshotId(),typed.modelRevision(),typed.captureStartedAt(),typed.captureEndedAt(),typed.startWatermarks(),typed.endWatermarks(),1,facts,typed.sourceCompleteness(),typed.stateFingerprint());
            projector.applySnapshot(invalid,"run",1);assertTrue(old.state(system.state()).attributeValue("current").isUndefined());
            assertEquals("ERROR",projector.mutations().metadata(old,"xml:observer:quality:current"));assertEquals(BooleanValue.TRUE,old.state(system.state()).attributeValue("ever"));
            projector.applySnapshot(cut(helper,f,1,1,false,false),"new-run",2);assertSame(system,projector.system());
            var next=system.state().allObjects().stream().filter(o->o.cls().name().equals("ShippingEvidence")).findFirst().orElseThrow();assertNotSame(old,next);
            assertTrue(next.state(system.state()).attributeValue("ever").isUndefined());assertTrue(next.state(system.state()).attributeValue("frozen").isUndefined());
            assertEquals(1,system.state().allObjects().stream().filter(o->o.cls().name().equals("ShippingEvidence")).count());
        }
    }
    @Test void declaredObserverRecordKeysAllowMultipleEvidenceRowsForOnePhysicalSubjectButRejectAmbiguousSameRecord()throws Exception {
        // Port component: framework receipt qualification is exercised by the source dataset suites.
        var helper=helper();String runtime=RUNTIME.replace("<column name=\"flag\" path=\"scalar.value\"","<column name=\"record\" path=\"values.propertyId\" required=\"true\"/><column name=\"flag\" path=\"scalar.value\"");
        var f=helper.fixture(runtime,OBSERVERS.replace("keyFields=\"uuid\"","keyFields=\"uuid record\""));
        var projection=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.ObserverProjection(f.built().program().xmlPlan().inputs().specification(),f.engine().system().model(),f.engine().canonicalProgramHash());
        var access=f.engine().getClass().getDeclaredMethod("cslPort");assertTrue(access.trySetAccessible());var port=(org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port)access.invoke(f.engine());
        var rows=new ArrayList<org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables.Row>();
        for(String record:List.of("event-one","event-two"))rows.add(new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables.Row("physical-subject",Map.of("uuid","actual-uuid","record",record,"flag",true,"history",true,"p",1),Completeness.COMPLETE,Map.of("qualified","component-fixture"),Set.of("physical-subject")));
        var table=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables.Result(rows,Completeness.COMPLETE,List.of());
        projection.apply(port,"epoch","cut",Map.of("flags",table));assertEquals(2,f.engine().system().state().numObjects());
        var duplicate=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables.Result(List.of(rows.getFirst(),rows.getFirst()),Completeness.COMPLETE,List.of());
        assertThrows(IllegalArgumentException.class,()->projection.apply(port,"epoch","cut",Map.of("flags",duplicate)));assertEquals(2,f.engine().system().state().numObjects());
    }
}
