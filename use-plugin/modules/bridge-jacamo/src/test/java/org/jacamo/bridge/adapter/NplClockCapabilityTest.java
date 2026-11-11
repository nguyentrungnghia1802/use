package org.jacamo.bridge.adapter;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NplClockCapabilityTest {
    @Test void registryOwnsNeutralClockAndRetiresItWithoutThreadOrNewTransport() {
        var frames=new ArrayList<VerificationObservation>();var request=new ObservationCapabilityRequest("source-clock",NplClockCapability.PROVIDER,true,Map.of());
        var registry=new ObservationCapabilityRegistry(Map.of(NplClockCapability.PROVIDER,r->new NplClockCapability(r,"run",9,"revision",frames::add)));
        NplClockCapability handle;
        try(var session=registry.open(List.of(request))) {
            assertTrue(session.requiredUnavailable().isEmpty());handle=session.handle(request.id(),NplClockCapability.class).orElseThrow();
            handle.pulse(false);assertEquals(1,frames.size());assertEquals("npl-system-wall:run:9",frames.getFirst().sourceTime().clockId());
        }
        handle.pulse(true);assertEquals(1,frames.size());assertEquals(CapabilityStatus.UNAVAILABLE,handle.capability().status());
    }
    @Test void requestedPinnedHashMustMatchActualNplBinary() {
        String hash=(String)NplRuntimeProfile.evidence().get("artifactSha256");
        var request=new ObservationCapabilityRequest("clock",NplClockCapability.PROVIDER,true,Map.of("hash",hash));
        try(var handle=new NplClockCapability(request,"run",1,"revision",ignored->{})){assertEquals(CapabilityStatus.COMPLETE,handle.capability().status());}
        assertThrows(IllegalArgumentException.class,()->new NplClockCapability(new ObservationCapabilityRequest("clock",request.provider(),true,Map.of("hash","0".repeat(64))),"run",1,"revision",ignored->{}));
    }
}
