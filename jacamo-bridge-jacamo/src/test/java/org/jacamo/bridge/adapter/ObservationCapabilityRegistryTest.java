package org.jacamo.bridge.adapter;
import java.util.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ObservationCapabilityRegistryTest {
    @Test void absentAndRejectedProvidersRemainUnavailableWithoutBooleanValues() {
        var registry=new ObservationCapabilityRegistry(Map.of("framework.denied",r->{throw new IllegalAccessException("not granted");}));
        try(var session=registry.open(List.of(new ObservationCapabilityRequest("missing","framework.missing",true,Map.of()),new ObservationCapabilityRequest("denied","framework.denied",false,Map.of())))) {
            assertEquals(2,session.capabilities().size());assertEquals(1,session.requiredUnavailable().size());
            assertTrue(session.capabilities().stream().allMatch(c->c.status()==CapabilityStatus.UNAVAILABLE));
            assertTrue(session.requiredUnavailable().getFirst().reason().startsWith("UNSUPPORTED:"));
        }
    }
    @Test void lifecycleClosesEachHandleOnceInReverseOrderAndFencesOldSessions() {
        var retired=new ArrayList<String>();var registry=new ObservationCapabilityRegistry(Map.of("framework.known",r->new ObservationCapabilityRegistry.Handle(){
            public Capability capability(){return new Capability(r.id(),CapabilityStatus.COMPLETE,List.of("actual-source-proof"),"");}
            public void close(){retired.add(r.id());}
        }));
        var requests=List.of(new ObservationCapabilityRequest("one","framework.known",true,Map.of()),new ObservationCapabilityRequest("two","framework.known",true,Map.of()));
        var session=registry.open(requests);assertTrue(session.requiredUnavailable().isEmpty());session.close();session.close();
        assertEquals(List.of("two","one"),retired);assertThrows(IllegalStateException.class,session::capabilities);
        assertThrows(IllegalArgumentException.class,()->registry.open(List.of(requests.getFirst(),requests.getFirst())));
        try(var fresh=registry.open(requests)){assertTrue(fresh.requiredUnavailable().isEmpty());}
        assertEquals(List.of("two","one","two","one"),retired);
    }
    @Test void requestFreezesNestedDataAndBoundsUntrustedParameters() {
        var values=new ArrayList<>(List.of("field"));var request=new ObservationCapabilityRequest("probe","framework.known",true,Map.of("members",values));values.clear();
        assertEquals(List.of("field"),request.parameters().get("members"));assertThrows(UnsupportedOperationException.class,()->request.parameters().clear());
        assertThrows(ContractException.class,()->new ObservationCapabilityRequest("bad","Class.forName()",true,Map.of()));
    }
}
