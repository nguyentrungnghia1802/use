package org.jacamo.bridge.contract;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeControlContractTest {
    @Test void versionedRoundTripAndStrictOwnershipMetadata() {
        var request=new RuntimeControlContract.Request("1.0.0","session",1,"revision","id",RuntimeControlContract.Action.PAUSE,"OCL false");
        assertEquals(request,RuntimeControlContract.Request.decode(CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(request.payload())))));
        assertThrows(ContractException.class,()->new RuntimeControlContract.Request("2.0.0","session",1,"revision","id",RuntimeControlContract.Action.PAUSE,""));
        var invalid=new java.util.LinkedHashMap<>(request.payload()); invalid.put("goalRepair",true);
        assertThrows(ContractException.class,()->RuntimeControlContract.Request.decode(invalid));
        var status=new RuntimeControlContract.Status("1.0.0","session",1,"revision",true,RuntimeControlContract.State.PAUSED,"id",Set.of("incarnation-a"),Set.of("incarnation-a"),Set.of(),"",Instant.now());
        assertEquals(status,RuntimeControlContract.Status.decode(CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(status.payload())))));
    }
    @Test void partialAckAndUnavailableCannotMasqueradeAsPaused() {
        assertThrows(ContractException.class,()->new RuntimeControlContract.Status("1.0.0","s",1,"r",true,RuntimeControlContract.State.PAUSED,"p",Set.of("a","b"),Set.of("a"),Set.of(),"",Instant.now()));
        assertThrows(ContractException.class,()->new RuntimeControlContract.Status("1.0.0","s",1,"r",false,RuntimeControlContract.State.PAUSED,"p",Set.of("a"),Set.of("a"),Set.of(),"",Instant.now()));
    }
}
