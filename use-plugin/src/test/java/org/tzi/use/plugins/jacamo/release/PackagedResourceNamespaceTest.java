package org.tzi.use.plugins.jacamo.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PackagedResourceNamespaceTest {
    private static final String ROOT = "/org/tzi/use/plugins/jacamo/";

    @Test
    void activeNamespaceContainsV2WhileTargetV1ResourcesAreHistoricalOnly() throws Exception {
        for (String activeV1Path : Map.of(
                "ocl/jacamo-core.ocl", "src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core.ocl",
                "ocl/jacamo-core-manifest.json", "src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core-manifest.json",
                "runtime/jacamo-use-runtime-mapping-v1.json", "src/main/resources/org/tzi/use/plugins/jacamo/runtime/jacamo-use-runtime-mapping-v1.json",
                "runtime/runtime-mapping.schema.json", "src/main/resources/org/tzi/use/plugins/jacamo/runtime/runtime-mapping.schema.json",
                "runtime/runtime-mapping-freeze.json", "src/main/resources/org/tzi/use/plugins/jacamo/runtime/runtime-mapping-freeze.json",
                "verification/jacamo-verification-profile-v1.json", "src/main/resources/org/tzi/use/plugins/jacamo/verification/jacamo-verification-profile-v1.json"
        ).keySet()) {
            assertNull(getClass().getResource(ROOT + activeV1Path),
                    () -> "historical target resource leaked into the active namespace: " + activeV1Path);
        }

        Map<String, String> historical = Map.of(
                "ocl/jacamo-core.ocl", "src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core.ocl",
                "ocl/jacamo-core-manifest.json", "src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core-manifest.json",
                "runtime/jacamo-use-runtime-mapping-v1.json", "src/main/resources/org/tzi/use/plugins/jacamo/runtime/jacamo-use-runtime-mapping-v1.json",
                "runtime/runtime-mapping.schema.json", "src/main/resources/org/tzi/use/plugins/jacamo/runtime/runtime-mapping.schema.json",
                "runtime/runtime-mapping-freeze.json", "src/main/resources/org/tzi/use/plugins/jacamo/runtime/runtime-mapping-freeze.json",
                "verification/jacamo-verification-profile-v1.json", "src/main/resources/org/tzi/use/plugins/jacamo/verification/jacamo-verification-profile-v1.json"
        );
        for (Map.Entry<String, String> item : historical.entrySet()) {
            try (InputStream input = getClass().getResourceAsStream(
                    ROOT + "historical/version-1/" + item.getKey())) {
                assertNotNull(input, item.getKey());
                assertArrayEquals(Files.readAllBytes(Path.of(item.getValue())), input.readAllBytes(), item.getKey());
            }
        }

        for (String activeV2 : java.util.List.of(
                "canonical/version-2/jacamo_v2_complete.ecore",
                "canonical/version-2/jacamo-use-mapping-v2.json",
                "ocl/jacamo-core-v2.ocl",
                "runtime/jacamo-use-runtime-mapping-v2.json",
                "verification/jacamo-verification-profile-v2.json")) {
            assertNotNull(getClass().getResource(ROOT + activeV2), activeV2);
        }
    }
}
