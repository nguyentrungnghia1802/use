package org.tzi.use.plugins.jacamo.verify;

import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;
import org.tzi.use.plugins.jacamo.verify.oracle.SourcePackageRelocation;
import org.vnu.sme.goal.dsl.csl.parser.CslCompiler;
import org.vnu.sme.goal.dsl.istar.mm.GoalType;
import org.vnu.sme.goal.verify.istar.IStarMarking.GoalMarking;
import org.vnu.sme.goal.verify.istar.IStarMarking.GoalTaskStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class EngineExtractionTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test void selectedSourcesPreservePinnedBytesExceptRecordedPackagesAndPatches() throws Exception {
        String manifestText = Files.readString(ROOT.resolve("src/main/resources/META-INF/use4goal-provenance.json"));
        var manifest = CanonicalJson.object(CanonicalJson.decode(manifestText.getBytes(StandardCharsets.UTF_8)));
        var patched = new HashMap<String, Map<String, Object>>();
        for (Object raw : (List<?>) manifest.get("files")) {
            var file = CanonicalJson.object(raw);
            if (((List<?>) file.get("appliedPatchIds")).contains("P-ISTAR-GOAL-TASK-01"))
                patched.put((String) file.get("upstreamPath"), file);
        }
        assertEquals(Set.of("ContributionPolarity.java", "QualityStatus.java", "GoalMarking.java", "TaskMarking.java",
                        "GoalTaskStatus.java", "IStarMarking.java", "IStarPropagation.java",
                        "GoalModelInstanceTranslator.java", "ScenarioStmt.java"),
                patched.keySet().stream().map(path -> Path.of(path).getFileName().toString())
                        .collect(java.util.stream.Collectors.toSet()));
        int javaFiles = 0, grammars = 0;
        try (var archive = new ZipFile(ROOT.resolve("src/test/resources/oracle/pinned-original-core.zip").toFile())) {
            for (var entry : archive.stream().toList()) {
                if (entry.isDirectory()) continue;
                String upstream = entry.getName();
                byte[] expected = archive.getInputStream(entry).readAllBytes();
                var patch = patched.remove(upstream);
                if (patch != null) {
                    javaFiles++;
                    assertEquals(patch.get("sourceSha256"), sha256(expected), upstream);
                    if ("REMOVED_BY_PATCH".equals(patch.get("extractionStatus"))) {
                        assertEquals(Boolean.FALSE, patch.get("productionEvaluationAllowed"), upstream);
                        assertNull(patch.get("targetPath"), upstream);
                    } else {
                        Path target = ROOT.resolve((String) patch.get("targetPath"));
                        assertApprovedPatchBytes(patch, Files.readAllBytes(target));
                    }
                    continue;
                }
                Path target;
                if (upstream.startsWith("goal/src/main/java/")) {
                    target = SourcePackageRelocation.sourcePath(ROOT, upstream);
                    javaFiles++;
                } else if (upstream.endsWith(".g4")) {
                    target = ROOT.resolve("src/main/resources/grammars/" + Path.of(upstream).getFileName());
                    grammars++;
                } else {
                    assertEquals("LICENSE", upstream);
                    target = ROOT.resolve("src/main/resources/META-INF/licenses/use4Goal-GPL-3.0.txt");
                }
                byte[] targetBytes = Files.readAllBytes(target);
                byte[] actual = upstream.endsWith(".java")
                        ? SourcePackageRelocation.originalSource(upstream, targetBytes) : targetBytes;
                if (upstream.endsWith("/GoalModelValidator.java") || upstream.endsWith("/IStarCompiler.java")) {
                    verifyAdmissionPatchScope(target.getFileName().toString(),expected,actual);
                    assertTrue(manifestText.contains(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(targetBytes))),"Unrecorded P-IADM-01 target hash");
                } else if(List.of("Actor.java","IStarModelFactory.java","IStar.g4").contains(target.getFileName().toString())) {
                    assertEquals(new String(expected,StandardCharsets.UTF_8).replace("\r\n","\n"),reverseGenericActorPatch(target.getFileName().toString(),new String(actual,StandardCharsets.UTF_8).replace("\r\n","\n")),"Unregistered change outside P-ACTOR-01: "+upstream);
                    assertTrue(manifestText.contains(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(targetBytes))),"Unrecorded P-ACTOR-01 target hash");
                } else assertArrayEquals(expected,actual,upstream);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected));
                assertTrue(manifestText.contains(hash), "Unrecorded source hash: " + upstream);
            }
        }
        assertTrue(patched.isEmpty(), "Recorded patch sources missing from pinned archive: " + patched.keySet());
        assertEquals(123, javaFiles);
        assertEquals(2, grammars);
    }
    static void verifyAdmissionPatchScope(String name,byte[] original,byte[] patched) {
        String expected=new String(original,StandardCharsets.UTF_8).replace("\r\n","\n");
        String actual=new String(patched,StandardCharsets.UTF_8).replace("\r\n","\n");
        if(name.equals("GoalModelValidator.java")) {
            // Reverse only the registered P-IADM-02 diff, then apply the original
            // P-IADM-01 guard. Every unrelated validator algorithm stays pinned.
            int extension=actual.indexOf("    // P-IADM-02-BEGIN:");
            if(extension>=0) {
                int finish=actual.indexOf("    // P-IADM-02-END",extension)+"    // P-IADM-02-END\n\n".length();
                assertTrue(finish>extension,"Missing P-IADM-02 terminator");
                actual=actual.substring(0,extension)+actual.substring(finish);
                actual=actual.replace("validateRefinements(model, errors, policy)","validateRefinements(model, errors)")
                        .replace("validateDerivedAndDependees(model, errors, policy)","validateDerivedAndDependees(model, errors)")
                        .replace("private static void validateDerivedAndDependees(GoalModel model, List<String> errors, GoalModelValidationPolicy policy)","private static void validateDerivedAndDependees(GoalModel model, List<String> errors)")
                        .replace("and.children().size() < 2 && !unaryRealization(model,actor,and,policy)","and.children().size() < 2")
                        .replace("private static void validateRefinements(GoalModel model, List<String> errors, GoalModelValidationPolicy policy)","private static void validateRefinements(GoalModel model, List<String> errors)")
                        .replace("and.children().size() < 2 && !unaryRealization(model, actor, and, policy)","and.children().size() < 2");
            }
        }
        String marker=name.equals("GoalModelValidator.java")
                ? "        // P-IADM-01: strict public default is preserved."
                : "        // P-IADM-01: existing entry points retain strict upstream validation.";
        int start=actual.indexOf(marker),end=actual.indexOf("        List<String> errors = new ArrayList<>();",start);
        assertTrue(start>=0&&end>start,"Missing explicit P-IADM-01 dispatch");
        actual=actual.substring(0,start)+actual.substring(end);
        if(name.equals("GoalModelValidator.java")) {
            actual=actual.replace("        if (policy == GoalModelValidationPolicy.STRICT_ORIGINAL)\n            validateRefinementParentsAreNotDependencyEndpoints(model, errors);\n        else validateDerivedAndDependees(model, errors);",
                    "        validateRefinementParentsAreNotDependencyEndpoints(model, errors);");
            start=actual.indexOf("    // P-IADM-01-BEGIN:");end=actual.indexOf("    // P-IADM-01-END",start)+"    // P-IADM-01-END\n\n".length();
            assertTrue(start>=0&&end>start,"Missing neutral P-IADM-01 admission block");
            actual=actual.substring(0,start)+actual.substring(end);
        } else {
            actual=actual.replace("import org.vnu.sme.goal.dsl.istar.mm.GoalModelValidationPolicy;\n","")
                    .replace("GoalModelValidator.validate(model, policy)","GoalModelValidator.validate(model)");
        }
        assertEquals(expected,actual,"Original code changed outside registered admission scope");
    }
    @Test void provenanceRejectsAnUnapprovedOriginalValidationChange() throws Exception {
        String suffix="/GoalModelValidator.java";
        try(var archive=new ZipFile(ROOT.resolve("src/test/resources/oracle/pinned-original-core.zip").toFile())) {
            var entry=archive.getEntry("goal/src/main/java/org/vnu/sme/goal/dsl/istar/mm"+suffix);
            byte[] changed=Files.readString(ROOT.resolve("src/main/java/org/vnu/sme/goal/dsl/istar/mm"+suffix))
                    .replace("        validateContributions(model, errors);","        // removed validation").getBytes(StandardCharsets.UTF_8);
            assertThrows(AssertionError.class,()->verifyAdmissionPatchScope("GoalModelValidator.java",archive.getInputStream(entry).readAllBytes(),changed));
        }
    }
    // Invert only the exact registered type/syntax additions. Unrelated upstream bytes stay pinned.
    static String reverseGenericActorPatch(String name,String source) {
        return switch(name) {
            case "Actor.java" -> source.replace("/** Actor supertype; GenericActor is the project's explicit unspecified-specialization extension. */","/** iStar 2.0 Actor is abstract — only {@link Agent} and {@link Role} are concrete. */")
                    .replace("permits Agent, Role, GenericActor","permits Agent, Role");
            case "IStar.g4" -> source.replace("//  Actor is abstract; project extension P-ACTOR-01 adds a concrete GenericActor\n//  spelled 'actor', alongside Role and Agent.","//  Actor is abstract — only Role and Agent are concrete, both generalizing\n//  to Actor.")
                    .replace("actorKind : 'role' | 'agent' | 'actor' ;","actorKind : 'role' | 'agent' ;");
            case "IStarModelFactory.java" -> source.replace("            case \"actor\" -> new GenericActor(cs.id(), elements, refinements, contributions, qualifications,\n                    neededBys, assocs);\n","");
            default -> throw new IllegalArgumentException(name);
        };
    }
    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void assertApprovedPatchBytes(Map<String, Object> patch, byte[] target) throws Exception {
        assertEquals(patch.get("targetSha256"), sha256(target),
                "Unrecorded change to " + patch.get("targetPath"));
    }

    @Test void provenanceRejectsAnUnapprovedPrivateAlgorithmChange() throws Exception {
        var manifest = CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(
                ROOT.resolve("src/main/resources/META-INF/use4goal-provenance.json"))));
        var patch = ((List<?>) manifest.get("files")).stream().map(CanonicalJson::object)
                .filter(file -> ((String) file.get("upstreamPath")).endsWith("/IStarMarking.java"))
                .findFirst().orElseThrow();
        byte[] changed = Files.readString(ROOT.resolve((String) patch.get("targetPath")))
                .replace("MAX_DEPENDENCY_STEPS = 10_000", "MAX_DEPENDENCY_STEPS = 1")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(AssertionError.class, () -> assertApprovedPatchBytes(patch, changed));
    }

    @Test void semanticSourcesRemainIndependentOfTheImportedRuntimeAndGui() throws Exception {
        for (Path directory : List.of(ROOT.resolve("src/main/java/org/vnu/sme/goal"),
                ROOT.resolve("src/main/java/org/tzi/use/plugins/jacamo/verify"))) {
            try (var files = Files.walk(directory)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    boolean presentation=file.toString().replace('\\', '/').matches(".*/dsl/[^/]+/(view|form)/.*");
                    for (String forbidden : List.of("import jason.", "import cartago.", "import moise.", "import jacamo.",
                            "import org.tzi.use.plugins.jacamo.bridge.",
                            "import org.tzi.use.plugins.jacamo.integration.",
                            "import org.tzi.use.plugins.jacamo.runtime.",
                            "import org.tzi.use.plugins.jacamo.session.",
                            "import org.tzi.use.plugins.jacamo.tool."))
                        assertFalse(source.contains(forbidden), file + ": " + forbidden);
                    if (!presentation) {
                        assertFalse(source.contains("import org.tzi.use.gui."), file + ": semantic code imports USE GUI");
                        assertFalse(source.contains("import org.tzi.use.plugins.jacamo.gui."),
                                file + ": semantic code imports plugin GUI");
                    }
                }
            }
        }
        for (String framework : List.of("org/jacamo", "jacamo", "jason", "cartago", "moise", "org/tzi/use/uml"))
            assertFalse(Files.exists(ROOT.resolve("src/main/java/" + framework)), "Imported framework must not be reimplemented: " + framework);
        assertTrue(Files.isRegularFile(ROOT.resolve("src/main/java/org/vnu/sme/goal/dsl/csl/parser/CslCompiler.java")));
        assertTrue(Files.isRegularFile(ROOT.resolve("src/main/java/org/vnu/sme/goal/dsl/istar/parser/IStarCompiler.java")));
    }

    @Test void generatedFrontendAndOriginalRecurrenceLoadWithoutRuntimeStartup() {
        var result = CslCompiler.compile("csl v4.0 Smoke { entity Counter { p : Integer; } }");
        assertTrue(result.ok(), result.errors().toString());
        assertEquals("Counter", result.model().entities().getFirst().name());
        assertEquals(GoalTaskStatus.PENDING, GoalMarking.initial(GoalType.NONE).update(true, false).status());
    }
}
