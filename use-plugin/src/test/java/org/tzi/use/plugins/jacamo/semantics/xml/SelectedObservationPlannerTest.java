package org.tzi.use.plugins.jacamo.semantics.xml;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.SelectedObservationPlanner;
import static org.junit.jupiter.api.Assertions.*;

class SelectedObservationPlannerTest {
    @Test void realAuthoredSecondCaseCompilesSelectedReadsWithoutRequestingMentalStateOrOptionalObservers() throws Exception {
        var root=java.nio.file.Path.of("examples/csl-counter");var inputs=root.resolve("authored-v1");
        var source=GenericMappingEngineTest.official(root.resolve("counter.jcm"));
        var selected=org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingInputs.read(root.resolve("counter.jcm"),
                inputs.resolve("stock-mapping.xml"),inputs.resolve("stock.ocl"),inputs.resolve("stock.csl"),inputs.resolve("stock.istar"));
        var program=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericMappingEngine().map(source,selected);
        var prepared=new org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlNativePreparation().prepare(source,program,
                new org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector());
        var scope=ObservationCaptureScope.fromMap(CanonicalJson.object(prepared.preflight().get("captureScope")));
        assertEquals(Set.of("p"),scope.propertyNames());assertFalse(scope.allProperties());
        assertEquals(List.of(),program.xmlPlan().requirements());
        assertFalse(scope.nplObservation());assertEquals(Set.of(RuntimeFactKind.GROUP_BOARD),scope.moiseKinds());
        assertFalse(org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources.producerFields(selected.specification()).get("jason").contains("storedBeliefObservation"));
        var reads=CanonicalJson.object(prepared.preflight().get("readSet"));
        assertTrue(((List<?>)reads.get("attributes")).stream().map(CanonicalJson::object).anyMatch(r->r.get("field").equals("Pallet::weight")));
        assertTrue(((List<?>)reads.get("populations")).stream().map(CanonicalJson::object).anyMatch(r->r.get("population").equals("Pallet")));
        assertEquals(scope.hash(),prepared.preflight().get("captureScopeHash"));
    }
    @Test void declarativePropertyNamesAndProviderFieldsDriveCaptureWithoutCaseJava() {
        var spec=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",XmlRuntimeMappingTest.PLAN));
        var scope=SelectedObservationPlanner.compile(spec,"a".repeat(64),List.of());
        assertFalse(scope.allProperties());assertEquals(Set.of("p"),scope.propertyNames());
        assertTrue(scope.allArtifactClasses(),"A global property dataset must not be narrowed by a downstream class join");
        var origins=SelectedObservationPlanner.origins(spec,"object-input");
        assertEquals(Set.of("raw-artifacts","raw-properties"),origins.stream().map(r->r.get("dataset")).collect(java.util.stream.Collectors.toSet()));
        var renamed=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",XmlRuntimeMappingTest.PLAN.replace("equals=\"p\"","equals=\"reading\"")));
        assertEquals(Set.of("reading"),SelectedObservationPlanner.compile(renamed,"a".repeat(64),List.of()).propertyNames());
        assertNotEquals(scope.hash(),SelectedObservationPlanner.compile(renamed,"a".repeat(64),List.of()).hash());
        var request=new ObservationCapabilityRequest("fields","cartago.artifact-field.v1",true,
                Map.of("class","example.Meter","properties",Map.of("voltage","INTEGER")));
        var providerScope=SelectedObservationPlanner.compile(MappingXmlReaderTest.parse(MappingXmlReaderTest.XML),"a".repeat(64),List.of(request));
        assertEquals(Set.of("example.Meter"),providerScope.artifactClasses());
        assertEquals(Set.of("voltage"),providerScope.propertyNames());
        var empty=SelectedObservationPlanner.compile(MappingXmlReaderTest.parse(MappingXmlReaderTest.XML),"a".repeat(64),List.of());
        assertFalse(empty.includesArtifactClass("example.Meter"));assertFalse(empty.includesProperty("voltage"));
    }

    @Test void broadOrMultipleConsumersCannotAccidentallyAcquireAnIncompletePopulation() {
        String runtime="""
            <runtime><datasets><facts id="raw" authority="cartago" kind="ARTIFACT"/></datasets><tables>
              <table id="one" dataset="raw"><where path="values.artifactTypeJavaClassName" type="String" equals="example.One"/><column name="uuid" path="identity" required="true"/></table>
              <table id="two" dataset="raw"><where path="values.artifactTypeJavaClassName" type="String" equals="example.Two"/><column name="uuid" path="identity" required="true"/></table>
            </tables></runtime>
            """;
        var spec=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",runtime));
        var scope=SelectedObservationPlanner.compile(spec,"a".repeat(64),List.of());
        assertEquals(Set.of("example.One","example.Two"),scope.artifactClasses());assertFalse(scope.allArtifactClasses());
        var broad=MappingXmlReaderTest.parse(MappingXmlReaderTest.XML.replace("<runtime/>",runtime.replace("<where path=\"values.artifactTypeJavaClassName\" type=\"String\" equals=\"example.Two\"/>","")));
        assertTrue(SelectedObservationPlanner.compile(broad,"a".repeat(64),List.of()).allArtifactClasses());
        assertFalse(scope.allProperties());assertTrue(scope.propertyNames().isEmpty());
    }
}
