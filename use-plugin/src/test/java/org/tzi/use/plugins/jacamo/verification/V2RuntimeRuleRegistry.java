package org.tzi.use.plugins.jacamo.verification;

import java.util.Objects;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEvent;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMapping;
import org.tzi.use.plugins.jacamo.runtime.RuntimeMappingLoader;

/** Explicit compatibility adapter for the historical Runtime Mapping V2 document. */
public final class V2RuntimeRuleRegistry implements RuntimeRuleRegistry {
    private final RuntimeMapping mapping;

    public V2RuntimeRuleRegistry(RuntimeMapping mapping) {
        this.mapping = Objects.requireNonNull(mapping, "mapping");
    }

    public static V2RuntimeRuleRegistry loadDefault() {
        return new V2RuntimeRuleRegistry(new RuntimeMappingLoader().loadDefault());
    }

    @Override public Rule select(RuntimeEvent event) {
        RuntimeMapping.Rule selected = mapping.select(event);
        return new Rule(selected.id(), selected.checkpoint());
    }

    public RuntimeMapping mapping() { return mapping; }
}
