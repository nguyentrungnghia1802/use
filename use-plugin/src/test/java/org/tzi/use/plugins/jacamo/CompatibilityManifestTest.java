package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class CompatibilityManifestTest {
    @Test
    void manifestSeparatesBuildRequirementsFromAppendOnlyHostEvidence() throws Exception {
        JsonNode manifest = new ObjectMapper().readTree(Files.readString(Path.of("compatibility.json")));
        Document rootPom = parse(Path.of("../pom.xml"));
        Document pluginPom = parse(Path.of("pom.xml"));
        Document pluginDescriptor = parse(Path.of("src/main/resources/useplugin.xml"));
        JsonNode release = new ObjectMapper().readTree(Files.readString(Path.of("release/release-manifest.json")));
        JsonNode requirements = manifest.path("requirements");

        assertEquals(release.path("releaseVersion").asText(), manifest.path("plugin").path("version").asText());
        assertTrue(release.path("gitTag").isNull(), "frozen candidate is not a published release tag");
        assertEquals(release.path("gitTag"), manifest.path("plugin").path("tag"));
        assertEquals("frozen-v2-release-candidate", manifest.path("plugin").path("status").asText());
        assertEquals(pluginDescriptor.getDocumentElement().getAttribute("version"),
                manifest.path("plugin").path("version").asText());

        assertEquals(directChild(rootPom.getDocumentElement(), "version"),
                requirements.path("use").path("version").asText());
        assertTrue(requirements.path("use").path("commit").asText().matches("[0-9a-f]{40}"),
                "the pinned USE baseline must remain a full commit SHA");
        assertEquals(directChild((Element) rootPom.getElementsByTagName("properties").item(0),
                "maven.compiler.target"), requirements.path("java").path("minimum").asText());
        assertEquals("3.9.9", requirements.path("maven").path("version").asText());
        assertEquals(dependencyVersion(pluginPom, "io.github.jason-lang", "jason-interpreter"),
                requirements.path("jacamo").path("components").path("jason").asText());
        assertEquals(dependencyVersion(pluginPom, "org.jacamo", "cartago"),
                requirements.path("jacamo").path("components").path("cartago").asText());
        assertEquals(dependencyVersion(pluginPom, "org.jacamo", "moise"),
                requirements.path("jacamo").path("components").path("moise").asText());
        assertTrue(requirements.path("jacamo").path("targetVersion").asText().matches("\\d+\\.\\d+\\.\\d+"),
                "the JaCaMo target must remain a pinned semantic version");

        JsonNode evidence = manifest.path("evidence");
        assertTrue(evidence.isArray() && !evidence.isEmpty());
        for (JsonNode record : evidence) {
            LocalDate.parse(record.path("date").asText());
            JsonNode host = record.path("host");
            assertFalse(host.path("osName").asText().isBlank());
            assertFalse(host.path("osVersion").asText().isBlank());
            assertFalse(host.path("architecture").asText().isBlank());
            assertFalse(host.path("mavenVersion").asText().isBlank());
            assertFalse(host.path("mavenJavaVersion").asText().isBlank());
            assertFalse(record.path("runtimeScope").asText().isBlank());
            assertTrue(Set.of("not-run", "passed", "failed")
                    .contains(record.path("cleanEnvironment").path("status").asText()));
        }
    }

    @Test
    void manifestPinsEveryFrozenV2ResourceByExactHash() throws Exception {
        ObjectMapper json = new ObjectMapper();
        JsonNode manifest = json.readTree(Path.of("compatibility.json").toFile());
        JsonNode baseline = manifest.path("activeBaseline");
        JsonNode metamodel = baseline.path("metamodel");
        JsonNode mapping = baseline.path("mapping");
        JsonNode runtime = manifest.path("runtimeMapping");

        assertEquals("FROZEN", baseline.path("status").asText());
        assertEquals("V2", metamodel.path("version").asText());
        assertEquals("FROZEN", metamodel.path("status").asText());
        assertPinned(metamodel.path("path").asText(), metamodel.path("sha256").asText());

        JsonNode mappingSource = json.readTree(Path.of(mapping.path("path").asText()).toFile());
        assertEquals(mappingSource.path("mappingId").asText(), mapping.path("mappingId").asText());
        assertEquals(mappingSource.path("schemaVersion").asText(), mapping.path("schemaVersion").asText());
        assertEquals(mappingSource.path("status").asText(), mapping.path("status").asText());
        assertPinned(mapping.path("path").asText(), mapping.path("sha256").asText());
        assertPinned(mapping.path("schemaPath").asText(), mapping.path("schemaSha256").asText());

        JsonNode runtimeSource = json.readTree(Path.of(runtime.path("path").asText()).toFile());
        assertEquals("2.0.0", runtime.path("mappingVersion").asText());
        assertEquals(runtimeSource.path("schemaVersion").asText(), runtime.path("schemaVersion").asText());
        assertEquals(runtimeSource.path("status").asText(), runtime.path("status").asText());
        assertEquals(runtimeSource.path("targetBaseline").asText(), runtime.path("targetBaseline").asText());
        assertFalse(runtime.path("provisional").asBoolean());
        assertPinned(runtime.path("path").asText(), runtime.path("sha256").asText());
        assertPinned(runtime.path("schemaPath").asText(), runtime.path("schemaSha256").asText());

        JsonNode profiles = manifest.path("oclProfiles");
        JsonNode coreManifest = json.readTree(Path.of(profiles.path("coreManifest").path("path").asText()).toFile());
        assertEquals(coreManifest.path("profileId").asText(), profiles.path("core").path("profileId").asText());
        assertEquals(coreManifest.path("version").asText(), profiles.path("core").path("version").asText());
        assertPinned(profiles.path("core").path("path").asText(), profiles.path("core").path("sha256").asText());
        assertPinned(profiles.path("coreManifest").path("path").asText(),
                profiles.path("coreManifest").path("sha256").asText());
        JsonNode verificationSource = json.readTree(
                Path.of(profiles.path("verificationProfile").path("path").asText()).toFile());
        assertEquals(verificationSource.path("profileId").asText(),
                profiles.path("verificationProfile").path("profileId").asText());
        assertEquals(verificationSource.path("version").asText(),
                profiles.path("verificationProfile").path("version").asText());
        assertPinned(profiles.path("verificationProfile").path("path").asText(),
                profiles.path("verificationProfile").path("sha256").asText());

        JsonNode frozen = json.readTree(Path.of("release/v2-freeze-manifest.json").toFile());
        assertEquals("V2", frozen.path("baseline").asText());
        assertEquals("FROZEN", frozen.path("status").asText());
        assertTrue(frozen.path("freeze").asBoolean());
        assertPinned(manifest.path("freezeManifest").path("path").asText(),
                manifest.path("freezeManifest").path("sha256").asText());
        frozen.path("resources").forEach(resource -> {
            if (resource.has("path") && resource.has("sha256"))
                assertPinned(resource.path("path").asText(), resource.path("sha256").asText());
            if (resource.has("schemaPath"))
                assertPinned(resource.path("schemaPath").asText(), resource.path("schemaSha256").asText());
            if (resource.has("manifestPath"))
                assertPinned(resource.path("manifestPath").asText(), resource.path("manifestSha256").asText());
        });
    }

    private void assertPinned(String path, String expected) {
        try {
            assertTrue(expected.matches("[0-9a-f]{64}"), path + " needs a lowercase SHA-256");
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(Path.of(path))));
            assertEquals(expected, actual, path);
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(path, exception);
        }
    }

    private Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private String dependencyVersion(Document pom, String groupId, String artifactId) {
        NodeList dependencies = pom.getElementsByTagName("dependency");
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            if (groupId.equals(directChild(dependency, "groupId"))
                    && artifactId.equals(directChild(dependency, "artifactId"))) {
                String scope = directChild(dependency, "scope");
                assertEquals("provided", scope, artifactId + " must remain an isolated host/runtime dependency");
                return directChild(dependency, "version");
            }
        }
        throw new AssertionError("missing dependency " + groupId + ":" + artifactId);
    }

    private String directChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && element.getTagName().equals(name)) {
                return element.getTextContent().trim();
            }
        }
        throw new AssertionError("missing " + name + " below " + parent.getTagName());
    }
}
