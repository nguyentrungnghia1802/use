package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.security.MessageDigest;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Historical fingerprint evidence only; the production loader rejects this target contract. */
class HistoricalRuntimeMappingTest {
    @Test void originalFrozenRuntimeAndTargetBytesRemainVerifiable() throws Exception {
        var manifest = new ObjectMapper().readTree(
                RuntimeMappingLoader.historicalResource("runtime/runtime-mapping-freeze.json"));
        assertEquals("FROZEN", manifest.path("status").asText());
        for (String name : List.of("runtime/jacamo-use-runtime-mapping-v1.json", "runtime/runtime-mapping.schema.json",
                "canonical/JaCaMo-Metamodel.ecore", "canonical/jacamo-use-mapping-v1.json")) {
            String resource = "/org/tzi/use/plugins/jacamo/historical/version-1/"
                    + (name.startsWith("canonical/") ? name.substring("canonical/".length()) : name);
            try (var input = getClass().getResourceAsStream(resource)) {
                assertNotNull(input, name); byte[] bytes = input.readAllBytes();
                String expected = manifest.path("hashes").path(name).asText();
                assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), name);
                byte[] altered = Arrays.copyOf(bytes, bytes.length + 1);
                assertNotEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(altered)), name);
            }
        }
    }
}
