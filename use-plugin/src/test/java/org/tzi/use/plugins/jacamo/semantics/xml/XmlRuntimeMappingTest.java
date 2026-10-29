package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;
/** Real native writer and savepoint; this is a snapshot integration gate, not a live producer/TCP test. */
class XmlRuntimeMappingTest {
    @TempDir Path temp;
    static final String PLAN="""
      <runtime><datasets><facts id="raw-artifacts" authority="cartago" kind="ARTIFACT"/><facts id="raw-properties" authority="cartago" kind="PROPERTY"/></datasets>
       <tables><table id="properties" dataset="raw-properties"><where path="values.name" type="String" equals="p"/>
         <column name="artifact" path="values.artifactSemanticId" required="true"/><column name="value" path="scalar.value" required="true"/>
        </table><table id="objects" dataset="raw-artifacts">
         <join tableRef="properties" leftPath="values.semanticId" rightPath="artifact" as="property" cardinality="ONE" required="true"/>
         <column name="uuid" path="identity" required="true"/><column name="p" path="property.value" required="true"/><identity mode="PHYSICAL" path="identity"/>
        </table></tables>
       <inputs><input id="object-input" identityKind="PHYSICAL" identityFields="uuid" tableRef="objects"><field name="uuid" type="String" required="true"/><field name="p" type="Integer" required="true"/></input></inputs>
       <converters><converter id="integer" inputType="Integer" outputType="Integer" algorithm="preserve-exact-integer" onMismatch="incomplete"/></converters>
       <rule id="map-object" inputRef="object-input"><requireEvidence proof="source-cut"/><upsertInstance targetType="Counter" keyFields="uuid" aliasFrom="uuid"><assign target="p" sourceField="p" converter="integer"/></upsertInstance></rule>
      </runtime>
      """;
    record Fixture(NativeRuntimeMutationEngine engine,String revision,CodeGroundedNativePipeline.CanonicalBuildResult built) { }
    Fixture fixture()throws Exception {return fixture(PLAN);}
    Fixture fixture(String plan)throws Exception {return fixture(plan,"<observers/>");}
    Fixture fixture(String plan,String observers)throws Exception {
        return fixture(plan,observers,"<requirements/>");
    }
    Fixture fixture(String plan,String observers,String requirements)throws Exception {
        var helper=new GenericMappingEngineTest();helper.temp=temp;helper.input();var xml=temp.resolve("mapping.xml");
        Files.writeString(xml,Files.readString(xml).replace("<runtime/>",plan).replace("<observers/>",observers).replace("<requirements/>",requirements).replace("</bindings>","<oclClause name=\"Nonnegative\" context=\"context Counter\"/></bindings>"));
        var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);
        var program=new GenericMappingEngine().map(source,MappingInputs.read(GenericMappingEngineTest.JCM,xml,temp.resolve("requirements.ocl")));
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        return new Fixture(new NativeRuntimeMutationEngine(built.nativeResult().state().system(),built.nativeResult().state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry(),program),source.modelRevision(),built);
    }
    static BridgeEntityId id(String uuid){return new BridgeEntityId("cartago","environment","artifact","/warehouse","stock-"+uuid,uuid);}
    RuntimeSnapshot cut(Fixture f,int sequence,Map<String,Integer> objects,Completeness population) {
        var facts=new ArrayList<RuntimeFact>();
        objects.forEach((uuid,p)->{
            var id=id(uuid);String semantic="physical:"+uuid;
            facts.add(new RuntimeFact(id,RuntimeFactKind.ARTIFACT,Map.of("uuid",uuid,"name",id.localId(),"semanticId",semantic,"artifactTypeJavaClassName","warehouse.Inventory"),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
            facts.add(new RuntimeFact(new BridgeEntityId("cartago","environment","observable-property-snapshot",semantic,"p","snapshot"),RuntimeFactKind.PROPERTY,Map.of("artifactSemanticId",semantic,"name","p","values",List.of(p.toString()),"valueTypes",List.of("java.lang.Integer")),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
        });
        var mark=Map.of("cartago",new SourceWatermark("cartago",sequence));
        return new RuntimeSnapshot("cut-"+sequence,f.revision(),java.time.Instant.EPOCH,java.time.Instant.EPOCH,mark,mark,1,facts,Map.of("cartago",population),"fingerprint-"+sequence);
    }
    @Test void genericProgramUsesExistingCutWriterAndNoLegacyFallbackAndPartialDoesNotInventFalse()throws Exception {
        var f=fixture();var engine=f.engine();assertTrue(engine.declarativeMapping(),"Existing case-cut seam must select the neutral XML adapter");
        var system=engine.system();var initial=cut(f,1,Map.of("A",1,"B",2),Completeness.COMPLETE);
        assertEquals(2,engine.applyMappingCut(initial,"run",1,List.of()).stream().filter(r->r.status()==NativeRuntimeMutationEngine.Status.MATERIALIZED).count());
        assertSame(system,engine.system());assertEquals(2,system.state().numObjects());
        var a=engine.objectForSemanticId(id("A").canonical());assertEquals(IntegerValue.valueOf(1),a.state(system.state()).attributeValue("p"));
        engine.applyMappingCut(cut(f,2,Map.of("A",3),Completeness.PARTIAL),"run",1,List.of());
        assertSame(a,engine.objectForSemanticId(id("A").canonical()));
        assertTrue(a.state(system.state()).attributeValue("p").isUndefined(),"An incomplete property join population cannot certify unique current value");
        assertTrue(engine.objectForSemanticId(id("B").canonical()).state(system.state()).attributeValue("p").isUndefined());
        assertEquals(2,system.state().numObjects(),"Missing population is unknown, not disposal");
    }
    @Test void laterRowFailureRestoresEarlierWritesAliasesAndMetadataOnSameNativeSavepoint()throws Exception {
        var f=fixture();var engine=f.engine();engine.applyMappingCut(cut(f,1,Map.of("A",1),Completeness.COMPLETE),"run",1,List.of());
        var before=engine.savepoint();var old=engine.objectForSemanticId(id("A").canonical());
        var rows=new LinkedHashMap<String,Integer>();rows.put("NEW",9);rows.put("A",8);
        var error=assertThrows(NativeRuntimeProtocolException.class,()->engine.applyMappingCut(cut(f,2,rows,Completeness.COMPLETE),"different-run",1,List.of()));
        assertTrue(error.getMessage().contains("M0_SOURCE_ALIAS_CONFLICT"));
        assertSame(old,engine.objectForSemanticId(id("A").canonical()));assertNull(engine.objectForSemanticId(id("NEW").canonical()));
        assertEquals(before.bindings().toMap(),engine.savepoint().bindings().toMap());assertEquals(before.tombstones(),engine.savepoint().tombstones());
        assertEquals(IntegerValue.valueOf(1),old.state(engine.system().state()).attributeValue("p"));
    }
    RuntimeEvent delta(Fixture f,long sequence,String uuid,int value) {
        var properties=List.of(Map.of("semanticId","property:"+uuid,"artifactSemanticId","physical:"+uuid,"propertyId","p","name","p","values",List.of(Integer.toString(value)),"valueTypes",List.of("java.lang.Integer")));
        return new RuntimeEvent("delta-"+sequence,"run",1,f.revision(),"cartago","cartago",sequence,java.time.Instant.EPOCH,RuntimeEventKind.CHANGED,RuntimeFactKind.ARTIFACT,ProjectionStatus.MATERIALIZED_FAITHFULLY,id(uuid),null,"","",Map.of(),Map.of("normalizedEventKind","APPLY_CARTAGO_PROPERTY_DELTA","semanticId","physical:"+uuid,"properties",properties,"removedPropertySemanticIds",List.of()),new SourceWatermark("cartago",sequence),Completeness.COMPLETE,List.of());
    }
    @Test void admittedPropertyEventsUseNeutralCutAndStaleGapRestartReuseExistingProtocol()throws Exception {
        var f=fixture();try(var projector=new NativeRuntimeProjector(f.built(),"run",1,f.revision(),temp.resolve("runtime"))) {
            var system=projector.system();projector.applySnapshot(cut(f,1,Map.of("A",1),Completeness.COMPLETE),"run",1);
            var object=projector.mutations().objectForSemanticId(id("A").canonical());assertNotNull(object);
            assertTrue(projector.apply(delta(f,2,"A",4)));assertSame(system,projector.system());
            assertEquals(IntegerValue.valueOf(4),object.state(system.state()).attributeValue("p"));
            assertEquals(2L,projector.sourceWatermarks().get("cartago"));
            assertThrows(NativeRuntimeProtocolException.class,()->projector.applySnapshot(cut(f,1,Map.of("A",0),Completeness.COMPLETE),"run",1));
            assertEquals(IntegerValue.valueOf(4),object.state(system.state()).attributeValue("p"));assertTrue(projector.resyncRequired());
            projector.applySnapshot(cut(f,2,Map.of("A",4),Completeness.COMPLETE),"run",1);assertFalse(projector.resyncRequired());
            assertThrows(NativeRuntimeProtocolException.class,()->projector.apply(delta(f,4,"A",8)));assertTrue(projector.resyncRequired());
            assertEquals(IntegerValue.valueOf(4),object.state(system.state()).attributeValue("p"));
            projector.applySnapshot(cut(f,1,Map.of("A",7),Completeness.COMPLETE),"new-run",2);
            assertSame(system,projector.system());var next=projector.mutations().objectForSemanticId(id("A").canonical());assertNotSame(object,next);
            assertEquals(IntegerValue.valueOf(7),next.state(system.state()).attributeValue("p"));assertEquals(1,system.state().numObjects());
        }
    }
    @Test void unrelatedJasonActionIsJournalledWithoutReprojectingTheSameWorldCut()throws Exception {
        var f=fixture();try(var projector=new NativeRuntimeProjector(f.built(),"run",1,f.revision(),temp.resolve("action-runtime"))) {
            projector.applySnapshot(cut(f,1,Map.of("A",1),Completeness.COMPLETE),"run",1);
            projector.coordinator().originalEngineDrained().get(10,java.util.concurrent.TimeUnit.SECONDS);projector.coordinator().read(()->null);
            long version=projector.coordinator().latest().stateVersion();
            long entries=projector.coordinator().journal().persistedEntries();
            var event=new RuntimeEvent("raw-action","run",1,f.revision(),"jason","jason",1,java.time.Instant.EPOCH,
                RuntimeEventKind.STARTED,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,new BridgeEntityId("jason","agent","runtime-agent","model","worker","incarnation"),null,"action-id","",
                Map.of(),Map.of("normalizedEventKind","ACTION_EXECUTION_STARTED","action","println(message)"),new SourceWatermark("jason",1),Completeness.COMPLETE,List.of());
            assertFalse(projector.apply(event),"Raw ActionExec is trace, not a changed XML dataset or Task checkpoint");
            assertEquals(version,projector.coordinator().latest().stateVersion());
            assertEquals(1L,projector.sourceWatermarks().get("jason"));
            assertTrue(projector.coordinator().journal().persistedEntries()>entries,"Do not discard admission or evidence");
            assertFalse(projector.apply(event),"Duplicate remains idempotent");
            assertFalse(projector.resyncRequired());
            projector.coordinator().originalEngineDrained().get(10,java.util.concurrent.TimeUnit.SECONDS);projector.coordinator().read(()->null);
            assertEquals("TRACE_ONLY",projector.coordinator().lastObservation().freshness());
            var expected=projector.coordinator().lastObservation();var replay=new NativeRuntimeReplay();var recording=temp.resolve("trace-replay");
            replay.exportBundle(projector,f.built().nativeResult().export().useText(),recording);
            var result=replay.replay(recording);assertTrue(result.complete(),result.diagnostics().toString());assertEquals(expected.resultHash(),result.finalResultHash());assertEquals(expected.stateHash(),result.finalStateHash());
        }
    }
    @Test void portableExportPinsTheActualJournalTailWhenAnOlderCompletionFollowsTrace()throws Exception {
        var f=fixture();try(var projector=new NativeRuntimeProjector(f.built(),"run",1,f.revision(),temp.resolve("completion-tail-runtime"))) {
            var coordinator=projector.coordinator();
            coordinator.originalEngineDrained().get(10,java.util.concurrent.TimeUnit.SECONDS);coordinator.read(()->null);
            coordinator.read(()->{
                projector.applySnapshot(cut(f,1,Map.of("A",1),Completeness.COMPLETE),"run",1);
                var event=new RuntimeEvent("trace-before-completion","run",1,f.revision(),"jason","jason",1,java.time.Instant.EPOCH,
                    RuntimeEventKind.STARTED,RuntimeFactKind.RUNTIME_EVENT,ProjectionStatus.EVIDENCE_ONLY,new BridgeEntityId("jason","agent","runtime-agent","model","worker","incarnation"),null,"action-id","",
                    Map.of(),Map.of("normalizedEventKind","ACTION_EXECUTION_STARTED","action","println(message)"),new SourceWatermark("jason",1),Completeness.COMPLETE,List.of());
                assertFalse(projector.apply(event));return null;
            });
            coordinator.originalEngineDrained().get(10,java.util.concurrent.TimeUnit.SECONDS);coordinator.read(()->null);
            assertEquals("TRACE_ONLY",coordinator.lastObservation().freshness());
            var tail=coordinator.journal().history().getLast();
            assertNotEquals(tail.resultHash(),coordinator.lastObservation().resultHash(),"Older completion is durable after the latest admitted trace");
            var recording=temp.resolve("completion-tail-replay");var replay=new NativeRuntimeReplay();
            replay.exportBundle(projector,f.built().nativeResult().export().useText(),recording);
            var result=replay.replay(recording);assertTrue(result.complete(),result.diagnostics().toString());
            assertEquals(tail.resultHash(),result.finalResultHash());assertEquals(coordinator.lastObservation().stateHash(),result.finalStateHash());
        }
    }
    static final String REMOVAL=PLAN.replace("</datasets>","<removals id=\"disposed\" authority=\"cartago\" kind=\"ARTIFACT\"/></datasets>")
      .replace("</tables>","<table id=\"disposals\" dataset=\"disposed\"><column name=\"uuid\" path=\"identity\" required=\"true\"/><identity mode=\"PHYSICAL\" path=\"identity\"/></table></tables>")
      .replace("</inputs>","<input id=\"removed-input\" identityKind=\"PHYSICAL\" identityFields=\"uuid\" tableRef=\"disposals\"><field name=\"uuid\" type=\"String\" required=\"true\"/></input></inputs>")
      .replace("</runtime>","<rule id=\"remove-object\" inputRef=\"removed-input\"><removeInstance targetType=\"Counter\" keyFields=\"uuid\" authorityProof=\"removed-identity\"/></rule></runtime>");
    @Test void explicitPhysicalDisposalAndSameNameNewUuidDoNotLeakOrResurrectOldRuntimeIdentity()throws Exception {
        var f=fixture(REMOVAL);try(var projector=new NativeRuntimeProjector(f.built(),"run",1,f.revision(),temp.resolve("disposed-runtime"))) {
            projector.applySnapshot(cut(f,1,Map.of("A",1),Completeness.COMPLETE),"run",1);
            var old=projector.mutations().objectForSemanticId(id("A").canonical());assertNotNull(old);
            var event=new RuntimeEvent("dispose-A","run",1,f.revision(),"cartago","cartago",2,java.time.Instant.EPOCH,RuntimeEventKind.DISPOSED,RuntimeFactKind.ARTIFACT,ProjectionStatus.MATERIALIZED_FAITHFULLY,id("A"),null,"","",Map.of(),Map.of("normalizedEventKind","DELETE_CARTAGO_ARTIFACT","semanticId","physical:A"),new SourceWatermark("cartago",2),Completeness.COMPLETE,List.of());
            assertTrue(projector.apply(event));assertNull(projector.mutations().objectForSemanticId(id("A").canonical()));assertEquals(0,projector.system().state().numObjects());
            var replacementId=new BridgeEntityId("cartago","environment","artifact",id("A").scope(),id("A").localId(),"new-uuid");
            var payload=Map.<String,Object>of("normalizedEventKind","UPSERT_CARTAGO_ARTIFACT","uuid","new-uuid","name",id("A").localId(),"semanticId","physical:new-uuid","artifactTypeJavaClassName","warehouse.Inventory","properties",List.of(Map.of("semanticId","property:new-uuid","artifactSemanticId","physical:new-uuid","propertyId","p","name","p","values",List.of("7"),"valueTypes",List.of("java.lang.Integer"))));
            var creation=new RuntimeEvent("create-new","run",1,f.revision(),"cartago","cartago",3,java.time.Instant.EPOCH,RuntimeEventKind.CREATED,RuntimeFactKind.ARTIFACT,ProjectionStatus.MATERIALIZED_FAITHFULLY,replacementId,null,"","",Map.of(),payload,new SourceWatermark("cartago",3),Completeness.COMPLETE,List.of());
            assertTrue(projector.apply(creation));var replacement=projector.mutations().objectForSemanticId(replacementId.canonical());assertNotNull(replacement);assertNotSame(old,replacement);
            assertEquals(IntegerValue.valueOf(7),replacement.state(projector.system().state()).attributeValue("p"));assertEquals(1,projector.system().state().numObjects());
            assertFalse(projector.mutations().savepoint().tombstones().isEmpty());assertNull(projector.mutations().objectForSemanticId(id("A").canonical()));
        }
    }
    @Test void invalidatingAFormerlyKnownValueIsAnActualStateChangeAndMustNotReuseCachedVerification()throws Exception {
        var f=fixture();try(var projector=new NativeRuntimeProjector(f.built(),"run",1,f.revision(),temp.resolve("invalidated-runtime"))) {
            projector.applySnapshot(cut(f,1,Map.of("A",1),Completeness.COMPLETE),"run",1);
            long before=projector.coordinator().latest().stateVersion();var event=delta(f,2,"A",4);
            var after=new LinkedHashMap<>(event.after());after.put("properties",List.of(Map.of("semanticId","property:A","artifactSemanticId","physical:A","propertyId","p","name","p","values",List.of("unknown"),"valueTypes",List.of("java.lang.String"))));
            var invalid=new RuntimeEvent(event.eventId(),event.sessionId(),event.generation(),event.modelRevision(),event.subsystem(),event.sourceId(),event.sourceSequence(),event.observedAt(),event.kind(),event.factKind(),event.projectionStatus(),event.entityId(),event.relationId(),event.correlationId(),event.causationId(),event.before(),after,event.watermark(),event.completeness(),event.evidence());
            assertTrue(projector.apply(invalid),"Known→undefined is a native mutation even when no row can certify a value");
            var object=projector.mutations().objectForSemanticId(id("A").canonical());assertTrue(object.state(projector.system().state()).attributeValue("p").isUndefined());
            assertTrue(projector.coordinator().latest().stateVersion()>before);assertNotEquals("CACHED",projector.coordinator().latest().freshness());
        }
    }
}
