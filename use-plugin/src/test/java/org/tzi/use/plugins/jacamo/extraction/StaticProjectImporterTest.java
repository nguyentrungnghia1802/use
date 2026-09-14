package org.tzi.use.plugins.jacamo.extraction;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticElement;

class StaticProjectImporterTest {
    @TempDir Path temporary;

    @Test
    void auctionProducesCoherentThreeDimensionModel() {
        ImportResult result = new StaticProjectImporter().importProject(
                Path.of("src/test/resources/auction/auction.jcm"));
        assertTrue(result.success(), () -> result.diagnostics().toString());
        Set<MetamodelKind> kinds = result.model().elements().stream().map(SemanticElement::kind).collect(Collectors.toSet());
        assertTrue(kinds.containsAll(Set.of(MetamodelKind.MAS, MetamodelKind.Agent, MetamodelKind.Workspace,
                MetamodelKind.Artifact, MetamodelKind.Belief, MetamodelKind.Rule, MetamodelKind.Goal,
                MetamodelKind.Plan, MetamodelKind.TriggeringEvent, MetamodelKind.Context, MetamodelKind.Body,
                MetamodelKind.ExternalAction, MetamodelKind.Message, MetamodelKind.ObsProperty,
                MetamodelKind.Operation, MetamodelKind.GuardOperation, MetamodelKind.InternalOperation,
                MetamodelKind.Organisation, MetamodelKind.Role, MetamodelKind.Group, MetamodelKind.Link,
                MetamodelKind.FormationConstraints, MetamodelKind.Scheme, MetamodelKind.Mission,
                MetamodelKind.OGoal, MetamodelKind.OPlan, MetamodelKind.Norm)));
        SemanticElement operation = only(result, MetamodelKind.Operation, "placeBid");
        assertEquals(new AttributeValue.Text("String item,int amount"), operation.attributes().get("parameters"));
        assertEquals(new AttributeValue.Text("signal(\"bid\", item, amount)"), operation.attributes().get("signalExpression"));
        assertEquals(new AttributeValue.Text("await(\"open\")"), operation.attributes().get("awaitExpression"));
        assertTrue(operation.references().stream().anyMatch(ref -> ref.feature().equals("guardedBy")
                && ref.targetId() != null));
        SemanticElement action = only(result, MetamodelKind.ExternalAction, "placeBid");
        assertTrue(action.references().stream().anyMatch(ref -> ref.feature().equals("operation")
                && ref.targetId() != null && ref.targetId().equals(operation.id())));
        SemanticElement agent = only(result, MetamodelKind.Agent, "auctioneer");
        assertTrue(agent.references().stream().anyMatch(ref -> ref.feature().equals("artifact")
                && ref.targetId() != null));
        assertTrue(agent.references().stream().anyMatch(ref -> ref.feature().equals("role")
                && ref.targetId() != null));
        SemanticElement norm = only(result, MetamodelKind.Norm, "n1");
        assertEquals(new AttributeValue.Text("obligation"), norm.attributes().get("type"));
        assertTrue(norm.references().stream().allMatch(ref -> ref.targetId() != null));
        assertEquals(3, result.model().sourceIndex().values().stream().filter(source ->
                source.kind().name().matches("ASL|JAVA|MOISE_XML")).count());
    }

    @Test
    void extractionIsDeterministic() {
        Path entry = Path.of("src/test/resources/auction/auction.jcm");
        ImportResult first = new StaticProjectImporter().importProject(entry);
        ImportResult second = new StaticProjectImporter().importProject(entry);
        assertEquals(first.model().elements(), second.model().elements());
        assertEquals(first.diagnostics(), second.diagnostics());
    }

