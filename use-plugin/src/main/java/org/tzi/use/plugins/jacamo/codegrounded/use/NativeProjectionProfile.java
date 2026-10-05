package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.util.*;

/** AUTO and FULL share the domain boundary; FULL never opts execution internals back into USE. */
public final class NativeProjectionProfile {
    private final NativeProjectionMode mode;
    private final Map<String,NativeProjectionStatus> statuses;
    private final Map<String,String> rationales;
    private NativeProjectionProfile(NativeProjectionMode mode) {
        this.mode=mode; Map<String,NativeProjectionStatus> s=new TreeMap<>(); Map<String,String> r=new TreeMap<>();
        for(String concept:NativeProjectionPolicy.knownConcepts()) {
            s.put(concept,statusFor(concept)); r.put(concept,"POLICY="+NativeProjectionPolicy.VERSION+"; DECISION="+NativeProjectionPolicy.decide(concept));
        }
        statuses=Collections.unmodifiableMap(s); rationales=Collections.unmodifiableMap(r);
    }
    public static NativeProjectionProfile forMode(NativeProjectionMode mode) { return new NativeProjectionProfile(mode==null ? NativeProjectionMode.AUTO : mode); }
    public NativeProjectionMode mode() { return mode; }
    public NativeProjectionStatus statusFor(String concept) {
        return switch(NativeProjectionPolicy.decide(concept)) {
            case EXPOSE_CLASS, EXPOSE_CONCRETE_SUBCLASS, EXPOSE_ASSOCIATION_CLASS, EXPOSE_ASSOCIATION,
                    FLATTEN_TO_ATTRIBUTE, FLATTEN_TO_OPERATION, FLATTEN_TO_RELATION -> NativeProjectionStatus.MATERIALIZED;
            case CONSTRAINT_ONLY -> NativeProjectionStatus.EVIDENCE_ONLY;
            case TRACE_ONLY -> NativeProjectionPolicy.executionOnly(concept)
                    ? NativeProjectionStatus.PROFILE_EXCLUDED : NativeProjectionStatus.EVIDENCE_ONLY;
            case UNSUPPORTED -> NativeProjectionStatus.UNSUPPORTED;
        };
    }
    public boolean materializesClass(String name) { return switch(NativeProjectionPolicy.decide(name)) {
        case EXPOSE_CLASS, EXPOSE_CONCRETE_SUBCLASS, EXPOSE_ASSOCIATION_CLASS -> true;
        default -> false;
    }; }
    public boolean materializesOrderEntries() { return false; }
    public Map<String,NativeProjectionStatus> conceptStatuses() { return statuses; }
    public Map<String,String> rationales() { return rationales; }
    public Set<String> profileExcludedClasses() { return conceptsWith(NativeProjectionStatus.PROFILE_EXCLUDED); }
    public Set<String> evidenceOnlyClasses() { return conceptsWith(NativeProjectionStatus.EVIDENCE_ONLY); }
    private Set<String> conceptsWith(NativeProjectionStatus status) {
        var result=new TreeSet<String>(); statuses.forEach((concept,value)->{ if(value==status) result.add(concept); });
        return Collections.unmodifiableSet(result);
    }
}
