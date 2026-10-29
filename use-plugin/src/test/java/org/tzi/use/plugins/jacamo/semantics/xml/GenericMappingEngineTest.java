package org.tzi.use.plugins.jacamo.semantics.xml;
import java.nio.file.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.vnu.sme.goal.dsl.istar.mm.*;
import static org.junit.jupiter.api.Assertions.*;
class GenericMappingEngineTest {
    @TempDir Path temp;
    @Test void staticEntityBindingRequiresATypeViewRatherThanAnObservedArtifactInstance()throws Exception {
        var program=new GenericMappingEngine().map(official(JCM),input());
        var entity=program.bindings().find(SemanticBindingIndex.Kind.ENTITY,"Counter").orElseThrow();
        for(String kind:java.util.List.of("CARTAGO_ARTIFACT","CARTAGO_ARTIFACT_INFO")) {
            var original=program.source();var changed=original.nodes().stream().map(n->!n.id().equals(entity.sourceId())?n:new NormalizedSourceModel.Node(n.id(),n.concept(),NormalizedSourceModel.Layer.RUNTIME_OBSERVATION,kind,n.sourceJavaFqcn(),n.container(),n.features())).toList();
            var instance=new NormalizedSourceModel(original.frontend(),original.sourceHash(),original.metamodelHash(),changed,original.diagnostics());
            assertThrows(IllegalArgumentException.class,()->program.bindings().validate(instance,program.cslModel(),program.goalModel(),java.util.List.of()),"An M0 Artifact cannot become the M1 Entity definition");
        }
    }
    static final Path JCM=Path.of("src/test/resources/semantics/profile-v1/monitor.jcm");
    static ModelSnapshot official(Path file)throws Exception{return new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(file),file);}
    MappingInputs input()throws Exception {
        return input(JCM);
    }
    MappingInputs input(Path project)throws Exception {
        var source=official(project);var artifact=source.semanticContract().artifactDeclarations().getFirst();
        var ocl=Files.writeString(temp.resolve("requirements.ocl"),"context Counter inv Nonnegative: self.p >= 0\n");
        var xml=new StringBuilder("<mapping xmlns=\"urn:use:jacamo-mapping:2\" version=\"2.0\" id=\"counter-xml\" engineProfile=\"USE4GOAL_OBSERVED_V1\" jcmRef=\"project\"><sources>");
        xml.append("<file id=\"project\" kind=\"JCM\" path=\"").append(project.getFileName()).append("\" sha256=\"").append(hash(project)).append("\"/>");
        xml.append("</sources><ocl sha256=\"").append(hash(ocl)).append("\"/><selectors>")
            .append("<select id=\"project-row\" sourceRef=\"project\" syntax=\"regex\" expectedCount=\"1\"><pattern>mas\\s+(?P&lt;name&gt;\\w+)</pattern></select></selectors>")
            .append("<modelRules targetVersion=\"v4.0\" targetModel=\"CounterView\"><rule id=\"entity\" selectRef=\"project-row\"><createClassifier kind=\"Entity\" name=\"Counter\"/><createAttribute name=\"p\" owner=\"Counter\" type=\"Integer\" optional=\"false\" mutable=\"true\"/></rule></modelRules>")
            .append("<istar name=\"Requirements\"><actor id=\"worker\" name=\"Worker\" kind=\"role\"><goal id=\"positive\" name=\"Positive\" type=\"Achieve\"><condition>self.p >= 0</condition></goal></actor></istar>")
            .append("<runtime/><observers/><bindings>")
            .append("<static id=\"entity-binding\" kind=\"ENTITY\" target=\"Counter\" sourceKind=\"DERIVED_ARTIFACT_CLASS_ALIAS\" feature=\"javaClass\" value=\"").append(artifact.javaClass()).append("\" reason=\"Explicit passive Artifact view\"/>")
            .append("<static id=\"actor-binding\" kind=\"ACTOR\" target=\"Worker\" sourceKind=\"MOISE_ROLE\" feature=\"roleId\" value=\"worker\" reason=\"Explicit intentional role abstraction\"/>")
            .append("<actorRuntime actor=\"Worker\" bindingRef=\"actor-binding\" roleBindingRef=\"actor-binding\" activationPolicy=\"ENACTMENT_INTERVAL\" episodePolicy=\"ENACTMENT_INCARNATION\"/>")
            .append("<elementRuntime target=\"Positive\" actor=\"Worker\" self=\"Counter\" derived=\"false\" selfKind=\"ARTIFACT\" reason=\"Authored world condition, not an agent mental goal\"/>")
            .append("</bindings><requirements/><omittedConcepts/></mapping>");
        var mapping=Files.writeString(temp.resolve("mapping.xml"),xml);
        return MappingInputs.read(project,mapping,ocl);
    }
    static String hash(Path file)throws Exception{return TraceComplement.Asset.of("check","bytes",Files.readAllBytes(file)).sha256();}
    @Test void ownsGeneratedModelsFrozenInputsAndCompleteOfficialComplement()throws Exception {
        var source=official(JCM);var inputs=input();var program=new GenericMappingEngine().map(source,inputs);
        assertEquals(program.hash(),new GenericMappingEngine().map(source,inputs).hash());
        assertTrue(program.cslModel().findEntity("Counter").isPresent());assertTrue(program.goalModel().findElement("Positive").isPresent());
        assertFalse(java.util.Arrays.stream(org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram.class.getDeclaredFields()).anyMatch(field->field.getName().equals("casePlan")));assertEquals(3,program.bindings().bindings().size());
        assertEquals(program.source().nodes().stream().mapToInt(n->n.features().size()).sum(),program.complement().decisions().size());
        assertTrue(program.complement().assets().stream().anyMatch(a->a.sha256().equals(inputs.xml().sha256())));
        assertThrows(UnsupportedOperationException.class,()->program.xmlPlan().evidence().clear());
        Files.writeString(temp.resolve("mapping.xml"),"broken later");
        assertEquals(program.hash(),new GenericMappingEngine().map(source,inputs).hash());
        ((Goal)program.goalModel().findElement("Positive").orElseThrow()).setCondition(new IStarOclConstraint("false"));
        assertThrows(IllegalStateException.class,program::assertUnchanged);
    }
    @Test void planCannotBeReboundOrBypassedAndUnpreparedNativePathCannotFallback()throws Exception {
        var source=official(JCM);var program=new GenericMappingEngine().map(source,input());
        assertThrows(IllegalArgumentException.class,()->SemanticProgram.fromXml(program.profile(),program.rules(),program.source(),program.cslModel(),program.goalModel(),new SemanticBindingIndex(java.util.List.of()),program.complement(),program.goalBindings(),program.xmlPlan()));
        var trace=program.complement();
        var incomplete=new TraceComplement(trace.version(),trace.ruleVersion(),trace.sourceHash(),trace.metamodelHash(),trace.assets().stream().filter(a->!a.id().equals("authored-ocl")).toList(),trace.decisions());
        assertThrows(IllegalArgumentException.class,()->SemanticProgram.fromXml(program.profile(),program.rules(),program.source(),program.cslModel(),program.goalModel(),program.bindings(),incomplete,program.goalBindings(),program.xmlPlan()));
        assertThrows(IllegalArgumentException.class,()->new SemanticProgram(program.profile(),program.rules(),program.source(),program.cslModel(),program.goalModel(),program.bindings(),program.complement(),java.util.List.of(),program.goalBindings()));
        var error=assertThrows(IllegalArgumentException.class,()->new org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline().buildCanonicalProgram(source,org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.AUTO,program));
        assertEquals("OCL_SPEC_CLAUSE_INVENTORY_MISMATCH",error.getMessage(),"G05 now admits native preparation, but never missing declarative OCL bindings");
    }
    @Test void rejectsOfficialSourceDriftAmbiguousQueriesAndAgentAsEntity()throws Exception {
        var inputs=input();var source=official(JCM);
        Path other=Files.writeString(temp.resolve("other.jcm"),"mas another {}\n");
        assertThrows(IllegalArgumentException.class,()->new GenericMappingEngine().map(official(other),inputs));
        var file=temp.resolve("mapping.xml");var original=Files.readString(file);
        for(String xml:new String[]{original.replace("feature=\"roleId\" value=\"worker\"","feature=\"missing\" value=\"worker\""),
                original.replace("sourceKind=\"DERIVED_ARTIFACT_CLASS_ALIAS\" feature=\"javaClass\"","sourceKind=\"JACAMO_AGENT_DECLARATION\" feature=\"name\"").replace(source.semanticContract().artifactDeclarations().getFirst().javaClass(),source.semanticContract().agentDeclarations().getFirst().name()),
                original.replace("derived=\"false\"","derived=\"true\"")}) {
            Files.writeString(file,xml);
            assertThrows(IllegalArgumentException.class,()->new GenericMappingEngine().map(source,MappingInputs.read(JCM,file,temp.resolve("requirements.ocl"))));
        }
    }
    @Test void selectedRequirementMustBeAUniqueRealGoalOrTaskNeverAnIgnoredTypo()throws Exception {
        var source=official(JCM);input();Path mapping=temp.resolve("mapping.xml");String original=Files.readString(mapping);
        for(String selection:java.util.List.of("<requirement target=\"Unknown\"/>","<requirement target=\"Positive\"/><requirement target=\"Positive\"/>")) {
            Files.writeString(mapping,original.replace("</bindings>",selection+"</bindings>"));
            assertThrows(IllegalArgumentException.class,()->new GenericMappingEngine().map(source,MappingInputs.read(JCM,mapping,temp.resolve("requirements.ocl"))),selection);
        }
    }

}
