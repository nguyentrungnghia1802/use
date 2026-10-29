package org.tzi.use.plugins.jacamo.semantics.auction;

import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionStaticMappingAdapter;
import cartago.Op;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.ocl.ConstraintCoverageResolver;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.ocl.value.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionRuntimeProjectionTest {
    @Test void actualStartedDeltaCompletedEventsUpdateTheSameStateAndKeepTheOtherAuctionUntouched()throws Exception {
        try(var fixture=new AuctionOrgFixture()) {
            var live=fixture.live;fixture.group();fixture.scheme("a1","service(one)");fixture.scheme("a2","service(two)");
            var first=live.auction("a1");live.auction("a2");
            var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));
            var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
            try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                var initial=fixture.cut();projector.applySnapshot(initial,live.run.id(),live.run.generation());long watermark=initial.endWatermarks().get("cartago").sequence();
                var system=projector.system();var a=auction(projector,"a1");var b=auction(projector,"a2");
                live.actor.doAction(first,new Op("start","event service"),5000);live.actor.doAction(first,new Op("bid",42.5),5000);live.actor.doAction(first,new Op("bid",100.0),5000);live.actor.doAction(first,new Op("stop"),5000);
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(live.events.stream().noneMatch(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"stop".equals(e.after().get("operation")))&&System.nanoTime()<deadline)Thread.sleep(5);
                assertTrue(live.events.stream().anyMatch(e->e.kind()==RuntimeEventKind.SUCCEEDED&&"stop".equals(e.after().get("operation"))));
                var actual=live.events.stream().filter(e->e.sourceSequence()>watermark).sorted(Comparator.comparingLong(RuntimeEvent::sourceSequence)).toList();
                for(var event:actual)projector.apply(event);
                assertSame(system,projector.system());assertSame(a,auction(projector,"a1"));assertFalse(projector.resyncRequired());
                assertEquals("42.5",a.state(system.state()).attributeValue("best_bid").toString());assertEquals(BooleanValue.FALSE,a.state(system.state()).attributeValue("running"));
                assertEquals(live.actor.getName(),((StringValue)a.state(system.state()).attributeValue("winner")).value());assertEquals(live.actor.getName(),((StringValue)a.state(system.state()).attributeValue("currentWinner")).value());
                assertEquals(Double.MAX_VALUE,((RealValue)b.state(system.state()).attributeValue("best_bid")).value());assertEquals("no_winner",((StringValue)b.state(system.state()).attributeValue("currentWinner")).value());
                assertTrue(projector.trace().stream().anyMatch(t->t.ruleId().equals("AUCTION-XML-R05")&&t.eventId().contains(":xml:")));
            }
        }
    }
    @Test void unavailableAndMixedFieldCutsSkipTheirDependentNativeCheckButKeepObservedIdentity()throws Exception {
        try(var fixture=new AuctionOrgFixture()) {
            var live=fixture.live;fixture.group();fixture.scheme("a1","service(one)");live.auction("a1");
            var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));
            var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
            try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision());var ocl=new NativeOclBackend(pipeline.binding().model(),"R02-coverage","AUCTION_XML/1.0")) {
                projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());var a=auction(projector,"a1");
                var running=ocl.compile(new OclContractSpec("running",OclContractSpec.Kind.INVARIANT,a.cls(),null,"self.running"));
                var identity=ocl.compile(new OclContractSpec("identity",OclContractSpec.Kind.INVARIANT,a.cls(),null,"self.id = 'a1'"));
                var coverage=new ConstraintCoverageResolver();var sources=Map.of("cartago",Completeness.COMPLETE);var variables=Map.<String,Value>of("self",a.value());
                assertEquals("",coverage.unavailableSelf(running.expression(),projector.mutations(),a,sources,false));
                assertEquals(NativeOclOutcome.Status.FAIL,ocl.evaluate(running,UseStateReadView.pin(null,projector.system().state()),variables,"").status(),"The actual known false initial running value is a Boolean, not missing evidence");
                for(String mutation:List.of("missing-field","mixed-value","wrong-class-hash")) {
                    var cut=mutate(fixture.cut(),fact->{if(fact.kind()!=RuntimeFactKind.ARTIFACT||!"a1".equals(fact.values().get("name")))return fact;
                        var values=new LinkedHashMap<>(fact.values());var observation=new LinkedHashMap<>(CanonicalJson.object(values.get("auctionStateObservation")));
                        if(mutation.equals("missing-field"))observation.put("fields",Map.of());
                        if(mutation.equals("wrong-class-hash"))observation.put("classHash","0".repeat(64));
                        if(mutation.equals("mixed-value")){var properties=new LinkedHashMap<>(CanonicalJson.object(observation.get("properties")));var price=new LinkedHashMap<>(CanonicalJson.object(properties.get("best_bid")));price.put("value","42.5");properties.put("best_bid",price);observation.put("properties",properties);}
                        values.put("auctionStateObservation",observation);return new RuntimeFact(fact.id(),fact.kind(),values,fact.relations(),fact.projectionStatus(),fact.completeness(),fact.evidence());});
                    projector.applySnapshot(cut,live.run.id(),live.run.generation());assertSame(a,auction(projector,"a1"));
                    assertTrue(a.state(projector.system().state()).attributeValue("currentWinner").isUndefined());assertEquals("INCOMPLETE",projector.mutations().metadata(a,"auctionFullState"));
                    var unavailable=coverage.unavailableSelf(running.expression(),projector.mutations(),a,sources,false);assertFalse(unavailable.isBlank());
                    assertEquals(NativeOclOutcome.Status.SKIPPED,ocl.evaluate(running,UseStateReadView.pin(null,projector.system().state()),variables,unavailable).status());
                    assertEquals("",coverage.unavailableSelf(identity.expression(),projector.mutations(),a,sources,false));
                    assertEquals(NativeOclOutcome.Status.PASS,ocl.evaluate(identity,UseStateReadView.pin(null,projector.system().state()),variables,"").status());
                    projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());assertEquals("COMPLETE",projector.mutations().metadata(a,"auctionFullState"));
                }
            }
        }
    }
    static org.tzi.use.uml.sys.MObject auction(NativeRuntimeProjector projector,String id){return projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")&&o.state(projector.system().state()).attributeValue("id") instanceof StringValue value&&value.value().equals(id)).findFirst().orElseThrow();}
    static RuntimeSnapshot mutate(RuntimeSnapshot snapshot,UnaryOperator<RuntimeFact> change){return new RuntimeSnapshot(snapshot.snapshotId()+":typed-negative",snapshot.modelRevision(),snapshot.captureStartedAt(),snapshot.captureEndedAt(),snapshot.startWatermarks(),snapshot.endWatermarks(),snapshot.validationAttempts(),snapshot.facts().stream().map(change).toList(),snapshot.sourceCompleteness(),snapshot.stateFingerprint()+":typed-negative",snapshot.captureMetadata());}
    @Test void actualOrganizationGroupRoleAndTwoArtifactsUseOneNativeStateAndCoherentTypedFields()throws Exception {
        try(var fixture=new AuctionOrgFixture()) {
            var live=fixture.live;fixture.group();fixture.scheme("a1","flight_ticket(paris,athens)");fixture.scheme("a2","flight_ticket(rome,paris)");
            var first=live.auction("a1");live.auction("a2");
            var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));
            var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
            try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                var initial=fixture.cut();
                var frozen=org.jacamo.bridge.contract.semantic.SemanticContractCodec.organizationToTree(live.source.semanticContract().moiseOrganizations().getFirst());
                var actual=initial.facts().stream().filter(f->f.kind()==org.jacamo.bridge.contract.RuntimeFactKind.GROUP_BOARD).findFirst().orElseThrow().values().get("organisationDefinition");
                var differences=new java.util.ArrayList<String>();differences(frozen,actual,"OS",differences);System.out.println("AUCTION_RUNTIME_DEFINITION_DIFF="+differences.stream().limit(8).toList());
                projector.applySnapshot(initial,live.run.id(),live.run.generation());
                var system=projector.system();assertSame(pipeline.nativeResult().state().system(),system);
                assertEquals(1,system.state().allObjects().stream().filter(o->o.cls().name().equals("AuctionOrg")).count());
                assertEquals(1,system.state().allObjects().stream().filter(o->o.cls().name().equals("auctionGroup")).count());
                assertEquals(1,system.state().allObjects().stream().filter(o->o.cls().name().equals("auctioneer")).count());
                assertEquals(2,system.state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).count());
                var a=system.state().allObjects().stream().filter(o->o.cls().name().equals("Auction")&&o.state(system.state()).attributeValue("id").toString().equals("'a1'")).findFirst().orElseThrow();
                var b=system.state().allObjects().stream().filter(o->o.cls().name().equals("Auction")&&o!=a).findFirst().orElseThrow();
                assertEquals(Double.MAX_VALUE,((org.tzi.use.uml.ocl.value.RealValue)a.state(system.state()).attributeValue("best_bid")).value());
                assertEquals("'no_winner'",a.state(system.state()).attributeValue("currentWinner").toString());
                live.actor.doAction(first,new Op("start","actual 'quoted' \\ service"),5000);live.actor.doAction(first,new Op("bid",42.5),5000);
                projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());assertSame(system,projector.system());
                assertEquals("42.5",a.state(system.state()).attributeValue("best_bid").toString());assertEquals("'"+live.actor.getName()+"'",a.state(system.state()).attributeValue("currentWinner").toString());
                assertEquals("'no_winner'",a.state(system.state()).attributeValue("winner").toString());assertEquals("'no_winner'",b.state(system.state()).attributeValue("currentWinner").toString());
                assertTrue(projector.trace().stream().anyMatch(t->t.ruleId().equals("AUCTION-XML-R05")));
            }
        }
    }
    static void differences(Object expected,Object actual,String path,java.util.List<String> output) {
        if(expected instanceof java.util.Map<?,?> left&&actual instanceof java.util.Map<?,?> right){var keys=new java.util.HashSet<Object>(left.keySet());keys.addAll(right.keySet());for(var key:keys)differences(left.get(key),right.get(key),path+"."+key,output);return;}
        if(expected instanceof java.util.List<?> left&&actual instanceof java.util.List<?> right){if(left.size()!=right.size()){output.add(path+":sizes="+left.size()+"/"+right.size());return;}for(int i=0;i<left.size();i++)differences(left.get(i),right.get(i),path+"["+i+"]",output);return;}
        if(!java.util.Objects.equals(expected,actual))output.add(path+":"+expected+" != "+actual);
    }
    @Test void actualHelpersUseTheExistingNativeWriterButWrongOrganizationCannotCreateAuctionScope()throws Exception {
        try(var live=new AuctionLiveFixture(true)) {
            var program=new AuctionStaticMappingAdapter().map(live.source,new AuctionStaticMappingAdapter.Selection(live.bundle,live.specification));
            var pipeline=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
            try(var projector=new NativeRuntimeProjector(pipeline,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                var authoritative=pipeline.nativeResult().state().system();projector.applySnapshot(live.cut(),live.run.id(),live.run.generation());
                assertSame(authoritative,projector.system());
                assertTrue(authoritative.state().allObjects().stream().anyMatch(o->o.cls().name().equals("WorkspaceObservation")),"The admitted XML case must reuse native observation helpers");
                // This fixture is actually in /main, while the reviewed case requires aorg.
                // Names/configuration alone cannot manufacture the root context.
                assertEquals(0,authoritative.state().allObjects().stream().filter(o->o.cls().name().equals("AuctionOrg")).count());
                assertEquals(0,authoritative.state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).count());
            }
        }
    }
}
