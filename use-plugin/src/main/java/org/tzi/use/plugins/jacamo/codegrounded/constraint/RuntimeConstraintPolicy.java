package org.tzi.use.plugins.jacamo.codegrounded.constraint;

import java.util.Set;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointType;
import org.tzi.use.plugins.jacamo.verification.ConstraintOrigin;

/** Explicit enforcement admission, independent of the invariant's textual name/expression. */
public record RuntimeConstraintPolicy(ConstraintOrigin origin,Severity severity,Enforcement enforcement,
                                      Set<CheckpointType> checkpoints,Set<String> requiredCapabilities,
                                      boolean approved,String provenance,java.util.Map<String,String> exactEvidenceTargets) {
    public enum Severity { HARD, SOFT }
    public enum Enforcement { REPORT_ONLY, PAUSE_ON_FAIL }
    public RuntimeConstraintPolicy {
        java.util.Objects.requireNonNull(origin);java.util.Objects.requireNonNull(severity);java.util.Objects.requireNonNull(enforcement);
        checkpoints=Set.copyOf(checkpoints);requiredCapabilities=Set.copyOf(requiredCapabilities);
        exactEvidenceTargets=java.util.Map.copyOf(exactEvidenceTargets);
        if(exactEvidenceTargets.entrySet().stream().anyMatch(e->e.getKey().isBlank() || e.getValue().isBlank()) || exactEvidenceTargets.size()>64)
            throw new IllegalArgumentException("RUNTIME_POLICY_EXACT_EVIDENCE_INVALID");
        if(provenance==null || provenance.isBlank() || checkpoints.isEmpty()
                || enforcement==Enforcement.PAUSE_ON_FAIL && (severity!=Severity.HARD || !approved))
            throw new IllegalArgumentException("RUNTIME_ENFORCEMENT_NOT_APPROVED");
    }
    public RuntimeConstraintPolicy(ConstraintOrigin origin,Severity severity,Enforcement enforcement,
            Set<CheckpointType> checkpoints,Set<String> requiredCapabilities,boolean approved,String provenance) {
        this(origin,severity,enforcement,checkpoints,requiredCapabilities,approved,provenance,java.util.Map.of());
    }
    public static RuntimeConstraintPolicy reportOnly(ConstraintOrigin origin,String provenance) {
        return new RuntimeConstraintPolicy(origin,Severity.SOFT,Enforcement.REPORT_ONLY,Set.of(CheckpointType.SNAPSHOT,
                CheckpointType.AFTER_MUTATION,CheckpointType.STREAM_BOUNDARY,CheckpointType.OPERATION_POST),Set.of(),false,provenance);
    }
    public java.util.Map<String,Object> toMap() {
        return java.util.Map.of("schemaVersion","1.1.0","origin",origin.name(),"severity",severity.name(),
                "enforcement",enforcement.name(),"approved",approved,"provenance",provenance,
                "checkpoints",checkpoints.stream().map(Enum::name).sorted().toList(),
                "requiredCapabilities",requiredCapabilities.stream().sorted().toList(),"exactEvidenceTargets",exactEvidenceTargets);
    }
    public static RuntimeConstraintPolicy fromMap(java.util.Map<String,Object> values) {
        var keys=new java.util.HashSet<>(Set.of("schemaVersion","origin","severity","enforcement","approved","provenance","checkpoints","requiredCapabilities"));
        boolean extended="1.1.0".equals(values.get("schemaVersion"));if(extended)keys.add("exactEvidenceTargets");
        if(!values.keySet().equals(keys) || !Set.of("1.0.0","1.1.0").contains(values.get("schemaVersion"))
                || !(values.get("approved") instanceof Boolean))throw new IllegalArgumentException("RUNTIME_POLICY_SCHEMA_UNSUPPORTED");
        var checkpoints=new java.util.HashSet<CheckpointType>();
        for(Object value:(java.util.List<?>)values.get("checkpoints"))
            if(!(value instanceof String name) || !checkpoints.add(CheckpointType.valueOf(name)))throw new IllegalArgumentException("RUNTIME_POLICY_CHECKPOINT_INVALID");
        var capabilities=new java.util.HashSet<String>();
        for(Object value:(java.util.List<?>)values.get("requiredCapabilities"))
            if(!(value instanceof String name) || name.isBlank() || !capabilities.add(name))throw new IllegalArgumentException("RUNTIME_POLICY_CAPABILITY_INVALID");
        var targets=new java.util.LinkedHashMap<String,String>();
        if(extended)org.jacamo.bridge.contract.CanonicalJson.object(values.get("exactEvidenceTargets")).forEach((id,rationale)->targets.put(id,(String)rationale));
        return new RuntimeConstraintPolicy(ConstraintOrigin.valueOf((String)values.get("origin")),Severity.valueOf((String)values.get("severity")),
                Enforcement.valueOf((String)values.get("enforcement")),checkpoints,capabilities,(Boolean)values.get("approved"),(String)values.get("provenance"),targets);
    }
}
