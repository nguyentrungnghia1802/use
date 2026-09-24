package org.tzi.use.plugins.jacamo.runtime;

import java.util.List;

public record RuntimeMapping(String schemaVersion, String status, String targetBaseline, List<Rule> rules,
                             java.util.Map<String, String> targetContract) {
    public RuntimeMapping(String schemaVersion, String status, String targetBaseline, List<Rule> rules) {
        this(schemaVersion, status, targetBaseline, rules, java.util.Map.of());
    }
    public RuntimeMapping { rules = List.copyOf(rules); targetContract = java.util.Map.copyOf(targetContract); }
    public record Rule(String id, String runtime, String dimension, RuntimeEventKind eventKind,
            boolean authoritative, String identity, boolean correlationRequired, List<String> payload,
            RuntimeSemanticAction action, String targetKind, String anchor, boolean traceRequired,
            String mutation, String checkpoint, String support, List<String> evidence,
            List<String> assumptions, List<String> unsupportedConditions, String migrationRisk) {
        public Rule {
            payload = List.copyOf(payload); evidence = List.copyOf(evidence);
            assumptions = List.copyOf(assumptions); unsupportedConditions = List.copyOf(unsupportedConditions);
        }
    }
    public Rule select(RuntimeEvent event) {
        return rules.stream().filter(rule -> rule.eventKind() == event.kind())
            .filter(rule -> rule.dimension().equals("ANY") || rule.dimension().equals(event.dimension().name()))
            .findFirst().orElseThrow(() -> new RuntimeMappingException("RUNTIME_MAPPING_RULE_MISSING", event.kind().name(), "selector"));
    }
}
