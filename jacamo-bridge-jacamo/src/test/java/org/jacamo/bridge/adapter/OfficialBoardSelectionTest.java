package org.jacamo.bridge.adapter;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OfficialBoardSelectionTest {
    private record Marker(String id) { }
    @Test void copyingAMutableFrameworkRegistryNeverUsesItsFailFastIterator() {
        var registry=new ArrayList<Marker>(){@Override public Iterator<Marker> iterator(){throw new ConcurrentModificationException("Mutable official registry iterator");}};
        var marker=new Marker("owned");registry.add(marker);
        assertEquals(List.of(marker),List.copyOf(JaCaMoBridgePlatform.officialBoardArtifacts(Marker.class,registry)));
    }
    @Test void actualSchemeAndGroupEnginesCannotBeSelectedByTheNormativeBoardSubclassRegistryAlone() {
        assertTrue(ora4mas.nopl.OrgArt.class.isAssignableFrom(ora4mas.nopl.SchemeBoard.class));
        assertTrue(ora4mas.nopl.OrgArt.class.isAssignableFrom(ora4mas.nopl.GroupBoard.class));
        assertFalse(ora4mas.nopl.NormativeBoard.class.isAssignableFrom(ora4mas.nopl.SchemeBoard.class));
        assertFalse(ora4mas.nopl.NormativeBoard.class.isAssignableFrom(ora4mas.nopl.GroupBoard.class));
    }
}
