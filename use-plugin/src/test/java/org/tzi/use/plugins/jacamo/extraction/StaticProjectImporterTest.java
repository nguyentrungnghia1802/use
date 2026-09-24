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
        assertTrue(kinds.containsAll(Set.of(MetamodelKind.Agent, MetamodelKind.Environment, MetamodelKind.Workspace,
                MetamodelKind.Artifact, MetamodelKind.Belief, MetamodelKind.AGoal, MetamodelKind.Plan, MetamodelKind.Event,
                MetamodelKind.Action, MetamodelKind.Property, MetamodelKind.Operation, MetamodelKind.Signal,
                MetamodelKind.Organization, MetamodelKind.Role, MetamodelKind.Group, MetamodelKind.Link,
                MetamodelKind.Scheme, MetamodelKind.Mission, MetamodelKind.OGoal, MetamodelKind.Norm)));
        assertTrue(kinds.stream().allMatch(k -> MetamodelKind.registry().resolve(k.name()).kind() != null));
        assertEquals("auction", result.model().declaration().name());
        assertNull(result.model().mas(), "Project declaration must not masquerade as an EClass");
        SemanticElement operation = only(result, MetamodelKind.Operation, "placeBid");
        assertEquals(new AttributeValue.Text("String item,int amount"), operation.sourceFacts().get("parameters"));
        assertEquals(new AttributeValue.Text("signal(\"bid\", item, amount)"), operation.sourceFacts().get("signalExpression"));
        assertEquals(new AttributeValue.IntegerNumber(2), operation.attributes().get("arity"));
        assertNotNull(operation.sourceFacts().get("guardedBy"));
        assertNotNull(only(result, MetamodelKind.Operation, "closeAuction"));
        assertNotNull(only(result, MetamodelKind.Operation, "removeOpen"));
        SemanticElement artifact = only(result, MetamodelKind.Artifact, "auction1");
        assertTrue(artifact.references().stream().anyMatch(ref -> ref.feature().equals("properties") && ref.targetId() != null));
        assertTrue(artifact.sourceFacts().keySet().stream().anyMatch(k -> k.startsWith("guard:")));
        SemanticElement action = only(result, MetamodelKind.Action, "placeBid");
        assertEquals(new AttributeValue.EnumLiteral("ActionKind", "EXTERNAL"), action.attributes().get("kind"));
        assertTrue(action.references().stream().anyMatch(ref -> ref.feature().equals("operation") && operation.id().equals(ref.targetId())));
        SemanticElement agent = only(result, MetamodelKind.Agent, "auctioneer");
        assertTrue(agent.references().stream().anyMatch(ref -> ref.feature().equals("artifacts") && ref.targetId() != null));
        assertTrue(agent.sourceFacts().keySet().stream().anyMatch(k -> k.startsWith("rule@")));
        SemanticElement norm = only(result, MetamodelKind.Norm, "n1");
        assertEquals(new AttributeValue.EnumLiteral("NormType", "OBLIGATION"), norm.attributes().get("type"));
        assertTrue(norm.references().stream().allMatch(ref -> ref.targetId() != null));
        assertEquals(new AttributeValue.Text("before auction closes"), norm.attributes().get("timeConstraint"));
        SemanticElement group = only(result, MetamodelKind.Group, "auction_group");
        assertTrue(group.references().stream().anyMatch(ref -> ref.feature().equals("roles") && ref.targetId() != null));
        SemanticElement role = only(result, MetamodelKind.Role, "auctioneer");
        assertTrue(role.references().stream().anyMatch(ref -> ref.feature().equals("agents") && agent.id().equals(ref.targetId())));
        SemanticElement scheme = only(result, MetamodelKind.Scheme, "auction_scheme");
        assertTrue(scheme.references().stream().anyMatch(ref -> ref.feature().equals("missions") && ref.targetId() != null));
        assertTrue(scheme.references().stream().anyMatch(ref -> ref.feature().equals("rootGoal") && ref.targetId() != null));
        assertTrue(scheme.sourceFacts().containsKey("unownedPlan@1"));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("MOISE_PLAN_OWNER_UNRESOLVED")));
        assertFalse(kinds.contains(MetamodelKind.OPlan), "Do not invent ownership for a sibling source plan");
        assertNotNull(only(result, MetamodelKind.AGoal, "start"));
        SemanticElement plan = only(result, MetamodelKind.Plan, "plan@5");
        assertTrue(plan.references().stream().anyMatch(ref -> ref.feature().equals("actions")));
        assertTrue(plan.references().stream().anyMatch(ref -> ref.feature().equals("triggeringEvent")));
        assertEquals(new AttributeValue.Text("auction_open"), plan.attributes().get("context"));
        assertTrue(only(result, MetamodelKind.Organization, "auction_org").references().stream()
                .anyMatch(ref -> ref.feature().equals("groups") && group.id().equals(ref.targetId())));
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
        SemanticElement action = only(result, MetamodelKind.Action, "bid");
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
        SemanticElement action = only(result, MetamodelKind.Action, "placeBidd");
        assertNull(action.references().getFirst().targetId());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("RESOLUTION_UNRESOLVED")));
    }

    @Test
    void partialProjectRetainsValidAgentSourceWithoutInventingMissingSourceContents() throws Exception {
        Files.writeString(temporary.resolve("app.jcm"),
                "mas app { agent present:present.asl agent missing:missing.asl }\n");
        Files.writeString(temporary.resolve("present.asl"), "ready.\n+!go <- .print(ready).\n");
        ImportResult result = new StaticProjectImporter().importProject(temporary.resolve("app.jcm"));
        assertFalse(result.success());
        assertNotNull(result.model());
        assertNotNull(only(result, MetamodelKind.Belief, "ready"));
        assertEquals(2, result.model().elements().stream().filter(e -> e.kind() == MetamodelKind.Agent).count());
        assertTrue(result.model().sourceIndex().values().stream().noneMatch(s ->
                s.path().getFileName().toString().equals("missing.asl")));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("JCM_SOURCE_MISSING")
                && d.sourceLocation() != null && !d.remediation().isBlank()));
    }

    @Test
    void staticImportNeverInitializesProjectSourceOrClasspathClasses() throws Exception {
        Path sourceRoot = Files.createDirectories(temporary.resolve("src/demo"));
        Path classes = Files.createDirectories(temporary.resolve("classes"));
        Path marker = temporary.resolve("executed.txt");
        String literal = marker.toString().replace("\\", "\\\\");
        Path source = Files.writeString(sourceRoot.resolve("Unsafe.java"),
                "package demo; public class Unsafe { static { try { java.nio.file.Files.writeString("
                + "java.nio.file.Path.of(\"" + literal + "\"), \"executed\"); } catch(Exception e) { throw new RuntimeException(e); } } }");
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-proc:none", "-d", classes.toString(), source.toString()));
        Path entry = temporary.resolve("app.jcm");
        Files.writeString(entry, "mas app { workspace w { artifact unsafe:demo.Unsafe() } java-path:src class-path:classes }\n");
        ImportResult fromSource = new StaticProjectImporter().importProject(entry);
        assertTrue(fromSource.success(), () -> fromSource.diagnostics().toString());
        assertNotNull(only(fromSource, MetamodelKind.Artifact, "unsafe"));
        assertFalse(Files.exists(marker), "source parsing must not initialize imported classes");
        Files.writeString(entry, "mas app { workspace w { artifact unsafe:demo.Unsafe() } java-path:absent class-path:classes }\n");
        ImportResult fromClass = new StaticProjectImporter().importProject(entry);
        assertTrue(fromClass.success(), () -> fromClass.diagnostics().toString());
        assertTrue(fromClass.diagnostics().stream().anyMatch(d -> d.code().equals("CARTAGO_BYTECODE_UNSUPPORTED")));
        assertFalse(Files.exists(marker), "classpath inspection must not initialize imported classes");
        // Positive control proves the sentinel is executable, rather than inert test data.
        try (var loader = new java.net.URLClassLoader(new java.net.URL[] { classes.toUri().toURL() }, null)) {
            Class.forName("demo.Unsafe", true, loader);
        }
        assertEquals("executed", Files.readString(marker));
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

    @Test
    void xmlBooleansAreValidatedWhileV2CardinalitiesRetainTheirStringDatatype() throws Exception {
        Files.createDirectories(temporary.resolve("src/org"));
        Files.writeString(temporary.resolve("app.jcm"), "mas app { organisation o:o.xml org-path:src/org }");
        String source = Files.readString(Path.of("src/test/resources/auction/src/org/auction.xml"));
        for (String literal : List.of("nonsense", "1", "0", "true", "false")) {
            Files.writeString(temporary.resolve("src/org/o.xml"), source.replace("bi-dir=\"true\"", "bi-dir=\"" + literal + "\"")
                    .replace("min=\"1\"", "min=\"bad\""));
            var result = new StaticProjectImporter().importProject(temporary.resolve("app.jcm"));
            assertEquals(literal.equals("nonsense"), result.diagnostics().stream().anyMatch(d -> d.code().equals("MOISE_ATTRIBUTE_INVALID") && d.sourceLocation() != null));
            var link = result.model().elements().stream().filter(e -> e.kind() == MetamodelKind.Link).findFirst().orElseThrow();
            if (literal.equals("nonsense")) assertFalse(link.attributes().containsKey("bidirectional"));
            else assertEquals(new AttributeValue.Bool(literal.equals("true") || literal.equals("1")), link.attributes().get("bidirectional"));
            assertTrue(result.model().elements().stream().anyMatch(e -> new AttributeValue.Text("bad").equals(e.attributes().get("minCardinality"))));
        }
    }

    private SemanticElement only(ImportResult result, MetamodelKind kind, String name) {
        List<SemanticElement> matches = result.model().elements().stream()
                .filter(element -> element.kind() == kind && element.name().equals(name)).toList();
        assertEquals(1, matches.size(), () -> kind + " " + name + " => " + matches);
        return matches.getFirst();
    }
}
