package org.tzi.use.plugins.jacamo.session;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ProducerProcessTest {
    @TempDir Path directory;

    @Test void compilesArtifactSourcesFromTheProjectLayout() throws Exception {
        Path project = Files.createFile(directory.resolve("application.jcm"));
        Path source = Files.createDirectories(directory.resolve("src/env/example"));
        Files.writeString(source.resolve("ExampleArtifact.java"), "package example; class ExampleArtifact {}");
        Path other = Files.createDirectory(directory.resolve("other"));
        Files.writeString(other.resolve("OtherSource.java"), "class OtherSource {}");
        Path work = directory.resolve("work");
        ProducerProcess.compileArtifacts(project, work, "", message -> {});
        assertTrue(Files.isRegularFile(work.resolve("artifact-classes/example/ExampleArtifact.class")));
        assertFalse(Files.exists(work.resolve("artifact-classes/OtherSource.class")));
    }

    @Test void skipsCompilationForProjectsWithoutArtifactSources() throws Exception {
        Path project = Files.createFile(directory.resolve("application.jcm"));
        Path work = directory.resolve("work");
        ProducerProcess.compileArtifacts(project, work, "", message -> {});
        try (var classes = Files.list(work.resolve("artifact-classes"))) {
            assertTrue(classes.findAny().isEmpty());
        }
    }
}
