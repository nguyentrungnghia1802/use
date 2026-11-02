package org.jacamo.bridge.contract;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol.*;

class VerifiedCheckpointProtocolTest {
    static final String HASH="a".repeat(64);
    static Map<String,Object> request(Map<String,Object> snapshot,Map<String,Object> barrier) {
        var identity=new Identity("session","run",1,"revision","token",1,1,HASH,VerifiedCheckpointProtocol.hash(Map.of("state","original")),"",Set.of("execution"));
        var map=new LinkedHashMap<String,Object>();map.put("identity",identity.payload());map.put("checkpointId","source-post");map.put("sourceOccurrences",List.of("event"));map.put("requiredChecks",List.of(VerifiedCheckpointProtocol.REQUIRED_CHECK));map.put("inputHashes",Map.of("model",HASH));map.put("watermarks",Map.of("source",1));map.put("barrier",barrier);map.put("snapshot",snapshot);map.put("ready",true);
        var signed=new LinkedHashMap<>(identity.payload());signed.put("requestHash",VerifiedCheckpointProtocol.hash(map));map.put("identity",signed);return map;
    }
    @Test void policyAndCommandRoundTripBindNativeInventoryAndEveryIdentityField() {
        var rule=new Rule("source","observation:source","fixture.Domain",Set.of("change"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK));
        var policy=new Policy(HASH,Map.of("model",HASH),List.of(rule));assertEquals(policy,Policy.decode(policy.payload()));
        assertThrows(ContractException.class,()->new Policy(HASH,Map.of("model",HASH),List.of(new Rule("source","observation:source","fixture.Domain",Set.of("change"),Set.of("unrelated")))));
        var identity=new Identity("session","run",1,"revision","token",1,1,HASH,HASH,HASH,Set.of("execution"));
        var command=new Command(Action.HOLD_UNVERIFIED,"command",identity,HASH,HASH,Map.of("required",new Check("UNKNOWN","APPLICABLE","missing evidence")),"evidence unavailable");
        assertEquals(command,Command.decode(command.payload()));
        var changed=new LinkedHashMap<>(command.payload());changed.put("extra",true);assertThrows(ContractException.class,()->Command.decode(changed));
        var bad=new LinkedHashMap<>(identity.payload());bad.put("generation",1.5);assertThrows(ContractException.class,()->Identity.decode(bad));
    }
    @Test void requestRejectsARehashedWrongSnapshotAndAnIncompleteBarrier() {
        var barrier=Map.<String,Object>of("admissionClosed",true,"clockFrozen",true,"active",Map.of(),"pending",0,"epoch",1);
        assertDoesNotThrow(()->VerifiedCheckpointProtocol.requireRequest(request(Map.of("state","original"),barrier)));
        assertThrows(ContractException.class,()->VerifiedCheckpointProtocol.requireRequest(request(Map.of("state","replaced"),barrier)));
        var incomplete=new LinkedHashMap<>(barrier);incomplete.put("clockFrozen",false);
        assertThrows(ContractException.class,()->VerifiedCheckpointProtocol.requireRequest(request(Map.of("state","original"),incomplete)));
    }
}
