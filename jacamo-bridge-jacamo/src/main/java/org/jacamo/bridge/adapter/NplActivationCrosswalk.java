package org.jacamo.bridge.adapter;

import java.util.*;
import npl.NormInstance;
import jason.asSyntax.*;

/** NPL listeners receive copies (0.6.1 Notifier); public created annotations provide a checked crosswalk. */
final class NplActivationCrosswalk {
    private static final String API_FINGERPRINT=fingerprint();
    private static String fingerprint(){try(var in=NormInstance.class.getResourceAsStream("NormInstance.class")){if(in==null)throw new IllegalStateException("NPL_CLASS_RESOURCE_UNAVAILABLE");return AdapterEvidence.digest(in.readAllBytes());}catch(java.io.IOException error){throw new IllegalStateException(error);}}
    private boolean overflow;
    private final String run;private final long generation;
    NplActivationCrosswalk(){this("",0);}
    NplActivationCrosswalk(String run,long generation){this.run=run;this.generation=generation;}
    private final Set<String> created=new HashSet<>(),ambiguous=new HashSet<>();
    synchronized Map<String,Object> observe(String board,NormInstance value,String origin,String reportedState) {
        var result=new LinkedHashMap<String,Object>();result.put("observationVersion","NPL_OBSERVATION/1.0");result.put("sourceVersion",NplRuntimeProfile.evidence().get("artifactVersion"));result.put("sourceArtifact",NplRuntimeProfile.evidence());result.put("nplNormInstanceClassSha256",API_FINGERPRINT);result.put("norm",value.toString());result.put("normDefinition",value.getNorm().getId());result.put("subject",value.getAg().toString());result.put("substitution",value.getUnifierAsTerm().toString());result.put("maintenance",value.getMaintenanceCondition().toString());result.put("aim",value.getAim().toString());result.put("state",reportedState);result.put("origin",origin);
        result.put("modality",value.isObligation()?"OBLIGATION":value.isPermission()?"PERMISSION":value.isProhibition()?"PROHIBITION":"UNKNOWN");result.put("deadlineRaw",value.getTerm(3).toString());
        String deadlineKind=value.getStateDeadline()!=null?"STATE":value.getTerm(3) instanceof npl.TimeTerm?"ABSOLUTE_NPL_TIME":"UNRESOLVED_TIME";result.put("deadlineKind",deadlineKind);result.put("deadline","");
        if(deadlineKind.equals("ABSOLUTE_NPL_TIME"))try{result.put("deadline",Long.toString(epoch((npl.TimeTerm)value.getTerm(3))));}catch(RuntimeException unavailable){result.put("deadlineTimeDiagnostic","NPL_TIME_NOT_EXACTLY_REPRESENTABLE");}
        result.put("clockEvidence","SOURCE_NPL_SYSTEM_TIME_REPORTED_NO_REPLAY_CLOCK");result.put("boardArtifactIdentity",board);
        result.put("sourceLifecycle",NplLifecycleEvidence.capture(value,run,generation,reportedState));
        if(!run.isBlank())result.put("timeBasis",NplSourceClockObservation.timeBasis(run,generation));
        String activation="";var annotations=value.getAnnots("created");
        try {if(annotations!=null && annotations.size()==1 && annotations.get(0) instanceof Structure annotation && annotation.getArity()==1 && annotation.getTerm(0) instanceof npl.TimeTerm time) {
            long instant=epoch(time);result.put("createdEpochMillis",Long.toString(instant));activation=AdapterEvidence.digest(org.jacamo.bridge.contract.CanonicalJson.encode(List.of(board,value.getNorm().getId(),value.getAg().toString(),value.getUnifierAsTerm().toString(),instant)));
        }}catch(RuntimeException unsupported){result.put("activationDiagnostic",unsupported.getClass().getSimpleName());}
        String status=activation.isBlank()?"ACTIVATION_ORIGIN_UNAVAILABLE":"BOUND_PUBLIC_CREATED_ANNOTATION";
        if(!activation.isBlank() && origin.equals("LISTENER_CREATED") && !created.add(activation))ambiguous.add(activation);
        if(ambiguous.contains(activation))status="AMBIGUOUS_NORM_INSTANCE";
        if(created.size()>8192 || ambiguous.size()>8192){ambiguous.clear();created.clear();overflow=true;}
        if(overflow)status="ACTIVATION_CROSSWALK_CAPACITY_EXCEEDED";
        result.put("activationKey",activation);result.put("activationIdentityStatus",status);result.put("history",!activation.isBlank() && created.contains(activation)?"CREATED_EVENT_OBSERVED":"ACTIVATION_HISTORY_UNPROVEN");return JasonStructuredTerms.immutable(result);
    }
    /** Public TimeTerm.solve exposes a double. Reject the ambiguous range,
     * especially the 'never' sentinel; never truncate/round it into an epoch. */
    static long epoch(npl.TimeTerm time) {
        double raw=time.solve();if(!Double.isFinite(raw)||raw<0||raw>=9007199254740992d||raw!=Math.rint(raw))throw new IllegalArgumentException("NPL_TIME_NOT_EXACTLY_REPRESENTABLE");
        return java.math.BigDecimal.valueOf(raw).longValueExact();
    }
}
