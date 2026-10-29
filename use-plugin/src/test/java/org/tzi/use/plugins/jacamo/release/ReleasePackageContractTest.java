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
    @Test void canonicalAuctionHasItsOwnCompletePinnedInventoryAndKeepsTheLegacyToyPath()throws Exception {
        var json=new ObjectMapper();var release=json.readTree(Path.of("release/release-manifest.json").toFile());
        var declared=new java.util.TreeMap<String,String>();for(var entry:release.path("packageEntries"))assertTrue(declared.put(entry.path("path").asText(),entry.path("source").asText())==null);
        assertEquals("src/test/resources/auction/auction.jcm",declared.get("examples/auction/auction.jcm"),"Preserve the old native toy under its existing compatibility path");
        assertEquals("src/test/resources/auction-reference/case-manifest.json",declared.get("reference/auction/case-manifest.json"),"The actual canonical Auction must be separately shipped");
        var manifest=json.readTree(Path.of("src/test/resources/auction-reference/case-manifest.json").toFile());assertEquals(21,manifest.path("assets").size(),"Retired case-rules XML is no longer distributed");
        for(var asset:manifest.path("assets"))assertTrue(!"case-rules".equals(asset.path("id").asText()));
        for(var asset:manifest.path("assets")) {
            String relative=asset.path("path").asText();assertEquals("src/test/resources/auction-reference/"+relative,declared.get("reference/auction/"+relative),relative);
            assertEquals(asset.path("sha256").asText(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("src/test/resources/auction-reference").resolve(relative)))));
        }
        assertEquals("src/test/resources/auction-reference/auction-mapping.xml",declared.get("reference/auction/auction-mapping.xml"),"The declarative Auction mapping XML must be shipped for generic consumers");
        assertEquals("src/test/resources/auction-reference/README.md",declared.get("reference/auction/README.md"));
        String readme=Files.readString(Path.of("src/test/resources/auction-reference/README.md"));
        for(String required:java.util.List.of("AUCTION_OBSERVED_V1","RuntimeJournalMaxBytes","268435456","Import","Original iStar history","INCOMPLETE","auction-semantic"))assertTrue(readme.contains(required),required);
        for(String required:java.util.List.of("P-IADM-01.md","P-IADM-01.diff","single-owner-context-v1.md"))assertTrue(declared.containsKey("provenance/"+required),required);
    }
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
            "bridge/lib/jacamo-bridge-contract-1.0.0.jar",
            "bridge/lib/jacamo-bridge-jacamo-1.0.0.jar",
            "lib/plugins/use-jacamo-plugin-1.0.1.jar",
            "release/v2-freeze-manifest.json",
            "ocl/jacamo-core-v2-manifest.json",
            "ocl/jacamo-core-v2.ocl",
            "profiles/jacamo-verification-profile-v2.json",
            "release-manifest.json",
            "runtime/jacamo-use-runtime-mapping-v2.json",
            "runtime/runtime-mapping-v2.schema.json",
            "schemas/binding-v1.schema.json",
            "schemas/runtime-event-v1.schema.json",
            "schemas/trace-v1.schema.json",
            "tools/jacamo-bridge.ps1",
            "docs/use4goal-run.md",
            "docs/semantic-replay-v3.md",
            "docs/semantic-compatibility-routing.md",
            "profiles/use4goal-observed-v1.json",
            "licenses/ANTLR4-4.9.3.txt",
            "licenses/use4Goal-GPL-3.0.txt",
            "provenance/use4goal-provenance.json",
            "provenance/README.md",
            "provenance/host-compatibility-patches.md",
            "provenance/P-USE-PRINT-02.md",
            "provenance/P-CTX-01.diff",
            "examples/csl-counter/counter.jcm",
            "examples/csl-counter/mapping.json",
            "examples/csl-counter/README.md",
            "examples/csl-counter/src/agt/alice.asl",
            "examples/csl-counter/src/agt/bob.asl",
            "examples/csl-counter/src/env/demo/counter/Counter.java",
            "examples/csl-counter/src/org/organization.xml");

    @Test
    void releaseManifestPinsVersionAndCompletePackageInventory() throws Exception {
        Path root = Path.of(".").toRealPath();
        Path manifestPath = root.resolve("release/release-manifest.json");
        assertTrue(Files.isRegularFile(manifestPath), "release manifest is required before packaging");

        JsonNode manifest = new ObjectMapper().readTree(manifestPath.toFile());
        assertEquals("1.0.1", manifest.path("releaseVersion").asText());
        assertTrue(manifest.path("gitTag").isNull());
        assertEquals("FROZEN_V2_RELEASE_CANDIDATE", manifest.path("status").asText());
        assertEquals("7.5.0", manifest.path("compatibility").path("use").asText());
        assertTrue(manifest.path("compatibility").path("useCommit").asText().matches("[0-9a-f]{40}"));
        assertEquals("1.0.1", manifest.path("compatibility").path("pluginDescriptor").asText());
        assertEquals("3.9.9", manifest.path("compatibility").path("maven").asText());
        assertEquals("SHA-256", manifest.path("integrity").path("packageAlgorithm").asText());
        assertFalse(manifest.path("limitations").isEmpty());
        assertTrue(manifest.path("activeBaseline").path("freeze").asBoolean());
        assertEquals("release/v2-freeze-manifest.json",
                manifest.path("activeBaseline").path("freezeManifest").asText());
        JsonNode frozen = new ObjectMapper().readTree(root.resolve(
                "release/v2-freeze-manifest.json").toFile());
        assertEquals("FROZEN", frozen.path("status").asText());
        assertTrue(frozen.path("freeze").asBoolean());

        Set<String> declared = new LinkedHashSet<>();
        for (JsonNode entry : manifest.path("packageEntries")) {
            String source = entry.path("source").asText();
            String target = entry.path("path").asText();
            assertFalse(source.isBlank(), "every package entry needs a repository source");
            assertFalse(target.isBlank(), "every package entry needs an archive path");
            if (entry.path("generated").asBoolean()) {
                assertTrue(Set.of("target/use-plugin-1.0.1.jar",
                                "../jacamo-bridge-contract/target/jacamo-bridge-contract-1.0.0-SNAPSHOT.jar",
                                "../jacamo-bridge-jacamo/target/jacamo-bridge-jacamo-1.0.0-SNAPSHOT.jar")
                        .contains(source), "generated package input must be a reactor-built JAR: " + source);
            } else {
                assertTrue(Files.isRegularFile(root.resolve(source)), () -> "missing release source: " + source);
            }
            assertTrue(declared.add(target), () -> "duplicate archive path: " + target);
        }
        var required=new LinkedHashSet<>(REQUIRED_ENTRIES);
        var caseManifest=new ObjectMapper().readTree(root.resolve("src/test/resources/auction-reference/case-manifest.json").toFile());
        for(var asset:caseManifest.path("assets"))required.add("reference/auction/"+asset.path("path").asText());
        required.addAll(Set.of("reference/auction/case-manifest.json","reference/auction/README.md","reference/auction/auction-mapping.xml","provenance/P-IADM-01.md","provenance/P-IADM-01.diff","provenance/single-owner-context-v1.md"));
        required.addAll(Set.of("reference/auction/authored-v1/auction_csl.csl","reference/auction/authored-v1/auction_istar_final.istar","reference/auction/authored-v1/auction_constraints_final_fixed.ocl","reference/auction/authored-v1/auction-mapping.xml","reference/auction/authored-v1/provenance.json","reference/auction/authored-v1/README.md","provenance/use4goal-view-provenance.json","licenses/use4Goal-views-GPL-3.0.txt","provenance/P-IADM-02.diff"));
        required.addAll(Set.of("examples/csl-counter/authored-v1/stock.csl","examples/csl-counter/authored-v1/stock.istar","examples/csl-counter/authored-v1/stock.ocl","examples/csl-counter/authored-v1/stock-mapping.xml","examples/csl-counter/authored-v1/README.md"));
        // The explicit confirmation profile ships separately; strict authored oracles stay unchanged.
        required.addAll(Set.of("reference/auction/source-confirmed-v1/auction-mapping.xml","reference/auction/source-confirmed-v1/auction_confirmed.ocl","reference/auction/source-confirmed-v1/README.md"));
        for(String file:java.util.List.of("README.md","auction.jcm","auction.csl","auction.istar","auction-mapping.xml","auction.ocl","build.gradle","src/agt/auction_capabilities.asl","src/org/auction-os.xml","src/env/auction_env/AuctionArtifact.java"))required.add("examples/auction-current/"+file);
        assertEquals(required, declared);
        assertTrue(manifest.path("packageTrees").isMissingNode());
        assertEquals("authenticated-loopback-tcp",manifest.path("runtimeArchitecture").path("transport").asText());
        assertEquals("independent-jacamo-jvm",manifest.path("runtimeArchitecture").path("producer").asText());
    }
}
