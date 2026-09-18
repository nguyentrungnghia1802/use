package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import jason.asSemantics.*;
import jason.asSyntax.Trigger;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantic.Dimension;

class RuntimeIdentityHardeningTest {
    @Test void eventOwnsNestedPayloadSnapshot() {
        List<Object> values = new ArrayList<>(List.of("original"));
        RuntimeEvent event = RuntimeEvent.create("e", Instant.now(), 1, Dimension.ENVIRONMENT,
            RuntimeEventKind.SIGNAL, "a", null, Map.of("signal", "s", "values", values), null);
        values.set(0, "changed");
        assertEquals(List.of("original"), event.payload().get("values"));
        assertThrows(UnsupportedOperationException.class,
            () -> ((List<Object>)event.payload().get("values")).add("bad"));
        assertEquals(event, new RuntimeEventCodec().read(new RuntimeEventCodec().write(event)));
    }

    @Test void replacedAgentCannotEmitThroughOldHooks() throws Exception {
        Agent old = agent(), next = agent();
        var connector = new JasonRuntimeConnector("j", Map.of("a", old.getTS()), Map.of("a", "semantic:a"));
        List<RuntimeEvent> events = new ArrayList<>();
        try {
            connector.connect(URI.create("jacamo://local/test"));
            connector.subscribe(events::add);
            connector.attachAgent("a", next.getTS());
            old.getTS().getC().addEvent(new Event(Trigger.parseTrigger("+stale"), Intention.EmptyInt));
            assertTrue(events.isEmpty(), "retired TS must not publish");
            next.getTS().getC().addEvent(new Event(Trigger.parseTrigger("+current"), Intention.EmptyInt));
            assertEquals(1, events.size());
            connector.disconnect();
            next.getTS().getC().addEvent(new Event(Trigger.parseTrigger("+closed"), Intention.EmptyInt));
            assertEquals(1, events.size());
        } finally { connector.disconnect(); }
    }
    private Agent agent() throws Exception {
        Agent result = new Agent(); result.setConsiderToAddMIForThisAgent(false); result.initAg(); return result;
    }
}
