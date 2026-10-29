package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.semantic.engine.frontend.CslFrontend;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.sys.MSystem;
import static org.junit.jupiter.api.Assertions.*;
import static org.tzi.use.semantic.engine.use75.NativeOclOutcome.Status.*;

class AuctionOclSpecificationTest {
    private static final AuctionCaseBundle BUNDLE=AuctionCaseBundle.read(Path.of("examples/auction"));
    private final AuctionOclSpecificationLoader loader=new AuctionOclSpecificationLoader();
    private static AuctionMappingSpecification mapping(){return new AuctionMappingXmlLoader().load(BUNDLE);}
    private static String source(){return new String(BUNDLE.require("input-ocl").bytes(),StandardCharsets.UTF_8);}
    private static Use75ModelBinding binding(boolean properAliases) throws Exception {
        var csl=new CslFrontend().compile(new String(BUNDLE.require("target-csl").bytes(),StandardCharsets.UTF_8),"auction.csl").requireModel();
        var binding=new AuctionUseModelAdapter().compile(csl,mapping());var api=new UseModelApi(binding.model());
        // Header-only fixture. R01/O02 must later attest real Java declarations.
        api.createOperation("Auction","start",new String[][]{{properAliases?"service":"p0","String"}},null);
        api.createOperation("Auction","bid",new String[][]{{properAliases?"amount":"p0","Real"}},null);
        api.createOperation("Auction","stop",new String[0][],null);
        return binding;
    }
    @Test void parsesAllEightClausesWithExactRawBodyRangesAndNoInventedBidPost() {
        var specification=loader.load(BUNDLE);
        assertEquals(BUNDLE.require("input-ocl").sha256(),specification.sourceHash());
        assertEquals(8,specification.clauses().size());assertEquals(3,specification.operations().size());
        assertEquals(Set.of("Idle","Opened","RunningForBid","RunningForStop","Closed","SchemeOrder","BidEvidence","WithinDeadline"),
                specification.clauses().stream().map(AuctionOclSpecificationLoader.Clause::name).collect(java.util.stream.Collectors.toSet()));
        for(var clause:specification.clauses()) {
            assertEquals(clause.body(),source().substring(clause.bodyStart(),clause.bodyEnd()));
            assertTrue(clause.line()>0);assertTrue(clause.column()>0);assertFalse(clause.body().isBlank());
        }
        var bid=specification.clauses().stream().filter(c->c.context().operation().equals("bid")).toList();
        assertEquals(1,bid.size());assertEquals(AuctionOclSpecificationLoader.Kind.PRE,bid.getFirst().kind());
        assertThrows(UnsupportedOperationException.class,()->specification.clauses().clear());
    }
    @Test void compilesTenAuthoredPredicatesAtomicallyWithOriginalHostCompiler() throws Exception {
        var binding=binding(true);var prepared=loader.compile(loader.load(BUNDLE),binding,mapping());
        assertEquals(8,prepared.clauses().size());assertEquals(2,prepared.cslInvariants().size());
        assertTrue(binding.model().prePostConditions().isEmpty());assertEquals(3,binding.model().classInvariants().size());
        assertTrue(prepared.clauses().stream().allMatch(c->c.expression().type().isTypeOfBoolean()));
        prepared.install(new UseModelApi(binding.model()));
        assertEquals(5,binding.model().prePostConditions().size());assertEquals(6,binding.model().classInvariants().size());
        assertEquals(0,binding.model().getClass("Auction").operation("bid",false).postConditions().size());
        assertThrows(IllegalArgumentException.class,()->prepared.install(new UseModelApi(binding.model())));
    }
    @Test void malformedDuplicateUnknownContextNonBooleanAndAliasMismatchCannotPartiallyInstall() throws Exception {
        var negatives=Map.of(
                source().replace("pre Idle:","pre :"),"IDENTIFIER_REQUIRED",
                source()+"\ncontext Auction\ninv Idle: true","DUPLICATE_CLAUSE",
                source().replace("Auction::start", "Auction:::start"),"IDENTIFIER_REQUIRED",
                source().replace("context AuctionMonitor","context Missing"),"CONTEXT_BINDING_MISMATCH",
                source().replace("not self.running","self.task"),"OCL_BOOLEAN_CONDITION_REQUIRED",
                source().replace("self.unfulfilled","self.missing"),"NATIVE_INVARIANT_REJECTED");
        for(var negative:negatives.entrySet()) {
            String changed=negative.getKey();
            var binding=binding(true);
            // A separately derived in-memory negative fixture pins the changed
            // bytes, so native Boolean failures cannot pass via a hash mismatch.
            var fixtureMapping=mappingFor(changed);
            var error=assertThrows(IllegalArgumentException.class,()->loader.compile(loader.parse(changed,"invalid.ocl"),binding,fixtureMapping));
            assertTrue(error.getMessage().contains(negative.getValue()),error.toString());
            assertTrue(binding.model().prePostConditions().isEmpty());assertEquals(3,binding.model().classInvariants().size());
        }
        assertTrue(assertThrows(IllegalArgumentException.class,()->loader.compile(loader.load(BUNDLE),binding(false),mapping())).getMessage().contains("SIGNATURE"));
    }
    private static AuctionMappingSpecification mappingFor(String ocl) {
        String xml=new String(BUNDLE.require("case-rules").bytes(),StandardCharsets.UTF_8);
        xml=xml.replace(BUNDLE.require("input-ocl").sha256(),ExternalOclConstraintService.sha256(ocl.getBytes(StandardCharsets.UTF_8)));
        return new AuctionMappingXmlLoader().parse(xml.getBytes(StandardCharsets.UTF_8),"derived-negative-fixture.xml");
    }
    @Test void nativeLexingPreservesCommentAndStringBoundariesAndRejectsUnownedSyntax() {
        String input="-- pre fake: false\ncontext X\ninv One: 'context pre Fake:' = 'context pre Fake:'\ninv Two: true\n";
        var parsed=loader.parse(input,"tokens.ocl");assertEquals(2,parsed.clauses().size());
        assertTrue(parsed.clauses().getFirst().body().contains("'context pre Fake:'"));
        for(String invalid:List.of("context X inv Named: true unexpected stuff", "context X inv Missing", "context X::f(a:String,a:String) pre Named: true", "use Other"))
            assertThrows(IllegalArgumentException.class,()->loader.parse(invalid,"bad.ocl"));
        assertThrows(IllegalArgumentException.class,()->loader.parse(new byte[]{(byte)0xc3,0x28},"bad-utf8.ocl"));
    }
    @Test void nativeOutcomesKeepMissingPartialUndefinedErrorAndFalseSeparate() throws Exception {
        var binding=binding(true);var system=new MSystem(binding.model());var api=UseSystemApi.create(system,false);
        var object=api.createObject("Auction","a");
        try(var backend=new NativeOclBackend(binding.model(),"ocl-component","AUCTION_OBSERVED_V1")) {
            var predicate=backend.compile(new OclContractSpec("running",OclContractSpec.Kind.GOAL,object.cls(),null,"self.running"));
            var values=Map.<String,Value>of("self",object.value());var view=UseStateReadView.pin(null,system.state());
            assertEquals(UNDEFINED,backend.evaluate(predicate,view,values,"").status());
            for(String unavailable:List.of("PARTIAL","MISSING","GAP","UNSUPPORTED","ERROR")) {
                var outcome=backend.evaluate(predicate,view,values,unavailable);assertEquals(SKIPPED,outcome.status());assertNull(outcome.knownBoolean());
            }
            api.setAttributeValue("a","running","false");assertEquals(FAIL,backend.evaluate(predicate,UseStateReadView.pin(null,system.state()),values,"").status());
            assertEquals(ERROR,backend.evaluate(predicate,view,Map.of("self",new StringValue("a")),"").status());
        }
    }
    @Test void foreignSourceHashAndForgedLocatedClauseCannotBypassSpecificationAuthority() throws Exception {
        var binding=binding(true);
        String changed=source().replace("pre RunningForBid: self.running","pre RunningForBid: true");
        assertTrue(assertThrows(IllegalArgumentException.class,()->loader.compile(loader.parse(changed,"changed.ocl"),binding,mapping()))
                .getMessage().contains("SOURCE_HASH"));
        var original=loader.load(BUNDLE);var clauses=new ArrayList<>(original.clauses());var first=clauses.getFirst();
        clauses.set(0,new AuctionOclSpecificationLoader.Clause(first.name(),first.kind(),first.context(),"true",first.bodyStart(),first.bodyEnd(),first.line(),first.column()));
        var forged=new AuctionOclSpecificationLoader.Specification(original.sourceName(),original.sourceHash(),original.source(),clauses);
        assertTrue(assertThrows(IllegalArgumentException.class,()->loader.compile(forged,binding,mapping())).getMessage().contains("PARSED_SOURCE"));
        assertTrue(binding.model().prePostConditions().isEmpty());assertEquals(3,binding.model().classInvariants().size());
    }
}
