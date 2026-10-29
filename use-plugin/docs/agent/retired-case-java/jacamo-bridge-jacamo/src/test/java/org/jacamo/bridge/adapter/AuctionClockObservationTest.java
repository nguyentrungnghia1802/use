package org.jacamo.bridge.adapter;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuctionClockObservationTest {
    @Test void oneFrozenAnchorAndBoundedIdlePulsesUseSourceTimeAndRetireOnClose() {
        var millis=new AtomicLong(1800000000000L);var nanos=new AtomicLong();var frames=new ArrayList<VerificationObservation>();
        var clock=new AuctionClockObservation("run",3,"revision",frames::add,millis::get,nanos::get);
        clock.pulse(false);assertEquals(1,frames.size());clock.pulse(false);clock.pulse(true);assertEquals(1,frames.size());
        millis.addAndGet(1000);nanos.addAndGet(1_000_000_000);clock.pulse(true);assertEquals(2,frames.size());
        var proof=CanonicalJson.object(frames.getLast().data().get("sourceProof"));assertEquals("1800000000000",proof.get("anchorEpochMillis"));
        assertEquals("1800000001000",frames.getLast().sourceTime().rawValue());assertEquals("UNPROVEN_WALL_CLOCK",proof.get("monotonicity"));
        assertEquals(Completeness.COMPLETE,frames.getLast().quality());assertTrue(frames.stream().allMatch(f->f.subject().equals(clock.clockIdentity())));
        clock.close();nanos.addAndGet(1_000_000_000);clock.pulse(true);assertEquals(2,frames.size());
    }
    @Test void regressionIsLatchedAndRangeFailurePreservesRawEpochWithoutTruncation() {
        var millis=new AtomicLong(1800000000000L);var nanos=new AtomicLong();var frames=new ArrayList<VerificationObservation>();
        try(var clock=new AuctionClockObservation("run",0,"r",frames::add,millis::get,nanos::get)) {
            clock.pulse(false);millis.decrementAndGet();nanos.addAndGet(1_000_000_000);clock.pulse(true);
            assertEquals(Completeness.PARTIAL,frames.getLast().quality());assertTrue(frames.getLast().diagnostic().contains("REGRESSION"));
            millis.addAndGet(1001);nanos.addAndGet(1_000_000_000);clock.pulse(true);assertEquals(Completeness.PARTIAL,frames.getLast().quality());
        }
        frames.clear();millis.set(1800000000000L);
        try(var clock=new AuctionClockObservation("run",1,"r",frames::add,millis::get,nanos::get)) {
            clock.pulse(false);millis.addAndGet((long)Integer.MAX_VALUE+1);nanos.addAndGet(1_000_000_000);clock.pulse(true);
            assertEquals(Long.toString(millis.get()),frames.getLast().sourceTime().rawValue());assertTrue(frames.getLast().diagnostic().contains("INTEGER_RANGE"));
        }
    }
    @Test void differentGenerationHasAnIndependentClockAndAnchor() {
        var frames=new ArrayList<VerificationObservation>();
        try(var first=new AuctionClockObservation("run",0,"r",frames::add,()->1000L,()->0L);
            var next=new AuctionClockObservation("run",1,"r",frames::add,()->2000L,()->0L)) {
            first.pulse(false);next.pulse(false);assertNotEquals(frames.get(0).sourceTime().clockId(),frames.get(1).sourceTime().clockId());
            assertNotEquals(frames.get(0).data().get("sourceProof"),frames.get(1).data().get("sourceProof"));
        }
    }
}
