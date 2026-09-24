package org.tzi.use.plugins.jacamo.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReleasePackageContractTest {
    private static final Set<String> REQUIRED_ENTRIES = Set.of(
            "CHANGELOG.md",
            "Core/Mapping/version-2/jacamo-use-mapping-v2.json",
            "Core/Mapping/version-2/jacamo-use-mapping-v2.schema.json",
            "Core/Metamodel/version-2/jacamo_v2_complete.ecore",
            "KNOWN-LIMITATIONS.md",
            "LICENSE",
            "licenses/APACHE-2.0.txt",
            "licenses/SLF4J-MIT.txt",
            "NOTICE",
            "README.md",
            "compatibility.json",
            "docs/architecture.md",
            "docs/build-release.md",
            "docs/user-workflow.md",
            "examples/auction/auction.jcm",
            "examples/auction/src/agt/auctioneer.asl",
            "examples/auction/src/env/auction/AuctionArtifact.java",
            "examples/auction/src/org/auction.xml",
            "examples/auction/verification/auction.ocl",
            "lib/plugins/use-jacamo-plugin-1.0.1.jar",
            "ocl/jacamo-core-v2.ocl",
            "profiles/jacamo-verification-profile-v2.json",
            "release-manifest.json",
            "runtime/jacamo-use-runtime-mapping-v2.json",
            "runtime/runtime-mapping-v2.schema.json",
            "schemas/binding-v1.schema.json",
            "schemas/runtime-event-v1.schema.json",
            "schemas/trace-v1.schema.json");

    @Test
    void releaseManifestPinsVersionAndCompletePackageInventory() throws Exception {
        Path root = Path.of(".").toRealPath();
        Path manifestPath = root.resolve("release/release-manifest.json");
        assertTrue(Files.isRegularFile(manifestPath), "release manifest is required before packaging");

        JsonNode manifest = new ObjectMapper().readTree(manifestPath.toFile());
        assertEquals("1.0.1", manifest.path("releaseVersion").asText());
        assertTrue(manifest.path("gitTag").isNull());
        assertEquals("WORKING_V2_NOT_RELEASED", manifest.path("status").asText());
        assertEquals("7.5.0", manifest.path("compatibility").path("use").asText());
        assertEquals("1.0.1", manifest.path("compatibility").path("pluginDescriptor").asText());
        assertEquals("SHA-256", manifest.path("integrity").path("packageAlgorithm").asText());
        assertFalse(manifest.path("limitations").isEmpty());

        Set<String> declared = new LinkedHashSet<>();
        for (JsonNode entry : manifest.path("packageEntries")) {
            String source = entry.path("source").asText();
            String target = entry.path("path").asText();
            assertFalse(source.isBlank(), "every package entry needs a repository source");
            assertFalse(target.isBlank(), "every package entry needs an archive path");
            if (entry.path("generated").asBoolean()) {
                assertEquals("target/use-plugin-1.0.1.jar", source,
                        "only the Maven-built plugin JAR may be a generated package input");
            } else {
                assertTrue(Files.isRegularFile(root.resolve(source)), () -> "missing release source: " + source);
            }
            assertTrue(declared.add(target), () -> "duplicate archive path: " + target);
        }
        assertEquals(REQUIRED_ENTRIES, declared);
    }
}
