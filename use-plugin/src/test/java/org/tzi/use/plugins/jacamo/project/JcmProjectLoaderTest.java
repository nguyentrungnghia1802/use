package org.tzi.use.plugins.jacamo.project;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JcmProjectLoaderTest {
    @TempDir Path temporary;

    @Test
    void discoversMinimalProjectAndIncludesWithStableGraph() {
        Path entry = Path.of("src/test/resources/jcm/minimal/app.jcm");
        ProjectDiscoveryResult first = new JcmProjectLoader().discover(entry);
        ProjectDiscoveryResult second = new JcmProjectLoader().discover(entry);
        assertTrue(first.success(), () -> first.diagnostics().toString());
        assertEquals("demo", first.graph().root().projectId());
        assertEquals(first.graph(), second.graph());
        assertEquals(Set.of("app.jcm", "shared.jcm", "alice.asl", "bob.asl", "team.xml", "Counter.java"),
                first.graph().sources().stream().map(source -> source.path().getFileName().toString()).collect(Collectors.toSet()));
        assertEquals(5, first.graph().edges().size());
        assertEquals(Set.of(ProjectEdgeKind.INCLUDE, ProjectEdgeKind.AGENT_SOURCE,
                ProjectEdgeKind.ORGANISATION_SOURCE, ProjectEdgeKind.ARTIFACT_SOURCE),
                first.graph().edges().stream().map(ProjectEdge::kind).collect(Collectors.toSet()));
    }

    @Test
    void invalidEntryReturnsActionableDiagnostic() {
        ProjectDiscoveryResult result = new JcmProjectLoader().discover(temporary.resolve("missing.jcm"));
        assertFalse(result.success());
        assertNull(result.graph());
        assertEquals("JCM_ENTRY_MISSING", result.diagnostics().getFirst().code());
        assertFalse(result.diagnostics().getFirst().remediation().isBlank());
    }

    @Test
    void includeCycleReportsSourceLocation() throws Exception {
        Files.writeString(temporary.resolve("a.jcm"), "mas a uses b { }\n");
        Files.writeString(temporary.resolve("b.jcm"), "mas b uses a { }\n");
        ProjectDiscoveryResult result = new JcmProjectLoader().discover(temporary.resolve("a.jcm"));
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.code().equals("JCM_INCLUDE_CYCLE") && d.sourceLocation() != null));
    }

    @Test
    void includeTraversalOutsideRootIsRejected() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("project"));
        Files.writeString(temporary.resolve("outside.jcm"), "mas outside {}\n");
        Files.writeString(root.resolve("app.jcm"), "mas app uses ../outside { }\n");
        ProjectDiscoveryResult result = new JcmProjectLoader().discover(root.resolve("app.jcm"));
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("JCM_PATH_ESCAPE")));
    }

    @Test
    void missingAndAmbiguousAgentSourceAreReportedWithoutChoosing() throws Exception {
        Files.createDirectories(temporary.resolve("src/agt"));
        Files.writeString(temporary.resolve("app.jcm"), "mas app { agent a : absent.asl agent b : dup.asl }\n");
        Files.writeString(temporary.resolve("dup.asl"), "!go.\n");
        Files.writeString(temporary.resolve("src/agt/dup.asl"), "!go.\n");
        ProjectDiscoveryResult result = new JcmProjectLoader().discover(temporary.resolve("app.jcm"));
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("JCM_SOURCE_MISSING")));
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("JCM_SOURCE_AMBIGUOUS")));
        assertTrue(result.graph().edges().isEmpty());
    }

    @Test
    void explicitlyConfiguredExternalSourcePathIsAllowedButDirectTraversalIsNot() throws Exception {
        Path project = Files.createDirectory(temporary.resolve("project"));
        Path shared = Files.createDirectory(temporary.resolve("shared"));
        Files.writeString(shared.resolve("alice.asl"), "!go.\n");
        Files.writeString(project.resolve("ok.jcm"), "mas ok { agent alice : alice.asl asl-path: ../shared }\n");
        ProjectDiscoveryResult allowed = new JcmProjectLoader().discover(project.resolve("ok.jcm"));
        assertTrue(allowed.success(), () -> allowed.diagnostics().toString());
        Files.writeString(project.resolve("bad.jcm"), "mas bad { agent alice : ../shared/alice.asl }\n");
        ProjectDiscoveryResult rejected = new JcmProjectLoader().discover(project.resolve("bad.jcm"));
        assertFalse(rejected.success());
        assertTrue(rejected.diagnostics().stream().anyMatch(d -> d.code().equals("JCM_PATH_ESCAPE")));
    }

    @Test
    void resolvesArtifactClassFromExplicitClasspathWithoutLoadingIt() throws Exception {
        Path lib = Files.createDirectory(temporary.resolve("lib"));
        Path jar = lib.resolve("types.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("demo/Counter.class"));
            output.write(new byte[] { 0, 1, 2 });
            output.closeEntry();
        }
        Files.writeString(temporary.resolve("app.jcm"),
                "mas app { workspace w { artifact c: demo.Counter() } class-path: lib/types.jar }\n");
        ProjectDiscoveryResult result = new JcmProjectLoader().discover(temporary.resolve("app.jcm"));
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertTrue(result.graph().sources().stream().anyMatch(source ->
                source.kind() == SourceKind.JAR && source.path().equals(jar.toAbsolutePath().normalize())));
        assertEquals(1, result.graph().edges().size());
    }

    @Test
    void malformedSourcePathDirectiveIsDiagnosed() throws Exception {
        Files.writeString(temporary.resolve("app.jcm"), "mas app { asl-path: agent a }\n");
        ProjectDiscoveryResult result = new JcmProjectLoader().discover(temporary.resolve("app.jcm"));
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("JCM_PATH_INVALID")));
    }
}
