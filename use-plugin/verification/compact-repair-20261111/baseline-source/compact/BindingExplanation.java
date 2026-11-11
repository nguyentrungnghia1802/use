package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.Objects;

/**
 * Transparent explanation for an observation binding:
 * source -> target -> framework rule -> evidence requirement.
 */
public record BindingExplanation(
        String source,
        String target,
        String frameworkRule,
        String evidenceRequirement) {

    public BindingExplanation {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(frameworkRule, "frameworkRule");
        Objects.requireNonNull(evidenceRequirement, "evidenceRequirement");
    }

    @Override
    public String toString() {
        return source + " -> " + target + " [" + frameworkRule + "] (requires: " + evidenceRequirement + ")";
    }
}

