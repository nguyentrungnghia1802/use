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
                reject(violations, file, unscopedCaseSelector(file,source),
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
        List<Path> frozen = List.of(
                Path.of("Core/Metamodel/version-2/jacamo_v2_complete.ecore"),
                Path.of("Core/Mapping/version-2/jacamo-use-mapping-v2.json"),
                Path.of("Core/Mapping/version-2/jacamo-use-mapping-v2.schema.json"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/runtime/jacamo-use-runtime-mapping-v2.json"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/runtime/runtime-mapping-v2.schema.json"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core-v2.ocl"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/verification/jacamo-verification-profile-v2.json"));
        for (Path file : frozen) {
            String text = Files.readString(file);
            assertFalse(text.contains("ORDER_V1"), file.toString());
            assertFalse(text.contains("dynamic V1 state slot"), file.toString());
            assertFalse(text.contains("jacamo-use-mapping-v1"), file.toString());
        }
        assertTrue(Files.readString(Path.of("src/main/java/org/tzi/use/plugins/jacamo/mapping/ActiveBaseline.java"))
                .contains("public static final String VERSION = \"V2\""));
        assertFalse(Files.exists(Path.of("src/main/java/org/tzi/use/plugins/jacamo/runtime/RuntimeMappingLoader.java")),
                "The frozen runtime mapper has no production execution path");
        assertTrue(Files.readString(Path.of("src/test/java/org/tzi/use/plugins/jacamo/runtime/RuntimeMappingLoader.java"))
                .contains("jacamo-use-runtime-mapping-v2.json"));
    }

    @Test void activeDocumentationDoesNotPresentV1AsTheCurrentBaseline() throws Exception {
        List<Path> active = List.of(
                Path.of("README.md"),
                Path.of("docs/agent/agent.md"),
                Path.of("docs/agent/task.md"),
                Path.of("docs/project/01-vision-scope.md"),
                Path.of("docs/project/02-system-architecture.md"),
                Path.of("docs/project/12-plugin-ui-workflow.md"),
                Path.of("docs/project/15-build-release-operations.md"),
                Path.of("docs/project/16-research-evidence-boundaries.md"),
                Path.of("docs/project/runtime-event-identity.md"));
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

    // This gate predates explicit XML case adapters. A case label in a comment
    // or UI chooser is not model-name dispatch. Shared code still cannot select
    // domain behavior by the literal model/class names; only the admitted case
    // package may contain those domain selectors. Engine purity has separate
    // module/layer and GenericAgentProjectionTest gates.
    private static boolean unscopedCaseSelector(Path file,String source) {
        if(file.normalize().startsWith(Path.of("src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/auction")))return false;
        return java.util.regex.Pattern.compile("\"(?:Auction|CounterTeam)\"").matcher(source).find();
    }

    @Test void caseSelectorAuditDistinguishesDispatchFromExplicitAdapterCommentsAndUiLabels() {
        Path shared=Path.of("src/main/java/org/tzi/use/plugins/jacamo/codegrounded/Shared.java");
        assertTrue(unscopedCaseSelector(shared,"if (model.equals(\"Auction\")) applyRules();"));
        assertTrue(unscopedCaseSelector(shared,"switch (name) {case \"CounterTeam\": break;}"));
        assertFalse(unscopedCaseSelector(shared,"// Exact Auction step binding is in the case adapter."));
        assertFalse(unscopedCaseSelector(shared,"chooser.setDialogTitle(\"Select Auction mapping specification\");"));
        Path adapter=Path.of("src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/auction/Adapter.java");
        assertFalse(unscopedCaseSelector(adapter,"object.cls().name().equals(\"Auction\")"));
        Path lookalike=Path.of("src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/auction-other/Adapter.java");
        assertTrue(unscopedCaseSelector(lookalike,"object.cls().name().equals(\"Auction\")"));
    }
}
