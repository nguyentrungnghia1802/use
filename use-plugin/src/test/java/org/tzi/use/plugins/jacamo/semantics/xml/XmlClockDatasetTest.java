package org.tzi.use.plugins.jacamo.semantics.xml;
import java.util.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeTables;
import static org.junit.jupiter.api.Assertions.*;
class XmlClockDatasetTest {
    @Test void explicitClockSampleKeepsMissingTimeSeparateFromQualifiedIndependentColumns()throws Exception {
        var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);String hash=(String)NplRuntimeProfile.evidence().get("artifactSha256");
        String xml=MappingXmlReaderTest.XML.replace("<requirements/>","<requirements><capability id=\"clock\" provider=\"npl.source-clock.v1\" required=\"true\" hash=\""+hash+"\"/></requirements>")
          .replace("<runtime/>","<runtime><datasets><clock id=\"time\" requestRef=\"clock\"/></datasets><tables><table id=\"samples\" dataset=\"boards\"><sampleClock as=\"sample\" requestRef=\"clock\" sourceClockPath=\"clock\"/><column name=\"now\" path=\"sample.now\" required=\"false\"/><column name=\"timeKnown\" path=\"sample.known\" required=\"true\"/><column name=\"actual\" path=\"actual\" required=\"true\"/></table></tables></runtime>");
        var spec=MappingXmlReaderTest.parse(xml);var tables=new RuntimeTables(spec);
        var frames=new ArrayList<VerificationObservation>();try(var producer=new NplSourceClockObservation("run",1,source.modelRevision(),frames::add)){producer.pulse(false);}
        var clock=org.tzi.use.plugins.jacamo.semantics.observer.SourceClock.decode(frames.getFirst(),"run",1,source.modelRevision(),hash);
        var row=new RuntimeTables.Row("scheme",Map.of("clock",clock.clockIdentity(),"actual",List.of("actor-A")),Completeness.COMPLETE,Map.of("source-cut","qualified component cut"),Set.of("scheme"));
        var input=Map.of("boards",new RuntimeTables.Dataset(List.of(row),Completeness.PARTIAL));
        var known=tables.execute(input,Map.of("clock",clock)).get("samples").rows().getFirst();assertEquals(true,known.values().get("timeKnown"));assertEquals(0,((Number)known.values().get("now")).intValue());
        for(var clocks:List.of(Map.<String,org.tzi.use.plugins.jacamo.semantics.observer.SourceClock>of(),Map.of("clock",org.tzi.use.plugins.jacamo.semantics.observer.SourceClock.unavailable()))) {
            var missing=tables.execute(input,clocks).get("samples").rows().getFirst();
            assertEquals(Completeness.COMPLETE,missing.quality());assertFalse(missing.values().containsKey("now"));assertEquals(false,missing.values().get("timeKnown"),"This is an availability flag, not a domain false/time sentinel");assertEquals(List.of("actor-A"),missing.values().get("actual"));
        }
        var wrongRow=new RuntimeTables.Row(row.identity(),Map.of("clock","another-source-clock","actual",List.of("actor-A")),row.quality(),row.proofs(),row.aliases());
        assertFalse(tables.execute(Map.of("boards",new RuntimeTables.Dataset(List.of(wrongRow),Completeness.PARTIAL)),Map.of("clock",clock)).get("samples").rows().getFirst().values().containsKey("now"));
        assertThrows(IllegalArgumentException.class,()->new RuntimeTables(MappingXmlReaderTest.parse(xml.replace("provider=\"npl.source-clock.v1\"","provider=\"receiver-clock\""))));
    }
    @Test void xmlRequestsPinnedSourceClockAndMissingOrWrongBinaryNeverGetsReceiverTime()throws Exception {
        var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);String hash=(String)NplRuntimeProfile.evidence().get("artifactSha256");
        var request=new ObservationCapabilityRequest("clock",NplClockCapability.PROVIDER,true,Map.of("hash",hash));
        var spec=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>","<runtime><datasets><clock id=\"time\" requestRef=\"clock\"/></datasets></runtime>"));
        var frames=new ArrayList<VerificationObservation>();try(var producer=new NplSourceClockObservation("run",1,source.modelRevision(),frames::add)){producer.pulse(false);}
        var event=frames.getFirst().event("run",1,source.modelRevision(),1,java.time.Instant.EPOCH,"","");
        var fact=new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),List.of());
        var marks=Map.of(VerificationObservation.SOURCE_ID,event.watermark());
        var cut=new RuntimeSnapshot("clock-cut",source.modelRevision(),java.time.Instant.EPOCH,java.time.Instant.EPOCH,marks,marks,1,List.of(fact),Map.of(VerificationObservation.SOURCE_ID,Completeness.PARTIAL),"clock-proof");
        var sources=new RuntimeFactSources(spec,source,List.of(request));var result=sources.capture(cut,"run",1).get("time");
        assertEquals(Completeness.COMPLETE,result.rows().getFirst().quality());assertEquals(Completeness.PARTIAL,result.populationQuality());
        assertEquals(0,((Number)result.rows().getFirst().values().get("relativeNow")).intValue());
        assertFalse(result.rows().getFirst().proofs().get("capability:clock").isBlank());
        var absent=new RuntimeSnapshot("no-clock",source.modelRevision(),java.time.Instant.EPOCH,java.time.Instant.EPOCH,marks,marks,1,List.of(),Map.of(VerificationObservation.SOURCE_ID,Completeness.PARTIAL),"no-proof");
        var missing=sources.capture(absent,"run",1).get("time");assertEquals(Completeness.UNAVAILABLE,missing.populationQuality());assertTrue(missing.rows().isEmpty());
        var wrong=new RuntimeFactSources(spec,source,List.of(new ObservationCapabilityRequest("clock",request.provider(),true,Map.of("hash","0".repeat(64)))));
        assertThrows(IllegalArgumentException.class,()->wrong.capture(cut,"run",1));
        assertThrows(IllegalArgumentException.class,()->new RuntimeFactSources(spec,source,List.of()));
        assertThrows(IllegalArgumentException.class,()->new RuntimeFactSources(spec,source,List.of(new ObservationCapabilityRequest("clock",request.provider(),true,Map.of()))));
    }
    @Test void declaredRelativeTimeConversionUsesOnlyValidatedCutClockWithExactEpochAndNativeIntegerRange()throws Exception {
        var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);String hash=(String)NplRuntimeProfile.evidence().get("artifactSha256");
        var request=new ObservationCapabilityRequest("clock",NplClockCapability.PROVIDER,true,Map.of("hash",hash));
        String xml=MappingXmlReaderTest.XML.replace("<requirements/>","<requirements><capability id=\"clock\" provider=\"npl.source-clock.v1\" required=\"true\" hash=\""+hash+"\"/></requirements>").replace("<runtime/>","""
          <runtime><datasets><clock id="time" requestRef="clock"/></datasets><tables><table id="relative" dataset="instants">
            <relativeTime as="millis" requestRef="clock" epochPath="epoch" sourceClockPath="clock"/>
            <column name="value" path="millis" required="true"/>
          </table></tables></runtime>
          """);
        var spec=MappingXmlReaderTest.parse(xml);var frames=new ArrayList<VerificationObservation>();
        try(var producer=new NplSourceClockObservation("run",1,source.modelRevision(),frames::add)){producer.pulse(false);}
        var event=frames.getFirst().event("run",1,source.modelRevision(),1,java.time.Instant.EPOCH,"","");var mark=Map.of(VerificationObservation.SOURCE_ID,event.watermark());
        var cut=new RuntimeSnapshot("clock",source.modelRevision(),java.time.Instant.EPOCH,java.time.Instant.EPOCH,mark,mark,1,List.of(new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),List.of())),Map.of(VerificationObservation.SOURCE_ID,Completeness.PARTIAL),"clock");
        var sources=new RuntimeFactSources(spec,source,List.of(request));var clocks=sources.clocks(cut,"run",1);var clock=clocks.get("clock");var tables=new RuntimeTables(spec);
        long anchor=clock.anchorEpochMillis();
        var good=new RuntimeTables.Row("activation",Map.of("epoch",Long.toString(anchor+1000),"clock",clock.clockIdentity()),Completeness.COMPLETE,Map.of(),Set.of("activation"));
        assertEquals(1000,((Number)tables.execute(Map.of("instants",new RuntimeTables.Dataset(List.of(good),Completeness.PARTIAL)),clocks).get("relative").rows().getFirst().values().get("value")).intValue());
        var absent=tables.execute(Map.of("instants",new RuntimeTables.Dataset(List.of(good),Completeness.PARTIAL))).get("relative");assertEquals(Completeness.PARTIAL,absent.rows().getFirst().quality());assertFalse(absent.rows().getFirst().values().containsKey("value"));
        var optional=new RuntimeTables(MappingXmlReaderTest.parse(xml.replace("epochPath=\"epoch\"","onMissing=\"unavailable-value\" epochPath=\"epoch\"").replace("path=\"millis\" required=\"true\"","path=\"millis\" required=\"false\"")));
        var optionalMissing=optional.execute(Map.of("instants",new RuntimeTables.Dataset(List.of(good),Completeness.PARTIAL))).get("relative").rows().getFirst();
        assertEquals(Completeness.COMPLETE,optionalMissing.quality());assertFalse(optionalMissing.values().containsKey("value"),"Independent lifecycle evidence stays qualified; time itself remains absent");
        assertThrows(IllegalArgumentException.class,()->new RuntimeTables(MappingXmlReaderTest.parse(xml.replace("epochPath=\"epoch\"","onMissing=\"zero\" epochPath=\"epoch\""))));
        for(var values:List.of(Map.of("epoch",Long.toString(anchor-1),"clock",clock.clockIdentity()),Map.of("epoch",Long.toString(anchor+Integer.MAX_VALUE+1L),"clock",clock.clockIdentity()),Map.of("epoch","3.5","clock",clock.clockIdentity()),Map.of("epoch",Long.toString(anchor),"clock","foreign"))) {
            var row=new RuntimeTables.Row("activation",new HashMap<>(values),Completeness.COMPLETE,Map.of(),Set.of("activation"));
            assertEquals(Completeness.PARTIAL,tables.execute(Map.of("instants",new RuntimeTables.Dataset(List.of(row),Completeness.COMPLETE)),clocks).get("relative").rows().getFirst().quality());
        }
        assertThrows(IllegalArgumentException.class,()->new RuntimeTables(MappingXmlReaderTest.parse(xml.replace("requestRef=\"clock\" epochPath","requestRef=\"absent\" epochPath"))));
    }
}
