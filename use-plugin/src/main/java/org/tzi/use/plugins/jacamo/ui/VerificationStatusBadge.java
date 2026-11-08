package org.tzi.use.plugins.jacamo.ui;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JLabel;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol.Check;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.VerifiedCheckpointService.View;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ExecutionScopeViews;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut;

/** Read-only presentation of admitted results; never issues a checkpoint decision. */
final class VerificationStatusBadge {
    record Status(String text, Color foreground, Color background) { }
    private static final Color BLUE = new Color(0x1D4ED8), GREEN = new Color(0x166534),
            RED = new Color(0xB91C1C), AMBER = new Color(0x92400E), GRAY = new Color(0x475569);
    private String owner = "", token = "";
    private boolean terminal, issues;

    void reset() { owner = ""; token = ""; terminal = false; issues = false; }

    void displayed(String owner, String token, Cut cut, Map<String,Check> checks) {
        if (!owner.equals(this.owner)) { reset(); this.owner = owner; }
        this.token = token;
        issues |= checks.values().stream().anyMatch(c -> c.applicability().equals("APPLICABLE") && !c.truth().equals("PASS"));
        var inventory = ExecutionScopeViews.object(cut.visualEvidence().get("requirementInventory"));
        var scopedTargets = ExecutionScopeViews.object(inventory.get("scopedTargets"));
        var contexts = ExecutionScopeViews.contexts(cut).stream().filter(ExecutionScopeViews::boundContext).toList();
        terminal = !contexts.isEmpty();
        // Reuse the presenter's qualified native occurrence results, never raw markings
        // or a display row aggregated across agents/executions.
        for (var context : contexts) {
            var targets = org.tzi.use.plugins.jacamo.semantics.runtime.RequirementInventory.targetsForContext(inventory, context);
            var rows = cut.evaluations().stream().filter(r -> r.kind().equals("GOAL_TASK") && r.required()
                    && r.context().equals(context.get("context")))
                    .filter(r -> !(r.status().equals("NOT_ASSIGNED") && r.quality().equals("SOURCE_NOT_ASSIGNED"))).toList();
            boolean contextTerminal = !rows.isEmpty() && targets.stream().allMatch(target -> rows.stream().anyMatch(r -> r.element().equals(target)))
                    && rows.stream().allMatch(r -> Set.of("KNOWN", "KNOWN_DERIVED", "KNOWN_HISTORY", "KNOWN_DERIVED_HISTORY").contains(r.quality())
                    && Set.of("FULFILLED", "FAILED", "VIOLATED").contains(r.status()));
            boolean contextFulfilled = contextTerminal && rows.stream().allMatch(r -> r.status().equals("FULFILLED"));
            var bindings = ExecutionScopeViews.object(ExecutionScopeViews.object(context.get("scopeView")).get("bindings"));
            String scheme = java.util.Objects.toString(bindings.get("scheme"), "");
            String def = java.util.Objects.toString(context.get("scopeDefinition"), "");
            if (contextFulfilled && !scheme.isBlank() && scopedTargets.size() > 1) {
                boolean downstreamPresent = contexts.stream().anyMatch(other -> !java.util.Objects.toString(other.get("scopeDefinition"), "").equals(def)
                        && scheme.equals(ExecutionScopeViews.object(ExecutionScopeViews.object(other.get("scopeView")).get("bindings")).get("scheme")));
                contextTerminal &= downstreamPresent;
            }
            terminal &= contextTerminal;
            issues |= rows.stream().anyMatch(r -> Set.of("FAILED", "VIOLATED").contains(r.status()));
        }
    }

    Status status(View view, String producer, String workflow) {
        if (Set.of("ERROR", "CLEANUP_ERROR").contains(producer) || workflow.equals("ERROR"))
            return badge("Error", RED, 0xFEE2E2);
        if (Set.of("STOPPED", "MONITOR_STOPPED").contains(workflow) || producer.equals("STOPPED"))
            return badge("Stopped", GRAY, 0xF1F5F9);
        if (Set.of("IMPORTING", "BOOTSTRAPPING").contains(workflow) || producer.equals("PREPARING"))
            return badge("Preparing", BLUE, 0xDBEAFE);
        if (view.pending()) return badge("Continuing", BLUE, 0xDBEAFE);
        return switch (view.state()) {
            case "HELD_FAILURE" -> badge("Paused · violation", RED, 0xFEE2E2);
            case "HELD_UNVERIFIED" -> badge("Paused · verification unavailable", AMBER, 0xFEF3C7);
            case "QUIESCING", "VERIFYING" -> badge("Verifying", BLUE, 0xDBEAFE);
            case "RUNNING" -> {
                if (producer.equals("EXITED")) yield badge("Disconnected", AMBER, 0xFEF3C7);
                // STATUS clears the active token after a release. An empty token
                // with RUNNING means there is no held cut; it must not erase the
                // last displayed completion. A different active token is never reused.
                yield terminal && (token.equals(view.token()) || view.token().isBlank())
                        ? badge(issues ? "Completed with issues" : "Completed", issues ? AMBER : GREEN, issues ? 0xFEF3C7 : 0xDCFCE7)
                        : badge("Running", BLUE, 0xDBEAFE);
            }
            case "STOPPED" -> badge("Stopped", GRAY, 0xF1F5F9);
            default -> badge(workflow.equals("NOT_IMPORTED") ? "Not started" : "Ready", GRAY, 0xF1F5F9);
        };
    }

    private static Status badge(String text, Color color, int background) { return new Status(text, color, new Color(background)); }

    static void apply(JLabel label, Status status) {
        label.setText(status.text()); label.setForeground(status.foreground()); label.setBackground(status.background());
        label.setOpaque(true);
        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 9, 5, 9));
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
    }
}
