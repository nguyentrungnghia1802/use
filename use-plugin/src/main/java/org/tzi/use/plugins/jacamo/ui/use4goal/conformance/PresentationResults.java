package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

/** Display vocabulary only. Native truth, quality and evidence are never rewritten. */
public final class PresentationResults {
    private PresentationResults() {}
    /** Inventory summaries and source failure certificates are audit diagnostics,
     * not additional OCL predicates. Keep all originals on the immutable Cut. */
    public static java.util.List<ObservedConformancePanel.Evaluation> checks(ObservedConformancePanel.Cut cut) {
        return cut.evaluations().stream().filter(row->!java.util.Set.of("GOAL_TASK","CHECKPOINT_CHECK","EXECUTION_OUTCOME").contains(row.kind()))
                .filter(row->!row.kind().equals("INVARIANT_BODY")||cut.evaluations().stream().noneMatch(aggregate->aggregate.kind().equals("INVARIANT")&&aggregate.element().equals(row.element()))).toList();
    }
    public static String goal(String status, boolean failedHistory) {
        if (failedHistory) return "Incomplete";
        return switch (status) {
            case "FULFILLED" -> "Complete";
            case "PENDING", "FAILED", "VIOLATED" -> "Incomplete";
            default -> "Unknown";
        };
    }
    public static String truth(String status) {
        return switch (status) {
            case "PASS", "CONDITION_TRUE" -> "True";
            case "FAIL", "VIOLATED", "CONDITION_FALSE", "EXECUTION_FAILED" -> "False";
            case "ERROR" -> "Error";
            default -> "Unknown";
        };
    }
    public static String body(ObservedConformancePanel.Evaluation row) {
        String evidence = row.evidence();
        int start = evidence.indexOf("expression=");
        if (start < 0) return evidence;
        start += "expression=".length();
        int end = evidence.indexOf(" / witness=", start);
        return evidence.substring(start, end < 0 ? evidence.length() : end);
    }
}
