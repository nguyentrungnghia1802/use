package org.tzi.use.plugins.jacamo.semantics.auction;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuctionFixtureCleanupTest {
    @Test void closingFixtureRetiresOnlyItsOwnedWorkspaceDescriptors()throws Exception {
        var fixture=new AuctionOrgFixture();var live=fixture.live;
        var shared=live.environment.resolveWSP(live.workspace.getFullName()).getWorkspace();
        var outer=live.environment.resolveWSP(fixture.organization.getFullName()).getParentInfo().getId();
        var existing=shared.getChildWSPs().stream().map(d->d.getId()).filter(id->!id.equals(outer)).collect(java.util.stream.Collectors.toSet());
        fixture.close();
        assertTrue(shared.getChildWSP(outer.getName()).isEmpty(),"Shutdown alone keeps CArtAgO child descriptors; the fixture must retire its exact owned subtree");
        assertEquals(existing,shared.getChildWSPs().stream().map(d->d.getId()).collect(java.util.stream.Collectors.toSet()));
    }
}
