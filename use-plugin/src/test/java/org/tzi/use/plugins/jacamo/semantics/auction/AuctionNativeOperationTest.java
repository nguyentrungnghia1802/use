package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import java.util.concurrent.*;
import cartago.*;
import cartago.util.agent.CartagoBasicContext;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.semantic.engine.use75.NativeOclCompilation;
import org.tzi.use.uml.mm.MModel;
import static org.junit.jupiter.api.Assertions.*;

/** Actual pinned Artifact callbacks into the production native owner. Clauses
 * prefixed O04 are explicit test contracts, never edits to the case oracle.
 * This component gate is not installed external-process E01/E02 acceptance. */
class AuctionNativeOperationTest {
    @Test void repeatedStartAndStopFailWithoutResettingTheActualStateOrCreatingJasonTaskCredit()throws Exception {
        try(var f=new AuctionCastFixture(1)) {
            invoke(f,0,f.org.live.actor,new Op("start","first service"),false);
            invoke(f,0,f.participants.getFirst(),new Op("bid",12.5),false);
            var object=artifact(f,f.artifacts.getFirst());var before=values(f,object);
            var startAgain=invoke(f,0,f.org.live.actor,new Op("start","must not overwrite"),true);
            assertEquals(before,values(f,object));
            assertTrue(startAgain.rows().stream().anyMatch(r->r.kind().equals("PRE")&&r.verdict()==SemanticVerdict.FAIL));
            assertTrue(startAgain.rows().stream().anyMatch(r->r.kind().equals("INVOCATION")&&r.verdict()==SemanticVerdict.FAIL));
            invoke(f,0,f.org.live.actor,new Op("stop"),false);before=values(f,object);
            var stopAgain=invoke(f,0,f.org.live.actor,new Op("stop"),true);assertEquals(before,values(f,object));
            assertTrue(stopAgain.rows().stream().anyMatch(r->r.kind().equals("PRE")&&r.verdict()==SemanticVerdict.FAIL));
            assertTrue(stopAgain.rows().stream().noneMatch(r->r.kind().equals("TASK_POST")),"Physical source failure cannot manufacture a Jason execution result");
            assertEquals("12.5",before.get("best_bid"));assertFalse(f.projector.resyncRequired());
        }
    }
    private static Map<String,String> values(AuctionCastFixture f,org.tzi.use.uml.sys.MObject object) {
        var result=new TreeMap<String,String>();for(var attribute:object.cls().attributes())result.put(attribute.name(),object.state(f.projector.system().state()).attributeValue(attribute).toString());return result;
    }
    record Invocation(RuntimeEvent terminal,List<SemanticConstraintResult> rows) { }
    static void contracts(MModel model) {
        var api=new UseModelApi(model);var auction=model.getClass("Auction");
        NativeOclCompilation.condition(api,auction.operation("start",false),"O04StartTransition","not self.running@pre and self.running and self.task = service",false);
        NativeOclCompilation.condition(api,auction.operation("bid",false),"O04BidMonotone","self.best_bid <= self.best_bid@pre",false);
        NativeOclCompilation.condition(api,auction.operation("bid",false),"O04BidStrictDecrease","self.best_bid < self.best_bid@pre",false);
    }
    @Test void actualSourceStartImprovingNonImprovingStopAndFailedBidKeepNativeCutsAndKnownFalseDistinct()throws Exception {
        try(var f=new AuctionCastFixture(1,1,false,AuctionNativeOperationTest::contracts)) {
            var system=f.projector.system();String service="flight 'quoted' \\ route";
            var start=invoke(f,0,f.org.live.actor,new Op("start",service),false);
            var transition=condition(start,"O04StartTransition");assertEquals(SemanticVerdict.PASS,transition.verdict(),transition.toString());
            assertEquals("false",transition.witness().get("before.running"));assertEquals("true",transition.witness().get("after.running"));
            cutsAndPerformer(f,start,transition);
            var improving=invoke(f,0,f.participants.getFirst(),new Op("bid",42.5),false);
            assertEquals(SemanticVerdict.PASS,condition(improving,"O04BidMonotone").verdict());assertEquals(SemanticVerdict.PASS,condition(improving,"O04BidStrictDecrease").verdict());
            assertEquals(Double.MAX_VALUE,Double.parseDouble(condition(improving,"O04BidMonotone").witness().get("before.best_bid")));
            assertEquals(42.5,Double.parseDouble(condition(improving,"O04BidMonotone").witness().get("after.best_bid")));
            cutsAndPerformer(f,improving,condition(improving,"O04BidMonotone"));
            var nonImproving=invoke(f,0,f.org.live.actor,new Op("bid",100.0),false);
            assertEquals(SemanticVerdict.PASS,condition(nonImproving,"O04BidMonotone").verdict());
            assertEquals(SemanticVerdict.FAIL,condition(nonImproving,"O04BidStrictDecrease").verdict(),"The source operation succeeds but the explicitly authored strict-decrease test predicate is truthfully false");
            assertEquals("42.5",condition(nonImproving,"O04BidMonotone").witness().get("before.best_bid"));assertEquals("42.5",condition(nonImproving,"O04BidMonotone").witness().get("after.best_bid"));
            var stop=invoke(f,0,f.org.live.actor,new Op("stop"),false);assertTrue(stop.rows().stream().anyMatch(r->r.kind().equals("POST")&&r.verdict()==SemanticVerdict.PASS));
            var failed=invoke(f,0,f.participants.getFirst(),new Op("bid",1.0),true);
            assertTrue(failed.rows().stream().anyMatch(r->r.kind().equals("INVOCATION")&&r.verdict()==SemanticVerdict.FAIL&&r.diagnostic().startsWith("SOURCE_REPORTED_OPERATION_FAILURE")),failed.rows().toString());
            assertTrue(failed.rows().stream().filter(r->r.kind().equals("POST")).allMatch(r->r.verdict()==SemanticVerdict.SKIPPED&&r.diagnostic().equals("OPERATION_FAILED_OR_ABORTED")));
            assertTrue(failed.rows().stream().noneMatch(r->r.kind().equals("TASK_POST")),"Physical operation failure cannot manufacture a Jason Task result");
            assertSame(system,f.projector.system());assertFalse(f.projector.resyncRequired());
            java.nio.file.Files.write(java.nio.file.Path.of("target/auction-implementation/O04-real-operations.json"),CanonicalJson.encode(List.of(start,improving,nonImproving,stop,failed).stream().map(i->Map.of("source",ContractPayloads.event(i.terminal()),"native",SemanticJson.tree(i.rows()))).toList()));
        }
    }
    @Test void concurrentRealCallersRetainDistinctCorrelationsAndNeverMutateTheOtherAuction()throws Exception {
        try(var f=new AuctionCastFixture(1,1,false,AuctionNativeOperationTest::contracts)) {
            invoke(f,0,f.org.live.actor,new Op("start","first"),false);invoke(f,1,f.org.live.actor,new Op("start","second"),false);
            var marks=f.projector.sourceWatermarks();var pool=Executors.newFixedThreadPool(2);
            try {
                var a=pool.submit(()->{f.org.live.actor.doAction(f.artifacts.getFirst(),new Op("bid",50.0),5000);return true;});
                var b=pool.submit(()->{f.participants.getFirst().doAction(f.artifacts.getFirst(),new Op("bid",25.0),5000);return true;});
                assertTrue(a.get(10,TimeUnit.SECONDS));assertTrue(b.get(10,TimeUnit.SECONDS));
            }finally{pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
            AuctionMonitorProjectionTest.await(()->terminals(f.eventsSince(marks),"bid").size()==2);
            var terminals=terminals(f.eventsSince(marks),"bid");var rows=drain(f,marks);var callers=new HashSet<String>();
            for(var terminal:terminals) {
                var invocation=new Invocation(terminal,rows.stream().filter(r->r.invocation().equals(terminal.correlationId())).distinct().toList());
                var monotone=condition(invocation,"O04BidMonotone");assertEquals(SemanticVerdict.PASS,monotone.verdict(),monotone.toString());cutsAndPerformer(f,invocation,monotone);callers.add(monotone.witness().get("performerSemanticId"));
            }
            assertEquals(2,callers.size());assertNotEquals(terminals.getFirst().correlationId(),terminals.getLast().correlationId());
            var first=artifact(f,f.artifacts.getFirst());var second=artifact(f,f.artifacts.getLast());
            assertEquals("25.0",first.state(f.projector.system().state()).attributeValue("best_bid").toString());assertEquals(Double.MAX_VALUE,Double.parseDouble(second.state(f.projector.system().state()).attributeValue("best_bid").toString()));
        }
    }
    static Invocation invoke(AuctionCastFixture f,int index,CartagoBasicContext actor,Op operation,boolean fails)throws Exception {
        var marks=f.projector.sourceWatermarks();
        if(fails)assertThrows(CartagoException.class,()->actor.doAction(f.artifacts.get(index),operation,5000));else actor.doAction(f.artifacts.get(index),operation,5000);
        AuctionMonitorProjectionTest.await(()->!terminals(f.eventsSince(marks),operation.getName()).isEmpty());
        var terminal=terminals(f.eventsSince(marks),operation.getName()).getLast();assertEquals(fails?RuntimeEventKind.FAILED:RuntimeEventKind.SUCCEEDED,terminal.kind());
        return new Invocation(terminal,drain(f,marks).stream().filter(r->r.invocation().equals(terminal.correlationId())).distinct().toList());
    }
    static List<RuntimeEvent> terminals(List<RuntimeEvent> events,String operation){return events.stream().filter(e->e.subsystem().equals("cartago")&&Set.of(RuntimeEventKind.SUCCEEDED,RuntimeEventKind.FAILED).contains(e.kind())&&operation.equals(e.after().get("operation"))).toList();}
    static List<SemanticConstraintResult> drain(AuctionCastFixture f,Map<String,Long> marks) {
        var rows=new ArrayList<SemanticConstraintResult>();for(var event:f.eventsSince(marks)){f.projector.apply(event);var coordinator=f.projector.coordinator();rows.addAll(coordinator.awaitEvaluation(coordinator.latest(),15000).semantic().constraints());}return rows;
    }
    static SemanticConstraintResult condition(Invocation invocation,String name){return invocation.rows().stream().filter(r->r.kind().equals("POST")&&r.id().endsWith("::"+name)).findFirst().orElseThrow(()->new AssertionError(name+" missing: "+invocation.rows()));}
    static org.tzi.use.uml.sys.MObject artifact(AuctionCastFixture f,ArtifactId id){return f.projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")&&id.getId().toString().equals(f.projector.mutations().metadata(o,"uuid"))).findFirst().orElseThrow();}
    static void cutsAndPerformer(AuctionCastFixture f,Invocation invocation,SemanticConstraintResult row) {
        var witness=row.witness();assertTrue(witness.getOrDefault("preStateHash","").matches("[0-9a-f]{64}"),"Native operation evidence must identify its actual borrowed PRE: "+witness);
        assertTrue(witness.getOrDefault("postStateHash","").matches("[0-9a-f]{64}"));assertTrue(witness.getOrDefault("operationDescriptorHash","").matches("[0-9a-f]{64}"));
        assertEquals(invocation.terminal().after().get("performerSemanticId"),witness.get("performerSourceId"));assertEquals("CARTAGO_OPID_AGENT_BODY_ID",witness.get("performerSourceAuthority"));
        var actor=f.projector.mutations().objectForSemanticId(witness.get("performerSourceId"));assertNotNull(actor);assertEquals(f.projector.mutations().metadata(actor,"semanticId"),witness.get("performerSemanticId"));
    }
}
