package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.constraint.ConstraintExtractor;
import org.tzi.use.plugins.jacamo.evidence.EvidenceNormalizer;
import org.tzi.use.plugins.jacamo.evidence.EvidenceSourceCommit;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.MappingLoader;
import org.tzi.use.plugins.jacamo.mapping.TransformationPlanner;
import org.tzi.use.plugins.jacamo.materialization.InstancePlanner;
import org.tzi.use.plugins.jacamo.materialization.DirectUseBackend;
import org.tzi.use.plugins.jacamo.materialization.TextBackend;
import org.tzi.use.plugins.jacamo.ocl.OclGenerator;
import org.tzi.use.plugins.jacamo.ocl.OclProfileLoader;
import org.tzi.use.plugins.jacamo.trace.TraceBuilder;
import org.tzi.use.plugins.jacamo.trace.TraceStore;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;
import org.tzi.use.plugins.jacamo.verification.ConstraintRegistry;
import org.tzi.use.plugins.jacamo.verification.DefaultVerificationService;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.plugins.jacamo.verification.VerificationReportExporter;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationProfileLoader;
import org.tzi.use.plugins.jacamo.verification.profile.VerificationSemanticLayer;
import org.tzi.use.uml.ocl.value.BooleanValue;

class GoldenPipelineTest {
    @TempDir Path temporary;

    @Test
    void auctionArtifactsMatchReviewedGoldenDigests() throws Exception {
        String sourceCommit = EvidenceSourceCommit.verify(Path.of(".."), EvidenceSourceCommit.PHASE14_INPUTS);
        Path fixture = Path.of("src/test/resources/auction");
        Path project = Files.createDirectory(temporary.resolve("auction")).toRealPath();
        // Fixed LF fixture bytes keep provenance hashes independent of Git autocrlf.
        try (var files = Files.walk(fixture)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path target = project.resolve(fixture.relativize(file));
                Files.createDirectories(target.getParent());
                Files.writeString(target, Files.readString(file).replace("\r\n", "\n"));
            }
        }
        var imported = new StaticProjectImporter().importProject(project.resolve("auction.jcm"));
        assertTrue(imported.success(), () -> imported.diagnostics().toString());
        var semantic = imported.model();
        var mapping = new MappingLoader().loadCanonical(Path.of("."));
        var structure = new VerificationSemanticLayer().apply(new TransformationPlanner().plan(semantic, mapping),
                new VerificationProfileLoader().loadV1()).transformation();
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        var artifacts = new TextBackend().generate("auction", structure, instances);
        var constraints = new ConstraintExtractor().extract(semantic, structure, Map.of());
        var profiles = new OclProfileLoader();
        var ocl = new OclGenerator().generate("auction", structure, constraints,
                List.of(profiles.loadCore(), profiles.loadCase(project, Path.of("verification/auction.ocl"))));
        Path output = Files.createDirectories(Path.of("target/phase14-auction-evidence/offline"));
        Files.deleteIfExists(output.resolve("semantic-summary.json"));
        var trace = new TraceBuilder().build(semantic, mapping, structure, instances);
        new TraceStore().write(output.resolve("trace.json"), trace);
        var golden = new Properties();
        try (var input = Files.newInputStream(Path.of("src/test/resources/golden/auction-sha256.properties"))) {
            golden.load(input);
        }
        Map<String, String> actual = Map.of(
                "auction.use", artifacts.useModel(), "auction.cmd", artifacts.initialCommands(),
                "auction-ocl.use", ocl.useModel(),
                "trace.json", Files.readString(output.resolve("trace.json")),
                "diagnostics.txt", imported.diagnostics().toString());
        for (var entry : actual.entrySet()) {
            Files.writeString(output.resolve(entry.getKey()),
                    entry.getKey().endsWith(".json")
                            ? EvidenceNormalizer.normalizeJson(entry.getValue(), project, "<auction>")
                            : EvidenceNormalizer.normalizeText(entry.getValue(), project, "<auction>"));
        }
        Files.writeString(output.resolve("translated-ocl-provenance.txt"),
                EvidenceNormalizer.normalizeText(ocl.provenanceManifest(), project, "<auction>"));
        Files.writeString(output.resolve("core.ocl"), profiles.loadCore().content());
        var caseProfile = profiles.loadCase(project, Path.of("verification/auction.ocl"));
        Files.writeString(output.resolve("case.ocl"), caseProfile.content());

