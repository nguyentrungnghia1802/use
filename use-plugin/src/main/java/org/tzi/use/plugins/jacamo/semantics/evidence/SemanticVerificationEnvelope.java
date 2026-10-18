package org.tzi.use.plugins.jacamo.semantics.evidence;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.semantics.istar.IntentionalMarking;

/** Portable logical evidence; no wall-clock timing or mutable USE references participate. */
public record SemanticVerificationEnvelope(String version,String profile,String configurationHash,String boundary,
        List<SemanticConstraintResult> constraints,Map<String,IntentionalMarking> requirements,
        Map<String,String> coverage,List<String> decisions,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY) Map<String,Object> observation) {
    public SemanticVerificationEnvelope(String version,String profile,String configurationHash,String boundary,List<SemanticConstraintResult> constraints,Map<String,IntentionalMarking> requirements,Map<String,String> coverage,List<String> decisions){this(version,profile,configurationHash,boundary,constraints,requirements,coverage,decisions,Map.of());}
    private static final ObjectMapper JSON=new ObjectMapper();
    public SemanticVerificationEnvelope {
        if(!"1.0".equals(version))throw new IllegalArgumentException("SEMANTIC_EVIDENCE_VERSION_UNSUPPORTED");
        observation=SemanticJson.object(observation==null?Map.of():observation);
        constraints=List.copyOf(constraints);requirements=Map.copyOf(requirements);coverage=Map.copyOf(coverage);decisions=List.copyOf(decisions);
    }
    public SemanticVerificationEnvelope failClosed(String reason) {
        var values=constraints.stream().map(r->{var witness=new TreeMap<String,String>(r.witness());witness.putIfAbsent("lastObservedVerdict",r.verdict().name());return new SemanticConstraintResult(r.id(),r.kind(),r.contextClass(),r.contextObject(),r.sourceIdentity(),r.expression(),r.verdict()==SemanticVerdict.UNSUPPORTED?SemanticVerdict.UNSUPPORTED:SemanticVerdict.SKIPPED,r.backendValue(),reason,r.stateVersion(),r.invocation(),witness);}).toList();
        var markings=new TreeMap<String,IntentionalMarking>();requirements.forEach((id,m)->markings.put(id,new IntentionalMarking(m.occurrenceId(),IntentionalMarking.Status.UNDETERMINED,IntentionalMarking.Status.UNDETERMINED,IntentionalMarking.Lifecycle.UNKNOWN,SemanticVerdict.SKIPPED,m.episodeIdentity(),m.bindingToken(),m.firstSatisfied(),m.firstViolated(),true,reason)));
        var quality=new TreeMap<String,String>(coverage);quality.put("history","GAP");quality.put("failure",reason);
        var observed=new TreeMap<String,Object>(observation);
        if(observation.get("originalEngine") instanceof Map<?,?> source) {
            var core=new TreeMap<String,Object>(CanonicalJson.object(source));core.put("historyQuality","GAP");core.put("qualityOverlay","HISTORY_GAP");core.put("failure",reason);observed.put("originalEngine",core);
        }
        return new SemanticVerificationEnvelope(version,profile,configurationHash,boundary,values,markings,quality,decisions,observed);
    }
    public Map<String,Object> toMap() {try{return CanonicalJson.object(CanonicalJson.decode(JSON.writeValueAsBytes(this)));}
        catch(java.io.IOException e){throw new IllegalStateException("SEMANTIC_EVIDENCE_ENCODING",e);}}
    public static SemanticVerificationEnvelope fromMap(Map<String,Object> map) {try{return JSON.readValue(CanonicalJson.encode(map),SemanticVerificationEnvelope.class);}
        catch(java.io.IOException e){throw new IllegalArgumentException("SEMANTIC_EVIDENCE_DECODING",e);}}
}
