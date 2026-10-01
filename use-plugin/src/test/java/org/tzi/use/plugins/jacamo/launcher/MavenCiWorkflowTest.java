package org.tzi.use.plugins.jacamo.launcher;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the CI contract that used to hide Maven failure behind tee and a missing ZIP. */
class MavenCiWorkflowTest {
    @Test void mavenFailureIsNotHiddenAndGuiTestsHaveTheirRequiredInputs() throws Exception {
        JsonNode job = workflow().path("jobs").path("build");
        assertEquals("bash", job.path("defaults").path("run").path("shell").asText());
        assertEquals("use", job.path("defaults").path("run").path("working-directory").asText());
        JsonNode build = step(job, "Build with Maven");
        String command = build.path("run").asText();
        assertTrue(command.contains("set -o pipefail"));
        assertTrue(command.contains("xvfb-run --auto-servernum mvn"));
        assertTrue(command.contains("-Djava.awt.headless=false verify"));
        assertTrue(command.contains("2>&1 | tee build_output.log"));
        assertFalse(command.contains("skipTests"));
        assertFalse(command.contains("maven.test.skip"));
        assertFalse(command.contains("|| true"));
        assertTrue(step(job, "Install virtual display for GUI integration tests").path("run").asText()
                .contains("xvfb xauth"));
        JsonNode cases = step(job, "Check out audited JaCaMo case studies").path("with");
        assertEquals("jacamo-lang/jacamo", cases.path("repository").asText());
        assertTrue(cases.path("ref").asText().matches("[0-9a-f]{40}"));
        assertEquals("jacamo", cases.path("path").asText());
        assertEquals("ln -s jacamo JaCaMo", step(job, "Preserve legacy fixture path casing").path("run").asText());
    }

    @Test void realReleaseFilesAreRequiredAndFailureEvidenceIsAlwaysUploaded() throws Exception {
        JsonNode job = workflow().path("jobs").path("build");
        String check = step(job, "Check release archives").path("run").asText();
        assertTrue(check.contains("use-assembly/target/*.zip"));
        assertTrue(check.contains("use-plugin/target/use-jacamo-plugin-*.zip"));
        assertTrue(check.contains("test \"${#use_archives[@]}\" -gt 0"));
        assertTrue(check.contains("test \"${#plugin_archives[@]}\" -gt 0"));
        List<String> always = new ArrayList<>();
        for (JsonNode step : job.path("steps")) {
            if (!"actions/upload-artifact@v4".equals(step.path("uses").asText())) continue;
            String name = step.path("with").path("name").asText();
            if ("Package".equals(name)) {
                assertEquals("error", step.path("with").path("if-no-files-found").asText());
                String paths = step.path("with").path("path").asText();
                assertTrue(paths.contains("use/use-assembly/target/*.zip"));
                assertTrue(paths.contains("use/use-plugin/target/use-jacamo-plugin-*.zip"));
            } else if (List.of("build-log", "failure-reports").contains(name)) {
                assertEquals("always()", step.path("if").asText());
                always.add(name);
            }
        }
        assertEquals(List.of("build-log", "failure-reports"), always);
    }

    private static JsonNode workflow() throws Exception {
        Path file = Path.of("../.github/workflows/maven.yml");
        return new ObjectMapper(new YAMLFactory()).readTree(Files.readString(file));
    }

    private static JsonNode step(JsonNode job, String name) {
        for (JsonNode step : job.path("steps")) if (name.equals(step.path("name").asText())) return step;
        throw new AssertionError("Missing CI step: " + name);
    }
}
