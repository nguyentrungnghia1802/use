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
    public enum Action {
        ATTRIBUTE_SET, ATTRIBUTE_UNSET, LINK_INSERT, LINK_DELETE,
        UPSERT_CARTAGO_WORKSPACE, UPSERT_CARTAGO_AGENT_IDENTITY,
        UPSERT_CARTAGO_ARTIFACT, UPSERT_CARTAGO_PROPERTY_SNAPSHOT,
        SET_CARTAGO_FOCUS, DELETE_CARTAGO_AGENT_IDENTITY,
        APPLY_CARTAGO_PROPERTY_DELTA, DELETE_CARTAGO_ARTIFACT,
        UPSERT_MOISE_GROUP, INSERT_MOISE_ROLE_LINK, DELETE_MOISE_ROLE_LINK,
        UPSERT_MOISE_SCHEME, UPSERT_JASON_AGENT_STATE, UPSERT_CARTAGO_OPERATION, UPSERT_MOISE_GROUP_PARENT,
        EVIDENCE_ONLY
    }

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
                        Set.of("SET_ATTRIBUTE"), Set.of("agent-program"), Action.ATTRIBUTE_SET),
                rule("R-JASON-AGENT-ATTRIBUTE-UNSET", RuntimeFactKind.AGENT,
                        Set.of("UNSET_ATTRIBUTE"), Set.of("agent-program"), Action.ATTRIBUTE_UNSET),
                rule("R-CARTAGO-ARTIFACT-ATTRIBUTE", RuntimeFactKind.ARTIFACT,
                        Set.of("SET_ATTRIBUTE"), Set.of("artifact"), Action.ATTRIBUTE_SET),
                rule("R-CARTAGO-ARTIFACT-ATTRIBUTE-UNSET", RuntimeFactKind.ARTIFACT,
                        Set.of("UNSET_ATTRIBUTE"), Set.of("artifact"), Action.ATTRIBUTE_UNSET),
                rule("R-CARTAGO-PROPERTY-ATTRIBUTE", RuntimeFactKind.PROPERTY,
                        Set.of("SET_ATTRIBUTE"), Set.of("artifact"), Action.ATTRIBUTE_SET),
                rule("R-CARTAGO-PROPERTY-ATTRIBUTE-UNSET", RuntimeFactKind.PROPERTY,
                        Set.of("UNSET_ATTRIBUTE"), Set.of("artifact"), Action.ATTRIBUTE_UNSET),
                upsert("R-CARTAGO-WORKSPACE-UPSERT", RuntimeFactKind.WORKSPACE,
                        "UPSERT_CARTAGO_WORKSPACE", "Workspace", Action.UPSERT_CARTAGO_WORKSPACE),
                upsert("R-CARTAGO-AGENT-IDENTITY-UPSERT", RuntimeFactKind.AGENT,
                        "UPSERT_CARTAGO_AGENT_IDENTITY", "agent-program",
                        Action.UPSERT_CARTAGO_AGENT_IDENTITY),
                upsert("R-CARTAGO-AGENT-IDENTITY-DELETE",RuntimeFactKind.AGENT,
                        "DELETE_CARTAGO_AGENT_IDENTITY","agent-program",Action.DELETE_CARTAGO_AGENT_IDENTITY),
                upsert("R-CARTAGO-FOCUS",RuntimeFactKind.RELATION_STATE,
                        "SET_CARTAGO_FOCUS","artifact",Action.SET_CARTAGO_FOCUS),
                upsert("R-CARTAGO-ARTIFACT-UPSERT", RuntimeFactKind.ARTIFACT,
                        "UPSERT_CARTAGO_ARTIFACT", "artifact", Action.UPSERT_CARTAGO_ARTIFACT),
                upsert("R-CARTAGO-PROPERTY-SNAPSHOT-UPSERT", RuntimeFactKind.PROPERTY,
                        "UPSERT_CARTAGO_PROPERTY_SNAPSHOT", "artifact",
                        Action.UPSERT_CARTAGO_PROPERTY_SNAPSHOT),
                upsert("R-CARTAGO-PROPERTY-DELTA", RuntimeFactKind.ARTIFACT,
                        "APPLY_CARTAGO_PROPERTY_DELTA", "artifact", Action.APPLY_CARTAGO_PROPERTY_DELTA),
                upsert("R-CARTAGO-ARTIFACT-DELETE", RuntimeFactKind.ARTIFACT,
                        "DELETE_CARTAGO_ARTIFACT", "artifact", Action.DELETE_CARTAGO_ARTIFACT),
                rule("R-NATIVE-RELATION-INSERT", RuntimeFactKind.RELATION_STATE,
                        Set.of("INSERT_LINK"), Set.of("*"), Action.LINK_INSERT),
                rule("R-NATIVE-RELATION-DELETE", RuntimeFactKind.RELATION_STATE,
                        Set.of("DELETE_LINK"), Set.of("*"), Action.LINK_DELETE),
                upsert("R-MOISE-GROUP-UPSERT",RuntimeFactKind.GROUP_BOARD,"UPSERT_MOISE_GROUP","group",Action.UPSERT_MOISE_GROUP),
                upsert("R-MOISE-SCHEME-UPSERT",RuntimeFactKind.SCHEME_BOARD,"UPSERT_MOISE_SCHEME","scheme",Action.UPSERT_MOISE_SCHEME),
                upsert("R-MOISE-GROUP-PARENT-UPSERT",RuntimeFactKind.RELATION_STATE,"UPSERT_MOISE_GROUP_PARENT","group",Action.UPSERT_MOISE_GROUP_PARENT),
                upsert("R-JASON-AGENT-STATE-UPSERT",RuntimeFactKind.AGENT,"UPSERT_JASON_AGENT_STATE","agent-program",Action.UPSERT_JASON_AGENT_STATE),
                upsert("R-CARTAGO-OPERATION-UPSERT",RuntimeFactKind.OPERATION,"UPSERT_CARTAGO_OPERATION","artifact",Action.UPSERT_CARTAGO_OPERATION),
                upsert("R-MOISE-ROLE-LINK-INSERT",RuntimeFactKind.ROLE_PLAYER,"INSERT_MOISE_ROLE_LINK","group",Action.INSERT_MOISE_ROLE_LINK),
                upsert("R-MOISE-ROLE-LINK-DELETE",RuntimeFactKind.ROLE_PLAYER,"DELETE_MOISE_ROLE_LINK","group",Action.DELETE_MOISE_ROLE_LINK),
                evidence("R-MOISE-GROUP-BOARD-EVIDENCE", RuntimeFactKind.GROUP_BOARD),
                evidence("R-MOISE-SCHEME-BOARD-EVIDENCE", RuntimeFactKind.SCHEME_BOARD),
                evidence("R-MOISE-ROLE-PLAYER-EVIDENCE", RuntimeFactKind.ROLE_PLAYER),
                evidence("R-MOISE-MISSION-COMMITMENT-EVIDENCE", RuntimeFactKind.MISSION_COMMITMENT),
                evidence("R-MOISE-GOAL-STATE-EVIDENCE", RuntimeFactKind.ORGANISATIONAL_GOAL_STATE),
                evidence("R-NPL-NORM-EVIDENCE", RuntimeFactKind.NORM_INSTANCE),
                evidence("R-JASON-A12-ACTION-EXECUTION-EVIDENCE", RuntimeFactKind.ACTION_EXECUTION),
                evidence("R-JASON-A13-INTENTION-EVIDENCE", RuntimeFactKind.INTENTION),
                evidence("R-JASON-A14-RUNTIME-EVENT-EVIDENCE", RuntimeFactKind.RUNTIME_EVENT),
                evidence("R-JASON-A15-TRANSITION-SYSTEM-EVIDENCE", RuntimeFactKind.TRANSITION_SYSTEM),
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

    private static Rule upsert(String id, RuntimeFactKind factKind, String eventKind,
                               String targetClass, Action action) {
        return new Rule(id, factKind, Set.of(eventKind), Set.of(targetClass), action, false);
    }
}
