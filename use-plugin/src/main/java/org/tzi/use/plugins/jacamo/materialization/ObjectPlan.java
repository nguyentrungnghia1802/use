package org.tzi.use.plugins.jacamo.materialization;

import java.util.Map;
import java.util.TreeMap;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;

public record ObjectPlan(String name, String className, String semanticId,
                         Map<String, AttributeValue> values) {
    public ObjectPlan { values = java.util.Collections.unmodifiableMap(new TreeMap<>(values)); }
}
