package org.tzi.use.plugins.jacamo.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeHistoryPage;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

/** View filtering only: immutable journal evidence is never rewritten or re-evaluated. */
public final class RuntimeHistoryRows {
    private RuntimeHistoryRows() { }
    public record Row(RuntimeHistoryPage.Entry entry, ExternalOclConstraintService.Outcome outcome) {
        public String event() { return "#" + entry.ordinal() + " " + (entry.evidenceOnly() ? "OBSERVED/EVIDENCE_ONLY " : "")
                + entry.kind() + " " + entry.result().eventId(); }
        public String detail() {
            var result=entry.result();
            return "Origin: LIVE_OBSERVATION (supported projection only)\nRecord ordinal: " + entry.ordinal()
                    + " | persisted=" + entry.persisted() + "\nInterval: " + entry.intervalId()
                    + "\nSession/generation: " + result.sessionId() + " / " + result.generation()
                    + "\nModel revision: " + result.modelRevision() + "\nConstraint set: " + result.constraintSetHash()
                    + "\nSource/sequence: " + result.sourceId() + " / " + result.sourceSequence()
                    + "\nCheckpoint: " + result.checkpointId() + "\nCoverage/freshness: " + result.coverage() + " / " + result.freshness()
                    + "\nObserved/applied/verified: " + result.observedAt() + " / " + result.appliedAt() + " / " + result.verifiedAt()
                    + "\nDiagnostic: " + result.diagnostic() + " / " + outcome.diagnostic() + "\nOCL: " + outcome.expression();
        }
    }
    public static List<Row> expand(RuntimeHistoryPage page, boolean changesOnly, boolean showEvidenceOnly) {
        var rows=new ArrayList<Row>(); Map<String,VerificationOutcome> previous=new HashMap<>();
        Map<String,String> coverage=new HashMap<>();
        for (var entry:page.entries()) {
            boolean marker=entry.kind().equals("GAP") || entry.kind().equals("COVERAGE") || entry.kind().equals("REJECTED")
                    || entry.result().freshness().equals("STALE");
            var evidence=entry.result();
            String scope=evidence.sessionId()+"/"+evidence.generation()+"/"+evidence.modelRevision()+"/"+entry.intervalId()+"/"+evidence.constraintSetHash();
            String oldCoverage=coverage.put(scope,evidence.coverage());
            if (oldCoverage != null && !oldCoverage.equals(evidence.coverage())) marker=true;
            if (entry.evidenceOnly() && !showEvidenceOnly && !marker) continue;
            var outcomes=entry.result().outcomes().isEmpty() ? List.of(new ExternalOclConstraintService.Outcome(
                    "(no evaluated invariant)","",VerificationOutcome.SKIPPED,entry.result().diagnostic(),"")) : entry.result().outcomes();
            for (var outcome:outcomes) {
                var result=entry.result();
                String key=result.sessionId()+"/"+result.generation()+"/"+result.modelRevision()+"/"+entry.intervalId()
                        +"/"+result.constraintSetHash()+"/"+outcome.constraintId();
                var old=entry.evidenceOnly()?null:previous.put(key,outcome.outcome());
                if (!changesOnly || old != outcome.outcome() || marker) rows.add(new Row(entry,outcome));
            }
        }
        return List.copyOf(rows);
    }
}
