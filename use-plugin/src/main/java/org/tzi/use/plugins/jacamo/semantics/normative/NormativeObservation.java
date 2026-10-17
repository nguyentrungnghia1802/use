package org.tzi.use.plugins.jacamo.semantics.normative;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

public record NormativeObservation(String identity,String boardUuid,String activation,String norm,String subject,String substitution,String modality,State state,String history,Map<String,Object> raw) {
    public enum State { ACTIVE, FULFILLED, UNFULFILLED, INACTIVE }
    public NormativeObservation {raw=SemanticJson.object(raw);}
    public static NormativeObservation decode(BridgeEntityId identity,Map<String,Object> payload) {
        if(identity==null || !identity.authority().equals("npl") || !identity.kind().equals("norm-instance") || !"NPL_OBSERVATION/1.0".equals(payload.get("observationVersion")))throw new IllegalArgumentException("NPL_OBSERVER_IDENTITY_OR_VERSION");
        if(!"BOUND_PUBLIC_CREATED_ANNOTATION".equals(payload.get("activationIdentityStatus")))throw new IllegalArgumentException("UNSUPPORTED:"+payload.get("activationIdentityStatus"));
        String uuid=text(payload,"boardArtifactIdentity"),activation=text(payload,"activationKey");UUID.fromString(uuid);
        long created;try{created=Long.parseLong(text(payload,"createdEpochMillis"));}catch(NumberFormatException invalid){throw new IllegalArgumentException("NPL_CREATED_TIME_INVALID",invalid);}
        String expected=SemanticJson.hash(List.of(uuid,text(payload,"normDefinition"),text(payload,"subject"),text(payload,"substitution"),created));if(!activation.equals(expected))throw new IllegalArgumentException("NPL_ACTIVATION_CROSSWALK_DIGEST_MISMATCH");
        if(!identity.incarnation().endsWith(":"+uuid) || !identity.localId().equals(activation))throw new IllegalArgumentException("NPL_BOARD_OR_ACTIVATION_IDENTITY_MISMATCH");
        if(!Set.of("OBLIGATION","PERMISSION","PROHIBITION").contains(payload.get("modality")))throw new IllegalArgumentException("UNSUPPORTED:NPL_MODALITY");
        for(String field:List.of("aim","maintenance","deadlineKind","deadlineRaw","deadline","clockEvidence","sourceVersion"))if(!(payload.get(field) instanceof String))throw new IllegalArgumentException("NPL_OBSERVER_FIELD:"+field);
        return new NormativeObservation(identity.canonical(),uuid,activation,text(payload,"normDefinition"),text(payload,"subject"),text(payload,"substitution"),text(payload,"modality"),State.valueOf(text(payload,"state")),text(payload,"history"),payload);
    }
    private static String text(Map<String,Object> map,String key){if(!(map.get(key) instanceof String s)||s.isBlank())throw new IllegalArgumentException("NPL_REQUIRED_TEXT:"+key);return s;}
}
