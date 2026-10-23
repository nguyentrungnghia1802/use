package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import java.util.function.Supplier;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.observer.SourceClock;

/** Historical record/API and diagnostic spelling; all source clock mechanics are shared. */
public record AuctionSourceClock(String clockIdentity,Long anchorEpochMillis,Long nowEpochMillis,
        Completeness quality,String diagnostic,Map<String,Object> proof) {
    public enum DeadlineStatus { NOT_DUE, EXPIRED, INCONCLUSIVE }
    public record Relative(Long rawEpochMillis,Integer value,String diagnostic) { }
    public record Deadline(DeadlineStatus status,String diagnostic) { }
    public AuctionSourceClock {proof=org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.object(proof);}
    private SourceClock shared(){return new SourceClock(clockIdentity,anchorEpochMillis,nowEpochMillis,quality,diagnostic,proof);}
    private static AuctionSourceClock wrap(SourceClock value){return new AuctionSourceClock(value.clockIdentity(),value.anchorEpochMillis(),value.nowEpochMillis(),value.quality(),value.diagnostic(),value.proof());}
    private static <T> T legacy(Supplier<T> action){try{return action.get();}catch(IllegalArgumentException failure){throw new IllegalArgumentException(failure.getMessage().replace("SOURCE_CLOCK_","AUCTION_CLOCK_"),failure);}}
    public static AuctionSourceClock unavailable(){return wrap(SourceClock.unavailable());}
    public static AuctionSourceClock fromCut(RuntimeSnapshot cut,String run,long generation,String revision,String nplHash){return legacy(()->wrap(SourceClock.fromCut(cut,run,generation,revision,nplHash)));}
    public static AuctionSourceClock decode(VerificationObservation observation,String run,long generation,String revision,String nplHash){return legacy(()->wrap(SourceClock.decode(observation,run,generation,revision,nplHash)));}
    public Relative relative(Long raw){var value=shared().relative(raw);return new Relative(value.rawEpochMillis(),value.value(),value.diagnostic());}
    public Deadline pending(String clock,Long deadline){var value=shared().pending(clock,deadline);return new Deadline(DeadlineStatus.valueOf(value.status().name()),value.diagnostic());}
    public static void requireContinuation(VerificationObservation previous,VerificationObservation next){legacy(()->{SourceClock.requireContinuation(previous,next);return null;});}
    public static boolean qualified(VerificationObservation value){return SourceClock.qualified(value);}
}
