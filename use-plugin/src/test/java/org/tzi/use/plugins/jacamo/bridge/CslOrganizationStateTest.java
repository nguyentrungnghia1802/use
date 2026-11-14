package org.tzi.use.plugins.jacamo.bridge;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CslOrganizationStateTest {
    @TempDir Path directory;
    private static final String CSL="""
        csl v4.0 Warehouse {
          orgContext Federation { attribute name : String required; role member; }
          orgContext Organization {
            attribute name : String required;
            orgContext Team {
              attribute name : String required;
              role lead;
              role contributor;
              orgContext Skills { attribute name : String required; }
              entity Metric { attribute reading : Integer required mutable; }
            }
          }
          context Federation inv CollectivePlayer: self.member->forAll(r | r.player.oclIsKindOf(Organization));
          context Team inv OneLead: self.lead->size() = 1;
          context Metric inv Scoped: self.cslEntityScope.oclIsKindOf(Team);
        }
        """;
    private static final String ISTAR="""
        istar StateGoals { agent Metric {
          goal RolesValid : Maintain condition {[ self.cslEntityScope.oclAsType(Team).lead->size() = 1 ]}
          goal Done : Achieve
          task Measured > Done pre {[ true ]} post {[ self.reading >= 1 ]}
          task Positive > Done pre {[ true ]} post {[ self.reading > 0 ]}
        } }
        """;
    private CslStateVerifier verifier()throws Exception {
        Files.writeString(directory.resolve("model.csl"),CSL);Files.writeString(directory.resolve("model.istar"),ISTAR);
        Files.writeString(directory.resolve("map.xml"),"""
          <mapping xmlns="urn:use:jacamo-state-mapping:1">
            <attribute source="organization:community/identity:name" target="Federation.name"/>
            <attribute source="organization:warehouse/identity:name" target="Organization.name"/>
            <attribute source="group:department/identity:name" target="Team.name"/>
            <attribute source="agent:worker/identity:name" target="Skills.name"/>
            <attribute source="artifact:demo.Sensor/property:value" target="Metric.reading"/>
          </mapping>
          """);
        return new CslStateVerifier(directory.resolve("map.xml"),directory.resolve("model.csl"),directory.resolve("model.istar"));
    }
    private List<CslStateVerifier.Node> nodes(){return List.of(
        new CslStateVerifier.Node("community-id","organization","community","community-1","/community","",Map.of()),
        new CslStateVerifier.Node("org-id","organization","warehouse","warehouse-1","/warehouse","",Map.of()),
        new CslStateVerifier.Node("group-id","group","department","department-1","/warehouse","org-id",Map.of()),
        new CslStateVerifier.Node("person-1","agent","worker","mika","","",Map.of()),
        new CslStateVerifier.Node("person-2","agent","worker","lin","","",Map.of()));}
    private List<CslStateVerifier.Enactment> enactments(){return List.of(
        new CslStateVerifier.Enactment("community-id","org-id","member"),
        new CslStateVerifier.Enactment("group-id","person-1","lead"),
        new CslStateVerifier.Enactment("group-id","person-2","contributor"));}
    private CslStateVerifier.Snapshot snapshot(long sequence,List<CslStateVerifier.Node> nodes,List<CslStateVerifier.Enactment> roles){return new CslStateVerifier.Snapshot("run",sequence,true,
        List.of(new CslStateVerifier.Artifact("sensor-id","demo.Sensor","sensor-9","/warehouse",Map.of("value",2))),nodes,roles);}

    @Test void projectsOrganizationContainmentAtomicPlayersAndAnOrganizationPlayingAHigherLevelRole()throws Exception {
        try(var verifier=verifier()) {
            var snapshot=snapshot(1,nodes(),enactments());var result=verifier.update(snapshot);
            assertEquals("APPROVE",result.decision(),()->result.checks().toString());
            assertEquals(snapshot,StateWire.snapshot(StateWire.snapshot(snapshot)));
            var state=verifier.system().state();var model=verifier.system().model();
            assertTrue(model.getClass("Organization").isSubClassifierOf(model.getClass("RuntimePlayer")));
            assertNull(model.getClass("AtomicAgent"));
            assertEquals(2,state.allObjects().stream().filter(o->o.cls().name().equals("Skills")).count());
            assertEquals(3,state.allLinks().stream().filter(l->l.association().name().equals("Plays")).count());
            assertTrue(state.allLinks().stream().filter(l->l.association().name().equals("Plays")).allMatch(l->l.linkedObjects().getFirst().cls().isSubClassifierOf(model.getClass("OrgContext"))));
            var team=result.state().stream().filter(r->r.get("type").equals("Team")).findFirst().orElseThrow();
            assertNotEquals("null",team.get("lead"));assertTrue(team.get("lead").toString().contains("state_"));
            assertTrue(state.allLinks().stream().anyMatch(l->l.association().name().equals("Plays")&&l.linkedObjects().getFirst().cls().name().equals("Organization")));
            assertTrue(state.allLinks().stream().anyMatch(l->l.association().name().equals("CslContextScopes")&&l.linkedObjects().getFirst().cls().name().equals("Organization")&&l.linkedObjects().get(1).cls().name().equals("Team")));
            assertTrue(result.elements().stream().anyMatch(e->e.element().equals("RolesValid")&&e.status().equals("FULFILLED")));
        }
    }
    @Test void directMembershipSurvivesRoleWithdrawalAndAllowsMultipleGroupsWithoutCopyingTheAgent()throws Exception {
        try(var verifier=verifier()) {
            var direct=List.of(new CslStateVerifier.Membership("person-1","group-id"),new CslStateVerifier.Membership("person-1","community-id"));
            var first=snapshot(1,nodes(),enactments());
            var initial=verifier.update(new CslStateVerifier.Snapshot(first.run(),1,true,first.artifacts(),first.nodes(),first.enactments(),direct));
            var second=snapshot(2,nodes(),enactments().stream().filter(e->!e.role().equals("lead")).toList());
            var removed=verifier.update(new CslStateVerifier.Snapshot(second.run(),2,true,second.artifacts(),second.nodes(),second.enactments(),direct));
            assertEquals("HOLD_FAILURE",removed.decision());
            assertEquals(2,verifier.system().state().allLinks().stream().filter(l->l.association().name().equals("CslMembership")).count());
            assertEquals(2,removed.state().stream().filter(r->r.get("type").equals("Skills")).count());
            assertEquals(first.nodes().size()+first.artifacts().size()+first.enactments().size(),initial.state().size());
            assertEquals(new CslStateVerifier.Snapshot(first.run(),1,true,first.artifacts(),first.nodes(),first.enactments(),direct),StateWire.snapshot(StateWire.snapshot(new CslStateVerifier.Snapshot(first.run(),1,true,first.artifacts(),first.nodes(),first.enactments(),direct))));
        }
    }
    @Test void leavingARoleRemovesTheOccurrenceAndBlocksFromBothCslAndIStar()throws Exception {
        try(var verifier=verifier()) {
            verifier.update(snapshot(1,nodes(),enactments()));
            var result=verifier.update(snapshot(2,nodes(),enactments().stream().filter(e->!e.role().equals("lead")).toList()));
            assertEquals("HOLD_FAILURE",result.decision());
            assertTrue(result.checks().stream().anyMatch(c->c.id().equals("Team::OneLead")&&c.verdict().equals("FAIL")));
            assertTrue(result.elements().stream().anyMatch(e->e.element().equals("RolesValid")&&e.status().equals("VIOLATED")));
            assertFalse(verifier.system().state().allObjects().stream().anyMatch(o->o.cls().name().equals("lead")));
            assertEquals("Set{}",result.state().stream().filter(r->r.get("type").equals("Team")).findFirst().orElseThrow().get("lead"));
            assertEquals(2,verifier.system().state().allLinks().stream().filter(l->l.association().name().equals("Plays")).count());
        }
    }
    @Test void unresolvedPlayersAndAmbiguousEntityScopesAreRejectedBeforeChangingAuthoritativeState()throws Exception {
        try(var verifier=verifier()) {
            var initial=verifier.update(snapshot(1,nodes(),enactments()));
            var invalid=new ArrayList<>(enactments());invalid.add(new CslStateVerifier.Enactment("group-id","missing-person","lead"));
            assertThrows(IllegalArgumentException.class,()->verifier.update(snapshot(2,nodes(),invalid)));
            var duplicated=new ArrayList<>(nodes());duplicated.add(new CslStateVerifier.Node("other-group","group","department","other","/warehouse","org-id",Map.of()));
            assertThrows(IllegalArgumentException.class,()->verifier.update(snapshot(2,duplicated,enactments())));
            assertEquals(initial,verifier.update(snapshot(1,nodes(),enactments())));
            assertEquals("APPROVE",verifier.update(snapshot(2,nodes(),enactments())).decision());
        }
    }
}