        DirectUseBackend.Result direct = new DirectUseBackend().materialize(
                new TextBackend.GeneratedArtifacts(ocl.useModel(), artifacts.initialCommands()), instances);
        var registry = ConstraintRegistry.load(direct.system().model(), ocl,
                List.of(ConstraintRegistry.profile(ConstraintOrigin.CORE, profiles.loadCore()),
                        ConstraintRegistry.profile(ConstraintOrigin.CASE, caseProfile)));
        var verifier = new DefaultVerificationService();
        var exporter = new VerificationReportExporter();
        var positive = verifier.runFullVerification(direct.system(), registry, trace);
        assertTrue(positive.results().stream().noneMatch(result -> result.outcome() == VerificationOutcome.FAIL),
                positive.results().toString());
        Files.writeString(output.resolve("verification-pass.json"),
                EvidenceNormalizer.normalizeJson(exporter.toJson(positive), project, "<auction>"));
        var artifact = direct.system().state().allObjects().stream()
                .filter(object -> object.cls().name().equals("AuctionArtifact")).findFirst().orElseThrow();
        artifact.state(direct.system().state()).setAttributeValue(
                artifact.cls().attribute("open", true), BooleanValue.get(false));
        var negative = verifier.runFullVerification(direct.system(), registry, trace);
        assertTrue(negative.results().stream().anyMatch(result -> result.outcome() == VerificationOutcome.FAIL
                && registry.byId(result.constraintId()) != null
                && registry.byId(result.constraintId()).name().equals("AuctionInitiallyOpen")),
                negative.results().toString());
        Files.writeString(output.resolve("verification-fail.json"),
                EvidenceNormalizer.normalizeJson(exporter.toJson(negative), project, "<auction>"));

