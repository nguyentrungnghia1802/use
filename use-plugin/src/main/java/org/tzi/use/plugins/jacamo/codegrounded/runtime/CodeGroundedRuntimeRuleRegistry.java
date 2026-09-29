package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFactKind;

/**
 * Native runtime rules are deliberately separate from the historical Runtime Mapping V2
 * registry.  A rule is selected by the typed Bridge fact kind and the explicit normalized
 * mutation kind carried by the authoritative event; names are never used as a selector.
 */
public final class CodeGroundedRuntimeRuleRegistry {
    public enum Action { ATTRIBUTE_SET, ATTRIBUTE_UNSET, LINK_INSERT, LINK_DELETE, EVIDENCE_ONLY }

    public record Rule(String id, RuntimeFactKind factKind, Set<String> normalizedEventKinds,
                       Set<String> targetClasses, Action action, boolean requiresExactBinding) {
        public Rule {
            if (id == null || id.isBlank() || factKind == null || normalizedEventKinds.isEmpty()
                    || targetClasses.isEmpty() || action == null)
                throw new IllegalArgumentException("NATIVE_RUNTIME_RULE_INVALID");
            normalizedEventKinds = Set.copyOf(normalizedEventKinds);
            targetClasses = Set.copyOf(targetClasses);
        }
    }

    private final Map<String, Rule> rules;

    public CodeGroundedRuntimeRuleRegistry() {
        this(List.of(
                rule("R-JASON-AGENT-ATTRIBUTE", RuntimeFactKind.AGENT,
                        Set.of("SET_ATTRIBUTE"), Set.of("Agent"), Action.ATTRIBUTE_SET),
                rule("R-JASON-AGENT-ATTRIBUTE-UNSET", RuntimeFactKind.AGENT,
                        Set.of("UNSET_ATTRIBUTE"), Set.of("Agent"), Action.ATTRIBUTE_UNSET),
                rule("R-CARTAGO-ARTIFACT-ATTRIBUTE", RuntimeFactKind.ARTIFACT,
                        Set.of("SET_ATTRIBUTE"), Set.of("Artifact"), Action.ATTRIBUTE_SET),
                rule("R-CARTAGO-ARTIFACT-ATTRIBUTE-UNSET", RuntimeFactKind.ARTIFACT,
                        Set.of("UNSET_ATTRIBUTE"), Set.of("Artifact"), Action.ATTRIBUTE_UNSET),
                rule("R-CARTAGO-PROPERTY-ATTRIBUTE", RuntimeFactKind.PROPERTY,
                        Set.of("SET_ATTRIBUTE"), Set.of("ObservablePropertySnapshot"), Action.ATTRIBUTE_SET),
                rule("R-CARTAGO-PROPERTY-ATTRIBUTE-UNSET", RuntimeFactKind.PROPERTY,
                        Set.of("UNSET_ATTRIBUTE"), Set.of("ObservablePropertySnapshot"), Action.ATTRIBUTE_UNSET),
                rule("R-NATIVE-RELATION-INSERT", RuntimeFactKind.RELATION_STATE,
                        Set.of("INSERT_LINK"), Set.of("*"), Action.LINK_INSERT),
                rule("R-NATIVE-RELATION-DELETE", RuntimeFactKind.RELATION_STATE,
                        Set.of("DELETE_LINK"), Set.of("*"), Action.LINK_DELETE),
                evidence("R-MOISE-GROUP-BOARD-EVIDENCE", RuntimeFactKind.GROUP_BOARD),
                evidence("R-MOISE-SCHEME-BOARD-EVIDENCE", RuntimeFactKind.SCHEME_BOARD),
                evidence("R-MOISE-ROLE-PLAYER-EVIDENCE", RuntimeFactKind.ROLE_PLAYER),
                evidence("R-MOISE-MISSION-COMMITMENT-EVIDENCE", RuntimeFactKind.MISSION_COMMITMENT),
                evidence("R-MOISE-GOAL-STATE-EVIDENCE", RuntimeFactKind.ORGANISATIONAL_GOAL_STATE),
                evidence("R-NPL-NORM-EVIDENCE", RuntimeFactKind.NORM_INSTANCE),
                evidence("R-NATIVE-UNPROJECTED-EVIDENCE", RuntimeFactKind.PLAN),
                evidence("R-NATIVE-UNPROJECTED-EVIDENCE-BELIEF", RuntimeFactKind.BELIEF),
                evidence("R-NATIVE-UNPROJECTED-EVIDENCE-GOAL", RuntimeFactKind.GOAL),
                evidence("R-NATIVE-UNPROJECTED-EVIDENCE-WORKSPACE", RuntimeFactKind.WORKSPACE),
                evidence("R-NATIVE-UNPROJECTED-EVIDENCE-OPERATION", RuntimeFactKind.OPERATION)));
    }

    public CodeGroundedRuntimeRuleRegistry(List<Rule> rules) {
        Map<String, Rule> index = new LinkedHashMap<>();
        for (Rule rule : rules)
            if (index.put(rule.id(), rule) != null)
                throw new IllegalArgumentException("NATIVE_RUNTIME_RULE_DUPLICATE: " + rule.id());
        this.rules = Map.copyOf(index);
    }

    public Map<String, Rule> rules() { return rules; }

    public Rule select(RuntimeFactKind factKind, String normalizedEventKind) {
        if (factKind == null || normalizedEventKind == null || normalizedEventKind.isBlank()) return null;
        return rules.values().stream()
                .filter(rule -> rule.factKind() == factKind)
                .filter(rule -> rule.normalizedEventKinds().contains(normalizedEventKind))
                .findFirst().orElse(null);
    }

    public Rule evidenceRule(RuntimeFactKind factKind) {
        return rules.values().stream().filter(rule -> rule.factKind() == factKind
                && rule.action() == Action.EVIDENCE_ONLY).findFirst().orElse(null);
    }

    private static Rule rule(String id, RuntimeFactKind factKind, Set<String> eventKinds,
                             Set<String> targetClasses, Action action) {
        return new Rule(id, factKind, eventKinds, targetClasses, action, true);
    }

    private static Rule evidence(String id, RuntimeFactKind factKind) {
        return new Rule(id, factKind, Set.of("*"), Set.of("*"),
                Action.EVIDENCE_ONLY, false);
    }
}
