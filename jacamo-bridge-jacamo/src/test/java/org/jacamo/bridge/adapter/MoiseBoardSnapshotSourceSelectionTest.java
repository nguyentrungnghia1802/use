package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MoiseBoardSnapshotSourceSelectionTest {
    @Test void unrequestedBoardsAreNotReadEvenForFingerprintAndPolling() throws Exception {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var source=new MoiseBoardSnapshotSource("declared","session",()->{calls.incrementAndGet();return List.of();},()->{calls.incrementAndGet();return List.of();});
        source.observationKinds(Set.of());source.attach(event->org.junit.jupiter.api.Assertions.fail("No unrequested event"));
        source.topologyFingerprint();source.capture();source.poll();assertEquals(0,calls.get());
        source.observationKinds(Set.of(org.jacamo.bridge.contract.RuntimeFactKind.GROUP_BOARD));
        source.capture();assertEquals(1,calls.get(),"Group capture must not query scheme suppliers");source.close();
    }
    @Test void staticAndDynamicSourcesAreDisjointAndDynamicSourceAcceptsUnseenOrganisations() {
        var declared = new MoiseBoardSnapshotSource("declared", "session", List::of, List::of);
        var dynamic = new MoiseBoardSnapshotSource(Set.of("declared"), "session", List::of, List::of);

        assertEquals("moise:declared", declared.sourceId());
        assertEquals("moise:dynamic", dynamic.sourceId());
        assertTrue(declared.acceptsOrganisation("declared"));
        assertFalse(declared.acceptsOrganisation("runtime-created"));
        assertFalse(dynamic.acceptsOrganisation("declared"));
        assertTrue(dynamic.acceptsOrganisation("runtime-created"));
        assertFalse(dynamic.acceptsOrganisation(""));
    }
}
