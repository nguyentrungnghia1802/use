package org.tzi.use.plugins.jacamo.semantics.auction;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.semantic.engine.api.CslModelFingerprint;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionUseModelAdapter;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.sys.MSystem;
import static org.junit.jupiter.api.Assertions.*;

class AuctionNativeSchemaTest {
    private Use75ModelBinding compile() throws Exception {
        var source = new CslFrontend().compile(Files.readString(Path.of("examples/auction/auction_csl.csl")), "auction.csl").requireModel();
        return new AuctionUseModelAdapter().compile(source);
    }
    @Test void compilesUnchangedPopulationPredicatesWithTypedDerivedNavigations() throws Exception {
        var binding = compile();
        assertTrue(binding.classifier("auctionGroup").attribute("auctioneer", true).isDerived());
        assertTrue(binding.classifier("auctionGroup").attribute("participant", true).isDerived());
        assertEquals("Set(auctioneer)", binding.classifier("auctionGroup").attribute("auctioneer", true).type().toString());
        assertEquals("478ce44d3226039d7b5dafe372f0d74e3cca2e04f35d67ac870e7ec66f2bc720", CslModelFingerprint.of(binding.cslModel()));
        assertTrue(binding.cslModel().relations().isEmpty());
    }
    @Test void permitsAndDetectsZeroOneTwoAuctioneersInsteadOfRejectingObservedReality() throws Exception {
        for (int count = 0; count <= 2; count++) {
            var binding = compile(); var system = new MSystem(binding.model()); var api = UseSystemApi.create(system, false);
            api.createObject("auctionGroup", "group");
            for (int i = 0; i < count; i++) occurrence(api, "auctioneer", "r" + i, "group");
            assertEquals(BooleanValue.get(count == 1), evaluate(binding, system, "AuctioneerPopulation"));
        }
    }
    @Test void participantBoundsAreActualDistinctEnactmentsIncludingViolatingPopulation() throws Exception {
        for (int count : new int[]{0,300,301}) {
            var binding = compile(); var system = new MSystem(binding.model()); var api = UseSystemApi.create(system, false);
            api.createObject("auctionGroup", "group");
            for (int i = 0; i < count; i++) occurrence(api, "participant", "r" + i, "group");
            assertEquals(BooleanValue.get(count <= 300), evaluate(binding, system, "ParticipantPopulation"));
        }
    }
    @Test void roleNavigationIsIsolatedByActualContextAndScopeDeletionDoesNotDisposeArtifact() throws Exception {
        var binding = compile(); var system = new MSystem(binding.model()); var api = UseSystemApi.create(system, false);
        api.createObject("AuctionOrg", "org1"); api.createObject("AuctionOrg", "org2");
        api.createObject("auctionGroup", "g1"); api.createObject("auctionGroup", "g2"); api.createObject("Auction", "a1");
        api.createLink("CslContextScopes", "org1", "g1"); api.createLink("CslContextScopes", "org2", "g2");
        api.createLink("CslEntityScopes", "org1", "a1");
        occurrence(api, "auctioneer", "first", "g1"); occurrence(api, "auctioneer", "second", "g1");
        assertEquals(BooleanValue.FALSE, evaluate(binding, system, "AuctioneerPopulation"));
        api.deleteLink("InContext", new String[]{"g1", "second"}); api.createLink("InContext", "g2", "second");
        assertEquals(BooleanValue.TRUE, evaluate(binding, system, "AuctioneerPopulation"));
        api.deleteObject("org1"); assertNotNull(system.state().objectByName("a1")); assertNotNull(system.state().objectByName("g1"));
    }
    @Test void nativeExportRecompilesWithSameDerivedExpressionsAndScopeMetadata() throws Exception {
        var binding = compile(); var text = new StringWriter();
        binding.model().processWithVisitor(new Use75ModelPrinter(new PrintWriter(text,true)));
        var errors = new StringWriter();
        var model = USECompiler.compileSpecification(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)), "auction.use", URI.create("memory:/auction.use"), new PrintWriter(errors,true), new ModelFactory());
        assertNotNull(model, errors.toString());
        assertTrue(model.getClass("auctionGroup").attribute("auctioneer",true).isDerived());
        var original = binding.classifier("auctionGroup").attribute("auctioneer",true);
        var reloaded = model.getClass("auctionGroup").attribute("auctioneer",true);
        assertEquals(original.getDeriveExpression().toString(), reloaded.getDeriveExpression().toString());
        assertEquals(original.getAnnotation(Use75CslModelBackend.HELPER).getValues(), reloaded.getAnnotation(Use75CslModelBackend.HELPER).getValues());
        assertEquals(binding.model().associations().size(), model.associations().size());
        assertEquals(binding.model().classInvariants().size(), model.classInvariants().size());
    }
    private static void occurrence(UseSystemApi api, String role, String id, String context) throws Exception {
        api.createObject("AtomicAgent", "p" + id); api.createObject(role, id);
        api.createLink("Plays", "p" + id, id); api.createLink("InContext", context, id);
    }
    private static org.tzi.use.uml.ocl.value.Value evaluate(Use75ModelBinding binding, MSystem system, String invariant) {
        var condition = binding.model().classInvariants().stream().filter(i -> i.name().equals(invariant)).findFirst().orElseThrow();
        return new Evaluator().eval(condition.expandedExpression(), system.state());
    }
}
