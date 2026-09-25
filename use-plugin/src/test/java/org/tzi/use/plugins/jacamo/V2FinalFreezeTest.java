package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.mapping.ActiveBaseline;
import org.tzi.use.plugins.jacamo.mapping.MappingException;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMappingLoader;

class V2FinalFreezeTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path FREEZE = Path.of("release/v2-freeze-manifest.json");
    @TempDir Path temporary;

    @Test void oneFinalManifestPinsEveryFrozenV2Contract() throws Exception {
        JsonNode freeze = JSON.readTree(FREEZE.toFile());
        JsonNode compatibility = JSON.readTree(Path.of("compatibility.json").toFile());
        JsonNode release = JSON.readTree(Path.of("release/release-manifest.json").toFile());

        assertEquals("V2", freeze.path("baseline").asText());
        assertEquals("FROZEN", freeze.path("status").asText());
        assertTrue(freeze.path("freeze").asBoolean());
        assertEquals("FROZEN", compatibility.path("activeBaseline").path("status").asText());
        assertEquals("FROZEN", compatibility.path("runtimeMapping").path("status").asText());
        assertFalse(compatibility.path("runtimeMapping").path("provisional").asBoolean());
        assertTrue(release.path("activeBaseline").path("freeze").asBoolean());
        assertEquals("release/v2-freeze-manifest.json",
                release.path("activeBaseline").path("freezeManifest").asText());

        freeze.path("resources").forEach(resource -> {
            if (resource.has("path")) assertHash(resource.path("path").asText(), resource.path("sha256").asText());
            if (resource.has("schemaPath"))
                assertHash(resource.path("schemaPath").asText(), resource.path("schemaSha256").asText());
            if (resource.has("manifestPath"))
                assertHash(resource.path("manifestPath").asText(), resource.path("manifestSha256").asText());
        });
        assertHash(compatibility.path("freezeManifest").path("path").asText(),
                compatibility.path("freezeManifest").path("sha256").asText());

        JsonNode mapping = JSON.readTree(Path.of(freeze.path("resources").path("structuralMapping")
                .path("path").asText()).toFile());
        JsonNode runtime = JSON.readTree(Path.of(freeze.path("resources").path("runtimeMapping")
                .path("path").asText()).toFile());
        assertEquals("FROZEN", mapping.path("status").asText());
        assertEquals("FROZEN", runtime.path("status").asText());
        assertEquals(freeze.path("resources").path("metamodel").path("sha256").asText(),
                mapping.path("sourceMetamodel").path("sha256").asText());
        assertEquals(freeze.path("resources").path("structuralMapping").path("sha256").asText(),
                runtime.path("targetContract").path("mappingSha256").asText());
        assertEquals("FROZEN", new RuntimeMappingLoader().loadDefault().status());

        assertFalse(Files.exists(Path.of("release/v2-working-baseline-manifest.json")));
        assertTrue(Files.isRegularFile(Path.of("release/historical/v2-working-baseline-manifest.json")));
        for (JsonNode evidence : freeze.path("evidence"))
            assertTrue(Files.isRegularFile(Path.of(evidence.asText())), evidence.asText());
        for (Path active : List.of(Path.of("compatibility.json"), Path.of("release/release-manifest.json"),
                FREEZE, Path.of("Core/Mapping/version-2/jacamo-use-mapping-v2.json"),
                Path.of("src/main/resources/org/tzi/use/plugins/jacamo/runtime/jacamo-use-runtime-mapping-v2.json"))) {
            assertFalse(Files.readString(active).contains("WORKING_BASELINE"), active.toString());
        }
    }

    @Test void mutatedFreezeFingerprintFailsClosedWithoutHistoricalFallback() throws Exception {
        Path metamodel = Files.createDirectories(temporary.resolve("Core/Metamodel/version-2"));
        Path mapping = Files.createDirectories(temporary.resolve("Core/Mapping/version-2"));
        Path release = Files.createDirectories(temporary.resolve("release"));
        Files.copy(Path.of("Core/Metamodel/version-2", ActiveBaseline.ECORE),
                metamodel.resolve(ActiveBaseline.ECORE));
        Files.copy(Path.of("Core/Mapping/version-2", ActiveBaseline.MAPPING),
                mapping.resolve(ActiveBaseline.MAPPING));
        Files.copy(Path.of("Core/Mapping/version-2", ActiveBaseline.SCHEMA),
                mapping.resolve(ActiveBaseline.SCHEMA));
        ObjectNode mutated = (ObjectNode) JSON.readTree(FREEZE.toFile());
        ((ObjectNode) mutated.path("resources").path("structuralMapping"))
                .put("sha256", "0".repeat(64));
        JSON.writerWithDefaultPrettyPrinter().writeValue(release.resolve(ActiveBaseline.FREEZE).toFile(), mutated);

        MappingException failure = assertThrows(MappingException.class,
                () -> new ActiveBaseline().fromCheckout(temporary));
        assertEquals("ACTIVE_FREEZE_HASH_MISMATCH", failure.code());
    }

    private static void assertHash(String path, String expected) {
        try {
            assertTrue(expected.matches("[0-9a-f]{64}"), path);
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(Path.of(path))));
            assertEquals(expected, actual, path);
        } catch (Exception exception) {
            throw new AssertionError(path, exception);
        }
    }
}
