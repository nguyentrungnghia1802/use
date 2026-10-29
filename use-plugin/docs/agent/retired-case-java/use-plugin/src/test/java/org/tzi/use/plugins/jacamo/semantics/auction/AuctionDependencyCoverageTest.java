package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source-backed component fixtures with explicit observation-loss fault
 * injection. No synthetic value is presented as a live producer observation. */
class AuctionDependencyCoverageTest {
    @Test void aStaleNativePlayerWithTheSameNameCannotBorrowAnotherPlayersCurrentObservation()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            var engine=f.projector.mutations();var cut=f.cut();var board=cut.facts().stream().filter(fact->fact.kind()==RuntimeFactKind.GROUP_BOARD).findFirst().orElseThrow();var values=new LinkedHashMap<>(board.values());
            var players=new ArrayList<Object>((List<?>)values.get("players"));var player=new LinkedHashMap<>(CanonicalJson.object(players.getFirst()));var actual=engine.objectForSemanticId((String)player.get("agentSemanticId"));assertNotNull(actual);
            var staleId=new BridgeEntityId("jason","agent","runtime-agent","explicit-previous-incarnation-fixture",engine.metadata(actual,"name"),"old-incarnation").canonical();var port=port(engine);var stale=port.upsert(staleId,"AtomicAgent","U03StalePlayer");port.metadata(stale,"name",engine.metadata(actual,"name"));
            player.put("agentSemanticId",staleId);players.set(0,player);values.put("players",players);
            var facts=cut.facts().stream().map(fact->fact==board?new RuntimeFact(fact.id(),fact.kind(),values,List.of(),fact.projectionStatus(),fact.completeness(),fact.evidence()):fact).toList();
            f.projector.applySnapshot(new RuntimeSnapshot(cut.snapshotId()+":stale-player",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,facts,cut.sourceCompleteness(),cut.stateFingerprint()+":explicit-stale-player-fixture"),f.run().id(),f.run().generation());
            var group=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("auctionGroup")).findFirst().orElseThrow();assertEquals("INCOMPLETE",engine.metadata(group,"auctionPopulationQuality"));
            assertTrue(engine.system().state().allObjects().stream().noneMatch(role->staleId.equals(engine.metadata(role,"rolePlayer"))&&"true".equals(engine.metadata(role,"auctionLiveEnactment"))),"Current name equality is not current actor identity");
        }
    }
    @Test void observerDependencyInventoryAndUnknownInputsNeverInventBooleans()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            var expected=Map.of("SchemeOrder",Set.of("bidEnabledAt","startSatisfiedAt","bidSatisfiedAt","decideEnabledAt","decideSatisfiedAt"),"BidEvidence",Set.of("bidDoneBy","actualBidBy","bidSatisfiedAt","adminBidOverride","bidCommittedBy"),"WithinDeadline",Set.of("deadlineAt","activatedAt","goal","unfulfilled","endedAt","now"));
            for(var item:expected.entrySet()) {
                var inv=f.projector.system().model().classInvariants().stream().filter(i->i.name().equals(item.getKey())).findFirst().orElseThrow();var visitor=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.dependencyVisitor();inv.bodyExpression().processWithVisitor(visitor);
                assertEquals(item.getValue(),visitor.attributeReads().stream().map(r->r.attribute().name()).collect(java.util.stream.Collectors.toSet()),item.getKey());
            }
            var gate=new org.tzi.use.plugins.jacamo.semantics.runtime.ObservationQualityGate();for(var status:NativeOclOutcome.Status.values()) {
                var decision=gate.booleanInput(new NativeOclOutcome(status,"","explicit-quality-fixture"),true,false);if(status==NativeOclOutcome.Status.PASS)assertEquals(Boolean.TRUE,decision.known());else if(status==NativeOclOutcome.Status.FAIL)assertEquals(Boolean.FALSE,decision.known());else assertNull(decision.known());
                assertNull(gate.booleanInput(new NativeOclOutcome(status,"","explicit-quality-fixture"),false,false).known());assertNull(gate.booleanInput(new NativeOclOutcome(status,"","explicit-quality-fixture"),true,true).known());
            }assertNull(gate.booleanInput(null,true,false).known());
        }
    }
    @Test void disabledOrMissingOrganizationScopeCannotEarnAContextCertificate()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            var engine=f.projector.mutations();var invariant=engine.system().model().classInvariants().stream().filter(i->i.name().equals("CslV4RoleCompatibility")).findFirst().orElseThrow();var evaluator=new org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator();var sources=Map.of("cartago",Completeness.COMPLETE);
            invariant.setActive(false);var inactive=evaluator.prepareScoped("inactive",invariant,engine,sources,false,Set.of(),"");assertTrue(org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator.evaluateScoped(inactive,engine.system().state()).stream().allMatch(r->r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.SKIPPED));invariant.setActive(true);
            var gap=evaluator.prepareScoped("gap",invariant,engine,sources,true,Set.of(),"");assertTrue(org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator.evaluateScoped(gap,engine.system().state()).stream().allMatch(r->r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.SKIPPED));
            var root=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("AuctionOrg")).findFirst().orElseThrow();port(engine).metadata(root,"auctionOrganizationQuality","UNAVAILABLE");
            var missing=evaluator.prepareScoped("missing",invariant,engine,sources,false,Set.of(),"");assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.SKIPPED,org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator.evaluateScoped(missing,engine.system().state()).stream().filter(r->r.contextObject().equals(root.name())).findFirst().orElseThrow().verdict());
        }
    }
    @Test void unsupportedAccessorAndCorruptedNativeValueRetainTheirQualityInsteadOfFalse()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            var engine=f.projector.mutations();var self=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("AuctionMonitor")).findFirst().orElseThrow();var port=port(engine);
            var expression=NativeOclCompilation.predicate(engine.system().model(),self.cls(),"U03FieldState","not self.adminBidOverride");
            var invariant=new org.tzi.use.api.UseModelApi(engine.system().model()).createInvariantEx("U03FieldState",self.cls().name(),expression,false);var evaluator=new org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator();
            for(var fault:Map.of("UNSUPPORTED:FIELD_ACCESSOR",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.UNSUPPORTED,"ERROR:SOURCE_HASH_CONFLICT",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.ERROR).entrySet()) {
                port.metadata(self,"auctionObserverQuality:adminBidOverride",fault.getKey());
                var prepared=evaluator.prepareScoped("U03FieldState",invariant,engine,Map.of("cartago",Completeness.PARTIAL),false,Set.of(),"");var rows=org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator.evaluateScoped(prepared,engine.system().state());
                assertEquals(fault.getValue(),rows.stream().filter(r->r.contextObject().equals(self.name())).findFirst().orElseThrow().verdict());assertTrue(rows.stream().noneMatch(r->r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL));
            }
            port.metadata(self,"auctionObserverQuality:adminBidOverride","COMPLETE");UseSystemApi.create(engine.system(),false).setAttributeValueEx(self,self.cls().attribute("adminBidOverride",true),BooleanValue.TRUE);
            var corrupted=evaluator.prepareScoped("U03FieldState",invariant,engine,Map.of("cartago",Completeness.COMPLETE),false,Set.of(),"");
            assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.ERROR,org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator.evaluateScoped(corrupted,engine.system().state()).stream().filter(r->r.contextObject().equals(self.name())).findFirst().orElseThrow().verdict());
        }
    }
    @Test void admittedGroupStructuralHelperPredicatesUseItsScopeProofInsteadOfUnboundIteratorFailure()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            var engine=f.projector.mutations();var group=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("auctionGroup")).findFirst().orElseThrow();
            var invariant=engine.system().model().classInvariants().stream().filter(i->i.name().equals("CslV4RoleCompatibility")).findFirst().orElseThrow();
            var prepared=new org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator().prepareScoped("compatibility",invariant,engine,Map.of("cartago",Completeness.PARTIAL),false,Set.of(),"");
            assertEquals(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS,org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator.evaluateScoped(prepared,engine.system().state()).stream().filter(r->r.contextObject().equals(group.name())).findFirst().orElseThrow().verdict());
        }
    }
    @Test void partialPlayerObservationDefersPopulationWithoutInventingRoleWithdrawal()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            var projector=f.projector;var group=projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("auctionGroup")).findFirst().orElseThrow();
            var roles=projector.system().state().allObjects().stream().filter(o->projector.mutations().metadata(group,"semanticId").equals(projector.mutations().metadata(o,"roleContext"))).toList();assertFalse(roles.isEmpty());
            var cut=f.cut();var sources=new TreeMap<>(cut.sourceCompleteness());sources.put("cartago",Completeness.PARTIAL);
            projector.applySnapshot(new RuntimeSnapshot(cut.snapshotId()+":player-loss",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,cut.facts().stream().filter(fact->fact.kind()!=RuntimeFactKind.AGENT).toList(),sources,cut.stateFingerprint()+":player-loss"),f.run().id(),f.run().generation());
            assertEquals("INCOMPLETE",projector.mutations().metadata(group,"auctionPopulationQuality"));assertTrue(projector.system().state().allObjects().containsAll(roles),"The positive GroupBoard tuples still exist; missing actor observation is not role withdrawal");
            var result=projector.coordinator().awaitEvaluation(projector.coordinator().latest(),15000);
            assertTrue(result.semantic().constraints().stream().filter(r->r.id().contains("AuctioneerPopulation")&&r.kind().equals("INVARIANT_BODY")).allMatch(r->r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.SKIPPED));
        }
    }
    @Test void allFiveOperationClausesRequireExactlyTheirCompiledAttributeDependencies()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            var engine=f.projector.mutations();var self=engine.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).findFirst().orElseThrow();var coverage=new ConstraintCoverageResolver();var port=port(engine);
            var clauses=self.cls().allOperations().stream().flatMap(op->java.util.stream.Stream.concat(op.preConditions().stream(),op.postConditions().stream())).toList();assertEquals(5,clauses.size());
            for(var clause:clauses) {
                var visitor=org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.dependencyVisitor();clause.expression().processWithVisitor(visitor);var fields=visitor.attributeReads().stream().map(read->read.attribute().name()).collect(java.util.stream.Collectors.toSet());assertFalse(fields.isEmpty());assertTrue(Set.of("running","task").containsAll(fields));
                var sources=Map.of("cartago",Completeness.PARTIAL);assertEquals("",coverage.unavailableSelf(clause.expression(),engine,self,sources,false));
                for(String field:fields) {
                    var saved=new TreeMap<String,String>();for(String key:List.of("auctionObserved:"+field,"propertySlot:"+field,"propertyAbsent:"+field))saved.put(key,engine.metadata(self,key));
                    try {port.metadata(self,"auctionObserved:"+field,"");port.metadata(self,"propertySlot:"+field,"");port.metadata(self,"propertyAbsent:"+field,"true");assertFalse(coverage.unavailableSelf(clause.expression(),engine,self,sources,false).isBlank(),clause.name()+":"+field);}
                    finally{saved.forEach((key,value)->port.metadata(self,key,value));}
                }
            }
        }
    }
    @Test void partialPopulationAndAnUnavailableUnrelatedFieldDoNotSuppressAnExactKnownSelfRead()throws Exception {
        try(var f=new AuctionCastFixture(1);var ocl=new NativeOclBackend(f.projector.system().model(),f.program.hash(),"U03")) {
            var engine=f.projector.mutations();var self=f.projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).findFirst().orElseThrow();
            // Lose only this feature's admission. This does not authorize a
            // partially coherent R05 full-state write or a guessed default.
            var port=port(engine);port.metadata(self,"auctionObserved:currentWinner","");port.metadata(self,"propertyAbsent:currentWinner","true");port.metadata(self,"auctionFullState","INCOMPLETE");
            UseSystemApi.create(engine.system(),false).setAttributeValueEx(self,self.cls().attribute("currentWinner",true),UndefinedValue.instance);
            var running=ocl.compile(new OclContractSpec("knownRunning",OclContractSpec.Kind.INVARIANT,self.cls(),null,"self.running"));var coverage=new ConstraintCoverageResolver();
            var sources=Map.of("cartago",Completeness.PARTIAL);String reason=coverage.unavailableSelf(running.expression(),engine,self,sources,false);
            assertEquals("",reason,"A complete exact field witness is independent of the global population and unrelated missing field");
            assertEquals(NativeOclOutcome.Status.FAIL,ocl.evaluate(running,UseStateReadView.pin(null,engine.system().state()),Map.of("self",self.value()),reason).status());
            var missing=ocl.compile(new OclContractSpec("undefinedEquality",OclContractSpec.Kind.INVARIANT,self.cls(),null,"self.currentWinner = self.currentWinner"));
            assertFalse(coverage.unavailableSelf(missing.expression(),engine,self,sources,false).isBlank());
            var population=ocl.compile(new OclContractSpec("global",OclContractSpec.Kind.INVARIANT,self.cls(),null,"Auction.allInstances()->forAll(a | a.running = a.running)"));
            assertFalse(coverage.unavailableSelf(population.expression(),engine,self,sources,false).isBlank());assertEquals("COVERAGE_LOST",coverage.unavailableSelf(running.expression(),engine,self,sources,true));
        }
    }
    @Test void productionResultIncludesScopedPopulationBodyAndDoesNotCertifyThePartialGlobalPopulation()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            var coordinator=f.projector.coordinator();var result=coordinator.awaitEvaluation(coordinator.latest(),15000);
            var rows=result.semantic().constraints().stream().filter(r->r.id().contains("AuctioneerPopulation")).toList();
            assertTrue(rows.stream().anyMatch(r->r.kind().equals("INVARIANT_BODY")&&r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.PASS),"The actual admitted group has exactly one auctioneer; its body witness must reach production evidence");
            assertTrue(rows.stream().anyMatch(r->r.kind().equals("INVARIANT")&&r.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.SKIPPED),"One observed group cannot certify a complete global MOISE population");
        }
    }
    @Test void aDifferentMonitorMissingItsClockDoesNotInvalidateThisObligationsExactMonitor()throws Exception {
        try(var f=new AuctionCastFixture(1,1,true)) {
            f.startAndEnableBid(0);f.startAndEnableBid(1);f.resync();var engine=f.projector.mutations();var state=engine.system().state();
            var obligation=state.allObjects().stream().filter(o->o.cls().name().equals("AuctionObligation")).findFirst().orElseThrow();
            var binding=engine.metadata(obligation,"auctionObligationArtifact");var monitors=state.allObjects().stream().filter(o->o.cls().name().equals("AuctionMonitor")).toList();
            var selected=monitors.stream().filter(o->binding.equals(engine.metadata(o,"auctionMonitorArtifact"))).findFirst().orElseThrow();var other=monitors.stream().filter(o->o!=selected).findFirst().orElseThrow();
            assertEquals("COMPLETE",engine.metadata(selected,"auctionObserverQuality:now"));port(engine).metadata(other,"auctionObserverQuality:now","INJECTED_CLOCK_OBSERVATION_LOSS");
            var invariant=engine.system().model().classInvariants().stream().filter(i->i.name().equals("WithinDeadline")).findFirst().orElseThrow();
            assertEquals("",new ConstraintCoverageResolver().unavailableSelf(invariant.bodyExpression(),engine,obligation,Map.of("cartago",Completeness.COMPLETE),false));
        }
    }
    private org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port port(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine engine)throws Exception {
        var method=engine.getClass().getDeclaredMethod("cslPort");assertTrue(method.trySetAccessible());return (org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port)method.invoke(engine);
    }
}
