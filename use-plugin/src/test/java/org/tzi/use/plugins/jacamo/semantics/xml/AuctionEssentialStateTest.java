package org.tzi.use.plugins.jacamo.semantics.xml;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import static org.junit.jupiter.api.Assertions.*;

class AuctionEssentialStateTest {
    @Test void selectedNativeRequirementsDoNotReadOptionalBeliefsAndKeepExactStepSources()throws Exception {
        Path project=Path.of("..").toAbsolutePath().normalize().getParent().resolve("jacamo/examples/auction/auction.jcm");
        Path inputs=Path.of("examples/auction/authored-v1");
        var frozen=MappingInputs.read(project,inputs.resolve("auction-mapping.xml"),inputs.resolve("auction_constraints_final_fixed.ocl"),inputs.resolve("auction_csl.csl"),inputs.resolve("auction_istar_final.istar"),false);
        var source=GenericMappingEngineTest.official(project);var program=new GenericMappingEngine().map(source,frozen);
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);
        var readSet=CanonicalJson.object(built.xmlPreparation().preflight().get("readSet"));
        for(Object raw:(List<?>)readSet.get("attributes")) {
            String field=(String)CanonicalJson.object(raw).get("field");
            assertFalse(field.endsWith("::beliefBase")||field.contains("::belief_"),"A required native expression must not lose its source: "+field);
            assertFalse(field.startsWith("AuctionObligation::")||field.equals("AuctionMonitor::now"),"Do not drop a required observer field: "+field);
        }
        assertNull(built.binding().model().getClass("AuctionObligation"),"An unused observer population must not allow a restored deadline clause to pass vacuously");
        assertFalse(((List<?>)readSet.get("populations")).stream().map(CanonicalJson::object).anyMatch(r->"AuctionObligation".equals(r.get("population"))));
        assertTrue(program.xmlPlan().requirements().stream().anyMatch(r->r.provider().equals("npl.lifecycle.v1")),"Keep raw norm evidence");
        assertTrue(program.xmlPlan().requirements().stream().anyMatch(r->r.provider().equals("npl.source-clock.v1")),"Keep raw clock evidence");
        var fields=RuntimeFactSources.producerFields(frozen.specification()).get("jason");
        assertFalse(fields.contains("cycles"));assertFalse(fields.contains("storedBeliefObservation"));
        assertTrue(fields.containsAll(List.of("agentRuntimeSemanticId","name","active")));
        assertTrue(program.xmlPlan().requirements().stream().anyMatch(r->r.provider().equals("jason.plan-boundaries.v1")),"Exact step capability must remain enabled");
        var pending=org.tzi.use.plugins.jacamo.semantics.runtime.SemanticResultAdapter.pending(program,"boundary","ORDERED_EVALUATION_PENDING");
        var pendingPolicy=CanonicalJson.object(pending.observation().get("presentationState"));
        assertEquals(false,pendingPolicy.get("executionMilestones"));assertEquals(true,pendingPolicy.get("pending"));assertEquals(false,pendingPolicy.get("qualifiedValues"));
        var presentation=frozen.specification().root().child("presentation");assertEquals("DOMAIN",presentation.attr("milestones"));
        assertEquals(5,presentation.children("state").size());
        assertEquals("id running task best_bid winner currentWinner",presentation.children("state").stream().filter(n->n.attr("owner").equals("Auction")).findFirst().orElseThrow().attr("attributes"));
        assertThrows(IllegalArgumentException.class,()->MappingXmlReaderTest.parse(new String(frozen.xml().bytes(),java.nio.charset.StandardCharsets.UTF_8).replace("milestones=\"DOMAIN\"","milestones=\"UNKNOWN_POLICY\"")));
    }

    @Test void publicObserverProfileUsesNativeOclAndNeverInventsMissingTimes()throws Exception {
        Path project=Path.of("..").toAbsolutePath().normalize().getParent().resolve("jacamo/examples/auction/auction.jcm");Path d=Path.of("examples/auction/authored-v1");
        var source=GenericMappingEngineTest.official(project);
        var program=new GenericMappingEngine().map(source,MappingInputs.read(project,d.resolve("auction-mapping.xml"),d.resolve("auction_constraints_final_fixed.ocl"),d.resolve("auction_csl.csl"),d.resolve("auction_istar_final.istar"),false));
        var strict=new GenericMappingEngine().map(source,MappingInputs.read(project,d.resolve("references/auction-mapping-strict-v1.xml"),d.resolve("references/auction_constraints_strict_v1.ocl"),d.resolve("auction_csl.csl"),d.resolve("references/auction_istar_success_only_v2.istar"),false));
        assertEquals(program.cslHash(),strict.cslHash());assertNotEquals(program.goalHash(),strict.goalHash(),"User-approved v3 attempt/accepted-bid split is an explicit model difference");assertNotEquals(program.hash(),strict.hash());
        var built=new CodeGroundedNativePipeline().buildCanonicalProgram(source,NativeProjectionMode.AUTO,program);var system=built.nativeResult().state().system();
        var api=org.tzi.use.api.UseSystemApi.create(system,false);var self=api.createObject("AuctionMonitor","observed");
        for(String field:List.of("bidDoneBy","actualBidBy","bidCommittedBy"))api.setAttributeValue(self.name(),field,"Set{'alice'}");
        api.setAttributeValue(self.name(),"adminBidOverride","false");api.setAttributeValue(self.name(),"bidSatisfiedObserved","true");
        try(var backend=new org.tzi.use.semantic.engine.use75.NativeOclBackend(system.model(),"auction-observer-test",program.hash())) {
            var contracts=built.xmlPreparation().authored().clauses();
            var bid=contracts.stream().filter(c->c.specification().name().equals("ObservedBidEvidence")).findFirst().orElseThrow();
            assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS,evaluate(bid.expression(),system,self));
            api.setAttributeValue(self.name(),"actualBidBy","Set{}");assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL,evaluate(bid.expression(),system,self));
            api.setAttributeValue(self.name(),"actualBidBy","Set{'alice'}");api.setAttributeValue(self.name(),"bidCommittedBy","Set{}");api.setAttributeValue(self.name(),"bidSatisfiedObserved","null");assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.UNDEFINED,evaluate(bid.expression(),system,self));
            int time=1;for(String name:List.of("startSatisfied","bidEnabled","bidSatisfied","decideEnabled","decideSatisfied"))api.setAttributeValue(self.name(),name+"ConfirmedAt",Integer.toString(time++));
            var order=contracts.stream().filter(c->c.specification().name().equals("ConfirmedSchemeOrder")).findFirst().orElseThrow();
            assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS,evaluate(order.expression(),system,self));
            api.setAttributeValue(self.name(),"bidEnabledConfirmedAt","0");assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL,evaluate(order.expression(),system,self));
            api.setAttributeValue(self.name(),"bidEnabledConfirmedAt","null");assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.UNDEFINED,evaluate(order.expression(),system,self));
        }
        assertTrue(java.nio.file.Files.readString(d.resolve("references/auction_constraints_strict_v1.ocl")).contains("self.bidSatisfiedAt >= 0"),"Strict authored input stays intact");
    }
    private static org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict evaluate(org.tzi.use.uml.ocl.expr.Expression expression,org.tzi.use.uml.sys.MSystem system,org.tzi.use.uml.sys.MObject self){var vars=new org.tzi.use.uml.ocl.value.VarBindings(system.state());vars.push("self",self.value());return new org.tzi.use.plugins.jacamo.semantics.ocl.OclEvaluationAdapter().evaluate(expression,system.state(),system.state(),vars).verdict();}
}
