package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.*;
import static org.junit.jupiter.api.Assertions.*;

class XmlNplDatasetTest {
    @TempDir java.nio.file.Path temp;
    @Test @Timeout(30) void actualNplActivationIsJoinedToItsPhysicalBoardAndKeepsUnknownPopulationAndTimes()throws Exception {
        var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);
        var request=new ObservationCapabilityRequest("norms","npl.lifecycle.v1",true,Map.of("hash",NplRuntimeProfile.evidence().get("artifactSha256")));
        var spec=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>","<runtime><datasets><npl id=\"activations\" requestRef=\"norms\"/></datasets></runtime>"));
        var reader=new RuntimeFactSources(spec,source,List.of(request));
        var events=new java.util.concurrent.CopyOnWriteArrayList<RuntimeEvent>();var environment=CartagoEnvironment.getInstance();environment.init();var actor=new CartagoBasicContext("normDataset"+UUID.randomUUID().toString().replace("-",""));ArtifactId artifact=null;
        try(var norms=new NplBoardSnapshotSource("dataset-run",2,source.modelRevision(),()->NeutralNormBoard.instance==null?List.of():List.of(NeutralNormBoard.instance));
            var cartago=new CartagoSnapshotSource(environment,List.of("/main"),"dataset-run",null,norms::artifactCreated);
            var snapshots=new SnapshotCoordinator(List.of(cartago,norms),1024,events::add)) {
            artifact=actor.makeArtifact(actor.getJoinedWspId("main"),"shipmentNorms",NeutralNormBoard.class.getName());
            actor.doAction(artifact,new Op("load","scope shipment { norm duty: start -> obligation(alice,true,missing,`now`+`1 seconds`). norm optional: start -> permission(bob,true,unused,`now`+`10 seconds`). }"),5000);
            actor.doAction(artifact,new Op("addFact","start"),5000);
            NplLifecycleCutTest.await(()->norms.capture().size()==2);
            var cut=snapshots.capture(source.modelRevision(),3).snapshot();var actual=reader.capture(cut,"dataset-run",2).get("activations");
            assertEquals(Completeness.PARTIAL,actual.populationQuality());assertEquals(2,actual.rows().size());
            assertTrue(actual.rows().stream().allMatch(r->r.quality()==Completeness.COMPLETE));
            var obligation=actual.rows().stream().filter(r->r.values().get("modality").equals("OBLIGATION")).findFirst().orElseThrow();
            assertEquals("alice",obligation.values().get("subject"));assertEquals(false,obligation.values().get("reportedUnfulfilled"));
            assertFalse(obligation.values().containsKey("terminalEpochMillis"));assertFalse(obligation.proofs().get("capability:norms").isBlank());
            assertEquals(artifact.getId().toString(),obligation.values().get("boardUuid"));
            String plan=XmlRuntimeMappingTest.PLAN.replace("</datasets>","<npl id=\"activations\" requestRef=\"norms\"/></datasets>").replace("</tables>","""
                <table id="normRows" dataset="activations"><where path="modality" type="String" equals="OBLIGATION"/>
                  <column name="key" path="activation" required="true"/><column name="raw" path="raw" required="true"/>
                  <column name="state" path="state" required="true"/><column name="failure" path="reportedUnfulfilled" required="false"/>
                  <column name="continuous" path="continuous" required="true"/>
                </table></tables>
                """);
            String observers="""
                <observers><view id="duty" name="ShipmentDuty" category="CLASS" keyFields="key">
                 <field name="state" type="String" origin="source reported state"/><field name="unfulfilled" type="Boolean" origin="source failure"/>
                </view><project id="dutyProjection" viewRef="duty" tableRef="normRows" keyFields="key">
                 <lifecycle kind="NPL_REPORTED" column="raw" requestRef="norms"/>
                 <write field="state" column="state" mode="current"/><write field="unfulfilled" column="failure" mode="latch-true" historyColumn="continuous"/>
                </project></observers>
                """;
            var helper=new XmlRuntimeMappingTest();helper.temp=temp;var nativeState=helper.fixture(plan,observers,"<requirements><capability id=\"norms\" provider=\"npl.lifecycle.v1\" hash=\""+request.parameters().get("hash")+"\" required=\"true\"/></requirements>");
            var engine=nativeState.engine();engine.applyMappingCut(cut,"dataset-run",2,List.of());
            var duty=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("ShipmentDuty")).findFirst().orElseThrow();
            assertEquals(org.tzi.use.uml.ocl.value.BooleanValue.FALSE,duty.state(engine.system().state()).attributeValue("unfulfilled"));
            var withoutBoard=copy(cut,cut.facts().stream().filter(f->f.kind()!=RuntimeFactKind.ARTIFACT).toList());
            var unavailable=reader.capture(withoutBoard,"dataset-run",2).get("activations");
            assertTrue(unavailable.rows().stream().allMatch(r->r.quality()==Completeness.UNAVAILABLE&&!r.values().containsKey("reportedUnfulfilled")));
            assertThrows(IllegalArgumentException.class,()->reader.capture(cut,"other-run",2));
            var wrong=new RuntimeFactSources(spec,source,List.of(new ObservationCapabilityRequest("norms",request.provider(),true,Map.of("hash","0".repeat(64)))));
            assertThrows(IllegalArgumentException.class,()->wrong.capture(cut,"dataset-run",2));
            var badWorkspace=copy(cut,cut.facts().stream().map(f->{if(f.kind()!=RuntimeFactKind.WORKSPACE)return f;var data=new LinkedHashMap<>(f.values());data.put("uuid",UUID.randomUUID().toString());return new RuntimeFact(f.id(),f.kind(),data,f.relations(),f.projectionStatus(),f.completeness(),f.evidence());}).toList());
            assertThrows(IllegalArgumentException.class,()->reader.capture(badWorkspace,"dataset-run",2));
            NplLifecycleCutTest.await(()->norms.capture().stream().anyMatch(f->"UNFULFILLED".equals(f.values().get("state"))));
            var terminalCut=snapshots.capture(source.modelRevision(),3).snapshot();
            var ended=reader.capture(terminalCut,"dataset-run",2).get("activations").rows().stream().filter(r->r.values().get("modality").equals("OBLIGATION")).findFirst().orElseThrow();
            assertEquals(true,ended.values().get("reportedUnfulfilled"));assertTrue(ended.values().containsKey("terminalEpochMillis"));assertEquals(obligation.identity(),ended.identity());
            var terminalEvent=events.stream().filter(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED&&e.entityId().canonical().equals(obligation.identity())).findFirst().orElseThrow();
            var facts=new LinkedHashMap<String,RuntimeFact>();cut.facts().forEach(f->facts.put(f.id().canonical(),f));var advanced=RuntimeObservationCut.advance(facts,terminalEvent);
            assertEquals("UNFULFILLED",advanced.get(obligation.identity()).values().get("state"),"Raw NPL event must replace its activation fact in the admitted evidence index");
            var marks=new HashMap<String,Long>();cut.endWatermarks().forEach((s,m)->marks.put(s,m.sequence()));
            var eventCut=RuntimeObservationCut.eventCut(advanced,marks,cut.sourceCompleteness(),terminalEvent);
            engine.applyMappingCut(eventCut,"dataset-run",2,List.of(),terminalEvent);assertEquals(org.tzi.use.uml.ocl.value.BooleanValue.TRUE,duty.state(engine.system().state()).attributeValue("unfulfilled"));
            engine.applyMappingCut(terminalCut,"dataset-run",2,List.of());assertEquals(org.tzi.use.uml.ocl.value.BooleanValue.TRUE,duty.state(engine.system().state()).attributeValue("unfulfilled"));
            var saved=engine.savepoint();
            // Deliberately bypass projector admission to exercise the independent lifecycle conflict guard.
            engine.applyMappingCut(cut,"dataset-run",2,List.of());assertEquals("ERROR",engine.metadata(duty,"xml:observer:lifecycle:verdict"));
            assertTrue(duty.state(engine.system().state()).attributeValue("unfulfilled").isUndefined());
            engine.applyMappingCut(terminalCut,"dataset-run",2,List.of());assertTrue(duty.state(engine.system().state()).attributeValue("unfulfilled").isUndefined(),"Terminal contradiction is sticky until rollback/reset, never a FAIL Boolean");
            engine.rollback(saved);assertEquals(org.tzi.use.uml.ocl.value.BooleanValue.TRUE,duty.state(engine.system().state()).attributeValue("unfulfilled"));
        }finally {
            if(artifact!=null){NeutralNormBoard.instance.getNormativeEngine().stop();environment.getController("/main").removeArtifact(artifact.getName());}NeutralNormBoard.instance=null;
            CslRuntimeTestRegistry.closeCartagoContext(actor);environment.shutdown();
        }
        assertThrows(IllegalArgumentException.class,()->new RuntimeFactSources(spec,source,List.of()));
        assertThrows(IllegalArgumentException.class,()->new RuntimeFactSources(spec,source,List.of(new ObservationCapabilityRequest("norms",request.provider(),true,Map.of()))));
    }
    static RuntimeSnapshot copy(RuntimeSnapshot cut,List<RuntimeFact> facts){return new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),cut.validationAttempts(),facts,cut.sourceCompleteness(),cut.stateFingerprint(),cut.captureMetadata());}
}
