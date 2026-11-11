package org.jacamo.bridge.adapter;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.Completeness;
import static org.junit.jupiter.api.Assertions.*;

class OptionalNplObservationTest {
    @Test void noNplRequirementMeansNoSupplierReadsNoSubscriptionsAndNoGlobalCompletenessClaim() {
        var reads=new AtomicInteger();
        try(var source=new NplBoardSnapshotSource("optional",1,"revision",()->{reads.incrementAndGet();return List.of();})) {
            source.deferObservation();source.attach(event->fail("Unrequested NPL event"));source.selectObservation(false);
            source.topologyFingerprint();assertTrue(source.capture().isEmpty());source.artifactCreated(null);
            assertFalse(source.hasPendingTimedObligations());assertEquals(0,reads.get());
            assertEquals("NOT_REQUESTED",source.captureMetadata().get("observationPolicy"));
            assertEquals(Completeness.PARTIAL,source.completeness(),"Omitted data is not globally COMPLETE");
            assertThrows(IllegalStateException.class,()->source.selectObservation(true));
        }
    }
}
