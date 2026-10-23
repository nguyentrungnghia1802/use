package org.jacamo.bridge.adapter;
import java.nio.file.*;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BridgeAgentRegistrationTest {
    @Test void lateOldIncarnationCleanupCannotEraseTheNewRegistration()throws Exception {
        Path jcm=Path.of("../use-plugin/examples/csl-counter/counter.jcm").toRealPath();
        var source=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm).semanticContract();
        String declaration=source.agentDeclarations().getFirst().name();String name="currentAgent";
        var old=new BridgeEntityId("jason","agent","runtime-agent",source.project().name(),name,"one");
        var current=new BridgeEntityId("jason","agent","runtime-agent",source.project().name(),name,"two");
        try {
            BridgeRuntimeRegistry.configureDeclarations(source);BridgeRuntimeRegistry.registerAgent(name,old,declaration);BridgeRuntimeRegistry.registerAgent(name,current,declaration);
            var expected=BridgeRuntimeRegistry.projectedAgentId(name);assertTrue(expected.isPresent());
            BridgeRuntimeRegistry.unregisterAgent(name,old);
            assertEquals(current,BridgeRuntimeRegistry.agentIdentity(name).orElseThrow());assertEquals(expected,BridgeRuntimeRegistry.projectedAgentId(name),"Stale cleanup cannot remove the current declaration alias");
        }finally{BridgeRuntimeRegistry.clearAgents();}
    }
}
