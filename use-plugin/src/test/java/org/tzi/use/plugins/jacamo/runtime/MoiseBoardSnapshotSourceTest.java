package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.util.*;
import moise.os.OSBuilder;
import ora4mas.nopl.GroupBoard;
import ora4mas.nopl.SchemeBoard;
import ora4mas.nopl.oe.Group;
import ora4mas.nopl.oe.Scheme;
import org.junit.jupiter.api.Test;

class MoiseBoardSnapshotSourceTest {
    @Test void readsActualBoardRepresentationWithoutInventingAnOE() throws Exception {
        OSBuilder os = new OSBuilder();
        os.addRootGroup("group"); os.addRole("group", "worker");
        os.addScheme("scheme", "done"); os.addMission("scheme", "work", "done");
        Group group = new Group("g"); group.setType("group");
        Scheme scheme = new Scheme(os.getOS().getFS().findScheme("scheme"), "s");
        var groups = new ArrayList<GroupBoard>();
        groups.add(new GroupBoard() {
            public String getOEId() { return "org"; }
            public String getArtId() { return "g"; }
            public Group getGrpState() { return group; }
            public moise.os.ss.Group getSpec() { return os.getOS().getSS().getRootGrSpec(); }
            public boolean isWellFormed() { return true; }
        });
        var schemes = List.of(new SchemeBoard() {
            public String getOEId() { return "org"; }
            public String getArtId() { return "s"; }
            public Scheme getSchState() { return scheme; }
            public moise.os.fs.Scheme getSpec() { return scheme.getSpec(); }
            public boolean isWellFormed() { return true; }
        });
        var binding = new MoiseRuntimeBinding("org", "org-id", Map.of("a", "agent-id"),
                Map.of("g", "group-id"), Map.of("s", "scheme-id"));
        var connector = MoiseRuntimeConnector.forBoards("board", binding,
                new MoiseBoardSnapshotSource("org", () -> groups, () -> schemes));
        connector.connect(URI.create("jacamo://local/org"));
        var first = connector.fullSnapshot();
        assertEquals(first.fingerprint(), connector.fullSnapshot().fingerprint());
        assertTrue(first.mutations().stream().anyMatch(e -> e.kind()==RuntimeEventKind.SCHEME_STATE_CHANGED
                && e.payload().get("state").equals("not_satisfied")));
        List<RuntimeEvent> received = new ArrayList<>();
        var subscription = connector.subscribe(received::add);
        group.addPlayer("a", "worker"); scheme.addPlayer("a", "work"); scheme.setAsSatisfied("done");
        connector.pollChanges();
        assertEquals(Set.of(RuntimeEventKind.ROLE_ADOPTED, RuntimeEventKind.MISSION_COMMITTED,
                RuntimeEventKind.SCHEME_STATE_CHANGED), received.stream().map(RuntimeEvent::kind).collect(java.util.stream.Collectors.toSet()));
        assertTrue(received.stream().allMatch(e -> e.semanticSourceId()!=null));
        assertThrows(UnsupportedOperationException.class, connector::normativeSnapshot);
        received.clear(); subscription.close();
        group.removePlayer("a", "worker"); connector.pollChanges(); assertTrue(received.isEmpty());
        connector.disconnect(); connector.connect(URI.create("jacamo://local/org"));
        assertTrue(connector.fullSnapshot().mutations().stream().noneMatch(e -> e.kind()==RuntimeEventKind.ROLE_ADOPTED));
        groups.add(groups.getFirst());
        assertThrows(IllegalStateException.class, connector::fullSnapshot, "duplicate board identity must not be selected heuristically");
        assertEquals(ConnectorState.ERROR, connector.state());
        assertThrows(IllegalStateException.class, () -> connector.connect(URI.create("jacamo://local/org")));
        connector.disconnect();
    }

    @Test void rejectsWrongOwnerAndUninitializedBoard() {
        var wrong = new GroupBoard() { public String getOEId() { return "other"; } };
        var source = new MoiseBoardSnapshotSource("org", () -> List.of(wrong), List::of);
        assertThrows(IllegalStateException.class, source::capture);
        var missing = new GroupBoard() { public String getOEId() { return "org"; } };
        assertThrows(IllegalStateException.class,
                () -> new MoiseBoardSnapshotSource("org", () -> List.of(missing), List::of).capture());
    }
}
