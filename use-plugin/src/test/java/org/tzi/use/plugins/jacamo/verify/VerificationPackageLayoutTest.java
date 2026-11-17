package org.tzi.use.plugins.jacamo.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies the core/backend boundary and absence of stale deployable class names. */
class VerificationPackageLayoutTest {
    private static final Path JAVA = Path.of("src/main/java");
    private static final Path CORE = JAVA.resolve("org/vnu/sme/goal/verify");

    @Test void languageCoreHasTwoOwnersAndNoRuntimeBackendDependencies() throws Exception {
        try (var entries = Files.list(CORE)) {
            assertEquals(Set.of("istar", "csl"), entries.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString()).collect(Collectors.toSet()));
        }
        try (var files = Files.walk(CORE)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                for (String forbidden : Set.of("import org.tzi.", "import jason.", "import cartago.",
                        "import jacamo.", "import moise.", "import javax.swing."))
                    assertFalse(source.contains(forbidden), path + ": " + forbidden);
            }
        }
        assertTrue(Files.isRegularFile(JAVA.resolve("org/vnu/sme/goal/dsl/istar/mm/GoalType.java")));
        assertTrue(Files.isRegularFile(JAVA.resolve("org/tzi/use/plugins/jacamo/verify/use75/NativeOclBackend.java")));
    }

    @Test void movedAndExtractedClassesLinkWithoutTheirPreviousBinaryNames() throws Exception {
        var properties = new Properties();
        try (var input = getClass().getResourceAsStream("/META-INF/verify-class-relocations.properties")) {
            assertNotNull(input);
            properties.load(input);
        }
        assertFalse(properties.isEmpty());
        var loader = getClass().getClassLoader();
        for (var entry : properties.entrySet()) {
            assertNotNull(Class.forName(entry.getValue().toString(), false, loader));
            assertNull(loader.getResource(entry.getKey().toString().replace('.', '/') + ".class"),
                    "Stale verification class: " + entry.getKey());
        }
        for (String name : Set.of("istar.temporal.GoalTemporalEvaluator", "istar.propagation.IStarDependencyPropagation"))
            assertNotNull(Class.forName("org.vnu.sme.goal.verify." + name, false, loader));
    }
}
