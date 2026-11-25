package org.tzi.use.plugins.jacamo.session;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentBundleTest {
    @TempDir Path work;

    @Test void unpackUsesManifestAndRetainsProjectRelativeSources() throws IOException {
        Path bundle = archive(Map.of("src/env/demo/Example.java", "class Example {}"));
        var config = ExperimentBundle.unpack(bundle, work.resolve("inputs"));
        Files.delete(bundle);
        Path project = config.project().getParent();
        assertEquals(project.resolve("application.jcm"), config.project());
        assertEquals(project.resolve("mapping rules.xml"), config.mapping());
        assertEquals(project.resolve("models/context.csl"), config.csl());
        assertEquals(project.resolve("models/goal.istar"), config.istar());
        assertEquals("class Example {}", Files.readString(project.resolve("src/env/demo/Example.java")));
        config.validate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../escaped.txt", "src/../../escaped.txt", "src\\..\\escaped.txt",
            "/escaped.txt", "C:/escaped.txt", "src/NUL.txt"})
    void rejectsUnsafePathsAndRemovesPartialExtraction(String name) throws IOException {
        Path bundle = archive(Map.of(name, "unsafe"));
        Path destination = work.resolve("inputs");
        assertThrows(IOException.class, () -> ExperimentBundle.unpack(bundle, destination));
        assertFalse(Files.exists(destination.resolve("escaped.txt")));
        assertEmpty(destination);
    }

    @Test void rejectsMissingManifestInput() throws IOException {
        Path bundle = archive(Map.of("mrv.json", """
                {"version":1,"project":"missing.jcm","mapping":"mapping rules.xml",
                 "csl":"models/context.csl","istar":"models/goal.istar"}
                """));
        Path destination = work.resolve("inputs");
        var failure = assertThrows(IOException.class, () -> ExperimentBundle.unpack(bundle, destination));
        assertTrue(failure.getMessage().contains("missing.jcm"));
        assertEmpty(destination);
    }

    @Test void rejectsAnUnsupportedVersion() throws IOException {
        Path bundle = archive(Map.of("mrv.json", "{\"version\":2}"));
        assertThrows(IOException.class, () -> ExperimentBundle.unpack(bundle, work.resolve("inputs")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"auction", "counter"})
    void theExampleBundleMatchesTheCaseFiles(String name) throws IOException {
        Path source = Path.of("src/main/resources/example").resolve(name).toAbsolutePath();
        var config = ExperimentBundle.unpack(source.resolve(name + ".mrv"), work);
        Path extracted = config.project().getParent();
        assertEquals(name + ".jcm", config.project().getFileName().toString());
        assertEquals(name + ".csl", config.csl().getFileName().toString());
        assertEquals(name + ".istar", config.istar().getFileName().toString());
        assertEquals("attribute-mapping.xml", config.mapping().getFileName().toString());
        try (var paths = Files.walk(source)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(p -> !ExperimentBundle.hasBundleExtension(p)).toList()) {
                Path relative = source.relativize(file);
                assertEquals(-1, Files.mismatch(file, extracted.resolve(relative)), relative.toString());
            }
        }
    }

    private Path archive(Map<String, String> additionalEntries) throws IOException {
        var entries = new LinkedHashMap<String, String>();
        entries.put("mrv.json", new String(CanonicalJson.encode(Map.of("version", 1, "project", "application.jcm",
                "mapping", "mapping rules.xml", "csl", "models/context.csl", "istar", "models/goal.istar")),
                StandardCharsets.UTF_8));
        entries.put("application.jcm", "");
        entries.put("mapping rules.xml", "");
        entries.put("models/context.csl", "");
        entries.put("models/goal.istar", "");
        entries.putAll(additionalEntries);
        Path file = work.resolve("portable.MRV");
        try (var output = new ZipOutputStream(Files.newOutputStream(file))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return file;
    }

    private static void assertEmpty(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            assertTrue(files.findAny().isEmpty());
        }
    }
}
