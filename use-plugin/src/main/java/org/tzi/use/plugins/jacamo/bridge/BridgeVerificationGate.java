package org.tzi.use.plugins.jacamo.bridge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;

/**
 * Admission gate in front of USE/OCL verification.  Evidence-only or unavailable
 * platform observations must never be silently interpreted as faithful USE state.
 */
public final class BridgeVerificationGate {
    public enum Decision { EVALUATE, INCONCLUSIVE, NOT_EVALUATED }

    public record Context(String sessionId, long generation, String modelRevision,
                          String snapshotId, String eventId, String correlationId,
                          String constraintHash, Set<String> negotiatedCapabilities) {
        public Context {
            sessionId = required(sessionId, "sessionId");
            modelRevision = required(modelRevision, "modelRevision");
            snapshotId = required(snapshotId, "snapshotId");
            eventId = eventId == null ? "" : eventId;
            correlationId = correlationId == null ? "" : correlationId;
            constraintHash = required(constraintHash, "constraintHash");
            negotiatedCapabilities = Set.copyOf(negotiatedCapabilities);
            if (generation < 0) throw new IllegalArgumentException("generation");
        }
        private static String required(String value, String field) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(field);
            return value;
        }
    }

    public record Assessment(Decision decision, List<String> diagnostics, Context context) {
        public Assessment {
            decision = Objects.requireNonNull(decision);
            diagnostics = List.copyOf(diagnostics);
            context = Objects.requireNonNull(context);
        }
        public boolean mayEvaluateOcl() { return decision == Decision.EVALUATE; }
    }

    public Assessment assess(BridgeClientState state, Collection<RuntimeFact> facts,
                             Set<RuntimeFactKind> requiredKinds, Context context) {
        Objects.requireNonNull(state); Objects.requireNonNull(facts);
        Objects.requireNonNull(requiredKinds); Objects.requireNonNull(context);
        var diagnostics = new ArrayList<String>();
        if (state != BridgeClientState.LIVE) {
            diagnostics.add("BRIDGE_STATE_NOT_LIVE:" + state);
            return new Assessment(Decision.INCONCLUSIVE, diagnostics, context);
        }
        var seen = EnumSet.noneOf(RuntimeFactKind.class);
        var unavailable = new TreeSet<String>();
        var evidenceOnly = new TreeSet<String>();
        for (RuntimeFact fact : facts) {
            if (!requiredKinds.contains(fact.kind())) continue;
            seen.add(fact.kind());
            if (fact.projectionStatus() == ProjectionStatus.UNAVAILABLE
                    || fact.completeness() == Completeness.UNAVAILABLE) unavailable.add(fact.kind().name());
            else if (fact.projectionStatus() != ProjectionStatus.MATERIALIZED_FAITHFULLY
                    || fact.completeness() != Completeness.COMPLETE) evidenceOnly.add(fact.kind().name());
        }
        var missing = requiredKinds.isEmpty() ? EnumSet.noneOf(RuntimeFactKind.class) : EnumSet.copyOf(requiredKinds);
        missing.removeAll(seen);
        if (!missing.isEmpty()) diagnostics.add("REQUIRED_RUNTIME_FACT_MISSING:" + missing);
        if (!unavailable.isEmpty()) diagnostics.add("RUNTIME_FACT_UNAVAILABLE:" + unavailable);
        if (!missing.isEmpty() || !unavailable.isEmpty())
            return new Assessment(Decision.NOT_EVALUATED, diagnostics, context);
        if (!evidenceOnly.isEmpty()) {
            diagnostics.add("RUNTIME_FACT_EVIDENCE_ONLY_OR_PARTIAL:" + evidenceOnly);
            return new Assessment(Decision.INCONCLUSIVE, diagnostics, context);
        }
        diagnostics.add("RUNTIME_PROJECTION_ADMITTED");
        return new Assessment(Decision.EVALUATE, diagnostics, context);
    }

    public Assessment assessOperationPost(BridgeClientState state, Collection<RuntimeFact> facts,
                                          Set<RuntimeFactKind> requiredKinds, Context context,
                                          boolean preStateCaptured, boolean terminalObserved) {
        Assessment projection = assess(state, facts, requiredKinds, context);
        if (!projection.mayEvaluateOcl()) return projection;
        var diagnostics = new ArrayList<>(projection.diagnostics());
        if (!preStateCaptured) diagnostics.add("OPERATION_PRE_STATE_MISSING");
        if (!terminalObserved) diagnostics.add("OPERATION_TERMINAL_EVENT_MISSING");
        if (!preStateCaptured || !terminalObserved)
            return new Assessment(Decision.NOT_EVALUATED, diagnostics, context);
        diagnostics.add("OPERATION_PRE_POST_ADMITTED");
        return new Assessment(Decision.EVALUATE, diagnostics, context);
    }
}
