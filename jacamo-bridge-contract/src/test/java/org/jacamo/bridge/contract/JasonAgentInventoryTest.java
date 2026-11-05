package org.jacamo.bridge.contract;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JasonAgentInventoryTest {
    static RuntimeFact agent(String id){return new RuntimeFact(new BridgeEntityId("jason","agent","runtime-agent","project",id,"incarnation-"+id),RuntimeFactKind.AGENT,Map.of("name",id),List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());}
    static RuntimeSnapshot cut(List<RuntimeFact> actual,List<RuntimeFact> roster,long sequence,Completeness quality) {
        var marks=Map.of("jason",new SourceWatermark("jason",sequence));
        return new RuntimeSnapshot("cut-"+sequence,"revision",Instant.EPOCH,Instant.EPOCH,marks,marks,1,actual,Map.of("jason",quality),"state",
                Map.of("sourceEvidence",Map.of("jason",Map.of("agentInventory",JasonAgentInventory.payload("run",1,roster.stream().map(f->f.id().canonical()).toList())))));
    }
    @Test void exactCurrentRosterIsCompleteButMissingDuplicateAndForeignRowsAreNot() {
        var one=agent("one");var two=agent("two");var roster=List.of(one,two);
        assertTrue(JasonAgentInventory.complete(cut(roster,roster,1,Completeness.COMPLETE),"run"));
        assertFalse(JasonAgentInventory.complete(cut(List.of(one),roster,1,Completeness.COMPLETE),"run"));
        assertFalse(JasonAgentInventory.complete(cut(List.of(one,one),roster,1,Completeness.COMPLETE),"run"));
        assertFalse(JasonAgentInventory.complete(cut(List.of(one,agent("other")),roster,1,Completeness.COMPLETE),"run"));
        assertThrows(ContractException.class,()->JasonAgentInventory.payload("run",1,List.of(one.id().canonical(),one.id().canonical())));
    }
    @Test void stalePartialAndAnotherRunCannotReuseTheRosterAndEmptyIsExplicit() {
        var one=List.of(agent("one"));
        assertFalse(JasonAgentInventory.complete(cut(one,one,2,Completeness.COMPLETE),"run"));
        assertFalse(JasonAgentInventory.complete(cut(one,one,1,Completeness.PARTIAL),"run"));
        assertFalse(JasonAgentInventory.complete(cut(one,one,1,Completeness.COMPLETE),"another-run"));
        assertTrue(JasonAgentInventory.complete(cut(List.of(),List.of(),1,Completeness.COMPLETE),"run"));
        assertFalse(JasonAgentInventory.complete(cut(List.of(),one,1,Completeness.COMPLETE),"run"));
    }
}
