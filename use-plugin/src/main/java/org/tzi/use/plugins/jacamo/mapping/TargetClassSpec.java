package org.tzi.use.plugins.jacamo.mapping;

import java.util.List;

public record TargetClassSpec(String name, boolean abstractClass, List<String> superclasses,
                              String sourceIdentity, String ruleId) {
    public TargetClassSpec { superclasses = List.copyOf(superclasses); }
}
