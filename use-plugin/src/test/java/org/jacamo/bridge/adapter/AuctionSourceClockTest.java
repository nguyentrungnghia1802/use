package org.jacamo.bridge.adapter;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.jacamo.bridge.adapter.AuctionClockObservation;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import static org.junit.jupiter.api.Assertions.*;

class AuctionSourceClockTest {
    final String nplHash=AuctionCaseBundle.read(Path.of("examples/auction")).runtimeRequirements().get("sha256:npl-0.6.1.jar");
    @Test void oldExplicitlyUnavailableClockRemainsEvidenceAndCannotAcquireDeadlineAuthority() {
        var old=new VerificationObservation(VerificationObservation.Kind.CLOCK,"recorded-clock",Completeness.PARTIAL,"SOURCE_CLOCK_NOT_QUALIFIED",
                VerificationObservation.SourceTime.unavailable("RECORDED_TEST_WITHOUT_SOURCE_TIME"),Map.of("clockIdentity","recorded-clock","sourceProof",Map.of("fixture","T01")));
        var cut=AuctionSourceClock.decode(old,"run",0,"r",nplHash);assertEquals(Completeness.UNAVAILABLE,cut.quality());assertNull(cut.anchorEpochMillis());
        assertEquals(AuctionSourceClock.DeadlineStatus.INCONCLUSIVE,cut.pending("recorded-clock",1800000000000L).status());
        var frames=new ArrayList<VerificationObservation>();try(var clock=new AuctionClockObservation("run",0,"r",frames::add,()->1800000000000L,()->0L)){clock.pulse(false);}
        assertThrows(IllegalArgumentException.class,()->AuctionSourceClock.requireContinuation(frames.getFirst(),old));
    }
    @Test void beforeExactAndAfterDeadlineReplayOnlyTheRecordedEpochs() {
        var time=new AtomicLong(1800000000000L);var nano=new AtomicLong();var frames=new ArrayList<VerificationObservation>();
        try(var clock=new AuctionClockObservation("run",0,"r",frames::add,time::get,nano::get)) {
            clock.pulse(false);var first=AuctionSourceClock.decode(frames.getFirst(),"run",0,"r",nplHash);
            assertEquals(0,first.relative(first.anchorEpochMillis()).value());
            assertEquals(AuctionSourceClock.DeadlineStatus.NOT_DUE,first.pending(first.clockIdentity(),first.anchorEpochMillis()+10000).status());
            time.addAndGet(10000);nano.addAndGet(1_000_000_000);clock.pulse(true);var exact=AuctionSourceClock.decode(frames.getLast(),"run",0,"r",nplHash);
            assertEquals(AuctionSourceClock.DeadlineStatus.NOT_DUE,exact.pending(exact.clockIdentity(),exact.nowEpochMillis()).status());
            time.incrementAndGet();nano.addAndGet(1_000_000_000);clock.pulse(true);var after=AuctionSourceClock.decode(frames.getLast(),"run",0,"r",nplHash);
            assertEquals(AuctionSourceClock.DeadlineStatus.EXPIRED,after.pending(after.clockIdentity(),after.anchorEpochMillis()+10000).status());
            var wire=frames.getLast().event("run",0,"r",3,java.time.Instant.EPOCH,"","");
            var replay=ContractPayloads.event(CanonicalJson.object(CanonicalJson.decode(CanonicalJson.encode(ContractPayloads.event(wire)))));
            assertEquals(after,AuctionSourceClock.decode(VerificationObservation.require(replay),"run",0,"r",nplHash));
            assertEquals(3600000,after.relative(after.anchorEpochMillis()+3600000).value());
            assertEquals(10000,after.relative(after.anchorEpochMillis()+10000).value());
            assertEquals(10000,after.relative(after.anchorEpochMillis()+35000).value()-after.relative(after.anchorEpochMillis()+25000).value(),"Late commitment keeps its own activation");
        }
    }
    @Test void absentWrongClockOverflowAndRegressedTimeStayInconclusive() {
        assertEquals(AuctionSourceClock.DeadlineStatus.INCONCLUSIVE,AuctionSourceClock.unavailable().pending("missing",0L).status());
        var time=new AtomicLong(1800000000000L);var nano=new AtomicLong();var frames=new ArrayList<VerificationObservation>();
        try(var clock=new AuctionClockObservation("run",0,"r",frames::add,time::get,nano::get)) {
            clock.pulse(false);var cut=AuctionSourceClock.decode(frames.getFirst(),"run",0,"r",nplHash);
            assertNull(cut.relative(cut.anchorEpochMillis()-1).value());assertNull(cut.relative(cut.anchorEpochMillis()+(long)Integer.MAX_VALUE+1).value());
            assertEquals(Integer.MAX_VALUE,cut.relative(cut.anchorEpochMillis()+Integer.MAX_VALUE).value());assertNull(cut.relative(Long.MAX_VALUE).value());
            assertEquals(AuctionSourceClock.DeadlineStatus.INCONCLUSIVE,cut.pending("another-clock",cut.anchorEpochMillis()).status());
            time.decrementAndGet();nano.addAndGet(1_000_000_000);clock.pulse(true);cut=AuctionSourceClock.decode(frames.getLast(),"run",0,"r",nplHash);
            assertEquals(AuctionSourceClock.DeadlineStatus.INCONCLUSIVE,cut.pending(cut.clockIdentity(),cut.anchorEpochMillis()).status());
        }
    }
    @Test void proofEpochAnchorBinaryAndQualityAreNotInferredFromEnvelopeReceiptTime() {
        var frames=new ArrayList<VerificationObservation>();
        try(var clock=new AuctionClockObservation("run",2,"r",frames::add,()->1800000000000L,()->0L)) {clock.pulse(false);}
        var original=frames.getFirst();assertThrows(IllegalArgumentException.class,()->AuctionSourceClock.decode(original,"other",2,"r",nplHash));
        assertThrows(IllegalArgumentException.class,()->AuctionSourceClock.decode(original,"run",3,"r",nplHash));
        assertThrows(IllegalArgumentException.class,()->AuctionSourceClock.decode(original,"run",2,"different",nplHash));
        assertThrows(IllegalArgumentException.class,()->AuctionSourceClock.decode(original,"run",2,"r","0".repeat(64)));
        var data=new LinkedHashMap<String,Object>(original.data());var proof=new LinkedHashMap<>(CanonicalJson.object(data.get("sourceProof")));proof.put("anchorEpochMillis","1800000000001");data.put("sourceProof",proof);
        var forged=new VerificationObservation(original.kind(),original.subject(),original.quality(),original.diagnostic(),original.sourceTime(),data);
        assertThrows(IllegalArgumentException.class,()->AuctionSourceClock.decode(forged,"run",2,"r",nplHash));
    }
}
