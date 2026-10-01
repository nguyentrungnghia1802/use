package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

/** Immutable observed-state evidence. No USE model, object or mutable state reference is retained. */
public record RuntimeVerificationResult(String sessionId, long generation, String modelRevision,
        long stateVersion, String eventId, String sourceId, long sourceSequence, Instant observedAt,
        Instant appliedAt, Instant verifiedAt, String checkpointId, String constraintSetHash,
        String stateHash, List<ExternalOclConstraintService.Outcome> outcomes, long durationNanos,
        String coverage, String freshness, String diagnostic) {
    public RuntimeVerificationResult { outcomes = List.copyOf(outcomes); }
    public long count(VerificationOutcome outcome) { return outcomes.stream().filter(item -> item.outcome() == outcome).count(); }
    public List<String> failingConstraints() { return outcomes.stream().filter(item -> item.outcome() == VerificationOutcome.FAIL)
            .map(ExternalOclConstraintService.Outcome::constraintId).toList(); }
    public Map<String, Object> semanticEvidence() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sessionId", sessionId); map.put("generation", generation); map.put("modelRevision", modelRevision);
        map.put("stateVersion", stateVersion); map.put("eventId", eventId); map.put("sourceId", sourceId);
        map.put("sourceSequence", sourceSequence); map.put("checkpointId", checkpointId);
        map.put("constraintSetHash", constraintSetHash); map.put("stateHash", stateHash);
        map.put("coverage", coverage); map.put("freshness", freshness); map.put("diagnostic", diagnostic);
        map.put("outcomes", outcomes.stream().map(item -> Map.of("constraintId", item.constraintId(),
                "contextClass", item.contextClass(), "outcome", item.outcome().name(), "diagnostic", item.diagnostic(),
                "expression", item.expression())).toList());
        return Map.copyOf(map);
    }
    public String resultHash() { return ExternalOclConstraintService.sha256(CanonicalJson.encode(semanticEvidence())); }
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>(semanticEvidence());
        map.put("observedAt", observedAt.toString()); map.put("appliedAt", appliedAt.toString());
        map.put("verifiedAt", verifiedAt.toString()); map.put("durationNanos", durationNanos);
        map.put("resultHash", resultHash()); return Map.copyOf(map);
    }
    public static RuntimeVerificationResult fromMap(Map<String,Object> value) {
        List<ExternalOclConstraintService.Outcome> outcomes = ((List<?>)value.get("outcomes")).stream().map(raw -> {
            var item = CanonicalJson.object(raw);
            return new ExternalOclConstraintService.Outcome((String)item.get("constraintId"), (String)item.get("contextClass"),
                    VerificationOutcome.valueOf((String)item.get("outcome")), (String)item.get("diagnostic"), (String)item.get("expression"));
        }).toList();
        var result = new RuntimeVerificationResult((String)value.get("sessionId"), ((Number)value.get("generation")).longValue(),
                (String)value.get("modelRevision"), ((Number)value.get("stateVersion")).longValue(), (String)value.get("eventId"),
                (String)value.get("sourceId"), ((Number)value.get("sourceSequence")).longValue(), Instant.parse((String)value.get("observedAt")),
                Instant.parse((String)value.get("appliedAt")), Instant.parse((String)value.get("verifiedAt")), (String)value.get("checkpointId"),
                (String)value.get("constraintSetHash"), (String)value.get("stateHash"), outcomes, ((Number)value.get("durationNanos")).longValue(),
                (String)value.get("coverage"), (String)value.get("freshness"), (String)value.get("diagnostic"));
        if (!result.resultHash().equals(value.get("resultHash"))) throw new IllegalArgumentException("HISTORY_RESULT_HASH_MISMATCH");
        return result;
    }
}
