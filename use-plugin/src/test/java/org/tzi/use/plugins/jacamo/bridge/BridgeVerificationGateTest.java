package org.tzi.use.plugins.jacamo.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.junit.jupiter.api.Test;

class BridgeVerificationGateTest {
    private final BridgeVerificationGate gate = new BridgeVerificationGate();
    private final BridgeVerificationGate.Context context = new BridgeVerificationGate.Context(
            "session", 4, "revision", "snapshot", "event", "correlation", "constraint-sha256", Set.of("runtime"));

    @Test void admitsOnlyCompleteFaithfulRequiredFacts() {
        var result = gate.assess(BridgeClientState.LIVE,
                List.of(fact(RuntimeFactKind.AGENT, ProjectionStatus.MATERIALIZED_FAITHFULLY, Completeness.COMPLETE)),
                Set.of(RuntimeFactKind.AGENT), context);
        assertEquals(BridgeVerificationGate.Decision.EVALUATE, result.decision());
        assertTrue(result.mayEvaluateOcl());
        assertSame(context, result.context());
    }

    @Test void evidenceOnlyIsInconclusiveAndUnavailableOrMissingIsNotEvaluated() {
        assertEquals(BridgeVerificationGate.Decision.INCONCLUSIVE,
                gate.assess(BridgeClientState.LIVE,
                        List.of(fact(RuntimeFactKind.BELIEF, ProjectionStatus.EVIDENCE_ONLY, Completeness.PARTIAL)),
                        Set.of(RuntimeFactKind.BELIEF), context).decision());
        assertEquals(BridgeVerificationGate.Decision.NOT_EVALUATED,
                gate.assess(BridgeClientState.LIVE,
                        List.of(fact(RuntimeFactKind.GOAL, ProjectionStatus.UNAVAILABLE, Completeness.UNAVAILABLE)),
                        Set.of(RuntimeFactKind.GOAL), context).decision());
        assertEquals(BridgeVerificationGate.Decision.NOT_EVALUATED,
                gate.assess(BridgeClientState.LIVE, List.of(), Set.of(RuntimeFactKind.ARTIFACT), context).decision());
    }

    @Test void staleMirrorAndIncompleteOperationCorrelationCannotReachOcl() {
        var facts = List.of(fact(RuntimeFactKind.OPERATION, ProjectionStatus.MATERIALIZED_FAITHFULLY, Completeness.COMPLETE));
        assertEquals(BridgeVerificationGate.Decision.INCONCLUSIVE,
                gate.assess(BridgeClientState.RESYNC_REQUIRED, facts, Set.of(RuntimeFactKind.OPERATION), context).decision());
        var post = gate.assessOperationPost(BridgeClientState.LIVE, facts, Set.of(RuntimeFactKind.OPERATION), context, true, false);
        assertEquals(BridgeVerificationGate.Decision.NOT_EVALUATED, post.decision());
        assertTrue(post.diagnostics().contains("OPERATION_TERMINAL_EVENT_MISSING"));
    }

    private RuntimeFact fact(RuntimeFactKind kind, ProjectionStatus projection, Completeness completeness) {
        return new RuntimeFact(new BridgeEntityId("test", "runtime", kind.name(), "owner", kind.name().toLowerCase(), "inc-1"),
                kind, Map.of(), List.of(), projection, completeness, List.of());
    }
}
