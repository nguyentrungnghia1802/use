package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionMappingSpecification.*;

class AuctionSourceSelectorTest {
    private static final AuctionCaseBundle BUNDLE = AuctionCaseBundle.read(Path.of("examples/auction"));
    private static final AuctionMappingSpecification SPEC = new AuctionMappingXmlLoader().load(BUNDLE);
    private static ModelSnapshot source;
    private final AuctionSourceSelector selector = new AuctionSourceSelector();
    @TempDir Path temporary;
    @BeforeAll static void officialSnapshot() throws Exception {
        Path jcm = Path.of("examples/auction/auction.jcm").toAbsolutePath();
        source = new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm), jcm);
    }

    @Test void executesAllEighteenSelectorsInOrderWithExactCountsAndOfficialSourceAnchors() {
        var result = selector.execute(SPEC, BUNDLE, source);
        int[] counts = {1,1,5,5,2,1,1,2,2,0,1,3,2,1,1,4,1,1};
        assertEquals(18, result.selections().size());
        for (int i = 0; i < counts.length; i++) assertEquals(counts[i], result.require(SPEC.selectors().get(i).attr("id")).size());
        assertEquals("sequence", result.require("sel-sequence").getFirst().values().get("operator"));
        assertEquals(List.of("start", "bid", "decide"), result.require("sel-sequence-goals").stream().map(r -> r.values().get("id")).toList());
        assertEquals("auction_env.AuctionArtifact", result.require("sel-create").getFirst().values().get("className"));
        assertTrue(result.require("sel-class").getFirst().line() > 0);
        assertFalse(result.sourceHash().isBlank()); assertEquals(BUNDLE.hash(), result.bundleHash());
        assertThrows(UnsupportedOperationException.class, () -> result.selections().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.require("sel-agents").getFirst().values().clear());
    }

    @Test void ignoresCommentedDeclarationsButPreservesCommentMarkersInsideQuotedValues() {
        String jcm = text("src-jcm") + "\n/* agent fake : ghost.asl */\n// agent phantom : ghost.asl\n";
        assertEquals(5, selector.select(SPEC.requireId("sel-agents"), bytes(jcm), SPEC.namespaces()).size());
        String quoted = text("src-jcm").replace("flight_ticket(paris,athens,date(15,12,2015))", "flight//ticket /* literal */");
        var rows = selector.select(SPEC.requireId("sel-requests"), bytes(quoted), SPEC.namespaces());
        assertEquals("flight//ticket /* literal */", rows.getFirst().values().get("service"));
        assertEquals(5, selector.select(SPEC.requireId("sel-agents"), bytes(jcm.replace("\r\n", "\n")), SPEC.namespaces()).size());
    }

    @Test void countAndNamespaceFailuresCannotProducePartialSuccessfulTransformation() {
        String extra = text("src-artifact").replace("// observable properties", "defineObsProperty(\"unexpected\", 0);");
        assertReason("SELECTOR_COUNT_MISMATCH", () -> selector.select(SPEC.requireId("sel-observables"), bytes(extra), SPEC.namespaces()));
        assertReason("SELECTOR_COUNT_MISMATCH", () -> selector.select(SPEC.requireId("sel-agents"), bytes("agent one : one.asl"), SPEC.namespaces()));
        assertReason("SELECTOR_COUNT_MISMATCH", () -> selector.select(SPEC.requireId("sel-os-root"), BUNDLE.require("src-os").bytes(), Map.of("os", "urn:wrong")));
    }
    @Test void aSecondOrganizationIsARejectedSourceVariantRatherThanAnImplicitFirstMatch() {
        String original=text("src-jcm");
        String two=original.replace("    organisation aorg : auction-os.xml {","    organisation other : auction-os.xml { }\n    organisation aorg : auction-os.xml {");
        assertNotEquals(original,two);
        assertReason("SELECTOR_COUNT_MISMATCH",()->selector.select(SPEC.requireId("sel-org"),bytes(two),SPEC.namespaces()));
    }

    @Test void requiredValuesAndSetsAreExecutedRatherThanOnlyParsed() throws Exception {
        String xml = text("case-rules").replace("values=\"auctioneer participant\"", "values=\"auctioneer stranger\"");
        var changedBundle = changedBundle(xml);
        var changed = new AuctionMappingXmlLoader().load(changedBundle);
        assertReason("REQUIRE_SET_MISMATCH", () -> selector.execute(changed, changedBundle, source));
        String wrong = text("case-rules").replace("expectedMax=\"300\"", "expectedMax=\"299\"");
        var boundBundle = changedBundle(wrong);
        assertReason("POPULATION_BOUND_MISMATCH", () -> selector.execute(new AuctionMappingXmlLoader().load(boundBundle), boundBundle, source));
        assertReason("SPECIFICATION_BUNDLE_MISMATCH", () -> selector.execute(changed, BUNDLE, source));
    }

    @Test void rejectsUnrelatedOfficialSnapshotBeforeUsingItsSourceFacts() {
        var empty = new ModelSnapshot("wrong", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of());
        assertReason("OFFICIAL_SOURCE_REQUIRED", () -> selector.execute(SPEC, BUNDLE, empty));
    }
    @Test void officialResolvedOsSourceNeedsTheExactPinnedBundlePathRatherThanABasename()throws Exception {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        for(String reference:List.of("file:src/org/auction-os.xml","file:other/auction-os.xml","file:src/org/../other/auction-os.xml","file:/external/auction-os.xml")) {
            var tree=json.readValue(org.jacamo.bridge.contract.CanonicalJson.encode(org.jacamo.bridge.contract.ContractPayloads.model(source)),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
            var semantic=(Map<String,Object>)tree.get("semanticContract");var organizations=(List<?>)semantic.get("organizationDeployments");((Map<String,Object>)organizations.getFirst()).put("source",reference);
            var observed=org.jacamo.bridge.contract.ContractPayloads.model(tree);
            if(reference.equals("file:src/org/auction-os.xml"))assertEquals(18,selector.execute(SPEC,BUNDLE,observed).selections().size());
            else assertReason("OFFICIAL_DEPLOYMENT_MISMATCH",()->selector.execute(SPEC,BUNDLE,observed));
        }
    }

    @Test void regexWorkIsBoundedWithoutUninterruptibleWorkerThreads() {
        var at = new Location("budget.xml", 1, 1, "budget");
        var pattern = new Node("pattern", Map.of(), "a*a*a*a*a*b", List.of(), at);
        var select = new Node("select", Map.of("id", "budget", "sourceRef", "src-jcm", "syntax", "regex", "expectedCount", "1"), "", List.of(pattern), at);
        assertReason("REGEX_WORK_BUDGET", () -> selector.select(select, bytes("a".repeat(4096)), Map.of()));
    }

    private static String text(String id) { return new String(BUNDLE.require(id).bytes(), StandardCharsets.UTF_8); }
    private AuctionCaseBundle changedBundle(String xml) throws Exception {
        Path directory = Files.createTempDirectory(temporary, "authored-rule-variant-");
        Path original = Path.of("examples/auction");
        try (var paths = Files.walk(original)) {
            for (Path path : paths.toList()) {
                Path destination = directory.resolve(original.relativize(path));
                if (Files.isDirectory(path)) Files.createDirectories(destination); else Files.copy(path, destination);
            }
        }
        Files.write(directory.resolve("auction_jacamo_to_csl_rules.xml"), bytes(xml));
        var manifest = new LinkedHashMap<>(CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(directory.resolve("case-manifest.json")))));
        var assets = new ArrayList<Object>((List<?>) manifest.get("assets"));
        for (int i = 0; i < assets.size(); i++) {
            var asset = new LinkedHashMap<>(CanonicalJson.object(assets.get(i)));
            if (!asset.get("id").equals("case-rules")) continue;
            asset.put("sha256", org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(bytes(xml))); assets.set(i, asset);
        }
        manifest.put("assets", assets); Files.write(directory.resolve("case-manifest.json"), CanonicalJson.encode(manifest));
        return AuctionCaseBundle.read(directory);
    }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static void assertReason(String reason, org.junit.jupiter.api.function.Executable operation) {
        var error = assertThrows(IllegalArgumentException.class, operation);
        assertTrue(error.getMessage().contains(reason), error.getMessage());
    }
}
