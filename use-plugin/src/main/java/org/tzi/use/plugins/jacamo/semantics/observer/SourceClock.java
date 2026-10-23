package org.tzi.use.plugins.jacamo.semantics.observer;

import java.util.*;
import org.jacamo.bridge.contract.*;

/** Immutable evidence view. No consumer/replay wall clock, hidden state or
 * Boolean fallback. Integer milliseconds use the frozen source run anchor. */
public record SourceClock(String clockIdentity,Long anchorEpochMillis,Long nowEpochMillis,
        Completeness quality,String diagnostic,Map<String,Object> proof) {
    private static final String AUTHORITY="JAVA_SYSTEM_CURRENT_TIME_MILLIS_SAME_PROCESS_AS_PINNED_NPL";
    public enum DeadlineStatus { NOT_DUE, EXPIRED, INCONCLUSIVE }
    public record Relative(Long rawEpochMillis,Integer value,String diagnostic) { }
    public record Deadline(DeadlineStatus status,String diagnostic) { }
    public SourceClock {proof=org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.object(proof);}
    public static SourceClock unavailable(){return new SourceClock("",null,null,Completeness.UNAVAILABLE,"SOURCE_CLOCK_UNAVAILABLE",Map.of());}
    public static SourceClock fromCut(RuntimeSnapshot cut,String run,long generation,String revision,String nplHash) {
        var clocks=cut.facts().stream().filter(f->f.values().containsKey(VerificationObservation.KEY)).map(f->VerificationObservation.require(f,cut,run,generation)).filter(o->o.kind()==VerificationObservation.Kind.CLOCK).toList();
        return clocks.size()==1?decode(clocks.getFirst(),run,generation,revision,nplHash):unavailable();
    }
    public static SourceClock decode(VerificationObservation observation,String run,long generation,String revision,String nplHash) {
        need(observation.kind()==VerificationObservation.Kind.CLOCK,"KIND");var time=observation.sourceTime();var data=observation.data();var proof=CanonicalJson.object(data.get("sourceProof"));
        // Older recordings may carry an explicitly unavailable clock
        // of another authority. Preserve its evidence, but never grant it a
        // time basis or use it for a deadline. Qualified claims stay strict.
        if(!qualified(observation)&&observation.quality()!=Completeness.COMPLETE&&time.status().equals("UNAVAILABLE"))
            return new SourceClock((String)data.get("clockIdentity"),null,null,Completeness.UNAVAILABLE,"SOURCE_CLOCK_PROOF_UNSUPPORTED:"+observation.diagnostic(),proof);
        String identity="npl-system-wall:"+run+":"+generation;
        need(proof.keySet().equals(Set.of("version","sessionId","generation","modelRevision","anchorEpochMillis","previousEpochMillis","highWaterEpochMillis","clockIdentity","unit","authority","monotonicity","nplArtifact","sampler","anchorMeaning","diagnostic")),"PROOF_FIELDS");
        need(identity.equals(observation.subject())&&identity.equals(data.get("clockIdentity"))&&identity.equals(proof.get("clockIdentity")),"IDENTITY");
        need("NPL_SYSTEM_WALL_CLOCK/1.0".equals(proof.get("version"))&&run.equals(proof.get("sessionId"))&&generation==integer(proof.get("generation"))&&revision.equals(proof.get("modelRevision")),"SCOPE_OR_VERSION");
        need("EPOCH_MILLISECONDS".equals(proof.get("unit"))&&AUTHORITY.equals(proof.get("authority"))&&"UNPROVEN_WALL_CLOCK".equals(proof.get("monotonicity"))
                &&"System.currentTimeMillis".equals(proof.get("sampler"))&&"FIRST_SOURCE_SAMPLE_REFERENCE_ONLY_NOT_ACTIVATION".equals(proof.get("anchorMeaning")),"TIME_BASIS");
        var binary=CanonicalJson.object(proof.get("nplArtifact"));need(nplHash!=null&&nplHash.equals(binary.get("artifactSha256"))&&"0.6.1".equals(binary.get("artifactVersion"))&&"SUPPORTED_PINNED_BINARY".equals(binary.get("admission")),"BINARY");
        long anchor=epoch(proof,"anchorEpochMillis"),previous=epoch(proof,"previousEpochMillis"),high=epoch(proof,"highWaterEpochMillis");
        need(anchor>=0&&previous>=0&&high>=anchor&&high>=previous&&observation.diagnostic().equals(proof.get("diagnostic")),"ANCHOR_OR_DIAGNOSTIC");
        Long now=null;if(time.status().equals("OBSERVED")){need(identity.equals(time.clockId())&&AUTHORITY.equals(time.authority()),"SOURCE_TIME");now=Long.valueOf(time.rawValue());need(high>=now,"HIGH_WATER");}
        var result=new SourceClock(identity,anchor,now,observation.quality(),observation.diagnostic(),proof);
        if(observation.quality()==Completeness.COMPLETE)need(now!=null&&now>=previous&&high==now&&result.relative(now).value()!=null,"FALSE_COMPLETENESS");
        if(now!=null&&now<high)need(observation.diagnostic().contains("REGRESSION"),"REGRESSION_UNREPORTED");
        return result;
    }
    public Relative relative(Long raw) {
        if(raw==null||anchorEpochMillis==null)return new Relative(raw,null,"SOURCE_TIME_UNAVAILABLE");
        try {long delta=Math.subtractExact(raw,anchorEpochMillis);if(delta<0||delta>Integer.MAX_VALUE)return new Relative(raw,null,"SOURCE_TIME_INTEGER_RANGE");
            return new Relative(raw,Math.toIntExact(delta),"");
        }catch(ArithmeticException invalid){return new Relative(raw,null,"SOURCE_TIME_INTEGER_RANGE");}
    }
    /** Pure pending comparison, not an NPL terminal event or verification FAIL. */
    public Deadline pending(String sourceClock,Long deadlineEpochMillis) {
        if(quality!=Completeness.COMPLETE||nowEpochMillis==null)return new Deadline(DeadlineStatus.INCONCLUSIVE,diagnostic);
        if(!clockIdentity.equals(sourceClock))return new Deadline(DeadlineStatus.INCONCLUSIVE,"SOURCE_CLOCK_MISMATCH");
        var now=relative(nowEpochMillis);var end=relative(deadlineEpochMillis);
        if(now.value()==null||end.value()==null)return new Deadline(DeadlineStatus.INCONCLUSIVE,end.value()==null?end.diagnostic():now.diagnostic());
        return new Deadline(nowEpochMillis<=deadlineEpochMillis?DeadlineStatus.NOT_DUE:DeadlineStatus.EXPIRED,"");
    }
    /** Keep the clock proof in the existing projector evidence/savepoint. */
    public static void requireContinuation(VerificationObservation previous,VerificationObservation next) {
        if(!qualified(previous))return;
        need(qualified(next),"QUALIFIED_CLOCK_PROOF_LOST");
        var old=CanonicalJson.object(previous.data().get("sourceProof"));var value=CanonicalJson.object(next.data().get("sourceProof"));
        need(previous.subject().equals(next.subject())&&old.get("anchorEpochMillis").equals(value.get("anchorEpochMillis")),"ANCHOR_CHANGED");
        need(epoch(value,"highWaterEpochMillis")>=epoch(old,"highWaterEpochMillis"),"HIGH_WATER_REGRESSED");
        if(previous.diagnostic().contains("REGRESSION"))need(next.diagnostic().contains("REGRESSION"),"REGRESSION_HISTORY_LOST");
    }
    public static boolean qualified(VerificationObservation value){return value.kind()==VerificationObservation.Kind.CLOCK&&"NPL_SYSTEM_WALL_CLOCK/1.0".equals(CanonicalJson.object(value.data().get("sourceProof")).get("version"));}
    private static long epoch(Map<String,Object> map,String key){try{if(!(map.get(key) instanceof String text))throw invalid(key);return Long.parseLong(text);}catch(NumberFormatException invalid){throw invalid(key);}}
    private static long integer(Object value){try{if(!(value instanceof Number))throw invalid("INTEGER");return new java.math.BigDecimal(value.toString()).longValueExact();}catch(ArithmeticException|NumberFormatException invalid){throw invalid("INTEGER");}}
    private static void need(boolean condition,String reason){if(!condition)throw invalid(reason);}
    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("SOURCE_CLOCK_"+reason);}
}
