package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jason.asSemantics.ActionExec;
import jason.asSemantics.Agent;
import jason.asSemantics.Event;
import jason.asSemantics.Intention;
import jason.asSyntax.ASSyntax;
import jason.asSyntax.Trigger;
import org.junit.jupiter.api.Test;

class JasonRuntimeConnectorTest {
    @Test
    void snapshotsBeliefsAndPublishesBeliefGoalActionAndMessageEventsFromOfficialHooks() throws Exception {
        Agent agent = new Agent();
        agent.setConsiderToAddMIForThisAgent(false);
        agent.initAg();
        agent.getBB().add(ASSyntax.parseLiteral("auction_open"));
        String semanticId = "jacamo:test:agent:Agent:auctioneer";
        JasonRuntimeConnector connector = new JasonRuntimeConnector("jason-live",
                Map.of("auctioneer", agent.getTS()), Map.of("auctioneer", semanticId));

        connector.connect(URI.create("jacamo://local/auction"));
        RuntimeSnapshot snapshot = connector.fullSnapshot();
        assertTrue(snapshot.mutations().stream().anyMatch(event -> event.kind() == RuntimeEventKind.BELIEF_ADDED
                && event.payload().get("belief").equals("auction_open")
                && semanticId.equals(event.semanticSourceId())));

        List<RuntimeEvent> events = new ArrayList<>();
        connector.subscribe(events::add);
        agent.getTS().getC().addEvent(new Event(Trigger.parseTrigger("+bid_seen(item1)"), Intention.EmptyInt));
        agent.getTS().getC().addEvent(new Event(Trigger.parseTrigger("+!sell_item"), Intention.EmptyInt));
        ActionExec action = new ActionExec(ASSyntax.parseLiteral("placeBid(item1,10)"), Intention.EmptyInt);
        JasonMonitorAgArch monitor = new JasonMonitorAgArch();
        monitor.setTS(agent.getTS());
        monitor.init();
        monitor.act(action);
        action.setResult(true);
        monitor.actionExecuted(action);
        connector.messageSent("auctioneer", "observer", "tell", "bid_placed(item1)");

        assertEquals(List.of(RuntimeEventKind.BELIEF_ADDED, RuntimeEventKind.GOAL_ADOPTED,
                        RuntimeEventKind.ACTION_STARTED, RuntimeEventKind.ACTION_SUCCEEDED,
                        RuntimeEventKind.MESSAGE_SENT),
                events.stream().map(RuntimeEvent::kind).toList());
        assertEquals("jason:agent:auctioneer", events.getFirst().runtimeSourceId());
        assertEquals("placeBid", events.get(2).payload().get("action"));
        assertEquals(List.of("item1", "10"), events.get(2).payload().get("arguments"));
        assertEquals(events.get(2).correlationId(), events.get(3).correlationId());
        monitor.stop();
        connector.disconnect();
        assertEquals(ConnectorState.DISCONNECTED, connector.state());
    }
}
