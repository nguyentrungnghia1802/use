package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.junit.jupiter.api.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.semantic.engine.api.CslModelFingerprint;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import static org.junit.jupiter.api.Assertions.*;

class AuctionCslRuleExecutorTest {
    private static final AuctionCaseBundle BUNDLE = AuctionCaseBundle.read(Path.of("examples/auction"));
    private static final AuctionMappingSpecification SPEC = new AuctionMappingXmlLoader().load(BUNDLE);
    private static AuctionSelectorResult rows;
    @BeforeAll static void source() throws Exception {
        Path jcm = Path.of("examples/auction/auction.jcm").toAbsolutePath();
        ModelSnapshot source = new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm), jcm);
        rows = new AuctionSourceSelector().execute(SPEC, BUNDLE, source);
    }

    @Test void executesAllEightRulesIntoOriginalCslModelMatchingBothOracles() {
        var result = new AuctionCslRuleExecutor().generate(SPEC, BUNDLE, rows);
        var model = result.model();
        assertEquals(1, model.entities().size()); assertEquals(2, model.roles().size()); assertEquals(2, model.orgContexts().size());
        assertEquals(6, model.entities().getFirst().attributes().size()); assertEquals(2, model.invariants().size()); assertTrue(model.relations().isEmpty());
        var oracle = new CslFrontend().compile(new String(BUNDLE.require("target-csl").bytes(), StandardCharsets.UTF_8), "oracle").requireModel();
        assertEquals(CslModelFingerprint.of(oracle), result.fingerprint());
        assertEquals("478ce44d3226039d7b5dafe372f0d74e3cca2e04f35d67ac870e7ec66f2bc720", result.fingerprint());
        assertEquals(result.fingerprint(), CslModelFingerprint.of(new CslFrontend().compile(result.generatedSource(), "generated").requireModel()));
        assertEquals(Set.of("M01", "M02", "M03", "M04", "M05", "M06", "M07", "M08"), result.traces().stream().map(AuctionCslRuleExecutor.Trace::ruleId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(13, result.traces().size()); assertEquals(12, result.omissions().size());
        assertTrue(result.traces().stream().allMatch(t -> !t.row().sourceDigest().isBlank() && !t.target().isBlank()));
        assertThrows(UnsupportedOperationException.class, () -> result.traces().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.omissions().clear());
    }

    @Test void doesNotCreateAnAgentBidObserverEntityOrInventABoundedBidDefault() {
        var result = new AuctionCslRuleExecutor().generate(SPEC, BUNDLE, rows);
        var entity = result.model().findEntity("Auction").orElseThrow();
        assertTrue(entity.attributes().stream().filter(a -> a.name().equals("best_bid")).findFirst().orElseThrow().defaultValue().isEmpty());
        assertEquals("'no_winner'", entity.attributes().stream().filter(a -> a.name().equals("currentWinner")).findFirst().orElseThrow().defaultValue().orElseThrow());
        assertFalse(result.generatedSource().contains("entity Bid")); assertFalse(result.generatedSource().contains("entity Agent"));
        assertFalse(result.generatedSource().contains("AuctionMonitor")); assertFalse(result.generatedSource().contains("AuctionObligation"));
        assertTrue(result.omissions().stream().allMatch(o -> o.disposition().equals("INTENTIONAL_M1_OMISSION_NOT_RUNTIME_VERDICT")));
    }

    @Test void rejectsForeignSelectorOwnershipAndDoesNotReadAnOracleInsteadOfExecutingRules() {
        var foreign = new AuctionSelectorResult("0".repeat(64), rows.specificationHash(), rows.sourceHash(), rows.selections(), rows.qualifications());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new AuctionCslRuleExecutor().generate(SPEC, BUNDLE, foreign)).getMessage().contains("SELECTOR_OWNERSHIP_MISMATCH"));
        var selections = new LinkedHashMap<>(rows.selections());
        var changed = new ArrayList<>(rows.require("sel-current-winner")); var original = changed.getFirst();
        var values = new LinkedHashMap<>(original.values()); values.put("name", "wrongCandidate");
        changed.set(0, new AuctionSelectorResult.Row(original.selectorId(), original.sourceId(), original.sourceDigest(), values, original.line(), original.column(), original.locator(), original.matched()));
        selections.put("sel-current-winner", changed);
        var mutated = new AuctionSelectorResult(rows.bundleHash(), rows.specificationHash(), rows.sourceHash(), selections, rows.qualifications());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new AuctionCslRuleExecutor().generate(SPEC, BUNDLE, mutated)).getMessage().contains("M1_TARGET_ORACLE_MISMATCH"));
    }

    @Test void requiresCompleteOmissionInventoryAndPreservesChangedDocumentationWithoutAVerdict() {
        String xml = new String(BUNDLE.require("case-rules").bytes(), StandardCharsets.UTF_8);
        String missing = xml.replaceFirst("(?s)<omit id=\"X01\".*?</omit>", "");
        var parsed = new AuctionMappingXmlLoader().parse(missing.getBytes(StandardCharsets.UTF_8), "missing-omission.xml");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new AuctionMappingValidator().validate(parsed, BUNDLE)).getMessage().contains("OMISSION_INVENTORY_MISMATCH"));
        String unknown = xml.replace("<omit id=\"X01\"", "<unsupportedOmission id=\"X01\"").replaceFirst("</omit>", "</unsupportedOmission>");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> new AuctionMappingXmlLoader().parse(unknown.getBytes(StandardCharsets.UTF_8), "unknown-omission.xml")).getMessage().contains("ELEMENT_OR_ATTRIBUTE_SHAPE_UNSUPPORTED"));
        String changed = xml.replace("reason=\"Functional allocation is not passive domain structure.\"", "reason=\"Changed documentation, no runtime assertion.\"");
        var documented = new AuctionMappingXmlLoader().parse(changed.getBytes(StandardCharsets.UTF_8), "documented.xml");
        new AuctionMappingValidator().validate(documented, BUNDLE);
        assertEquals("Changed documentation, no runtime assertion.", documented.requireId("X01").attr("reason"));
    }
}
