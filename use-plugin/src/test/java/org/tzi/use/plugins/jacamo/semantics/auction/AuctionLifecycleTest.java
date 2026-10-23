package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionLifecycleTest {
    @Test void actualSchemeDisposalArchivesItsObserverWithoutDeletingTheIndependentBusinessArtifact()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var projector=fixture.projector;var old=new AuctionOccurrenceBindingAdapter(fixture.program).resolve(projector.mutations(),fixture.run()).stream().filter(s->BridgeEntityId.parse(s.scheme()).localId().equals("sch_castOne")).findFirst().orElseThrow();
            var business=projector.mutations().objectForSemanticId(old.artifact());var marks=projector.sourceWatermarks();fixture.org.live.dispose(fixture.schemes.getFirst());for(var event:fixture.eventsSince(marks))projector.apply(event);
            assertSame(business,projector.mutations().objectForSemanticId(old.artifact()));assertEquals("ARCHIVED",projector.mutations().metadata(projector.mutations().objectForSemanticId(old.id()),"auctionMonitorLifecycle"));
            fixture.org.live.actor.doAction(fixture.artifacts.getFirst(),new cartago.Op("start","independent of retired scheme"),5000);
        }
    }
    @Test void staleRemovalAndSnapshotCannotCrossTheAdmittedGeneration()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var projector=fixture.projector;var scope=new AuctionOccurrenceBindingAdapter(fixture.program).resolve(projector.mutations(),fixture.run()).getFirst();var object=projector.mutations().objectForSemanticId(scope.artifact());var marks=projector.sourceWatermarks();
            fixture.org.live.dispose(fixture.artifacts.stream().filter(a->a.getId().toString().equals(projector.mutations().metadata(object,"uuid"))).findFirst().orElseThrow());
            var event=fixture.eventsSince(marks).stream().filter(e->e.kind()==RuntimeEventKind.DISPOSED).findFirst().orElseThrow();
            var stale=new RuntimeEvent(event.eventId(),event.sessionId(),event.generation()-1,event.modelRevision(),event.subsystem(),event.sourceId(),event.sourceSequence(),event.observedAt(),event.kind(),event.factKind(),event.projectionStatus(),event.entityId(),event.relationId(),event.correlationId(),event.causationId(),event.before(),event.after(),event.watermark(),event.completeness(),event.evidence());
            assertThrows(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProtocolException.class,()->projector.apply(stale));assertSame(object,projector.mutations().objectForSemanticId(scope.artifact()));
            fixture.resync();var oldCut=fixture.cut();var now=java.time.Instant.now();var next=new RuntimeSnapshot("generation-empty-boundary",fixture.run().modelRevision(),now,now,Map.of(),Map.of(),1,List.of(),Map.of("cartago",Completeness.COMPLETE),"explicit-empty-new-epoch-fixture");
            projector.applySnapshot(next,fixture.run().id(),fixture.run().generation()+1);assertTrue(projector.system().state().allObjects().stream().noneMatch(o->o.cls().name().equals("AuctionMonitor")||o.cls().name().equals("AuctionObligation")));
            assertThrows(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProtocolException.class,()->projector.apply(event));assertThrows(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProtocolException.class,()->projector.applySnapshot(oldCut,fixture.run().id(),fixture.run().generation()));
            assertNull(projector.mutations().objectForSemanticId(scope.artifact()));
        }
    }
    @Test void missingMoiseBoardIsNotDisposalAndSameNameGroupRecreationGetsFreshRoleIntervals()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var projector=fixture.projector;var old=new AuctionOccurrenceBindingAdapter(fixture.program).resolve(projector.mutations(),fixture.run()).getFirst();
            var group=projector.mutations().objectForSemanticId(old.context());var roles=projector.system().state().allObjects().stream().filter(o->old.context().equals(projector.mutations().metadata(o,"roleContext"))).toList();
            var cut=fixture.cut();var facts=cut.facts().stream().filter(f->f.kind()!=RuntimeFactKind.GROUP_BOARD).toList();
            projector.applySnapshot(new RuntimeSnapshot("moise-board-absent",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,facts,cut.sourceCompleteness(),"injected-polling-absence"),fixture.run().id(),fixture.run().generation());
            assertSame(group,projector.mutations().objectForSemanticId(old.context()));assertTrue(projector.system().state().allObjects().containsAll(roles));
            assertEquals("INCOMPLETE",projector.mutations().metadata(group,"auctionPopulationQuality"));
            var marks=projector.sourceWatermarks();fixture.org.live.dispose(fixture.group);for(var event:fixture.eventsSince(marks))projector.apply(event);assertNull(projector.mutations().objectForSemanticId(old.context()));
            // Retire the exact disposed instance in this fixture-owned board
            // supplier. A cached disposed board itself reports EVIDENCE_ONLY;
            // it is not a second live incarnation of the replacement.
            assertEquals(1,fixture.org.groups.size());assertEquals(fixture.group.getName(),fixture.org.groups.getFirst().getArtId());fixture.org.groups.removeFirst();
            var freshId=fixture.org.group();assertNotEquals(fixture.group.getId(),freshId.getId());fixture.org.live.actor.doAction(freshId,new cartago.Op("addScheme",fixture.schemes.getFirst().getName()),5000);fixture.resync();
            var freshGroups=projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("auctionGroup")).toList();assertEquals(1,freshGroups.size());var fresh=freshGroups.getFirst();assertNotSame(group,fresh);
            assertNotEquals(old.context(),projector.mutations().metadata(fresh,"semanticId"));assertTrue(projector.runtimeAliases().values().stream().noneMatch(a->a.targetUseId().equals(group.name())));
            var freshRoles=projector.system().state().allObjects().stream().filter(o->projector.mutations().metadata(fresh,"semanticId").equals(projector.mutations().metadata(o,"roleContext"))).toList();assertFalse(freshRoles.isEmpty());
            assertTrue(roles.stream().noneMatch(projector.system().state().allObjects()::contains));assertTrue(freshRoles.stream().noneMatch(r->roles.stream().anyMatch(oldRole->oldRole.name().equals(r.name()))));
        }
    }
    @Test void retiredWorkspaceLoggerCannotObserveAReusedPathAndCloseUsesTheOriginalOwner()throws Exception {
        try(var live=new AuctionLiveFixture(true)) {
            var id=live.otherWorkspace("reused");var old=live.environment.resolveWSP(id.getFullName());assertTrue(old.getWorkspace().getLoggerManager().isLogging());
            old.getWorkspace().shutdown();var parent=old.getParentInfo().getWorkspace();synchronized(parent){assertTrue(parent.getChildWSPs().removeIf(child->child==old));}live.joined.remove(id);
            var fresh=live.otherWorkspace("reused");assertNotEquals(id,fresh);assertFalse(old.getWorkspace().getLoggerManager().isLogging(),"The source must unregister the retired owner, not resolve its old path");
            var freshWorkspace=live.environment.resolveWSP(fresh.getFullName()).getWorkspace();assertTrue(freshWorkspace.getLoggerManager().isLogging());var artifact=live.auction("fresh",fresh);
            assertTrue(live.cartago.capture().stream().anyMatch(f->f.kind()==RuntimeFactKind.ARTIFACT&&artifact.getId().toString().equals(f.values().get("uuid"))));
            live.cartago.close();assertFalse(freshWorkspace.getLoggerManager().isLogging());
        }
    }
    @Test void actualGroupDisposalRetiresItsProjectionRolesWithoutDisposingEitherBusinessArtifact()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var projector=fixture.projector;var scope=new AuctionOccurrenceBindingAdapter(fixture.program).resolve(projector.mutations(),fixture.run()).getFirst();var context=projector.mutations().objectForSemanticId(scope.context());var marks=projector.sourceWatermarks();
            var auctions=projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).toList();
            fixture.org.live.dispose(fixture.group);for(var event:fixture.eventsSince(marks))projector.apply(event);
            assertNull(projector.mutations().objectForSemanticId(scope.context()));assertFalse(projector.system().state().allObjects().contains(context));
            assertTrue(projector.system().state().allObjects().containsAll(auctions));assertTrue(projector.system().state().allObjects().stream().noneMatch(o->scope.context().equals(projector.mutations().metadata(o,"roleContext"))));
            var live=fixture.org.live.cartago.capture();for(var artifact:fixture.artifacts)assertTrue(live.stream().anyMatch(f->f.kind()==RuntimeFactKind.ARTIFACT&&artifact.getId().toString().equals(f.values().get("uuid"))));
        }
    }
    @Test void actualOrganizationWorkspaceRetirementDetachesTheStillLiveArtifactsOutsideThatWorkspace()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var projector=fixture.projector;var old=new AuctionOccurrenceBindingAdapter(fixture.program).resolve(projector.mutations(),fixture.run()).getFirst();var auctions=projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).toList();
            var descriptor=fixture.org.live.environment.resolveWSP(fixture.org.organization.getFullName());descriptor.getWorkspace().shutdown();var parent=descriptor.getParentInfo().getWorkspace();
            synchronized(parent){assertTrue(parent.getChildWSPs().removeIf(child->child==descriptor&&child.getId().equals(fixture.org.organization)));}
            // These are fixture-owned registry entries of the workspace just
            // stopped/removed through the real public CArtAgO APIs.
            fixture.org.live.artifacts.removeIf(a->a.getWorkspaceId().equals(fixture.org.organization));fixture.org.live.joined.removeIf(w->w.equals(fixture.org.organization));
            var facts=new ArrayList<>(fixture.org.moise.capture());facts.addAll(fixture.org.live.cartago.capture());var marks=Map.of(fixture.org.moise.sourceId(),fixture.org.moise.watermark(),fixture.org.live.cartago.sourceId(),fixture.org.live.cartago.watermark());var now=java.time.Instant.now();
            assertEquals(Completeness.COMPLETE,fixture.org.live.cartago.completeness());
            projector.applySnapshot(new RuntimeSnapshot("actual-organization-workspace-retirement",fixture.run().modelRevision(),now,now,marks,marks,1,facts,Map.of(fixture.org.moise.sourceId(),Completeness.PARTIAL,"cartago",fixture.org.live.cartago.completeness()),"actual-retired-workspace-enumeration"),fixture.run().id(),fixture.run().generation());
            assertNull(projector.mutations().objectForSemanticId(old.organization()));assertTrue(projector.system().state().allObjects().containsAll(auctions));
            assertTrue(projector.system().state().allLinks().stream().noneMatch(l->l.association().name().equals(AuctionUseModelAdapter.ENTITY_SCOPES)));
            fixture.org.live.actor.doAction(fixture.artifacts.getFirst(),new cartago.Op("start","still physically live"),5000);
        }
    }
    @Test void actualBidExpiryAfterArtifactDisposalStillUpdatesTheOriginalArchivedActivation()throws Exception {
        try(var fixture=new AuctionCastFixture(1,1,true)) {
            fixture.startAndEnableBid(0);fixture.resync();var projector=fixture.projector;
            var obligation=projector.system().state().allObjects().stream().filter(o->o.cls().name().equals("AuctionObligation")&&new org.tzi.use.uml.ocl.value.StringValue("bid").equals(o.state(projector.system().state()).attributeValue("goal"))).findFirst().orElseThrow();
            String binding=projector.mutations().metadata(obligation,"auctionObligationArtifact");fixture.org.live.dispose(fixture.artifacts.getFirst());fixture.resync();
            AuctionMonitorProjectionTest.await(()->fixture.org.live.events.stream().anyMatch(e->e.kind()==RuntimeEventKind.NORM_UNFULFILLED&&e.after().getOrDefault("aim","").toString().contains(",bid,")),15000);fixture.resync();
            assertEquals(org.tzi.use.uml.ocl.value.BooleanValue.TRUE,obligation.state(projector.system().state()).attributeValue("unfulfilled"),"Physical disposal cannot hide a later real norm violation");
            assertEquals("UNFULFILLED",projector.mutations().metadata(obligation,"normReportedState"));assertEquals(binding,projector.mutations().metadata(obligation,"auctionObligationArtifact"));
            var ended=obligation.state(projector.system().state()).attributeValue("endedAt");fixture.org.live.actor.doAction(fixture.schemes.getFirst(),new cartago.Op("admCommand","goalSatisfied(bid)"),5000);fixture.resync();
            assertEquals(org.tzi.use.uml.ocl.value.BooleanValue.TRUE,obligation.state(projector.system().state()).attributeValue("unfulfilled"));assertEquals(ended,obligation.state(projector.system().state()).attributeValue("endedAt"));
        }
    }
    @Test void actualCompleteArtifactAbsenceRemovesOnlyItsIncarnationAndArchivesItsMonitor()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var projector=fixture.projector;var initial=new AuctionOccurrenceBindingAdapter(fixture.program).resolve(projector.mutations(),fixture.org.live.run);
            var old=initial.stream().filter(c->BridgeEntityId.parse(c.scheme()).localId().equals("sch_castOne")).findFirst().orElseThrow();var other=initial.stream().filter(c->!c.id().equals(old.id())).findFirst().orElseThrow();
            var first=projector.mutations().objectForSemanticId(old.artifact());var second=projector.mutations().objectForSemanticId(other.artifact());assertNotNull(first);assertNotNull(second);
            var monitorBefore=projector.mutations().objectForSemanticId(old.id());var committed=monitorBefore.state(projector.system().state()).attributeValue("bidCommittedBy");
            fixture.org.live.dispose(fixture.artifacts.getFirst());fixture.resync();
            assertNull(projector.mutations().objectForSemanticId(old.artifact()),"R06 must retire a proved absent physical incarnation");
            assertSame(second,projector.mutations().objectForSemanticId(other.artifact()));assertFalse(projector.system().state().allObjects().contains(first));
            var monitor=projector.mutations().objectForSemanticId(old.id());assertNotNull(monitor,"Historical observer evidence is retained");assertEquals("ARCHIVED",projector.mutations().metadata(monitor,"auctionMonitorLifecycle"));
            assertEquals(committed,monitor.state(projector.system().state()).attributeValue("bidCommittedBy"));assertNotEquals("COMPLETE",projector.mutations().metadata(monitor,"auctionObserverQuality:bidCommittedBy"));
        }
    }
    @Test void partialOmissionDoesNotRetireAnArtifactOrArchiveItsStillUnprovedScope()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var projector=fixture.projector;var old=new AuctionOccurrenceBindingAdapter(fixture.program).resolve(projector.mutations(),fixture.org.live.run).getFirst();var object=projector.mutations().objectForSemanticId(old.artifact());
            var cut=fixture.cut();var facts=cut.facts().stream().filter(f->!old.artifact().equals(f.values().get("semanticId"))&&!old.artifact().equals(f.values().get("artifactSemanticId"))).toList();
            projector.applySnapshot(new RuntimeSnapshot("partial-absence",cut.modelRevision(),cut.captureStartedAt(),cut.captureEndedAt(),cut.startWatermarks(),cut.endWatermarks(),1,facts,Map.of("cartago",Completeness.PARTIAL),"injected-partial-absence"),fixture.run().id(),fixture.run().generation());
            assertSame(object,projector.mutations().objectForSemanticId(old.artifact()));var monitor=projector.mutations().objectForSemanticId(old.id());assertEquals("UNAVAILABLE",projector.mutations().metadata(monitor,"auctionMonitorLifecycle"));
        }
    }
    @Test void actualDisposalAndSameNameRecreationOverEventsCannotReuseAnOldAliasMonitorOrEpisode()throws Exception {
        try(var fixture=new AuctionCastFixture(1)) {
            var projector=fixture.projector;var scopes=new AuctionOccurrenceBindingAdapter(fixture.program).resolve(projector.mutations(),fixture.org.live.run);var old=scopes.stream().filter(c->BridgeEntityId.parse(c.scheme()).localId().equals("sch_castOne")).findFirst().orElseThrow();
            var marks=projector.sourceWatermarks();fixture.org.live.dispose(fixture.artifacts.getFirst());var replacement=fixture.org.live.auction("castOne");assertNotEquals(fixture.artifacts.getFirst().getId(),replacement.getId());
            var events=fixture.eventsSince(marks);assertTrue(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.DISPOSED));for(var event:events)projector.apply(event);
            assertNull(projector.mutations().objectForSemanticId(old.artifact()));var fresh=new AuctionOccurrenceBindingAdapter(fixture.program).resolve(projector.mutations(),fixture.org.live.run).stream().filter(c->c.complete()&&c.scheme().equals(old.scheme())).findFirst().orElseThrow();
            assertNotEquals(old.artifact(),fresh.artifact());assertNotEquals(old.id(),fresh.id());assertNotEquals(old.revision(),fresh.revision());assertEquals("ARCHIVED",projector.mutations().metadata(projector.mutations().objectForSemanticId(old.id()),"auctionMonitorLifecycle"));
            var disposal=events.stream().filter(e->e.kind()==RuntimeEventKind.DISPOSED).findFirst().orElseThrow();assertFalse(projector.apply(disposal),"An exact duplicate removal is idempotent and cannot remove the replacement");assertNotNull(projector.mutations().objectForSemanticId(fresh.artifact()));
        }
    }
}
