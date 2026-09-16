package org.tzi.use.plugins.jacamo.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class ReleasePackageIT {
    @Test
    void builtReleaseMatchesManifestAndPublishedChecksum() throws Exception {
        Path root = Path.of(".").toRealPath();
        Path archive = root.resolve("target/use-jacamo-plugin-1.0.0.zip");
        Path checksum = root.resolve("target/use-jacamo-plugin-1.0.0.zip.sha256");
        assertTrue(Files.isRegularFile(archive), "release ZIP must be built in package phase");
        assertTrue(Files.isRegularFile(checksum), "release ZIP must have a SHA-256 sidecar");

        JsonNode manifest = new ObjectMapper().readTree(root.resolve("release/release-manifest.json").toFile());
        Map<String, JsonNode> declared = new LinkedHashMap<>();
        manifest.path("packageEntries").forEach(entry -> declared.put(entry.path("path").asText(), entry));

        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Set<String> actual = new LinkedHashSet<>();
            zip.stream().filter(entry -> !entry.isDirectory()).forEach(entry -> actual.add(entry.getName()));
            assertEquals(declared.keySet(), actual, "archive inventory must exactly match the release manifest");

            for (Map.Entry<String, JsonNode> item : declared.entrySet()) {
                var entry = zip.getEntry(item.getKey());
                assertNotNull(entry, item.getKey());
                byte[] packaged;
                try (InputStream input = zip.getInputStream(entry)) {
                    packaged = input.readAllBytes();
                }
                assertTrue(packaged.length > 0, () -> "empty release entry: " + item.getKey());
                Path source = root.resolve(item.getValue().path("source").asText());
                assertArrayEquals(Files.readAllBytes(source), packaged,
                        () -> "packaged bytes differ from source: " + item.getKey());
            }

            byte[] pluginJar;
            try (InputStream input = zip.getInputStream(zip.getEntry("lib/plugins/use-jacamo-plugin-1.0.0.jar"))) {
                pluginJar = input.readAllBytes();
            }
            Set<String> requiredJarEntries = Set.of(
                    "useplugin.xml",
                    "org/tzi/use/plugins/jacamo/JaCaMoPlugin.class",
                    "org/tzi/use/plugins/jacamo/canonical/JaCaMo-Metamodel.ecore",
                    "org/tzi/use/plugins/jacamo/canonical/jacamo-use-mapping-v1.json",
                    "org/tzi/use/plugins/jacamo/canonical/jacamo-use-mapping.schema.json",
                    "org/tzi/use/plugins/jacamo/canonical/freeze-manifest.json",
                    "org/tzi/use/plugins/jacamo/ocl/jacamo-core.ocl",
                    "org/tzi/use/plugins/jacamo/release/release-manifest.json");
            Set<String> jarEntries = new LinkedHashSet<>();
            try (JarInputStream jar = new JarInputStream(new java.io.ByteArrayInputStream(pluginJar))) {
                for (var entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
                    if (!entry.isDirectory()) jarEntries.add(entry.getName());
                }
            }
            assertTrue(jarEntries.containsAll(requiredJarEntries),
                    () -> "plugin JAR is missing required release resources: "
                            + requiredJarEntries.stream().filter(entry -> !jarEntries.contains(entry)).toList());
        }

        String expected = Files.readString(checksum).strip().split("\\s+")[0].toLowerCase();
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(archive)));
        assertEquals(expected, actual, "release checksum sidecar must match the final ZIP bytes");
    }
}
