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
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.zip.ZipFile;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.tzi.use.runtime.MainPluginRuntime;
import org.tzi.use.runtime.impl.PluginRuntime;

class ReleasePackageIT {
    @Test
    void installedArchiveImportsCanonicalMappingWithoutMavenTestClasspath() throws Exception {
        Path root = Path.of(".").toRealPath();
        Path hostJar = root.resolve("../use-gui/target/use-gui.jar").toRealPath();
        Path install = Files.createTempDirectory(root.resolve("target"), "release-install-");
        try (ZipFile zip = new ZipFile(root.resolve("target/use-jacamo-plugin-1.0.1-v2-frozen.zip").toFile())) {
            for (var entry : java.util.Collections.list(zip.entries())) {
                if (entry.isDirectory()) continue;
                if (!entry.getName().startsWith("Core/") && !entry.getName().startsWith("lib/plugins/")) continue;
                Path destination = install.resolve(entry.getName()).normalize();
                assertTrue(destination.startsWith(install), "ZIP entry must remain inside install root");
                Files.createDirectories(destination.getParent());
                try (InputStream input = zip.getInputStream(entry)) {
                    Files.copy(input, destination);
                }
            }
        }
        Path pluginJar = install.resolve("lib/plugins/use-jacamo-plugin-1.0.1.jar");
        String javaName = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", javaName);
        String classpath = hostJar + java.io.File.pathSeparator + root.resolve("target/test-classes");
        Process process = new ProcessBuilder(javaExecutable.toString(), "-cp", classpath,
                ReleaseIsolatedSmokeMain.class.getName(), install.toString(), pluginJar.toString())
                .directory(root.toFile()).redirectErrorStream(true).start();
        boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) process.destroyForcibly();
        assertTrue(finished, "isolated installed release smoke timed out");
        assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes()));
    }

    @Test
    void packagedPluginJarLoadsInPinnedUseRuntime() throws Exception {
        Path root = Path.of(".").toRealPath();
        Path pluginDirectory = root.resolve("target/release-plugin-smoke");
        Files.createDirectories(pluginDirectory);
        try (ZipFile zip = new ZipFile(root.resolve("target/use-jacamo-plugin-1.0.1-v2-frozen.zip").toFile());
             InputStream input = zip.getInputStream(zip.getEntry("lib/plugins/use-jacamo-plugin-1.0.1.jar"))) {
            Files.copy(input, pluginDirectory.resolve("use-jacamo-plugin-1.0.1.jar"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        MainPluginRuntime.run(pluginDirectory);
        var plugin = ((PluginRuntime) PluginRuntime.getInstance()).getPlugin("JaCaMo");
        assertNotNull(plugin, "USE must discover the JAR extracted from the actual release ZIP");
        assertEquals("JaCaMo", plugin.getPluginClass().getName());
    }

    @Test
    void builtReleaseMatchesManifestAndPublishedChecksum() throws Exception {
        Path root = Path.of(".").toRealPath();
        Path archive = root.resolve("target/use-jacamo-plugin-1.0.1-v2-frozen.zip");
        Path checksum = root.resolve("target/use-jacamo-plugin-1.0.1-v2-frozen.zip.sha256");
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
            try (InputStream input = zip.getInputStream(zip.getEntry("lib/plugins/use-jacamo-plugin-1.0.1.jar"))) {
                pluginJar = input.readAllBytes();
            }
            Map<String, String> jarResources = new LinkedHashMap<>(Map.of(
                    "useplugin.xml", "src/main/resources/useplugin.xml",
                    "org/tzi/use/plugins/jacamo/canonical/version-2/jacamo_v2_complete.ecore",
                    "Core/Metamodel/version-2/jacamo_v2_complete.ecore",
                    "org/tzi/use/plugins/jacamo/canonical/version-2/jacamo-use-mapping-v2.json",
                    "Core/Mapping/version-2/jacamo-use-mapping-v2.json",
                    "org/tzi/use/plugins/jacamo/canonical/version-2/jacamo-use-mapping-v2.schema.json",
                    "Core/Mapping/version-2/jacamo-use-mapping-v2.schema.json",
                    "org/tzi/use/plugins/jacamo/ocl/jacamo-core-v2.ocl",
                    "src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core-v2.ocl",
                    "org/tzi/use/plugins/jacamo/release/release-manifest.json",
                    "release/release-manifest.json",
                    "org/tzi/use/plugins/jacamo/release/compatibility.json",
                    "compatibility.json",
                    "org/tzi/use/plugins/jacamo/release/licenses/APACHE-2.0.txt",
                    "licenses/APACHE-2.0.txt",
                    "org/tzi/use/plugins/jacamo/release/licenses/SLF4J-MIT.txt",
                    "licenses/SLF4J-MIT.txt"));
            jarResources.put("org/tzi/use/plugins/jacamo/ocl/jacamo-core-v2-manifest.json",
                    "src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core-v2-manifest.json");
            jarResources.put("org/tzi/use/plugins/jacamo/verification/jacamo-verification-profile-v2.json",
                    "src/main/resources/org/tzi/use/plugins/jacamo/verification/jacamo-verification-profile-v2.json");
            jarResources.put("org/tzi/use/plugins/jacamo/release/v2-freeze-manifest.json",
                    "release/v2-freeze-manifest.json");
            for (String name : java.util.List.of("jacamo-use-runtime-mapping-v2.json", "runtime-mapping-v2.schema.json"))
                jarResources.put("org/tzi/use/plugins/jacamo/runtime/" + name,
                    "src/main/resources/org/tzi/use/plugins/jacamo/runtime/" + name);
            Map<String, String> historical = Map.of(
                    "ocl/jacamo-core.ocl", "src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core.ocl",
                    "ocl/jacamo-core-manifest.json", "src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core-manifest.json",
                    "runtime/jacamo-use-runtime-mapping-v1.json", "src/main/resources/org/tzi/use/plugins/jacamo/runtime/jacamo-use-runtime-mapping-v1.json",
                    "runtime/runtime-mapping.schema.json", "src/main/resources/org/tzi/use/plugins/jacamo/runtime/runtime-mapping.schema.json",
                    "runtime/runtime-mapping-freeze.json", "src/main/resources/org/tzi/use/plugins/jacamo/runtime/runtime-mapping-freeze.json",
                    "verification/jacamo-verification-profile-v1.json", "src/main/resources/org/tzi/use/plugins/jacamo/verification/jacamo-verification-profile-v1.json");
            historical.forEach((name, source) -> jarResources.put(
                    "org/tzi/use/plugins/jacamo/historical/version-1/" + name, source));
            Set<String> jarEntries = new LinkedHashSet<>();
            try (JarInputStream jar = new JarInputStream(new java.io.ByteArrayInputStream(pluginJar))) {
                for (var entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
                    if (entry.isDirectory()) continue;
                    jarEntries.add(entry.getName());
                    String source = jarResources.get(entry.getName());
                    if (source != null) {
                        String entryName = entry.getName();
                        assertArrayEquals(Files.readAllBytes(root.resolve(source)), jar.readAllBytes(),
                                () -> "JAR resource bytes differ from canonical source: " + entryName);
                    }
                    if (entry.getName().equals("META-INF/maven/org.tzi.use/use-plugin/pom.properties")) {
                        Properties coordinates = new Properties();
                        coordinates.load(jar);
                        assertEquals("1.0.1", coordinates.getProperty("version"),
                                "Maven JAR metadata must identify the plugin release version");
                    }
                }
            }
            org.junit.jupiter.api.Assertions.assertFalse(jarEntries.contains("org/tzi/use/plugins/jacamo/runtime/jacamo-use-runtime-mapping-draft.json"),
                    "Obsolete draft resource must not survive in release output; run clean package");
            for (String historicalName : historical.keySet())
                org.junit.jupiter.api.Assertions.assertFalse(
                        jarEntries.contains("org/tzi/use/plugins/jacamo/" + historicalName),
                        "V1 target resource must not survive in the active namespace: " + historicalName);
            Set<String> requiredJarEntries = new LinkedHashSet<>(jarResources.keySet());
            requiredJarEntries.add("org/tzi/use/plugins/jacamo/JaCaMoPlugin.class");
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
