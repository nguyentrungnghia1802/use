package org.tzi.use.plugins.jacamo.verify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.verify.oracle.SourcePackageRelocation;
import static org.junit.jupiter.api.Assertions.*;

/** Package ownership and deployable linkage after consolidating the DSL frontends. */
class DslPackageLayoutTest {
    private static final Path JAVA = Path.of("src/main/java");
    private static final Path DSL = JAVA.resolve("org/vnu/sme/goal/dsl");

    private static Set<String> directories(Path parent) throws Exception {
        try (var entries = Files.list(parent)) {
            return entries.filter(Files::isDirectory).map(p -> p.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    @Test void languageOwnershipHasExactlyTheRequestedPackages() throws Exception {
        assertEquals(Set.of("istar", "csl", "istar_marking", "csl_marking"), directories(DSL));
        for (String language : Set.of("istar", "csl"))
            assertEquals(Set.of("parser", "mm", "ast", "view", "form"), directories(DSL.resolve(language)));
        assertFalse(Files.exists(JAVA.resolve("org/tzi/use/plugins/jacamo/dsl")));
        try (var entries = Files.list(DSL.resolve("istar_marking"))) {
            assertFalse(entries.anyMatch(p -> p.toString().endsWith(".java")),
                    "Marking classes must belong to a responsibility package");
        }
    }

    @Test void sourcePathsAndDeclarationsAgreeWithoutLegacyImports() throws Exception {
        var declaration = Pattern.compile("(?m)^package ([\\w.]+);");
        try (var files = Files.walk(JAVA)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                var match = declaration.matcher(source);
                assertTrue(match.find(), file.toString());
                assertEquals(JAVA.relativize(file.getParent()).toString().replace('\\', '.').replace('/', '.'), match.group(1), file.toString());
                for (String legacy : Set.of("org.tzi.use.plugins.jacamo.dsl.", "org.vnu.sme.goal.dsl.iscn.",
                        "org.vnu.sme.goal.verify.conformance.semantics.", "org.vnu.sme.goal.trace.cslTrace.nativecsl."))
                    assertFalse(source.contains(legacy), file + ": " + legacy);
            }
        }
    }

    @Test void allMovedClassesLinkAndOldBinaryNamesAreAbsent() throws Exception {
        var loader = getClass().getClassLoader();
        for (var relocation : SourcePackageRelocation.classes().entrySet()) {
            assertNotNull(Class.forName(relocation.getValue(), false, loader));
            assertNull(loader.getResource(relocation.getKey().replace('.', '/') + ".class"),
                    "Stale class from the previous layout: " + relocation.getKey());
            if (relocation.getValue().startsWith("org.vnu.sme.goal.dsl.istar_marking.")) {
                String simpleName = relocation.getValue().substring(relocation.getValue().lastIndexOf('.') + 1);
                assertNull(loader.getResource("org/vnu/sme/goal/dsl/istar_marking/" + simpleName + ".class"),
                        "Stale marking class at the package root: " + simpleName);
            }
        }
    }
}
