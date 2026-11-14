package org.tzi.use.plugins.jacamo.verify;

import org.junit.jupiter.api.Test;
import org.vnu.sme.goal.dsl.csl.parser.CslCompiler;
import org.vnu.sme.goal.dsl.istar.mm.GoalType;
import org.vnu.sme.goal.verify.conformance.semantics.GoalMarking;
import org.vnu.sme.goal.verify.conformance.semantics.GoalTaskStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

class EngineExtractionTest {
    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test void selectedSourcesAreByteIdenticalToPinnedOriginalArchive() throws Exception {
        String manifest = Files.readString(ROOT.resolve("src/main/resources/META-INF/use4goal-provenance.json"));
        int javaFiles = 0, grammars = 0;
        try (var archive = new ZipFile(ROOT.resolve("src/test/resources/oracle/pinned-original-core.zip").toFile())) {
            for (var entry : archive.stream().toList()) {
                if (entry.isDirectory()) continue;
                String upstream = entry.getName();
                Path target;
                if (upstream.startsWith("goal/src/main/java/")) {
                    target = ROOT.resolve("src/main/java/" + upstream.substring("goal/src/main/java/".length()));
                    javaFiles++;
                } else if (upstream.endsWith(".g4")) {
                    target = ROOT.resolve("src/main/resources/grammars/" + Path.of(upstream).getFileName());
                    grammars++;
                } else {
                    assertEquals("LICENSE", upstream);
                    target = ROOT.resolve("src/main/resources/META-INF/licenses/use4Goal-GPL-3.0.txt");
                }
                byte[] expected = archive.getInputStream(entry).readAllBytes();
                byte[] actual=Files.readAllBytes(target);
                if(upstream.endsWith("/GoalModelInstanceTranslator.java")) {
                    verifyContextPatchScope(expected,actual);
                    String targetHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual));
                    assertTrue(manifest.contains(targetHash),"Unrecorded P-CTX-01 target hash");
                } else if(upstream.endsWith("/GoalModelValidator.java")||upstream.endsWith("/IStarCompiler.java")) {
                    verifyAdmissionPatchScope(target.getFileName().toString(),expected,actual);
                    assertTrue(manifest.contains(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual))),"Unrecorded P-IADM-01 target hash");
                } else if(List.of("Actor.java","IStarModelFactory.java","IStar.g4").contains(target.getFileName().toString())) {
                    assertEquals(new String(expected,StandardCharsets.UTF_8).replace("\r\n","\n"),reverseGenericActorPatch(target.getFileName().toString(),new String(actual,StandardCharsets.UTF_8).replace("\r\n","\n")),"Unregistered change outside P-ACTOR-01: "+upstream);
                    assertTrue(manifest.contains(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual))),"Unrecorded P-ACTOR-01 target hash");
                } else assertArrayEquals(expected,actual,upstream);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(expected));
                assertTrue(manifest.contains(hash), "Unrecorded source hash: " + upstream);
            }
        }
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
    static void verifyContextPatchScope(byte[] original,byte[] patched) {
        String expected=new String(original,StandardCharsets.UTF_8).replace("\r\n","\n");
        String actual=reverseGenericActorPatch("GoalModelInstanceTranslator.java",new String(patched,StandardCharsets.UTF_8).replace("\r\n","\n"));
        int start=actual.indexOf("    /** P-CTX-01: structured, in-memory topology seam.");
        int end=actual.indexOf("    public static GoalModelInstanceEvaluation evaluate(",start);
        assertTrue(start>=0&&end>start,"Missing registered P-CTX-01 seam");
        assertEquals(expected,actual.substring(0,start)+actual.substring(end),"Original algorithms changed outside P-CTX-01");
    }
    // Invert only the exact registered type/syntax additions. Unrelated upstream bytes stay pinned.
    static String reverseGenericActorPatch(String name,String source) {
        return switch(name) {
            case "Actor.java" -> source.replace("/** Actor supertype; GenericActor is the project's explicit unspecified-specialization extension. */","/** iStar 2.0 Actor is abstract — only {@link Agent} and {@link Role} are concrete. */")
                    .replace("permits Agent, Role, GenericActor","permits Agent, Role");
            case "IStar.g4" -> source.replace("//  Actor is abstract; project extension P-ACTOR-01 adds a concrete GenericActor\n//  spelled 'actor', alongside Role and Agent.","//  Actor is abstract — only Role and Agent are concrete, both generalizing\n//  to Actor.")
                    .replace("actorKind : 'role' | 'agent' | 'actor' ;","actorKind : 'role' | 'agent' ;");
            case "IStarModelFactory.java" -> source.replace("            case \"actor\" -> new GenericActor(cs.id(), elements, refinements, contributions, qualifications,\n                    neededBys, assocs);\n","");
            case "GoalModelInstanceTranslator.java" -> source.replace("import org.vnu.sme.goal.dsl.istar.mm.GenericActor;\n","")
                    .replace("                Actor actor = sourceActor instanceof GenericActor\n                        ? new GenericActor(actorId, new ArrayList<>(bucket.elements.values()), bucket.refinements,\n                                bucket.contributions, List.of(), List.of(), List.of())\n                        : sourceActor instanceof Role","                Actor actor = sourceActor instanceof Role");
            default -> throw new IllegalArgumentException(name);
        };
    }
    @Test void provenanceRejectsAnUnapprovedPrivateAlgorithmChange() throws Exception {
        Path target=ROOT.resolve("src/main/java/org/vnu/sme/goal/dsl/iscn/goalmodelinstance/parser/GoalModelInstanceTranslator.java");
        try(var archive=new ZipFile(ROOT.resolve("src/test/resources/oracle/pinned-original-core.zip").toFile())) {
            var entry=archive.getEntry("goal/src/main/java/org/vnu/sme/goal/dsl/iscn/goalmodelinstance/parser/GoalModelInstanceTranslator.java");
            byte[] original=archive.getInputStream(entry).readAllBytes();
            byte[] changed=Files.readString(target).replace("MAX_DEPENDENCY_STEPS = 10_000","MAX_DEPENDENCY_STEPS = 1").getBytes(StandardCharsets.UTF_8);
            assertThrows(AssertionError.class,()->verifyContextPatchScope(original,changed));
        }
    }

    @Test void semanticSourcesRemainIndependentOfTheImportedRuntimeAndGui() throws Exception {
        for (Path directory : List.of(ROOT.resolve("src/main/java/org/vnu/sme/goal"),
                ROOT.resolve("src/main/java/org/tzi/use/plugins/jacamo/verify"))) {
            try (var files = Files.walk(directory)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    for (String forbidden : List.of("import jason.", "import cartago.", "import moise.",
                            "import org.tzi.use.gui.", "import org.tzi.use.plugins.jacamo.bridge.",
                            "import org.tzi.use.plugins.jacamo.feature."))
                        assertFalse(source.contains(forbidden), file + ": " + forbidden);
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
