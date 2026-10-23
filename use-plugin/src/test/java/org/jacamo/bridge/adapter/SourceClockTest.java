package org.jacamo.bridge.adapter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.observer.SourceClock;
import org.tzi.use.plugins.jacamo.semantics.observer.SourceClockCut;
import static org.junit.jupiter.api.Assertions.*;
/** Pinned NPL producer/neutral consumer; source samples are explicit, receiver/replay clocks irrelevant. */
class SourceClockTest {
    final String hash=(String)NplRuntimeProfile.evidence().get("artifactSha256");
    @Test void recordedEpochDeadlineRangeAndRegressionKeepTheirActualAuthority() {
        var time=new AtomicLong(1800000000000L);var ticks=new AtomicLong();var frames=new ArrayList<VerificationObservation>();
        try(var producer=new NplSourceClockObservation("neutral-run",3,"revision",frames::add,time::get,ticks::get)) {
            producer.pulse(false);var first=SourceClock.decode(frames.getFirst(),"neutral-run",3,"revision",hash);
            assertEquals(0,first.relative(first.anchorEpochMillis()).value());assertNull(first.relative(Long.MAX_VALUE).value());
            time.addAndGet(10000);ticks.addAndGet(1000000000L);producer.pulse(true);var exact=SourceClock.decode(frames.getLast(),"neutral-run",3,"revision",hash);
            assertEquals(SourceClock.DeadlineStatus.NOT_DUE,exact.pending(exact.clockIdentity(),exact.nowEpochMillis()).status());
            var event=frames.getLast().event("neutral-run",3,"revision",2,java.time.Instant.EPOCH,"","");
            assertEquals(exact,SourceClock.decode(VerificationObservation.require(ContractPayloads.event(CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(ContractPayloads.event(event)))))),"neutral-run",3,"revision",hash));
            time.decrementAndGet();ticks.addAndGet(1000000000L);producer.pulse(true);var regression=SourceClock.decode(frames.getLast(),"neutral-run",3,"revision",hash);
            assertEquals(SourceClock.DeadlineStatus.INCONCLUSIVE,regression.pending(regression.clockIdentity(),regression.anchorEpochMillis()+1).status());
            assertThrows(IllegalArgumentException.class,()->SourceClock.requireContinuation(frames.getLast(),frames.getFirst()));
            assertThrows(IllegalArgumentException.class,()->SourceClock.decode(frames.getFirst(),"neutral-run",3,"revision","0".repeat(64)));
        }
        assertEquals(SourceClock.DeadlineStatus.INCONCLUSIVE,SourceClock.unavailable().pending("unknown",0L).status());
    }
    @Test void acceptedPrefixNeverReanchorsClockOrForgetsLatchedRegressionAcrossPartialResync() {
        var frames=new ArrayList<VerificationObservation>();var time=new AtomicLong(1800000000000L);var nanos=new AtomicLong();
        try(var producer=new NplSourceClockObservation("run",1,"revision",frames::add,time::get,nanos::get)) {
            producer.pulse(false);time.decrementAndGet();nanos.addAndGet(1000000000L);producer.pulse(true);
        }
        var event=frames.getLast().event("run",1,"revision",2,java.time.Instant.EPOCH,"","");
        var old=new RuntimeFact(event.entityId(),event.factKind(),event.after(),List.of(),event.projectionStatus(),event.completeness(),List.of());var before=Map.of(old.id().canonical(),old);
        var marks=Map.of(VerificationObservation.SOURCE_ID,event.watermark());
        var partial=new RuntimeSnapshot("resync","revision",java.time.Instant.EPOCH,java.time.Instant.EPOCH,marks,marks,1,List.of(),Map.of(VerificationObservation.SOURCE_ID,Completeness.PARTIAL),"partial");
        SourceClockCut.validateContinuation(before,partial,"run",1);assertEquals(1,before.size());
        var fresh=new ArrayList<VerificationObservation>();try(var producer=new NplSourceClockObservation("run",1,"revision",fresh::add,()->1900000000000L,()->0L)){producer.pulse(false);}
        assertThrows(IllegalArgumentException.class,()->SourceClockCut.validateContinuation(before,fresh.getFirst().event("run",1,"revision",3,java.time.Instant.EPOCH,"","")));
    }
}