    @Test
    void unsupportedJasonAndDynamicJavaArePreservedAsDiagnostics() throws Exception {
        Path project = Files.createDirectories(temporary.resolve("src/agt")).getParent().getParent();
        Files.createDirectories(project.resolve("src/env/demo"));
        Files.writeString(project.resolve("app.jcm"), "mas app { agent a:a.asl workspace w { artifact x:demo.X() } asl-path:src/agt java-path:src/env }\n");
        Files.writeString(project.resolve("src/agt/a.asl"), "+!g : p(X) | q(X) <- .custom(X).\n");
        Files.writeString(project.resolve("src/env/demo/X.java"), "package demo; class X { void x(){ defineObsProperty(name(), value()); } }\n");
        ImportResult result = new StaticProjectImporter().importProject(project.resolve("app.jcm"));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("JASON_UNSUPPORTED_EXPRESSION")));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("CARTAGO_DYNAMIC_PROPERTY")));
    }

    @Test
    void duplicateOperationNamesStayAmbiguousAcrossArtifacts() throws Exception {
        Files.createDirectories(temporary.resolve("src/env/demo"));
        Files.createDirectories(temporary.resolve("src/agt"));
        Files.writeString(temporary.resolve("app.jcm"), "mas app { agent a:a.asl workspace w { artifact x:demo.X() artifact y:demo.Y() } asl-path:src/agt java-path:src/env }\n");
        Files.writeString(temporary.resolve("src/agt/a.asl"), "+!g <- bid(1).\n");
        Files.writeString(temporary.resolve("src/env/demo/X.java"), "package demo; class X { @OPERATION void bid(int n){} }\n");
        Files.writeString(temporary.resolve("src/env/demo/Y.java"), "package demo; class Y { @OPERATION void bid(int n){} }\n");
        ImportResult result = new StaticProjectImporter().importProject(temporary.resolve("app.jcm"));
        SemanticElement action = only(result, MetamodelKind.ExternalAction, "bid");
        assertNull(action.references().stream().filter(ref -> ref.feature().equals("operation")).findFirst().orElseThrow().targetId());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("RESOLUTION_AMBIGUOUS")));
    }

    @Test
    void resolverNeverUsesSimilarNames() throws Exception {
        Files.createDirectories(temporary.resolve("src/env/demo"));
        Files.createDirectories(temporary.resolve("src/agt"));
        Files.writeString(temporary.resolve("app.jcm"), "mas app { agent a:a.asl workspace w { artifact x:demo.X() } asl-path:src/agt java-path:src/env }\n");
        Files.writeString(temporary.resolve("src/agt/a.asl"), "+!g <- placeBidd(1).\n");
        Files.writeString(temporary.resolve("src/env/demo/X.java"), "package demo; class X { @OPERATION void placeBid(int n){} }\n");
        ImportResult result = new StaticProjectImporter().importProject(temporary.resolve("app.jcm"));
        SemanticElement action = only(result, MetamodelKind.ExternalAction, "placeBidd");
        assertNull(action.references().getFirst().targetId());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("RESOLUTION_UNRESOLVED")));
    }

    @Test
    void malformedSourcesRecoverWithLocatedDiagnostics() throws Exception {
        Files.createDirectories(temporary.resolve("src/agt"));
        Files.createDirectories(temporary.resolve("src/org"));
        Files.writeString(temporary.resolve("app.jcm"), "mas app { agent a:a.asl organisation o:o.xml asl-path:src/agt org-path:src/org }\n");
        Files.writeString(temporary.resolve("src/agt/a.asl"), "+!broken : <- .print(x).\n");
        Files.writeString(temporary.resolve("src/org/o.xml"), "<broken>");
        ImportResult result = new StaticProjectImporter().importProject(temporary.resolve("app.jcm"));
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().startsWith("JASON_") && d.sourceLocation() != null));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("MOISE_XML_INVALID")));
    }

    private SemanticElement only(ImportResult result, MetamodelKind kind, String name) {
        List<SemanticElement> matches = result.model().elements().stream()
                .filter(element -> element.kind() == kind && element.name().equals(name)).toList();
        assertEquals(1, matches.size(), () -> kind + " " + name + " => " + matches);
        return matches.getFirst();
    }
}
