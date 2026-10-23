package org.tzi.use.semantic.engine;

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
        String actual=new String(patched,StandardCharsets.UTF_8).replace("\r\n","\n");
        int start=actual.indexOf("    /** P-CTX-01: structured, in-memory topology seam.");
        int end=actual.indexOf("    public static GoalModelInstanceEvaluation evaluate(",start);
        assertTrue(start>=0&&end>start,"Missing registered P-CTX-01 seam");
        assertEquals(expected,actual.substring(0,start)+actual.substring(end),"Original algorithms changed outside P-CTX-01");
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

    @Test void moduleBoundaryExcludesHostCopiesFrameworksAndUpstreamPluginDescriptor() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var pom = factory.newDocumentBuilder().parse(ROOT.resolve("pom.xml").toFile());
        var dependencies = pom.getElementsByTagName("dependency");
        for (int i = 0; i < dependencies.getLength(); i++) {
            var dependency = (org.w3c.dom.Element) dependencies.item(i);
            String artifact = dependency.getElementsByTagName("artifactId").item(0).getTextContent();
            assertFalse(List.of("use-plugin", "use-gui", "jacamo-bridge-contract", "jacamo-bridge-jacamo",
                    "jason-interpreter", "cartago", "moise", "npl", "kodkod", "sat4j").contains(artifact));
            if (artifact.equals("use-core")) {
                assertEquals("provided", dependency.getElementsByTagName("scope").item(0).getTextContent());
            }
        }
        assertFalse(Files.exists(ROOT.resolve("src/main/resources/useplugin.xml")));
        try (var files = Files.walk(ROOT.resolve("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                for (String forbidden : List.of("import jason.", "import cartago.", "import moise.",
                        "import org.tzi.use.gui.", "import org.tzi.use.plugins.", "import org.jacamo.bridge.")) {
                    assertFalse(source.contains(forbidden), file + ": " + forbidden);
                }
            }
        }
        assertFalse(Files.exists(ROOT.resolve("src/main/java/org/tzi/use/uml")));
        assertFalse(Files.exists(ROOT.resolve("src/main/java/org/tzi/use/parser")));
    }

    @Test void generatedFrontendAndOriginalRecurrenceLoadWithoutRuntimeStartup() {
        var result = CslCompiler.compile("csl v4.0 Smoke { entity Counter { p : Integer; } }");
        assertTrue(result.ok(), result.errors().toString());
        assertEquals("Counter", result.model().entities().getFirst().name());
        assertEquals(GoalTaskStatus.PENDING, GoalMarking.initial(GoalType.NONE).update(true, false).status());
    }
}
