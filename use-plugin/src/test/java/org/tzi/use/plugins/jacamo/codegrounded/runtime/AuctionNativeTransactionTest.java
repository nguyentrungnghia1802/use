package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.auction.AuctionCastFixture;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter;
import static org.junit.jupiter.api.Assertions.*;

/** Actual source boards/artifacts and existing writer fault seams. */
class AuctionNativeTransactionTest {
    @Test void retirementFailureRollsBackAliasesTombstonesArchiveAndSameObjectThenResyncRetiresIt()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var projector=fixture.projector();var coordinator=projector.coordinator();coordinator.awaitEvaluation(coordinator.latest(),15000);
            var old=new org.tzi.use.plugins.jacamo.semantics.mapping.auction.AuctionOccurrenceBindingAdapter(fixture.program()).resolve(projector.mutations(),fixture.run()).stream().filter(s->org.jacamo.bridge.contract.BridgeEntityId.parse(s.scheme()).localId().equals("sch_castOne")).findFirst().orElseThrow();
            var object=projector.mutations().objectForSemanticId(old.artifact());var before=projector.mutations().savepoint();var aliases=projector.runtimeAliases();var marks=projector.sourceWatermarks();
            fixture.disposeArtifact(0);var cut=fixture.cut();coordinator.faultProbe(point->{if(point.equals("AFTER_NATIVE_ACTION"))throw new IllegalStateException("R04_RETIREMENT_ROLLBACK");});
            assertThrows(IllegalStateException.class,()->projector.applySnapshot(cut,fixture.run().id(),fixture.run().generation()));coordinator.originalEngineDrained().get(15,TimeUnit.SECONDS);coordinator.read(()->null);
            assertSame(object,projector.mutations().objectForSemanticId(old.artifact()));assertEquals(before.bindings().toMap(),projector.mutations().savepoint().bindings().toMap());assertEquals(before.tombstones(),projector.mutations().savepoint().tombstones());
            assertEquals(aliases,projector.runtimeAliases());assertEquals(marks,projector.sourceWatermarks());assertNotEquals("ARCHIVED",projector.mutations().metadata(projector.mutations().objectForSemanticId(old.id()),"auctionMonitorLifecycle"));
            coordinator.faultProbe(point->{});fixture.resync();coordinator.awaitEvaluation(coordinator.latest(),15000);assertNull(projector.mutations().objectForSemanticId(old.artifact()));
            assertFalse(coordinator.originalEngine().checkpoint().get(10,TimeUnit.SECONDS).histories().containsKey(old.id()),"Retired live marking session is closed; archived evidence remains in the native monitor and journal");
        }
    }
    @Test void rejectedRealOperationEventRollsBackProtocolAndNativeObserverBindingsTogether()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var projector=fixture.projector();var coordinator=projector.coordinator();coordinator.awaitEvaluation(coordinator.latest(),15000);
            var before=projector.mutations().savepoint();var marks=projector.sourceWatermarks();var aliases=projector.runtimeAliases();var evidence=projector.evidence();
            String state=new NativeUseSoilExporter().export(projector.system()).commands();fixture.startAndEnableBid(0);
            var actual=fixture.eventsSince(marks).getFirst();assertEquals(org.jacamo.bridge.contract.RuntimeEventKind.STARTED,actual.kind());
            coordinator.faultProbe(stage->{if(stage.equals("AFTER_NATIVE_ACTION"))throw new IllegalStateException("U02_REAL_EVENT_ROLLBACK");});
            var failure=assertThrows(IllegalStateException.class,()->projector.apply(actual));assertEquals("U02_REAL_EVENT_ROLLBACK",failure.getMessage());
            coordinator.originalEngineDrained().get(10,TimeUnit.SECONDS);coordinator.read(()->null);
            assertEquals(state,new NativeUseSoilExporter().export(projector.system()).commands());assertEquals(before.bindings().toMap(),projector.mutations().savepoint().bindings().toMap());
            assertEquals(marks,projector.sourceWatermarks());assertEquals(aliases,projector.runtimeAliases());assertEquals(evidence,projector.evidence());assertTrue(projector.resyncRequired());
            coordinator.faultProbe(stage->{});fixture.resync();assertFalse(projector.resyncRequired());
            assertTrue(projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).anyMatch(o->o.state(projector.system().state()).attributeValue("running").toString().equals("true")));
        }
    }
    @Test void rejectedSnapshotRestoresMonitorObligationClockFreezeAliasesWatermarksAndExpansionHistory()throws Exception {
        for(String point:List.of("AFTER_NATIVE_ACTION","AFTER_NATIVE_VALIDATION"))try(var fixture=new AuctionCastFixture(1,1,true)) {
            fixture.startAndEnableBid(0);fixture.completeBid(0,0);fixture.resync();var projector=fixture.projector();var coordinator=projector.coordinator();
            coordinator.awaitEvaluation(coordinator.latest(),15000);var owner=coordinator.originalEngine();
            assertTrue(projector.system().state().allObjects().stream().anyMatch(o->o.cls().name().equals("AuctionObligation")));
            assertTrue(projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("AuctionMonitor")).anyMatch(o->projector.mutations().metadata(o,"auctionMonitorSource").contains("\"bidCommittedAtSatisfaction\"")),"Rollback image must contain an actual first-satisfaction freeze");
            var state=new NativeUseSoilExporter().export(projector.system()).commands();var before=projector.mutations().savepoint();
            var aliases=projector.runtimeAliases();var marks=projector.sourceWatermarks();var evidence=projector.evidence();String snapshot=projector.snapshotId();
            var history=owner.checkpoint().get(10,TimeUnit.SECONDS);var nativeObjects=Set.copyOf(projector.system().state().allObjects());
            fixture.additionalScheme("transactionThird");fixture.startAndEnableBid(2);var next=fixture.cut();
            coordinator.faultProbe(stage->{if(stage.equals(point))throw new IllegalStateException(point);});
            assertThrows(IllegalStateException.class,()->projector.applySnapshot(next,fixture.run().id(),fixture.run().generation()));
            coordinator.originalEngineDrained().get(10,TimeUnit.SECONDS);coordinator.read(()->null);
            assertEquals(state,new NativeUseSoilExporter().export(projector.system()).commands());
            assertEquals(before.bindings().toMap(),projector.mutations().savepoint().bindings().toMap(),"Observer latch/clock/freeze and role intervals share the exact native savepoint");
            assertEquals(nativeObjects,projector.system().state().allObjects(),"No half-created third monitor or obligation");
            assertEquals(aliases,projector.runtimeAliases());assertEquals(marks,projector.sourceWatermarks());assertEquals(evidence,projector.evidence());assertEquals(snapshot,projector.snapshotId());
            assertEquals(history.histories(),owner.checkpoint().get(10,TimeUnit.SECONDS).histories());assertTrue(projector.resyncRequired());
            assertEquals("STALE",coordinator.latest().freshness());assertEquals(0,owner.metrics().get("pendingPins"));
            var evidencePath=java.nio.file.Path.of("target/auction-implementation/U02-rollback-"+point+".json");java.nio.file.Files.createDirectories(evidencePath.getParent());
            java.nio.file.Files.write(evidencePath,org.jacamo.bridge.contract.CanonicalJson.encode(Map.of("scope","U02_REAL_BOARDS_NATIVE_TRANSACTION_NOT_E01","fault",point,"stateSha256",org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(state.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"bindings",before.bindings().toMap(),"watermarks",marks,"expansions",history.histories().size(),"nativeObjects",nativeObjects.size(),"resyncRequired",projector.resyncRequired())));
        }
    }
    @Test void publicationFailureKeepsActualNativeFactsAndCannotPublishCurrentSuccess()throws Exception {
        try(var fixture=new AuctionCastFixture(1,1,true)) {
            var projector=fixture.projector();var coordinator=projector.coordinator();coordinator.awaitEvaluation(coordinator.latest(),15000);
            coordinator.faultProbe(stage->{if(stage.equals("BEFORE_SNAPSHOT_COMPLETE"))throw new IllegalStateException(stage);});
            fixture.startAndEnableBid(0);fixture.resync();coordinator.originalEngineDrained().get(15,TimeUnit.SECONDS);coordinator.read(()->null);
            assertTrue(projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).anyMatch(o->o.state(projector.system().state()).attributeValue("running").toString().equals("true")));
            assertTrue(coordinator.journal().hasGap());assertEquals("STALE",coordinator.latest().freshness());assertEquals("INCOMPLETE",coordinator.latest().coverage());
        }
    }
}
