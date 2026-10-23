package org.tzi.use.plugins.jacamo.semantics.xml;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources;
import static org.junit.jupiter.api.Assertions.*;
class RuntimeFactSourcesTest {
    static final String SOURCES="<runtime><datasets><facts id=\"artifacts\" authority=\"cartago\" kind=\"ARTIFACT\"/><facts id=\"groups\" authority=\"moise\" kind=\"GROUP_BOARD\"/></datasets></runtime>";
    RuntimeFactSources sources(){return new RuntimeFactSources(MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",SOURCES)));}
    static RuntimeSnapshot cut(List<RuntimeFact> facts,Map<String,Completeness> quality){
        var marks=new TreeMap<String,SourceWatermark>();quality.keySet().forEach(s->marks.put(s,new SourceWatermark(s,1)));
        return new RuntimeSnapshot("source-cut","revision",Instant.EPOCH,Instant.EPOCH,marks,marks,1,facts,quality,"state");
    }
    static RuntimeFact artifact(String uuid,Completeness completeness,ProjectionStatus status) {
        return new RuntimeFact(new BridgeEntityId("cartago","environment","artifact","/main","inventory",uuid),RuntimeFactKind.ARTIFACT,
          Map.of("uuid",uuid,"name","inventory","artifactTypeJavaClassName","inventory.Stock","semanticId","forged-alias","auctionStateObservation",Map.of("currentWinner","guessed")),List.of(),status,completeness,List.of());
    }
    @Test void actualPhysicalIdentitiesStaySeparateAndNoProviderOrUnverifiedAliasLeaksIntoRawRows() {
        var result=sources().capture(cut(List.of(artifact("uuid-A",Completeness.COMPLETE,ProjectionStatus.MATERIALIZED_FAITHFULLY),artifact("uuid-B",Completeness.COMPLETE,ProjectionStatus.MATERIALIZED_FAITHFULLY)),Map.of("cartago",Completeness.COMPLETE)),"run",2);
        var rows=result.get("artifacts").rows();assertEquals(2,rows.size());assertNotEquals(rows.getFirst().identity(),rows.getLast().identity());
        assertEquals(Set.of(rows.getFirst().identity()),rows.getFirst().aliases());
        var values=CanonicalJson.object(rows.getFirst().values().get("values"));assertFalse(values.containsKey("auctionStateObservation"));
        assertEquals("inventory.Stock",values.get("artifactTypeJavaClassName"));assertEquals(Completeness.UNAVAILABLE,result.get("groups").populationQuality());
    }
    @Test void absentPopulationUnfaithfulEvidenceAndPartialRowsNeverBecomeCompleteOrFalse() {
        var source=sources();var unavailable=source.capture(cut(List.of(artifact("uuid",Completeness.COMPLETE,ProjectionStatus.EVIDENCE_ONLY)),Map.of("cartago",Completeness.COMPLETE)),"run",0).get("artifacts");
        assertNotEquals(Completeness.COMPLETE,unavailable.rows().getFirst().quality());assertNotEquals(Completeness.COMPLETE,unavailable.populationQuality());
        var partial=source.capture(cut(List.of(artifact("uuid",Completeness.COMPLETE,ProjectionStatus.MATERIALIZED_FAITHFULLY)),Map.of("cartago",Completeness.PARTIAL)),"run",0).get("artifacts");
        assertEquals(Completeness.PARTIAL,partial.populationQuality());assertEquals(Completeness.COMPLETE,partial.rows().getFirst().quality());
        assertEquals(Completeness.UNAVAILABLE,source.capture(cut(List.of(),Map.of()),"run",0).get("artifacts").populationQuality());
    }
    @Test void sourceLineageAndDuplicatePhysicalIdentityAreCheckedAndUnsupportedSourcesReject() {
        assertThrows(IllegalArgumentException.class,()->new RuntimeFactSources(MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",SOURCES.replace("authority=\"cartago\"","authority=\"guess\"")))));
        var fact=artifact("uuid",Completeness.COMPLETE,ProjectionStatus.MATERIALIZED_FAITHFULLY);
        assertThrows(IllegalArgumentException.class,()->sources().capture(cut(List.of(fact,fact),Map.of("cartago",Completeness.COMPLETE)),"run",0));
        assertThrows(IllegalArgumentException.class,()->sources().capture(cut(List.of(fact),Map.of()),"run",0));
    }
    @Test void publicTypedPropertiesReuseStrictScalarCodecAndNeverBooleanizeBadValues() {
        var source=new RuntimeFactSources(MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",SOURCES.replace("kind=\"ARTIFACT\"","kind=\"PROPERTY\""))));
        var id=new BridgeEntityId("cartago","environment","observable-property-snapshot","physical-artifact","p","snapshot");
        var fact=new RuntimeFact(id,RuntimeFactKind.PROPERTY,Map.of("artifactSemanticId","physical-artifact","name","p","values",List.of("false"),"valueTypes",List.of("java.lang.Boolean")),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        var row=source.capture(cut(List.of(fact),Map.of("cartago",Completeness.COMPLETE)),"run",0).get("artifacts").rows().getFirst();
        assertEquals(false,CanonicalJson.object(row.values().get("scalar")).get("value"));
        var bad=new RuntimeFact(id,RuntimeFactKind.PROPERTY,Map.of("name","p","values",List.of("unknown"),"valueTypes",List.of("java.lang.Boolean")),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
        assertFalse(source.capture(cut(List.of(bad),Map.of("cartago",Completeness.COMPLETE)),"run",0).get("artifacts").rows().getFirst().values().containsKey("scalar"));
    }
}
