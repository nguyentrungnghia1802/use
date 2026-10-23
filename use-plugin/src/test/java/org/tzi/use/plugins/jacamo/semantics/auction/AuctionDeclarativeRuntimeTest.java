package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.CslRuntimeTestRegistry;
import org.tzi.use.plugins.jacamo.codegrounded.CodeGroundedNativePipeline;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericMappingEngine;
import static org.junit.jupiter.api.Assertions.*;

/** Actual framework boards on the generic native projector; this is not live external TCP acceptance. */
class AuctionDeclarativeRuntimeTest {
    @Test void actualOrganizationAndRoleMembershipUseOnlyXmlRulesOnOneNativeState()throws Exception {
        try(var fixture=new AuctionOrgFixture()) {
            var live=fixture.live;fixture.organizationBoard();var group=fixture.group();
            var participant=AuctionMonitorProjectionTest.participant(fixture,group);
            try(var registration=CslRuntimeTestRegistry.bind(live.source.semanticContract(),participant.getName(),"alice")) {
                var program=new GenericMappingEngine().map(live.source,AuctionDeclarativeSpecificationTest.inputs(AuctionDeclarativeSpecificationTest.CASE));
                var built=new CodeGroundedNativePipeline().buildCanonicalProgram(live.source,NativeProjectionMode.AUTO,program);
                try(var projector=new NativeRuntimeProjector(built,live.run.id(),live.run.generation(),live.source.modelRevision())) {
                    projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());
                    var system=projector.system();assertSame(built.nativeResult().state().system(),system);
                    var objects=system.state().allObjects();
                    for(String type:List.of("AuctionOrg","auctionGroup","auctioneer","participant"))assertEquals(1,objects.stream().filter(o->o.cls().name().equals(type)).count(),type+" must come from an actual qualified observation");
                    assertEquals(1,system.state().allLinks().stream().filter(l->l.association().name().equals("CslContextScopes")).count());
                    assertEquals(2,system.state().allLinks().stream().filter(l->l.association().name().equals("Plays")).count());
                    assertEquals(2,system.state().allLinks().stream().filter(l->l.association().name().equals("InContext")).count());
                    assertTrue(projector.trace().stream().anyMatch(t->t.ruleId().startsWith("XML-M0-")));
                    assertNull(program.casePlan());assertTrue(projector.mutations().xmlCase());
                    projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());
                    assertSame(system,projector.system());assertEquals(objects.size(),system.state().numObjects(),"A second cut must update the same identities");
                    var oldRole=system.state().allObjects().stream().filter(o->o.cls().name().equals("participant")).findFirst().orElseThrow();
                    String oldInterval=projector.mutations().metadata(oldRole,"xml:m0:lifetime");
                    participant.doAction(group,new cartago.Op("leaveRole","participant"),5000);
                    projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());
                    assertEquals(0,system.state().allObjects().stream().filter(o->o.cls().name().equals("participant")).count(),"An actual complete board membership cut can retire this group's role even if the overall board population is PARTIAL");
                    participant.doAction(group,new cartago.Op("adoptRole","participant"),5000);
                    projector.applySnapshot(fixture.cut(),live.run.id(),live.run.generation());
                    var next=system.state().allObjects().stream().filter(o->o.cls().name().equals("participant")).findFirst().orElseThrow();
                    assertNotEquals(oldRole.name(),next.name());assertNotEquals(oldInterval,projector.mutations().metadata(next,"xml:m0:lifetime"));
                }
            }finally{CslRuntimeTestRegistry.closeCartagoContext(participant);}
        }
    }
}
