package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.JaCaMoFacade.ImportSelection;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthoredModelInputsTest {
    @TempDir Path root;
    static String authoredXml(String cslHash, String goalHash) {
        return MappingXmlReaderTest.XML.replace("<selectors/>",
                "<models mode=\"AUTHORED\" cslSha256=\"" + cslHash
                        + "\" istarSha256=\"" + goalHash + "\"/><selectors/>");
    }

    @Test void authoredDeclarationIsAnExplicitFiveInputContract() {
        var spec = MappingXmlReaderTest.parse(authoredXml("3".repeat(64), "4".repeat(64)));
        assertEquals("AUTHORED", spec.root().child("models").attr("mode"));
        assertEquals("3".repeat(64), spec.root().child("models").attr("cslSha256"));
        assertThrows(IllegalArgumentException.class, () -> MappingXmlReaderTest.parse(
                authoredXml("bad", "4".repeat(64))));
        assertThrows(IllegalArgumentException.class, () -> MappingXmlReaderTest.parse(
                authoredXml("3".repeat(64), "4".repeat(64)).replace("AUTHORED", "AUTO")));
    }

    private Path setup() throws Exception {
        Files.writeString(root.resolve("demo.jcm"),"mas demo {}\n");
        Files.writeString(root.resolve("demo.ocl"),"context Counter inv Nonnegative: self.p >= 0\n");
        Files.writeString(root.resolve("chosen.csl"),"csl v4.0 Sample { entity Counter { attribute p : Integer required mutable; } }\n");
        Files.writeString(root.resolve("chosen.istar"),"istar SampleGoals { role Worker { goal Positive : Achieve condition {[ self.p >= 0 ]} } }\n");
        return Files.writeString(root.resolve("mapping.xml"),authoredXml(hash("chosen.csl"),hash("chosen.istar"))
                .replace("1".repeat(64),hash("demo.jcm")).replace("2".repeat(64),hash("demo.ocl")));
    }
    private String hash(String name) throws Exception {return TraceComplement.Asset.of("hash","bytes",Files.readAllBytes(root.resolve(name))).sha256();}
    private MappingInputs read(Path xml) {return MappingInputs.read(root.resolve("demo.jcm"),xml,root.resolve("demo.ocl"),root.resolve("chosen.csl"),root.resolve("chosen.istar"));}

    @Test void freezesSelectedModelsAndReconstructsWithoutAnySourceFiles() throws Exception {
        var inputs=read(setup());assertTrue(inputs.authoredModels());
        var csl=new AuthoredModelLoader().csl(inputs);var goals=new AuthoredModelLoader().goals(inputs);
        assertEquals("Sample",csl.model().name());assertEquals("SampleGoals",goals.model().getName());
        for(String name:List.of("chosen.csl","chosen.istar","demo.jcm","demo.ocl","mapping.xml"))Files.delete(root.resolve(name));
        var restored=MappingInputs.fromFrozen(inputs.xml().bytes(),inputs.ocl().bytes(),inputs.sources(),"offline recording");
        assertEquals(csl.fingerprint(),new AuthoredModelLoader().csl(restored).fingerprint());
        assertEquals(goals.generatedSource(),new AuthoredModelLoader().goals(restored).generatedSource());
        assertEquals(3,restored.sources().size());
        var tampered=new LinkedHashMap<>(inputs.sources());
        tampered.put(MappingInputs.AUTHORED_CSL,TraceComplement.Asset.of(MappingInputs.AUTHORED_CSL,"NATIVE_CSL/4.0","csl v4.0 Other {}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,()->MappingInputs.fromFrozen(inputs.xml().bytes(),inputs.ocl().bytes(),tampered,"offline"));
    }

    @Test void rejectsMissingSelectionChangedBytesMixedGenerationAndWrongModelIdentity() throws Exception {
        Path xml=setup();String original=Files.readString(xml);
        assertThrows(IllegalArgumentException.class,()->MappingInputs.read(root.resolve("demo.jcm"),xml,root.resolve("demo.ocl")));
        Files.writeString(root.resolve("chosen.csl"),"csl v4.0 Other {}\n");assertThrows(IllegalArgumentException.class,()->read(xml));
        Files.writeString(xml,original.replaceAll("cslSha256=\"[a-f0-9]+\"","cslSha256=\""+hash("chosen.csl")+"\""));
        var mismatched=read(xml);assertThrows(IllegalArgumentException.class,()->new AuthoredModelLoader().csl(mismatched));
        assertThrows(IllegalArgumentException.class,()->MappingXmlReaderTest.parse(original.replace("<istar name=\"SampleGoals\"/>","<istar name=\"SampleGoals\"><actor id=\"ignored\" name=\"Hidden\" kind=\"role\"/></istar>")));
        assertThrows(IllegalArgumentException.class,()->new ImportSelection("JACAMO_DECLARATIVE_XML",xml,root.resolve("demo.ocl"),root.resolve("chosen.csl"),null));
    }

    @Test void buildsCanonicalProgramFromAuthoredModelsAndReplaysTheirExactBytes() throws Exception {
        var helper=new GenericMappingEngineTest();helper.temp=root;
        var generatedInputs=helper.input();var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);
        var generated=new GenericMappingEngine().map(source,generatedInputs);
        var csl=Files.writeString(root.resolve("case.csl"),generated.rules().cslSource());
        var goal=Files.writeString(root.resolve("case.istar"),generated.rules().iStarSource());
        String xml=Files.readString(root.resolve("mapping.xml"))
                .replaceFirst("(?s)<modelRules .*?</modelRules>","<modelRules targetVersion=\"v4.0\" targetModel=\"CounterView\"/>")
                .replaceFirst("(?s)<istar .*?</istar>","<istar name=\"Requirements\"/>")
                .replace("<selectors>","<models mode=\"AUTHORED\" cslSha256=\""+hash("case.csl")+"\" istarSha256=\""+hash("case.istar")+"\"/><selectors>");
        Path mapping=Files.writeString(root.resolve("mapping.xml"),xml);
        var inputs=MappingInputs.read(GenericMappingEngineTest.JCM,mapping,root.resolve("requirements.ocl"),csl,goal);
        var program=new GenericMappingEngine().map(source,inputs);
        assertEquals(generated.cslHash(),program.cslHash());assertEquals(generated.goalHash(),program.goalHash());
        assertNotEquals(generated.hash(),program.hash(),"Input mode and frozen authored bytes are provenance");
        assertEquals("AUTHORED_CSL_ISTAR/1.0",program.xmlPlan().evidence().get("modelInputMode"));
        byte[] recording=new SemanticProgramManifest().encode(program);
        assertEquals("1.3.0",org.jacamo.bridge.contract.CanonicalJson.object(org.jacamo.bridge.contract.CanonicalJson.decode(recording)).get("schemaVersion"));
        var wrongVersion=new TreeMap<>(org.jacamo.bridge.contract.CanonicalJson.object(org.jacamo.bridge.contract.CanonicalJson.decode(recording)));
        wrongVersion.put("schemaVersion","1.2.0");
        assertThrows(IllegalArgumentException.class,()->new SemanticProgramManifest().decode(org.jacamo.bridge.contract.CanonicalJson.encode(wrongVersion)));
        String oldHash=hash("case.csl");
        Files.writeString(csl,Files.readString(csl).replace("CounterView","RevisedView"));
        Files.writeString(mapping,xml.replace("CounterView","RevisedView").replace(oldHash,hash("case.csl")));
        var revised=new GenericMappingEngine().map(source,MappingInputs.read(GenericMappingEngineTest.JCM,mapping,root.resolve("requirements.ocl"),csl,goal));
        assertNotEquals(program.hash(),revised.hash());assertNotEquals(program.cslHash(),revised.cslHash());
        assertEquals(program.goalHash(),revised.goalHash());
        Files.delete(csl);Files.delete(goal);Files.delete(mapping);Files.delete(root.resolve("requirements.ocl"));
        assertEquals(program.hash(),new SemanticProgramManifest().decode(recording).program().hash());
    }

    @Test void authoredRuntimeInstructionsArePreflightedBeforeCreatingLiveState() throws Exception {
        var helper=new GenericMappingEngineTest();helper.temp=root;
        var baseline=helper.input();var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);
        var generated=new GenericMappingEngine().map(source,baseline);
        Path csl=Files.writeString(root.resolve("case.csl"),generated.rules().cslSource());
        Path goal=Files.writeString(root.resolve("case.istar"),generated.rules().iStarSource());
        String xml=Files.readString(root.resolve("mapping.xml"))
                .replaceFirst("(?s)<modelRules .*?</modelRules>","<modelRules targetVersion=\"v4.0\" targetModel=\"CounterView\"/>")
                .replaceFirst("(?s)<istar .*?</istar>","<istar name=\"Requirements\"/>")
                .replace("<selectors>","<models mode=\"AUTHORED\" cslSha256=\""+hash("case.csl")+"\" istarSha256=\""+hash("case.istar")+"\"/><selectors>")
                .replace("<runtime/>",XmlRuntimeMappingTest.PLAN)
                .replace("</bindings>","<oclClause name=\"Nonnegative\" context=\"context Counter\"/></bindings>");
        Path mapping=Files.writeString(root.resolve("mapping.xml"),xml);
        var program=new GenericMappingEngine().map(source,MappingInputs.read(GenericMappingEngineTest.JCM,mapping,root.resolve("requirements.ocl"),csl,goal));
        var preparation=new org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlNativePreparation();
        var prepared=preparation.prepare(source,program,new org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector());
        assertEquals("AUTHORED_ISTAR_CONDITION",prepared.intentional().contracts().getFirst().origin());
        assertEquals("XML_BINDING_PREFLIGHT/1.0",prepared.preflight().get("version"));
        var reads=(List<?>)prepared.preflight().get("reads");
        assertTrue(reads.stream().map(org.jacamo.bridge.contract.CanonicalJson::object).anyMatch(r->r.get("field").equals("Counter::p")&&r.get("status").equals("DECLARED_NOT_OBSERVED")));
        Files.writeString(mapping,xml.replace("assign target=\"p\"","assign target=\"absent\""));
        var invalid=new GenericMappingEngine().map(source,MappingInputs.read(GenericMappingEngineTest.JCM,mapping,root.resolve("requirements.ocl"),csl,goal));
        var error=assertThrows(IllegalArgumentException.class,()->preparation.prepare(source,invalid,new org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector()));
        assertTrue(error.getMessage().contains("M0_ASSIGNMENT_TYPE_OR_DUPLICATE"),error.getMessage());
    }

    @Test void cslInlineInvariantsAndImplicitInvariantPopulationEnterTheFrozenNativeReadSet() throws Exception {
        var helper=new GenericMappingEngineTest();helper.temp=root;
        var inputs=helper.input();var source=GenericMappingEngineTest.official(GenericMappingEngineTest.JCM);
        var generated=new GenericMappingEngine().map(source,inputs);
        String text=generated.rules().cslSource();int end=text.lastIndexOf('}');
        Path csl=Files.writeString(root.resolve("case.csl"),text.substring(0,end)+"\ncontext Counter inv InlineBound: self.p >= -100;\n"+text.substring(end));
        Path goal=Files.writeString(root.resolve("case.istar"),generated.rules().iStarSource());
        String xml=Files.readString(root.resolve("mapping.xml"))
                .replaceFirst("(?s)<modelRules .*?</modelRules>","<modelRules targetVersion=\"v4.0\" targetModel=\"CounterView\"/>")
                .replaceFirst("(?s)<istar .*?</istar>","<istar name=\"Requirements\"/>")
                .replace("<selectors>","<models mode=\"AUTHORED\" cslSha256=\""+hash("case.csl")+"\" istarSha256=\""+hash("case.istar")+"\"/><selectors>")
                .replace("<runtime/>",XmlRuntimeMappingTest.PLAN)
                .replace("</bindings>","<oclClause name=\"Nonnegative\" context=\"context Counter\"/></bindings>");
        Path mapping=Files.writeString(root.resolve("mapping.xml"),xml);
        var program=new GenericMappingEngine().map(source,MappingInputs.read(GenericMappingEngineTest.JCM,mapping,root.resolve("requirements.ocl"),csl,goal));
        var prepared=new org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlNativePreparation().prepare(source,program,
                new org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector());
        var readSet=org.jacamo.bridge.contract.CanonicalJson.object(prepared.preflight().get("readSet"));
        assertEquals("OBSERVATION_READ_SET/1.0",readSet.get("version"));
        assertTrue(((List<?>)readSet.get("attributes")).stream().map(org.jacamo.bridge.contract.CanonicalJson::object)
                .anyMatch(r -> r.get("contract").equals("CSL:Counter::InlineBound") && r.get("field").equals("Counter::p")));
        assertTrue(((List<?>)readSet.get("populations")).stream().map(org.jacamo.bridge.contract.CanonicalJson::object)
                .anyMatch(r -> r.get("contract").equals("OCL:Nonnegative") && r.get("population").equals("Counter")),
                "An invariant evaluates over its context population without authored allInstances syntax");
        assertTrue(((List<?>)readSet.get("populations")).stream().map(org.jacamo.bridge.contract.CanonicalJson::object)
                .anyMatch(r -> r.get("contract").equals("CSL:Counter::InlineBound") && r.get("population").equals("Counter")),
                "An intentional contract copy must not replace the CSL invariant's expanded context population");
        var scope=org.jacamo.bridge.contract.ObservationCaptureScope.fromMap(org.jacamo.bridge.contract.CanonicalJson.object(prepared.preflight().get("captureScope")));
        assertEquals(prepared.preflight().get("captureScopeHash"),scope.hash());
        assertEquals(prepared.preflight().get("readSetHash"),scope.readSetHash());
        assertEquals(Set.of("p"),scope.propertyNames());
    }
}
