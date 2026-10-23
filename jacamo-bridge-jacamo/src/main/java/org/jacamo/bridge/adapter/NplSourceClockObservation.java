package org.jacamo.bridge.adapter;

import java.util.*;
import java.util.function.*;
import org.jacamo.bridge.contract.*;

/** Run-owned source wall clock. NPL 0.6.1 TimeTerm/NormInstance use Date and
 * System.currentTimeMillis, not a monotonic clock. nanoTime only rate-limits
 * publication; it never supplies domain time. No thread or domain mutation. */
public class NplSourceClockObservation implements AutoCloseable {
    public static final String PROPERTY="jacamo.bridge.observation.clock";
    public static final String VERSION="NPL_SYSTEM_WALL_CLOCK/1.0";
    public static final String AUTHORITY="JAVA_SYSTEM_CURRENT_TIME_MILLIS_SAME_PROCESS_AS_PINNED_NPL";
    private final String run,revision,identity;private final long generation;
    private final Consumer<VerificationObservation> sink;private final LongSupplier millis,nanos;
    private Long anchor,previous,highWater,lastPulse;private boolean closed,regressed;
    public NplSourceClockObservation(String run,long generation,String revision,Consumer<VerificationObservation> sink) {
        this(run,generation,revision,sink,System::currentTimeMillis,System::nanoTime);
    }
    NplSourceClockObservation(String run,long generation,String revision,Consumer<VerificationObservation> sink,LongSupplier millis,LongSupplier nanos) {
        if(run==null||run.isBlank()||revision==null||revision.isBlank()||generation<0)throw new IllegalArgumentException("SOURCE_CLOCK_SCOPE");
        NplRuntimeProfile.requireSupported();this.run=run;this.generation=generation;this.revision=revision;identity=clockIdentity(run,generation);
        this.sink=Objects.requireNonNull(sink);this.millis=Objects.requireNonNull(millis);this.nanos=Objects.requireNonNull(nanos);
    }
    public static String clockIdentity(String run,long generation){return "npl-system-wall:"+run+":"+generation;}
    public String clockIdentity(){return identity;}
    public static Map<String,Object> timeBasis(String run,long generation) {
        return Map.of("clockIdentity",clockIdentity(run,generation),"unit","EPOCH_MILLISECONDS","authority",AUTHORITY,
                "monotonicity","UNPROVEN_WALL_CLOCK","nplArtifact",NplRuntimeProfile.evidence());
    }
    /** Initial anchor precedes reasoning; subsequent ticks need pending timed
     * obligations. At most one per second, on the existing publisher thread. */
    public synchronized void pulse(boolean pending) {
        if(closed||anchor!=null&&!pending)return;
        long tick=nanos.getAsLong();if(lastPulse!=null&&tick-lastPulse<1_000_000_000L)return;
        long now=millis.getAsLong();if(anchor==null){anchor=now;previous=now;highWater=now;}
        if(now<highWater)regressed=true;highWater=Math.max(highWater,now);
        String diagnostic=now<0?"SOURCE_CLOCK_NEGATIVE_EPOCH":regressed?"SOURCE_CLOCK_REGRESSION_LATCHED":now-anchor>Integer.MAX_VALUE?"SOURCE_CLOCK_INTEGER_RANGE":"";
        var proof=new LinkedHashMap<String,Object>();proof.put("version",VERSION);proof.put("sessionId",run);proof.put("generation",generation);proof.put("modelRevision",revision);
        proof.put("anchorEpochMillis",Long.toString(anchor));proof.put("previousEpochMillis",Long.toString(previous));proof.put("highWaterEpochMillis",Long.toString(highWater));
        proof.putAll(timeBasis(run,generation));proof.put("sampler","System.currentTimeMillis");proof.put("anchorMeaning","FIRST_SOURCE_SAMPLE_REFERENCE_ONLY_NOT_ACTIVATION");proof.put("diagnostic",diagnostic);
        var time=now<0?VerificationObservation.SourceTime.unavailable(diagnostic):new VerificationObservation.SourceTime("OBSERVED",identity,"EPOCH_MILLISECONDS",Long.toString(now),AUTHORITY,"");
        sink.accept(new VerificationObservation(VerificationObservation.Kind.CLOCK,identity,diagnostic.isEmpty()?Completeness.COMPLETE:Completeness.PARTIAL,diagnostic,time,
                Map.of("clockIdentity",identity,"sourceProof",proof)));
        previous=now;lastPulse=tick;
    }
    @Override public synchronized void close(){closed=true;}
}
