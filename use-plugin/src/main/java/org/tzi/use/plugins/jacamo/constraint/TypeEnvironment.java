package org.tzi.use.plugins.jacamo.constraint;

import java.util.Map;

public record TypeEnvironment(Map<String, Expression.ValueType> variables,
                              Map<String, PropertyBinding> properties) {
    public TypeEnvironment {
        variables = Map.copyOf(variables);
        properties = Map.copyOf(properties);
    }
    public record PropertyBinding(String oclNavigation, Expression.ValueType type) { }
}
