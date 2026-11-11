package org.jacamo.bridge.adapter;

import java.time.Instant;
import java.util.*;
import jason.infra.local.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JasonAgentInventorySourceTest {
    static class Runner extends RunLocalMAS {void current(){runner=this;}void clear(){runner=null;}}
    static RuntimeSnapshot snapshot(JasonSnapshotSource source) {
        var cut=source.captureCut();
        return new RuntimeSnapshot("source-cut","revision",Instant.EPOCH,Instant.EPOCH,Map.of("jason",cut.start()),Map.of("jason",cut.end()),1,
                cut.facts(),Map.of("jason",source.completeness()),"state",Map.of("sourceEvidence",Map.of("jason",source.captureMetadata())));
    }
    @Test void inventoryComesFromTheRunnerAndAnUnpublishedNewAgentRevokesClosure()throws Exception {
        var runner=new Runner();runner.current();var source=new JasonSnapshotSource("session");var registered=new ArrayList<BridgeEntityId>();
        try {
            for(String name:List.of("worker-one","worker-two")) {
                var arch=new LocalAgArch();arch.setAgName(name);runner.addAg(arch);
                var id=new BridgeEntityId("jason","agent","runtime-agent","neutral",name,"actual-"+name);registered.add(id);
                BridgeRuntimeRegistry.registerAgent(name,id);BridgeRuntimeRegistry.publishJasonState(name,Map.of("name",name));
            }
            assertTrue(JasonAgentInventory.complete(snapshot(source),"session"));
            var arch=new LocalAgArch();arch.setAgName("new-worker");runner.addAg(arch);
            assertFalse(JasonAgentInventory.complete(snapshot(source),"session"));
            var id=new BridgeEntityId("jason","agent","runtime-agent","neutral","new-worker","actual-new-worker");registered.add(id);
            BridgeRuntimeRegistry.registerAgent("new-worker",id);BridgeRuntimeRegistry.publishJasonState("new-worker",Map.of("name","new-worker"));
            var complete=snapshot(source);assertTrue(JasonAgentInventory.complete(complete,"session"));assertEquals(3,complete.facts().size());
        }finally{source.close();for(var id:registered)BridgeRuntimeRegistry.unregisterAgent(id.localId(),id);runner.clear();}
    }
}
