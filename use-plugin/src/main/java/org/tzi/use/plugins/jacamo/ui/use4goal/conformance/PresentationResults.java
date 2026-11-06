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
    private static final java.util.Set<String> QUALIFIED_QUALITIES = java.util.Set.of("KNOWN", "KNOWN_DERIVED", "KNOWN_HISTORY", "KNOWN_DERIVED_HISTORY");
    public static String goal(String status, boolean failedHistory) {
        if (failedHistory) return "Incomplete";
        return switch (status) {
            case "FULFILLED" -> "Complete";
            case "PENDING", "FAILED", "VIOLATED" -> "Incomplete";
            default -> "Unknown";
        };
    }
    public static String agentResult(String status, String quality) {
        if ("NOT_ASSIGNED".equals(status) && "SOURCE_NOT_ASSIGNED".equals(quality)) return "Not assigned";
        if (status == null || quality == null || !QUALIFIED_QUALITIES.contains(quality)) return "Unknown";
        return switch (status) {
            case "FULFILLED" -> "Complete";
            case "PENDING" -> "Incomplete";
            case "FAILED" -> "Failed";
            case "VIOLATED" -> "Violated";
            default -> "Unknown";
        };
    }
    public static String agentResult(ObservedConformancePanel.Evaluation instance) {
        return instance == null ? "Unknown" : agentResult(instance.status(), instance.quality());
    }
    public static String agentAggregateResult(java.util.List<ObservedConformancePanel.Evaluation> instances) {
        if (instances == null || instances.isEmpty()) return "Unknown";
        if (instances.size() == 1) return agentResult(instances.getFirst());
        var assigned = instances.stream().filter(r -> !("NOT_ASSIGNED".equals(r.status()) && "SOURCE_NOT_ASSIGNED".equals(r.quality()))).toList();
        if (assigned.isEmpty()) return "Not assigned";
        if (assigned.stream().anyMatch(r -> "VIOLATED".equals(r.status()) && QUALIFIED_QUALITIES.contains(r.quality()))) return "Violated";
        if (assigned.stream().anyMatch(r -> "FAILED".equals(r.status()) && QUALIFIED_QUALITIES.contains(r.quality()))) return "Failed";
        if (assigned.stream().anyMatch(r -> !QUALIFIED_QUALITIES.contains(r.quality()) || !java.util.Set.of("FULFILLED", "PENDING").contains(r.status()))) return "Unknown";
        return assigned.stream().allMatch(r -> "FULFILLED".equals(r.status())) ? "Complete" : "Incomplete";
    }
    public static String subjectLabel(String playerIdentity) {
        if (playerIdentity == null || playerIdentity.isBlank() || "UNAVAILABLE".equalsIgnoreCase(playerIdentity)) return "Unavailable";
        try {
            var id = org.jacamo.bridge.contract.BridgeEntityId.parse(playerIdentity);
            if ("jason".equals(id.authority()) && "agent".equals(id.dimension())) return id.localId();
            if ("moise".equals(id.authority()) && "scheme".equals(id.dimension())) return "Scheme: " + id.localId();
            if ("moise".equals(id.authority()) && "group".equals(id.dimension())) return "Group: " + id.localId();
            if ("cartago".equals(id.authority()) && "artifact".equals(id.dimension())) return "Artifact: " + id.localId();
            String dim = id.dimension();
            return (dim.isEmpty() ? "Entity" : Character.toUpperCase(dim.charAt(0)) + dim.substring(1)) + ": " + id.localId();
        } catch (org.jacamo.bridge.contract.ContractException invalid) {
            if ("organization".equalsIgnoreCase(playerIdentity)) return "Organization";
            return playerIdentity;
        }
    }
    public static String shortId(String id) {
        if (id == null || id.isBlank() || "UNAVAILABLE".equalsIgnoreCase(id)) return "—";
        if (id.startsWith("beid:v1:")) {
            try {
                var parsed = org.jacamo.bridge.contract.BridgeEntityId.parse(id);
                String inc = parsed.incarnation();
                if (!inc.isBlank() && !inc.equals(parsed.localId())) {
                    return parsed.localId() + "#" + (inc.length() > 8 ? inc.substring(0, 8) : inc);
                }
                return parsed.localId();
            } catch (org.jacamo.bridge.contract.ContractException ignored) {}
        }
        int lastUnderscore = id.lastIndexOf('_');
        if (lastUnderscore > 0 && id.length() - lastUnderscore - 1 >= 16) {
            String tail = id.substring(lastUnderscore + 1);
            if (tail.matches("[0-9a-fA-F]+")) return id.substring(0, lastUnderscore) + "#" + tail.substring(0, 8);
        }
        if (id.length() > 24) return id.substring(0, 12) + "…" + id.substring(id.length() - 6);
        return id;
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
