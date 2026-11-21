package org.tzi.use.plugins.jacamo.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.session.VerificationSession.Configuration;

import static org.junit.jupiter.api.Assertions.*;

class ProducerProcessTest {
    @TempDir Path directory;

    @Test void compilesOnlyTheSelectedProjectRelativeSourceDirectory() throws Exception {
        Path project = Files.createFile(directory.resolve("application.jcm"));
        Path mapping = Files.createFile(directory.resolve("mapping.xml"));
        Path csl = Files.createFile(directory.resolve("model.csl"));
        Path istar = Files.createFile(directory.resolve("model.istar"));
        var defaults = new Configuration(project, mapping, csl, istar);
        defaults.validate();
        assertEquals(directory.resolve("src/env"), defaults.artifactSourceDirectory());

        Path defaultSource = Files.createDirectories(defaults.artifactSourceDirectory());
        Files.writeString(defaultSource.resolve("DefaultSource.java"), "class DefaultSource {}");
        Path customSource = Files.createDirectories(directory.resolve("custom sources"));
        Files.writeString(customSource.resolve("CustomSource.java"), "class CustomSource {}");

        var custom = new Configuration(project, mapping, csl, istar, Path.of("custom sources"));
        custom.validate();
        assertEquals(customSource, custom.artifactSourceDirectory());
        Path work = directory.resolve("work");
        ProducerProcess.compileArtifacts(custom.artifactSourceDirectory(), work, "", message -> {});
        assertTrue(Files.isRegularFile(work.resolve("artifact-classes/CustomSource.class")));
        assertFalse(Files.exists(work.resolve("artifact-classes/DefaultSource.class")));

        var missing = new Configuration(project, mapping, csl, istar, Path.of("missing"));
        assertThrows(IOException.class, missing::validate);
    }
}
