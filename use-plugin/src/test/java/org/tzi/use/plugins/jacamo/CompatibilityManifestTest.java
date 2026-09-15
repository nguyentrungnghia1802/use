package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class CompatibilityManifestTest {
    @Test
    void manifestMatchesBuildPinsRuntimeDependenciesAndVerifiedHost() throws Exception {
        JsonNode manifest = new ObjectMapper().readTree(Files.readString(Path.of("compatibility.json")));
        Document rootPom = parse(Path.of("../pom.xml"));
        Document pluginPom = parse(Path.of("pom.xml"));

        assertEquals(directChild(rootPom.getDocumentElement(), "version"),
                manifest.path("use").path("version").asText());
        assertEquals(directChild((Element) rootPom.getElementsByTagName("properties").item(0),
                "maven.compiler.target"), manifest.path("java").path("minimum").asText());
        assertEquals(System.getProperty("java.version"), manifest.path("java").path("version").asText());
        assertEquals(dependencyVersion(pluginPom, "io.github.jason-lang", "jason-interpreter"),
                manifest.path("jacamo").path("components").path("jason").asText());
        assertEquals(dependencyVersion(pluginPom, "org.jacamo", "cartago"),
                manifest.path("jacamo").path("components").path("cartago").asText());
        assertEquals(dependencyVersion(pluginPom, "org.jacamo", "moise"),
                manifest.path("jacamo").path("components").path("moise").asText());
        assertEquals("verified-in-process", manifest.path("jacamo").path("integrationStatus").asText());
        assertFalse(manifest.path("jacamo").path("targetVersion").asText().isBlank());

        JsonNode host = manifest.path("verification").path("host");
        assertEquals(System.getProperty("os.name"), host.path("osName").asText());
        assertEquals(System.getProperty("os.version"), host.path("osVersion").asText());
        assertEquals(System.getProperty("os.arch"), host.path("architecture").asText());
        assertEquals(System.getProperty("java.version"), host.path("mavenJavaVersion").asText());
        assertEquals(System.getProperty("java.vendor"), host.path("mavenJavaVendor").asText());
        assertEquals(manifest.path("maven").path("version").asText(), host.path("mavenVersion").asText());
        assertEquals("not-run", manifest.path("verification").path("cleanEnvironmentStatus").asText());
        assertFalse(manifest.path("verification").path("runtimeScope").asText().isBlank());
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
