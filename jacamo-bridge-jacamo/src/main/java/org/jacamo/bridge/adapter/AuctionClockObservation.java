package org.jacamo.bridge.adapter;
import java.util.function.*;
import org.jacamo.bridge.contract.VerificationObservation;
/** Historical opt-in property adapter over the neutral pinned NPL clock sampler. */
public final class AuctionClockObservation extends NplSourceClockObservation {
    public static final String PROPERTY="jacamo.bridge.auction.clock";
    public AuctionClockObservation(String run,long generation,String revision,Consumer<VerificationObservation> sink){super(run,generation,revision,sink);}
    AuctionClockObservation(String run,long generation,String revision,Consumer<VerificationObservation> sink,LongSupplier millis,LongSupplier nanos){super(run,generation,revision,sink,millis,nanos);}
}
