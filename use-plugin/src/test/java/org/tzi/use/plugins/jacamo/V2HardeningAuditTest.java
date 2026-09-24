package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class V2HardeningAuditTest {
    @Test void activeProductionPathHasNoUnresolvedMarkersCaseDispatchOrShellExecution() throws Exception {
        Path root = Path.of("src/main/java/org/tzi/use/plugins/jacamo");
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                reject(violations, file, source.matches("(?s).*\\b(?:TODO|FIXME)\\b.*"),
                        "unresolved correctness marker");
                reject(violations, file, source.contains("ProcessBuilder("), "arbitrary process creation");
                reject(violations, file, source.contains("Runtime.getRuntime().exec"), "arbitrary shell execution");
                reject(violations, file, source.contains("ORDER_V1"), "obsolete projection id");
                reject(violations, file, source.matches("(?s).*\\b(?:Auction|CounterTeam)\\b.*"),
                        "case-specific core dispatch");
                boolean console = source.contains("System.out") || source.contains("System.err")
                        || source.contains("printStackTrace(");
                if (console && !file.getFileName().toString().equals("JaCaMoStatusCommand.java"))
                    violations.add(file + ": unnecessary production console logging");
            }
        }
        assertEquals(List.of(), violations);
    }

    @Test void canonicalV2ResourcesContainNoHistoricalTargetVocabulary() throws Exception {
        List<Path> active = List.of(
                Path.of("Core/Metamodel/version-2/jacamo_v2_complete.ecore"),
                Path.of("Core/Mapping/version-2/jacamo-use-mapping-v2.json"),
                Path.of("Core/Mapping/version-2/jacamo-use-mapping-v2.schema.json"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/runtime/jacamo-use-runtime-mapping-v2.json"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/runtime/runtime-mapping-v2.schema.json"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core-v2.ocl"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/verification/jacamo-verification-profile-v2.json"));
        for (Path file : active) {
            String text = Files.readString(file);
            assertFalse(text.contains("ORDER_V1"), file.toString());
            assertFalse(text.contains("dynamic V1 state slot"), file.toString());
            assertFalse(text.contains("jacamo-use-mapping-v1"), file.toString());
        }
        assertTrue(Files.readString(Path.of("src/main/java/org/tzi/use/plugins/jacamo/mapping/ActiveBaseline.java"))
                .contains("public static final String VERSION = \"V2\""));
        assertTrue(Files.readString(Path.of("src/main/java/org/tzi/use/plugins/jacamo/runtime/RuntimeMappingLoader.java"))
                .contains("jacamo-use-runtime-mapping-v2.json"));
    }

    @Test void activeDocumentationDoesNotPresentV1AsTheCurrentBaseline() throws Exception {
        List<Path> active = List.of(
                Path.of("README.md"),
                Path.of("docs/project/00-README.md"),
                Path.of("docs/project/01-vision-scope.md"),
                Path.of("docs/project/02-system-architecture.md"),
                Path.of("docs/project/04-jacamo-metamodel-baseline.md"),
                Path.of("docs/project/05-metamodel-mapping-contract.md"),
                Path.of("docs/project/08-use-transformation.md"),
                Path.of("docs/project/10-runtime-adapter.md"),
                Path.of("docs/project/13-testing-quality.md"),
                Path.of("docs/project/14-auction-case-study.md"),
                Path.of("docs/project/15-build-release-operations.md"),
                Path.of("docs/project/17-end-to-end-acceptance.md"),
                Path.of("docs/project/19-roadmap.md"));
        List<String> staleClaims = List.of(
                "The final structural target is unchanged V1",
                "Final metamodel remains unchanged canonical V1",
                "4. Load Mapping V1",
                "frozen Mapping V1, schema");
        for (Path file : active) {
            String text = Files.readString(file);
            for (String stale : staleClaims) assertFalse(text.contains(stale), file + ": " + stale);
        }
    }

    private void reject(List<String> violations, Path file, boolean condition, String message) {
        if (condition) violations.add(file + ": " + message);
    }
}
