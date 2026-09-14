package org.tzi.use.plugins.jacamo.mapping;

import java.util.List;

public record TargetOperationSpec(String owner, String name, List<Parameter> parameters, String returnType,
                                  String sourceIdentity, String ruleId) {
    public TargetOperationSpec { parameters = List.copyOf(parameters); }
    public record Parameter(String name, String type) { }
}
