package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Objects;
import java.util.List;

/** Entity relationship: association, aggregation, or composition. */
public record CslRelation(RelationKind kind, String name, List<CslEndpoint> endpoints) {
    public CslRelation {
        Objects.requireNonNull(kind, "kind"); Objects.requireNonNull(name, "name");
        endpoints = List.copyOf(endpoints);
        if (endpoints.size() < 2) throw new IllegalArgumentException("An association needs at least two ends");
    }
    public CslRelation(RelationKind kind, String name, CslEndpoint source, CslEndpoint target) {
        this(kind, name, List.of(source, target));
    }
    /** Legacy binary consumers must not silently truncate an n-ary association. */
    public CslEndpoint source() { requireBinary(); return endpoints.get(0); }
    public CslEndpoint target() { requireBinary(); return endpoints.get(1); }
    private void requireBinary() {
        if (endpoints.size() != 2)
            throw new UnsupportedOperationException("Association '" + name
                    + "' has " + endpoints.size() + " ends; this backend only supports binary associations");
    }
}
