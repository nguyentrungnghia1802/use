package org.tzi.use.plugins.jacamo.semantic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.project.ProjectRoot;
import org.tzi.use.plugins.jacamo.project.SourceFile;
import org.tzi.use.plugins.jacamo.project.SourceKind;
import org.tzi.use.plugins.jacamo.project.SourceSpan;

class SemanticModelTest {
    @TempDir Path temporary;

    @Test
    void metamodelKindsMatchCanonicalEcoreAndHaveDimensions() throws Exception {
        String ecore = Files.readString(Path.of("Core/Metamodel/version-2/jacamo_v2_complete.ecore"));
        var matcher = Pattern.compile("<eClassifiers[^>]*xsi:type=\"ecore:EClass\"[^>]*name=\"([^\"]+)\"").matcher(ecore);
        Set<String> names = matcher.results().map(match -> match.group(1)).collect(Collectors.toSet());
        assertEquals(names, Arrays.stream(MetamodelKind.values()).map(MetamodelKind::name).collect(Collectors.toSet()));
        assertTrue(Arrays.stream(MetamodelKind.values()).noneMatch(k -> k.dimension() == Dimension.PROJECT));
        assertEquals(Dimension.AGENT, MetamodelKind.Belief.dimension());
        assertEquals(Dimension.ENVIRONMENT, MetamodelKind.Artifact.dimension());
        assertEquals(Dimension.ORGANISATION, MetamodelKind.Norm.dimension());
    }

    @Test
    void immutableIrKeepsExactKindTypedValueUnresolvedReferenceAndProvenance() throws Exception {
        Path jcm = temporary.resolve("demo.jcm");
        Files.writeString(jcm, "mas demo {}\n");
        SourceFile source = SourceFile.read(jcm, SourceKind.JCM);
        SourceSpan span = new SourceSpan(jcm, 1, 1, 1, 11);
        SourceProvenance provenance = new SourceProvenance(span, "jcm-loader", source.sha256(), "demo");
        SemanticId id = SemanticId.of("demo", Dimension.ORGANISATION, "Role", List.of("project"), "demo");
        SemanticElement mas = new SemanticElement(id, MetamodelKind.Role, "demo", List.of(provenance),
                Map.of("id", new AttributeValue.Text("demo"), "isAbstract", new AttributeValue.Bool(true)),
                List.of(new SemanticReference("agents", "alice", null)));
        assertEquals(MetamodelKind.Role, mas.kind());
        assertEquals("jcm-loader", mas.provenance().getFirst().parser());
        assertNull(mas.references().getFirst().targetId());
        assertThrows(UnsupportedOperationException.class,
                () -> mas.attributes().put("changed", new AttributeValue.IntegerNumber(3)));
        assertThrows(IllegalArgumentException.class,
                () -> new SemanticElement(id, MetamodelKind.Agent, "demo", List.of(provenance), Map.of(), List.of()));
    }

    @Test
    void rootIndexesSourcesAndAmbiguousSymbolsWithoutChoosingOne() throws Exception {
        Path jcm = temporary.resolve("demo.jcm");
        Files.writeString(jcm, "mas demo {}\n");
        SourceFile source = SourceFile.read(jcm, SourceKind.JCM);
        SourceProvenance provenance = new SourceProvenance(new SourceSpan(jcm, 1, 1, 1, 11),
                "jcm-loader", source.sha256(), "demo");
        ProjectDeclaration project = new ProjectDeclaration("demo", List.of(provenance), Map.of());
        SemanticElement alice = element("demo", MetamodelKind.Agent, "same", List.of("MAS", "a"), provenance);
        SemanticElement bob = element("demo", MetamodelKind.Agent, "same", List.of("MAS", "b"), provenance);
        JaCaMoSemanticModel model = new JaCaMoSemanticModel(new ProjectRoot(temporary, "demo"), project, MetamodelKind.registry(),
                List.of(bob, alice), List.of(source), List.of());
        assertEquals(List.of(alice.id(), bob.id()), model.symbolIndex().get("same"));
        assertEquals(source, model.sourceIndex().get(jcm.toAbsolutePath().normalize()));
        assertEquals(List.of(alice.id(), bob.id()), model.elements().stream().map(SemanticElement::id).toList());
        assertThrows(IllegalArgumentException.class, () -> new JaCaMoSemanticModel(
                new ProjectRoot(temporary, "demo"), project, MetamodelKind.registry(), List.of(alice, alice), List.of(source), List.of()));
    }

    private SemanticElement element(String projectId, MetamodelKind kind, String name,
                                    List<String> owner, SourceProvenance provenance) {
        return new SemanticElement(SemanticId.of(projectId, kind.dimension(), kind.name(), owner, name),
                kind, name, List.of(provenance), Map.of(), List.of());
    }
}
