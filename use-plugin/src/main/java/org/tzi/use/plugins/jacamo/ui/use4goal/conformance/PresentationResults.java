package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

/** Display vocabulary only. Native truth, quality and evidence are never rewritten. */
public final class PresentationResults {
    private PresentationResults() {}
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
