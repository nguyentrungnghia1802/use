package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MoiseBoardSnapshotSourceSelectionTest {
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