        Path verificationProfile = Path.of("src/main/resources/org/tzi/use/plugins/jacamo/verification/"
                + "jacamo-verification-profile-v1.json");
        Path ecore = Path.of("Core/Metamodel/JaCaMo-Metamodel.ecore");
        Path mappingFile = Path.of("Core/Mapping/jacamo-use-mapping-v1.json");
        Path mappingFreezeManifest = Path.of("Core/Mapping/freeze-manifest.json");
        Path coreOcl = Path.of("src/main/resources/org/tzi/use/plugins/jacamo/ocl/jacamo-core.ocl");
        var summary = new ObjectMapper().createObjectNode()
                .put("schemaVersion", "1.0.0")
                .put("artifactKind", "PHASE_14_EVIDENCE_MANIFEST")
                .put("hashPolicy", "LF_NORMALIZED_UTF8")
                .put("projectId", semantic.projectId())
                .put("projectEntry", "<auction>/auction.jcm")
                .put("sourceRevisionPolicy", "CHECKED_IN_FIXTURE_CONTENT_HASHES")
                .put("repositoryBaseCommit", sourceCommit)
                .put("auctionJcmSha256", sha256(project.resolve("auction.jcm")))
                .put("artifactSourceSha256", sha256(project.resolve("src/env/auction/AuctionArtifact.java")))
                .put("agentSourceSha256", sha256(project.resolve("src/agt/auctioneer.asl")))
                .put("organisationSourceSha256", sha256(project.resolve("src/org/auction.xml")))
                .put("useVersion", "7.5.0")
                .put("jasonVersion", "3.3.0")
                .put("cartagoVersion", "3.1")
                .put("moiseVersion", "1.1")
                .put("pluginVersion", "7.5.0")
                .put("javaVersion", System.getProperty("java.version"))
                .put("sourceFiles", semantic.sourceIndex().size())
                .put("semanticElements", semantic.elements().size())
                .put("generatedClasses", structure.classes().size())
                .put("generatedObjects", instances.objects().size())
                .put("diagnostics", imported.diagnostics().size())
                .put("positiveOutcome", "PASS")
                .put("negativeOutcome", "EXPECTED_FAIL");
        summary.put("ecoreSha256", sha256(ecore));
        summary.put("mappingSha256", sha256(mappingFile));
        summary.put("mappingFreezeManifestSha256", sha256(mappingFreezeManifest));
        summary.put("coreOclSha256", sha256(coreOcl));
        summary.put("caseOclSha256", sha256(project.resolve("verification/auction.ocl")));
        summary.putObject("verificationProfile")
                .put("path", "use-plugin/src/main/resources/org/tzi/use/plugins/jacamo/verification/"
                        + "jacamo-verification-profile-v1.json")
                .put("sha256", sha256(verificationProfile));
        summary.putObject("bindings")
                .put("required", false)
                .put("reason", "Auction static references resolve exactly; no explicit binding file is needed");
        summary.putArray("assumptions")
                .add("USE is a verification mirror; JaCaMo remains the execution engine")
                .add("AuctionOpenForBid is an authored case-study policy over observable open state; the Artifact body does not enforce it")
                .add("PositiveBidAmount is authored OCL mirroring the explicit Artifact body failed boundary")
                .add("LF-normalized source hashes make fixture provenance independent of Git autocrlf");
        summary.putArray("limitations")
                .add("Arbitrary Java implementation effects are not inferred into OCL")
                .add("Moise norms are imported structurally and are not auto-collapsed into OCL obligations")
                .add("The evidence covers the checked-in Auction fixture and pinned runtime versions only");
        summary.putObject("provenance")
                .put("translatedGuardOcl", "CARTAGO_GUARD_EXACT_AMOUNT_GREATER_THAN_OR_EQUAL_TO_ZERO")
                .put("authoredPositiveAmountOcl", "OUR_EXT_SOURCE_BODY_FAILED_BOUNDARY")
                .put("authoredOpenAuctionOcl", "OUR_EXT_CASE_STUDY_POLICY")
                .put("moiseStaticModel", "CHECKED_IN_XML_STRUCTURAL_IMPORT_ONLY");
        var semanticSummary = summary.putObject("semanticSummary");
        var dimensionCounts = semanticSummary.putObject("dimensionCounts");
        for (var dimension : org.tzi.use.plugins.jacamo.semantic.Dimension.values())
            dimensionCounts.put(dimension.name(), semantic.elements().stream()
                    .filter(element -> element.id().dimension() == dimension).count());
        semanticSummary.put("referenceCount", semantic.elements().stream()
                .mapToInt(element -> element.references().size()).sum());
        semanticSummary.put("unresolvedReferenceCount", semantic.elements().stream()
                .flatMap(element -> element.references().stream()).filter(reference -> reference.targetId() == null)
                .count());
        var elements = semanticSummary.putArray("elements");
        semantic.elements().stream().sorted(java.util.Comparator.comparing(element -> element.id().value()))
                .forEach(element -> elements.addObject()
                        .put("semanticId", element.id().value())
                        .put("kind", element.kind().name())
                        .put("dimension", element.id().dimension().name())
                        .put("name", element.name())
                        .put("referenceCount", element.references().size()));
        summary.putArray("evidenceArtifacts")
                .add("offline/auction-ocl.use").add("offline/auction.cmd").add("offline/auction.use")
                .add("offline/case.ocl").add("offline/core.ocl").add("offline/diagnostics.txt")
                .add("offline/manifest.json").add("offline/trace.json")
                .add("offline/translated-ocl-provenance.txt").add("offline/verification-fail.json")
                .add("offline/verification-pass.json").add("runtime/event-log.json")
                .add("runtime/scenario-summary.json").add("runtime/verification-reports.json");
        var offlineHashes = summary.putObject("offlineArtifactSha256");
        for (String name : List.of("auction-ocl.use", "auction.cmd", "auction.use", "case.ocl", "core.ocl",
                "diagnostics.txt", "trace.json", "translated-ocl-provenance.txt", "verification-fail.json",
                "verification-pass.json")) offlineHashes.put(name, sha256(output.resolve(name)));
        Files.writeString(output.resolve("manifest.json"),
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(summary) + "\n");
        Path manifestPath = output.resolve("manifest.json");
        assertTrue(Files.exists(manifestPath), "Phase 14 evidence must include an explicit manifest");
        var manifest = new ObjectMapper().readTree(manifestPath.toFile());
        assertEquals("LF_NORMALIZED_UTF8", manifest.path("hashPolicy").asText());
        assertEquals(64, manifest.path("ecoreSha256").asText().length());
        assertEquals(64, manifest.path("mappingSha256").asText().length());
        Set<String> declaredArtifacts = new LinkedHashSet<>();
        manifest.path("evidenceArtifacts").forEach(node -> declaredArtifacts.add(node.asText()));
        assertEquals(Set.of(
                "offline/auction-ocl.use", "offline/auction.cmd", "offline/auction.use",
                "offline/case.ocl", "offline/core.ocl", "offline/diagnostics.txt",
                "offline/manifest.json", "offline/trace.json", "offline/translated-ocl-provenance.txt",
                "offline/verification-fail.json", "offline/verification-pass.json",
                "runtime/event-log.json", "runtime/scenario-summary.json", "runtime/verification-reports.json"),
                declaredArtifacts);
        assertFalse(manifest.path("assumptions").isEmpty());
        assertFalse(manifest.path("limitations").isEmpty());
        assertEquals(semantic.elements().size(), manifest.path("semanticSummary").path("elements").size(),
                "the manifest must carry the semantic inventory, not just aggregate counts");
        assertTrue(manifest.path("semanticSummary").path("dimensionCounts").path("AGENT").asInt() > 0);
        assertTrue(manifest.path("semanticSummary").path("dimensionCounts").path("ENVIRONMENT").asInt() > 0);
        assertTrue(manifest.path("semanticSummary").path("dimensionCounts").path("ORGANISATION").asInt() > 0);
        assertTrue(manifest.path("semanticSummary").path("referenceCount").asInt() > 0);
        assertAll(actual.keySet().stream().sorted().map(name -> () -> {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readString(output.resolve(name)).getBytes(StandardCharsets.UTF_8)));
            assertEquals(golden.getProperty(name), digest, name);
        }));
    }

    private String sha256(Path path) throws Exception {
        byte[] normalized = Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized));
    }

}
