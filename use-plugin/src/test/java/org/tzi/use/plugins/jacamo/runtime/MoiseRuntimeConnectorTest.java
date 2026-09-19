package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import moise.oe.GroupInstance;
import moise.oe.OE;
import moise.oe.OEAgent;
import moise.oe.SchemeInstance;
import moise.os.OSBuilder;
import moise.os.ns.NS;
import moise.os.ns.Norm;
import org.junit.jupiter.api.Test;

class MoiseRuntimeConnectorTest {
    @Test
    void snapshotsAndPollsRealMoiseOrganisationEntityState() throws Exception {
        OSBuilder builder = new OSBuilder();
        builder.addRootGroup("auction_group");
        builder.addRole("auction_group", "auctioneer");
        builder.addScheme("auction_scheme", "sell_item");
        builder.addMission("auction_scheme", "run_auction", "sell_item");
        Norm norm = new Norm(builder.getOS().getSS().getRoleDef("auctioneer"),
                builder.getOS().getFS().findMission("run_auction"), builder.getOS().getNS(), NS.OpTypes.obligation);
        norm.setId("n1");
        builder.getOS().getNS().addNorm(norm);
        OE organisation = new OE(null, builder.getOS());
        GroupInstance group = organisation.addGroup("auction_group", "auction_group");
        SchemeInstance scheme = organisation.startScheme("auction_scheme", "auction_scheme");
        scheme.addResponsibleGroup(group);
        OEAgent agent = organisation.addAgent("auctioneer");

        MoiseRuntimeBinding binding = new MoiseRuntimeBinding("auction_org", "semantic:organisation",
                Map.of("auctioneer", "semantic:agent"), Map.of("auction_group", "semantic:group"),
                Map.of("auction_scheme", "semantic:scheme"));
        MoiseRuntimeConnector connector = new MoiseRuntimeConnector("moise-live", organisation, binding);
        connector.connect(URI.create("jacamo://local/auction"));

        RuntimeSnapshot initial = connector.fullSnapshot();
        assertEquals(initial.fingerprint(), connector.fullSnapshot().fingerprint());
        assertTrue(initial.mutations().stream().anyMatch(event -> event.kind() == RuntimeEventKind.GROUP_CREATED
                && "auction_group".equals(event.payload().get("group"))));
        assertTrue(initial.mutations().stream().anyMatch(event -> event.kind() == RuntimeEventKind.SCHEME_CREATED
                && "auction_scheme".equals(event.payload().get("scheme"))));
        assertTrue(initial.mutations().stream().anyMatch(event -> event.kind() == RuntimeEventKind.SCHEME_STATE_CHANGED
                && "sell_item".equals(event.payload().get("goal"))));
        assertEquals(List.of("auction_group"), connector.discoveredGroups());
        assertEquals(List.of("auction_scheme"), connector.discoveredSchemes());

        List<RuntimeEvent> events = new ArrayList<>();
        connector.subscribe(events::add);
        agent.adoptRole("auctioneer", group);
        agent.commitToMission("run_auction", scheme);
        connector.pollChanges();
        assertTrue(events.stream().anyMatch(event -> event.kind() == RuntimeEventKind.ROLE_ADOPTED));
        assertTrue(events.stream().anyMatch(event -> event.kind() == RuntimeEventKind.MISSION_COMMITTED));

        events.clear();
        scheme.getGoal("sell_item").setAchieved(agent);
        connector.pollChanges();
        assertTrue(events.stream().anyMatch(event -> event.kind() == RuntimeEventKind.SCHEME_STATE_CHANGED
                && "satisfied".equals(event.payload().get("state"))));

        events.clear();
        agent.removeMission("run_auction", scheme);
        agent.removeRole("auctioneer", group);
        connector.pollChanges();
        assertTrue(events.stream().anyMatch(event -> event.kind() == RuntimeEventKind.MISSION_REMOVED));
        assertTrue(events.stream().anyMatch(event -> event.kind() == RuntimeEventKind.ROLE_REMOVED));
        assertFalse(connector.capabilityGaps().isEmpty());
        connector.disconnect();
        assertEquals(ConnectorState.DISCONNECTED, connector.state());
    }
}
