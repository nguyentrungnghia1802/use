package org.tzi.use.plugins.jacamo.semantics.auction;

import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime.RuntimeFactSources;
import static org.junit.jupiter.api.Assertions.*;

/** Current inventory closure is independent of lossless historical polling. */
class MoisePopulationClosureTest {
    @Test void physicalInventoryClosesCurrentGroupsButNeverMissingOrPartialBoards()throws Exception {
        try(var f=new AuctionOrgFixture()) {
            f.organizationBoard();f.group();
            Path root=AuctionDeclarativeSpecificationTest.CASE,input=root.resolve("authored-v1");
            var selected=MappingInputs.read(root.resolve("auction.jcm"),input.resolve("auction-mapping.xml"),input.resolve("auction_constraints_final_fixed.ocl"),input.resolve("auction_csl.csl"),input.resolve("auction_istar_final.istar"));
            var program=new GenericMappingEngine().map(f.live.source,selected);
            String xml=Files.readString(input.resolve("auction-mapping.xml"));
            String declaration="<facts id=\"source-groups\" authority=\"moise\" kind=\"GROUP_BOARD\"";
            if(!xml.contains("population=\"CARTAGO_BOARD_INVENTORY_V1\""))xml=xml.replace(declaration,declaration+" population=\"CARTAGO_BOARD_INVENTORY_V1\"");
            var specification=new MappingXmlReader().parse(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8),"population-test");
            String acceptedXml=xml;
            assertThrows(IllegalArgumentException.class,()->new MappingXmlReader().parse(acceptedXml.replace("CARTAGO_BOARD_INVENTORY_V1","GUESS_FROM_NATIVE_OBJECT_COUNT").getBytes(java.nio.charset.StandardCharsets.UTF_8),"unsupported-policy"));
            var sources=new RuntimeFactSources(specification,f.live.source,program.xmlPlan().requirements());
            var cut=f.cut();
            assertEquals(Completeness.PARTIAL,cut.sourceCompleteness().get("moise:aorg"));
            assertEquals(Completeness.COMPLETE,sources.capture(cut,f.live.run.id(),f.live.run.generation()).get("source-groups").populationQuality());
            var legacySpec=new MappingXmlReader().parse(xml.replace(" population=\"CARTAGO_BOARD_INVENTORY_V1\"","").getBytes(java.nio.charset.StandardCharsets.UTF_8),"legacy-population");
            assertEquals(Completeness.PARTIAL,new RuntimeFactSources(legacySpec,f.live.source,program.xmlPlan().requirements()).capture(cut,f.live.run.id(),f.live.run.generation()).get("source-groups").populationQuality());
            var noLineage=AuctionDeclarativeArtifactTest.copy(cut,cut.facts().stream().map(fact->{
                if(fact.kind()!=RuntimeFactKind.ARTIFACT)return fact;
                var values=new LinkedHashMap<>(fact.values());if(values.get("artifactProviderObservation") instanceof Map<?,?> old){var provider=new LinkedHashMap<>(CanonicalJson.object(old));provider.remove("classHierarchy");values.put("artifactProviderObservation",provider);}
                return new RuntimeFact(fact.id(),fact.kind(),values,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence());
            }).toList());
            assertNotEquals(Completeness.COMPLETE,sources.capture(noLineage,f.live.run.id(),f.live.run.generation()).get("source-groups").populationQuality(),"Historical observations without lineage cannot gain new closure authority");
            var group=cut.facts().stream().filter(x->x.kind()==RuntimeFactKind.GROUP_BOARD).findFirst().orElseThrow();
            var absent=AuctionDeclarativeArtifactTest.copy(cut,cut.facts().stream().filter(x->x!=group).toList());
            assertNotEquals(Completeness.COMPLETE,sources.capture(absent,f.live.run.id(),f.live.run.generation()).get("source-groups").populationQuality(),"A physical board without its public state cannot certify an empty population");
            var marks=new LinkedHashMap<>(cut.sourceCompleteness());marks.put("cartago",Completeness.PARTIAL);
            var partial=new RuntimeSnapshot(cut.snapshotId(),cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),cut.validationAttempts(),cut.facts(),marks,cut.stateFingerprint(),cut.captureMetadata());
            assertNotEquals(Completeness.COMPLETE,sources.capture(partial,f.live.run.id(),f.live.run.generation()).get("source-groups").populationQuality());
            var stale=AuctionDeclarativeArtifactTest.copy(cut,cut.facts().stream().map(fact->{
                if(fact.kind()!=RuntimeFactKind.ARTIFACT||!fact.id().incarnation().equals(group.values().get("boardArtifactIdentity")))return fact;
                var values=new LinkedHashMap<>(fact.values());var provider=new LinkedHashMap<>(CanonicalJson.object(values.get("artifactProviderObservation")));
                var roster=new LinkedHashMap<>(CanonicalJson.object(provider.get("publicGroupRoster")));roster.put("players",List.of());provider.put("publicGroupRoster",roster);values.put("artifactProviderObservation",provider);
                return new RuntimeFact(fact.id(),fact.kind(),values,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence());
            }).toList());
            assertNotEquals(Completeness.COMPLETE,sources.capture(stale,f.live.run.id(),f.live.run.generation()).get("source-groups").populationQuality(),"A complete physical inventory cannot certify a stale public roster");
            assertEquals(Completeness.PARTIAL,cut.sourceCompleteness().get("moise:aorg"),"No historical source completeness is upgraded");
            var inputs=MappingInputs.fromFrozen(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8),selected.ocl().bytes(),selected.sources(),"native-population");
            var nativeProgram=new GenericMappingEngine().map(f.live.source,inputs);
            var requests=nativeProgram.xmlPlan().requirements();var frames=new ArrayList<VerificationObservation>();
            var workspace=AuctionDeclarativeObserverTest.request(requests,"workspace-operations");var field=AuctionDeclarativeObserverTest.request(requests,"candidate-field");
            var context=new org.jacamo.bridge.adapter.FrameworkObservationProviders.Context(f.live.environment,f.live.source,root.toRealPath(),f.live.run.id(),f.live.run.generation(),frames::add,ignored->{});
            try(var history=new org.jacamo.bridge.adapter.CartagoOperationHistory(workspace,context);
                var fields=new org.jacamo.bridge.adapter.ArtifactFieldObservation(f.live.environment,f.live.source,field,root.toRealPath());
                var driver=new AuctionDeclarativeArtifactTest.SourceDriver(f,AuctionDeclarativeObserverTest.request(requests,"execution-steps"),frames)) {
                var built=new org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline().buildCanonicalProgram(f.live.source,org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode.AUTO,nativeProgram);
                try(var projector=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector(built,f.live.run.id(),f.live.run.generation(),f.live.source.modelRevision())) {
                    var observed=AuctionDeclarativeArtifactTest.observed(f,frames,driver.identity,workspace,history,field,fields);
                    projector.applySnapshot(observed,f.live.run.id(),f.live.run.generation());
                    var resolver=new org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver();var type=projector.system().model().getClass("auctionGroup");
                    assertFalse(resolver.populationComplete(type,observed.sourceCompleteness()));
                    assertTrue(resolver.populationComplete(type,observed.sourceCompleteness(),projector.mutations()),"Closed physical inventory must reach native population admission");
                    var unknownSources=new LinkedHashMap<>(observed.sourceCompleteness());unknownSources.put("cartago",Completeness.PARTIAL);
                    var unknown=new RuntimeSnapshot(observed.snapshotId(),observed.modelRevision(),observed.captureStartedAt(),observed.captureEndedAt(),observed.startWatermarks(),observed.endWatermarks(),observed.validationAttempts(),observed.facts(),unknownSources,observed.stateFingerprint(),observed.captureMetadata());
                    projector.applySnapshot(unknown,f.live.run.id(),f.live.run.generation());
                    assertFalse(resolver.populationComplete(type,unknown.sourceCompleteness(),projector.mutations()),"A prior certificate cannot survive a partial current inventory");
                    var noPlayers=AuctionDeclarativeArtifactTest.copy(observed,observed.facts().stream().filter(x->x.kind()!=RuntimeFactKind.AGENT).toList());
                    projector.applySnapshot(noPlayers,f.live.run.id(),f.live.run.generation());
                    var groupObject=projector.system().state().allObjects().stream().filter(o->o.cls()==type).findFirst().orElseThrow();
                    try(var ocl=new org.tzi.use.semantic.engine.use75.NativeOclBackend(projector.system().model(),"missing-players","current-inventory")) {
                        var expression=ocl.compile(new org.tzi.use.semantic.engine.use75.OclContractSpec("role-count",org.tzi.use.semantic.engine.use75.OclContractSpec.Kind.INVARIANT,type,null,"self.roles->size() = 1"));
                        assertFalse(resolver.unavailableSelf(expression.expression(),projector.mutations(),groupObject,noPlayers.sourceCompleteness(),false).isBlank(),"An unresolved mapped roster cannot become an authoritative native role count");
                        var quantified=ocl.compile(new org.tzi.use.semantic.engine.use75.OclContractSpec("quantified-role-count",org.tzi.use.semantic.engine.use75.OclContractSpec.Kind.INVARIANT,type,null,"auctionGroup.allInstances()->forAll(g | g.roles->size() = 1)"));
                        assertFalse(resolver.unavailableSelf(quantified.expression(),projector.mutations(),groupObject,noPlayers.sourceCompleteness(),false).isBlank(),"An unbound iterator receiver cannot bypass current role materialization admission");
                        assertThrows(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProtocolException.class,
                                ()->projector.applySnapshot(observed,f.live.run.id(),f.live.run.generation()),
                                "An old complete snapshot cannot resurrect a player incarnation retired by the later complete inventory");
                    }
                }
            }
        }
    }
}
