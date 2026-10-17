package org.tzi.use.plugins.jacamo.semantics.normative;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.api.UseApiException;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder;

public final class NormativeObservationMapper {
    public Result apply(Port port,BridgeEntityId identity,Map<String,Object> payload)throws UseApiException {
        var observed=NormativeObservation.decode(identity,payload);var object=port.upsert(observed.identity(),NormativeUseBinding.TYPE,"norm-observation");
        var previous=port.metadata(object,"normReportedState");var check=new NormLifecycleMonitor().observe(previous.isBlank()?null:NormativeObservation.State.valueOf(previous),observed,"true".equals(port.metadata(object,"normHistoryGap")));
        if("ERROR".equals(port.metadata(object,"normLifecycleVerdict")))check=new NormLifecycleMonitor.Result(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.ERROR,port.metadata(object,"normLifecycleDiagnostic"));
        for(var attribute:object.cls().attributes())port.value(object,attribute.name(),new StringValue(Objects.toString(payload.get(attribute.name()),"")));
        port.metadata(object,"authority","npl");port.metadata(object,"boardArtifactIdentity",observed.boardUuid());port.metadata(object,"normReportedState",observed.state().name());port.metadata(object,"normHistoryGap",Boolean.toString(check.verdict()==org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.INCONCLUSIVE));port.metadata(object,"normLifecycleVerdict",check.verdict().name());port.metadata(object,"normLifecycleDiagnostic",check.diagnostic());port.metadata(object,"normSource",NativeUseModelBuilder.canonicalJson(payload));port.metadata(object,"lastNormCut",SemanticJson.hash(payload));port.metadata(object,"sourceProjectionStatus","EVIDENCE_ONLY");
        return new Result(observed.identity(),object,"NPL_REPORTED_OBSERVER_ONLY_NO_DEONTIC_EQUIVALENCE");
    }
}
