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
    NplObserverHistory(String column,String request,String hash){this.column=column;this.request=request;decoder=new NplLifecycleCut(hash);}
    SemanticVerdict observe(Port port,MObject object,RuntimeTables.Row row,String run,long generation) {
        if("ERROR".equals(port.metadata(object,PREFIX+"verdict")))return SemanticVerdict.ERROR;
        String prior=port.metadata(object,PREFIX+"state");
        if(row==null||row.quality()!=Completeness.COMPLETE||!row.proofs().containsKey("capability:"+request)||!(row.values().get(column) instanceof Map<?,?>)) {
            if(prior.isBlank()||prior.equals("ACTIVE"))port.metadata(object,PREFIX+"gap","true");
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
        var lifecycle=new NormLifecycleMonitor().observe(prior.isBlank()?null:NormativeObservation.State.valueOf(prior),evidence.norm(),"true".equals(port.metadata(object,PREFIX+"gap"))||!evidence.continuous());
        port.metadata(object,PREFIX+"state",evidence.norm().state().name());port.metadata(object,PREFIX+"subscription",subscription);port.metadata(object,PREFIX+"sequence",Long.toString(evidence.sourceSequence()));
        if(evidence.terminalEpochMillis()!=null)port.metadata(object,PREFIX+"terminal",evidence.terminalEpochMillis().toString());
        if(lifecycle.verdict()==SemanticVerdict.INCONCLUSIVE)port.metadata(object,PREFIX+"gap","true");
        port.metadata(object,PREFIX+"sourceHash",SemanticJson.hash(raw));
        return record(port,object,lifecycle.verdict(),lifecycle.diagnostic());
    }
    private static SemanticVerdict record(Port port,MObject object,SemanticVerdict verdict,String diagnostic){port.metadata(object,PREFIX+"verdict",verdict.name());port.metadata(object,PREFIX+"diagnostic",diagnostic);return verdict;}
}
