package org.tzi.use.plugins.jacamo.mapping;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.junit.jupiter.api.Test;

/** Native EMF gate. Counts come from classifiers/features, never a V1 constant. */
public class V2EcoreAuditTest {
    public static final Path SOURCE = Path.of("Core/Metamodel/version-2/jacamo_v2_complete.ecore");

    public static EPackage load(String xml) throws Exception {
        if (xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE") || xml.toUpperCase(Locale.ROOT).contains("<!ENTITY"))
            throw new IllegalArgumentException("ECORE_UNSAFE_XML");
        var resources = new ResourceSetImpl();
        resources.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        var resource = resources.createResource(URI.createURI("memory:/audit.ecore"));
        resource.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), Map.of());
        EcoreUtil.resolveAll(resources);
        assertTrue(resource.getErrors().isEmpty(), resource.getErrors().toString());
        assertTrue(EcoreUtil.UnresolvedProxyCrossReferencer.find(resources).isEmpty(), "Unresolved Ecore proxy");
        return (EPackage) resource.getContents().get(0);
    }

    @Test void nativeEcoreValidationAndWorkingManifest() throws Exception {
        var pkg = load(Files.readString(SOURCE));
        var diagnostic = Diagnostician.INSTANCE.validate(pkg);
        assertEquals(Diagnostic.OK, diagnostic.getSeverity(), diagnostic.toString());
        var counts = new TreeMap<String, Integer>();
        counts.put("EClass", 0); counts.put("EEnum", 0); counts.put("EAttribute", 0);
        counts.put("EReference", 0); counts.put("inheritance", 0);
        for (EClassifier type : pkg.getEClassifiers()) {
            if (type instanceof EClass cls) {
                counts.merge("EClass", 1, Integer::sum);
                counts.merge("EAttribute", cls.getEAttributes().size(), Integer::sum);
                counts.merge("EReference", cls.getEReferences().size(), Integer::sum);
                counts.merge("inheritance", cls.getESuperTypes().size(), Integer::sum);
            } else if (type instanceof EEnum) counts.merge("EEnum", 1, Integer::sum);
        }
        var json = new ObjectMapper();
        var inventory = json.readTree(Path.of("docs/project/v2-migration/metamodel-v2-inventory.json").toFile());
        assertEquals(json.valueToTree(counts), inventory.path("counts"));
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("status", "WORKING_BASELINE");
        manifest.put("version", "2-working-2026-09-23");
        manifest.put("recordedDate", "2026-09-23");
        manifest.put("path", SOURCE.toString().replace('\\', '/'));
        manifest.put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(SOURCE))));
        manifest.put("package", pkg.getName()); manifest.put("nsURI", pkg.getNsURI());
        manifest.put("nsPrefix", pkg.getNsPrefix()); manifest.put("counts", counts);
        manifest.put("audit", "EMF Diagnostician OK; no unresolved proxies");
        manifest.put("toolchain", Map.of("emfEcore", "2.39.0", "emfXmi", "2.39.0", "emfCommon", "2.42.0"));
        manifest.put("provenance", "User-supplied canonical Ecore; native validation is structural, not source-language equivalence");
        manifest.put("unresolved", List.of("V2 production migration and downstream regression remain OPEN", "No automatic equivalence inferred for removed V1 concepts"));
        manifest.put("evolutionPolicy", "Exact diff, impact, reconcile, test and update revision hashes; final freeze only at Phase 44");
        Path output = Files.createDirectories(Path.of("target/phase30-ecore-audit"));
        Files.writeString(output.resolve("metamodel-v2-working-manifest.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(manifest) + "\n");
    }

    @Test void malformedUnknownClassifierAndDatatypeAreRejected() throws Exception {
        String original = Files.readString(SOURCE);
        assertThrows(Exception.class, () -> load("<broken>"));
        var missing = assertThrows(java.io.IOException.class, () -> load(original.replace("#//Mission\"", "#//Absent\"")));
        assertTrue(missing.getMessage().contains("//Absent"));
        assertThrows(AssertionError.class, () -> load(original.replace("Ecore#//EString", "Ecore#//ENotAType")));
    }

    @Test void duplicateInheritanceAndOppositeMutationControls() throws Exception {
        var duplicate = load(Files.readString(SOURCE));
        duplicate.getEClassifiers().get(1).setName(duplicate.getEClassifiers().get(0).getName());
        assertNotEquals(Diagnostic.OK, Diagnostician.INSTANCE.validate(duplicate).getSeverity());
        var cyclic = load(Files.readString(SOURCE));
        EClass agent = (EClass) cyclic.getEClassifier("Agent");
        agent.getESuperTypes().add(agent);
        assertNotEquals(Diagnostic.OK, Diagnostician.INSTANCE.validate(cyclic).getSeverity());
        var containment = load(Files.readString(SOURCE));
        EReference roles = (EReference) ((EClass) containment.getEClassifier("Agent")).getEStructuralFeature("roles");
        roles.setContainment(true); roles.getEOpposite().setContainment(true);
        assertNotEquals(Diagnostic.OK, Diagnostician.INSTANCE.validate(containment).getSeverity());
    }

    @Test void unsafeEntityIsRejectedBeforeLoading() {
        assertThrows(IllegalArgumentException.class, () -> load("<!DOCTYPE x [<!ENTITY y SYSTEM 'file:///secret'>]><x/>"));
    }
}
