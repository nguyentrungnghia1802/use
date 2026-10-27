package org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.normative.*;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.sys.MObject;

/** Adapt the existing reported-lifecycle monitor to rollback-owned native metadata. No deadline verdict. */
final class NplObserverHistory {
    private static final String PREFIX="xml:observer:lifecycle:";
    private final String column,request;
    private final NplLifecycleCut decoder;
    private final boolean sourceContinuity;
    NplObserverHistory(String column,String request,String hash){this(column,request,hash,"NPL_REPORTED");}
    NplObserverHistory(String column,String request,String hash,String policy){
        if(!Set.of("NPL_REPORTED","NPL_REPORTED_SOURCE_CONTINUOUS_V1").contains(policy))throw new IllegalArgumentException("NPL_OBSERVER_POLICY_UNSUPPORTED");
        this.column=column;this.request=request;decoder=new NplLifecycleCut(hash);sourceContinuity=policy.equals("NPL_REPORTED_SOURCE_CONTINUOUS_V1");
    }
    SemanticVerdict observe(Port port,MObject object,RuntimeTables.Row row,String run,long generation) {
        if("ERROR".equals(port.metadata(object,PREFIX+"verdict")))return SemanticVerdict.ERROR;
        String prior=port.metadata(object,PREFIX+"state");
        if(row==null||row.quality()!=Completeness.COMPLETE||!row.proofs().containsKey("capability:"+request)||!(row.values().get(column) instanceof Map<?,?>)) {
            // Missing mapping/crosswalk qualification is not evidence that the
            // producer missed NPL callbacks. Current fields remain unknown, but
            // the versioned source policy can recover from a later qualified
            // cut carrying the same continuous creation subscription.
            if(!sourceContinuity&&(prior.isBlank()||prior.equals("ACTIVE")))port.metadata(object,PREFIX+"gap","true");
            return record(port,object,SemanticVerdict.INCONCLUSIVE,"NPL_CURRENT_QUALIFIED_ACTIVATION_UNAVAILABLE");
        }
        if(run==null||run.isBlank()||generation<0)throw new IllegalArgumentException("NPL_OBSERVER_EXPLICIT_EPOCH_REQUIRED");
        var raw=CanonicalJson.object(row.values().get(column));var evidence=decoder.decode(BridgeEntityId.parse(row.identity()),raw,run,generation);
        String subscription=CanonicalJson.object(raw.get("boardObservationProof")).get("sourceSubscriptionNumber").toString();
        String storedSubscription=port.metadata(object,PREFIX+"subscription"),terminal=port.metadata(object,PREFIX+"terminal"),sequence=port.metadata(object,PREFIX+"sequence");
        boolean conflict=!storedSubscription.isBlank()&&!storedSubscription.equals(subscription)
                ||!terminal.isBlank()&&evidence.terminalEpochMillis()!=null&&!terminal.equals(evidence.terminalEpochMillis().toString())
                ||!sequence.isBlank()&&evidence.sourceSequence()<Long.parseLong(sequence);
        if(conflict)return record(port,object,SemanticVerdict.ERROR,"NPL_SUBSCRIPTION_TERMINAL_OR_SEQUENCE_CONFLICT");
        boolean gap="true".equals(port.metadata(object,PREFIX+"gap"))||!evidence.continuous();
        boolean restored=sourceContinuity&&prior.isBlank()&&!gap&&evidence.timeComplete()
                &&evidence.createdEpochMillis()!=null&&evidence.terminalEpochMillis()!=null
                &&evidence.norm().history().equals("CREATED_EVENT_OBSERVED")
                &&evidence.norm().state()!=NormativeObservation.State.ACTIVE;
        // The source prefix is verified by the decoder's pinned public
        // annotations and creation subscription. This is not a synthetic event
        // or a consumer-time transition; retain explicit provenance below.
        var previous=restored?NormativeObservation.State.ACTIVE:prior.isBlank()?null:NormativeObservation.State.valueOf(prior);
        var lifecycle=new NormLifecycleMonitor().observe(previous,evidence.norm(),gap);
        port.metadata(object,PREFIX+"state",evidence.norm().state().name());port.metadata(object,PREFIX+"subscription",subscription);port.metadata(object,PREFIX+"sequence",Long.toString(evidence.sourceSequence()));
        if(evidence.terminalEpochMillis()!=null)port.metadata(object,PREFIX+"terminal",evidence.terminalEpochMillis().toString());
        if(lifecycle.verdict()==SemanticVerdict.INCONCLUSIVE)port.metadata(object,PREFIX+"gap","true");
        port.metadata(object,PREFIX+"sourceHash",SemanticJson.hash(raw));
        return record(port,object,lifecycle.verdict(),restored&&lifecycle.verdict()==SemanticVerdict.PASS?"SOURCE_REPORTED_TERMINAL_PREFIX_RESTORED":lifecycle.diagnostic());
    }
    private static SemanticVerdict record(Port port,MObject object,SemanticVerdict verdict,String diagnostic){port.metadata(object,PREFIX+"verdict",verdict.name());port.metadata(object,PREFIX+"diagnostic",diagnostic);return verdict;}
}
