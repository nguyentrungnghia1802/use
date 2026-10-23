package org.jacamo.bridge.contract;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CapabilityObservationTest {
    @Test void exactRequestAndEpochAreDataOwnershipNotAvailabilityProof() {
        var request=new ObservationCapabilityRequest("field","cartago.artifact-field.v1",true,Map.of("class","sample.Inventory","field","token"));
        var scope=new CapabilityObservation.Scope("run",2,"revision","cartago",7);
        var receipt=CapabilityObservation.of(request,scope,Completeness.PARTIAL,"LOCK_UNAVAILABLE",Map.of("observed",false));
        var read=CapabilityObservation.require(receipt.payload(),request);
        assertEquals(Completeness.PARTIAL,read.quality());assertEquals(false,read.data().get("observed"));assertEquals(scope,read.scope());
        assertThrows(UnsupportedOperationException.class,()->read.data().put("observed",true));
        var changed=new ObservationCapabilityRequest("field",request.provider(),true,Map.of("class","sample.Other","field","token"));
        assertThrows(ContractException.class,()->CapabilityObservation.require(receipt.payload(),changed));
        var extra=new HashMap<>(receipt.payload());extra.put("unknown",true);assertThrows(ContractException.class,()->CapabilityObservation.require(extra,request));
        assertThrows(ContractException.class,()->CapabilityObservation.of(request,scope,Completeness.COMPLETE,"MISSING",Map.of()));
    }
}
