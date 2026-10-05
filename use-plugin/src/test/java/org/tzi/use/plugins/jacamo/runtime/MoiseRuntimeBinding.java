package org.tzi.use.plugins.jacamo.runtime;

import java.util.Map;

/** Exact runtime-to-static-trace bindings for one Moise organisational entity. */
public record MoiseRuntimeBinding(String organisation, String organisationSemanticId,
                                  Map<String, String> agents, Map<String, String> groups,
                                  Map<String, String> schemes) {
    public MoiseRuntimeBinding {
        if (organisation == null || organisation.isBlank()
                || organisationSemanticId == null || organisationSemanticId.isBlank()
                || agents == null || groups == null || schemes == null)
            throw new IllegalArgumentException("MOISE_BINDING_INVALID");
        agents = checked(agents);
        groups = checked(groups);
        schemes = checked(schemes);
    }

    public String organisationRuntimeId() { return "moise:organisation:" + organisation; }
    public String agentRuntimeId(String agent) { return "moise:agent:" + organisation + "/" + agent; }
    public String groupRuntimeId(String group) { return "moise:group:" + organisation + "/" + group; }
    public String schemeRuntimeId(String scheme) { return "moise:scheme:" + organisation + "/" + scheme; }

    private static Map<String, String> checked(Map<String, String> values) {
        if (values.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null || entry.getValue().isBlank()))
            throw new IllegalArgumentException("MOISE_BINDING_ENTRY_INVALID");
        return Map.copyOf(values);
    }
}
