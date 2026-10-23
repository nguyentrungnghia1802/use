package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import static org.junit.jupiter.api.Assertions.*;
class CapabilityRequirementsTest {
    @TempDir Path temp;
    @Test void officialBinaryOriginIsResolvedFromVerifiedProducerEvidenceAndCannotBeSpoofed()throws Exception {
        var fixture=new GenericMappingEngineTest();fixture.temp=temp;fixture.input();
        var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);String type=source.semanticContract().artifactDeclarations().getFirst().javaClass();
        Path xml=temp.resolve("mapping.xml");String original=Files.readString(xml);
        String selected=original.replace("<requirements/>","<requirements><capability id=\"history\" provider=\"cartago.operation-history.v1\" required=\"true\" class=\""+type+"\" hashOrigin=\"OFFICIAL_CLASS_PROOF\"/></requirements>");
        Files.writeString(xml,selected);var inputs=MappingInputs.read(GenericMappingEngineTest.JCM,xml,temp.resolve("requirements.ocl"));
        var request=CapabilityRequirements.compile(inputs,source).getFirst();
        assertEquals(org.tzi.use.plugins.jacamo.semantics.mapping.SourceArtifactTypeReferences.classProofs(source,type).getFirst().sourceDigest(),request.parameters().get("hash"));
        assertFalse(request.parameters().containsKey("hashOrigin"));assertThrows(IllegalArgumentException.class,()->CapabilityRequirements.compile(inputs));
        for(String changed:List.of(selected.replace("OFFICIAL_CLASS_PROOF","GUESS"),selected.replace("hashOrigin=", "hash=\""+"0".repeat(64)+"\" hashOrigin="),selected.replace(type,"missing.OtherArtifact"),selected.replace("cartago.operation-history.v1","framework.missing"))) {
            Files.writeString(xml,changed);var invalid=MappingInputs.read(GenericMappingEngineTest.JCM,xml,temp.resolve("requirements.ocl"));
            assertThrows(IllegalArgumentException.class,()->CapabilityRequirements.compile(invalid,source));
        }
    }
    @Test void xmlOwnsRequestDataAndSourcePinsWithoutClaimingProviderAvailability()throws Exception {
        var fixture=new GenericMappingEngineTest();fixture.temp=temp;fixture.input();Path xml=temp.resolve("mapping.xml");
        Files.writeString(xml,Files.readString(xml).replace("<requirements/>","<requirements><capability id=\"field\" provider=\"cartago.artifact-field.v1\" required=\"true\" sourceRef=\"project\" class=\"example.OtherArtifact\" field=\"owner\" type=\"java.lang.String\"><property name=\"p\" type=\"JAVA_INTEGER\"/></capability><capability id=\"future\" provider=\"framework.missing\" required=\"false\"/></requirements>"));
        var input=MappingInputs.read(GenericMappingEngineTest.JCM,xml,temp.resolve("requirements.ocl"));
        var requests=CapabilityRequirements.compile(input);assertEquals(2,requests.size());
        assertEquals("owner",requests.getFirst().parameters().get("field"));assertEquals("monitor.jcm",requests.getFirst().parameters().get("sourcePath"));
        assertEquals(input.require("project").sha256(),requests.getFirst().parameters().get("sourceHash"));
        var program=new GenericMappingEngine().map(GenericMappingEngineTest.official(GenericMappingEngineTest.JCM),input);
        assertEquals(requests,program.xmlPlan().requirements());assertThrows(UnsupportedOperationException.class,()->requests.clear());
        assertTrue(requests.getLast().parameters().isEmpty());
    }
}
