package org.tzi.use.plugins.jacamo.semantics.auction;

import java.util.*;
import cartago.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Repeated component fixtures must not grow the singleton CArtAgO inventory
 * subsequently captured by the real original-engine regression fixtures. */
class AuctionFixtureIsolationTest {
    @Test void repeatedFixtureCloseReleasesOnlyItsOwnAgentSessionsAndWorkspaces()throws Exception {
        var environment=CartagoEnvironment.getInstance();environment.init();var before=artifacts(environment.getRootWSP());
        for(int i=0;i<3;i++)try(var fixture=new AuctionCastFixture(1)) {assertFalse(fixture.projector().system().state().allObjects().isEmpty());}
        var after=artifacts(environment.getRootWSP());var leaked=new TreeSet<>(after);leaked.removeAll(before);
        assertEquals(Set.of(),leaked,"Fixture-owned physical artifacts survived close");assertTrue(after.containsAll(before),"Cleanup must preserve pre-existing artifacts");
    }
    private Set<String> artifacts(WorkspaceDescriptor workspace)throws Exception {
        var result=new TreeSet<String>();for(var id:CartagoEnvironment.getInstance().getController(workspace.getId().getFullName()).getCurrentArtifacts())result.add(id.getArtifactType()+":"+id.getId());
        for(var child:List.copyOf(workspace.getWorkspace().getChildWSPs()))result.addAll(artifacts(child));return result;
    }
}
