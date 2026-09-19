package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
        assertEquals(release.path("gitTag").asText(), manifest.path("plugin").path("tag").asText());
        assertEquals(pluginDescriptor.getDocumentElement().getAttribute("version"),
                manifest.path("plugin").path("version").asText());

        assertEquals(directChild(rootPom.getDocumentElement(), "version"),
                requirements.path("use").path("version").asText());
        assertTrue(requirements.path("use").path("commit").asText().matches("[0-9a-f]{40}"),
                "the pinned USE baseline must remain a full commit SHA");
        assertEquals(directChild((Element) rootPom.getElementsByTagName("properties").item(0),
                "maven.compiler.target"), requirements.path("java").path("minimum").asText());
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
