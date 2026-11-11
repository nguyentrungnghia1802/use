package org.jacamo.bridge.adapter;

import java.util.*;
import jason.asSyntax.Structure;
import npl.NormInstance;
import npl.TimeTerm;
import org.jacamo.bridge.contract.CanonicalJson;

/** Public annotations of the pinned NPL instance, not listener receipt time.
 * The numeric done annotation is a deadline delta and is deliberately unused. */
final class NplLifecycleEvidence {
    static final String VERSION="NPL_TIME_ANNOTATIONS/1.0",AUTHORITY="NPL061_PUBLIC_TIMETERM_ANNOTATION_EPOCH_MILLISECONDS";
    static Map<String,Object> capture(NormInstance value,String run,long generation,String state) {
        var result=new LinkedHashMap<String,Object>();var terminal=new TreeMap<String,String>();String diagnostic="",created="";
        try {
            created=annotation(value,"created",true);
            for(String name:List.of("fulfilled","unfulfilled","inactive")) {String time=annotation(value,name,false);if(!time.isBlank())terminal.put(name,time);}
            if(!state.equals(value.getState().name().toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("NPL_STATE_ANNOTATION_CONFLICT");
            if(state.equals("ACTIVE")&&!terminal.isEmpty()||!state.equals("ACTIVE")&&!terminal.containsKey(state.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("NPL_TERMINAL_ANNOTATION_UNAVAILABLE");
        }catch(RuntimeException unavailable){diagnostic=Objects.toString(unavailable.getMessage(),unavailable.getClass().getSimpleName());}
        var subject=JasonStructuredTerms.term(value.getAg());var aim=JasonStructuredTerms.term((jason.asSyntax.Term)value.getAim());var maintenance=JasonStructuredTerms.term((jason.asSyntax.Term)value.getMaintenanceCondition());
        if(!Boolean.TRUE.equals(subject.get("supported"))||!Boolean.TRUE.equals(aim.get("supported"))||!Boolean.TRUE.equals(maintenance.get("supported")))diagnostic="NPL_STRUCTURED_BINDING_UNSUPPORTED";
        result.put("version",VERSION);result.put("authority",AUTHORITY);result.put("clockIdentity",run.isBlank()?"":NplSourceClockObservation.clockIdentity(run,generation));
        result.put("createdEpochMillis",created);result.put("terminalTimes",terminal);result.put("terminalEpochMillis",terminal.values().stream().mapToLong(Long::parseLong).min().stream().mapToObj(Long::toString).findFirst().orElse(""));
        result.put("subjectTerm",subject);result.put("aimTerm",aim);result.put("maintenanceTerm",maintenance);result.put("state",state);
        result.put("status",diagnostic.isEmpty()?"COMPLETE":"INCOMPLETE");result.put("diagnostic",diagnostic);result.put("hash",AdapterEvidence.digest(CanonicalJson.encode(result)));
        return JasonStructuredTerms.immutable(result);
    }
    private static String annotation(NormInstance value,String name,boolean required) {
        var values=value.getAnnots(name);if(values==null||values.isEmpty()){if(required)throw new IllegalArgumentException("NPL_CREATED_ANNOTATION_UNAVAILABLE");return "";}
        if(values.size()!=1||!(values.get(0) instanceof Structure row)||row.getArity()!=1||!(row.getTerm(0) instanceof TimeTerm time))throw new IllegalArgumentException("NPL_TIME_ANNOTATION_AMBIGUOUS_OR_UNSUPPORTED:"+name);
        return Long.toString(NplActivationCrosswalk.epoch(time));
    }
    private NplLifecycleEvidence(){ }
}
