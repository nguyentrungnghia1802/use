package org.tzi.use.plugins.jacamo.verification;

import java.util.Objects;
import org.tzi.use.plugins.jacamo.runtime.RuntimeEvent;

/**
 * Explicit runtime-rule boundary for verification. The verifier does not know which
 * catalog or compatibility document selected a rule; callers inject that authority.
 */
@FunctionalInterface
public interface RuntimeRuleRegistry {
    Rule select(RuntimeEvent event);

    record Rule(String id, String checkpoint) {
        public Rule {
            if (id == null || id.isBlank() || checkpoint == null || checkpoint.isBlank())
                throw new IllegalArgumentException("RUNTIME_RULE_INVALID");
        }
    }

    static RuntimeRuleRegistry require(RuntimeRuleRegistry registry) {
        return Objects.requireNonNull(registry, "runtimeRules");
    }
}
