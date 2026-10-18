package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;

/** A named OCL state predicate evaluated over every CSL object in its context. */
public record CslInvariant(String contextType, String name, String expression) {
    public CslInvariant {
        Objects.requireNonNull(contextType, "contextType");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(expression, "expression");
    }

    public String qualifiedName() {
        return contextType + "::" + name;
    }
}
