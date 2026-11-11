package org.vnu.sme.goal.dsl.csl.ast;

import java.util.List;
import java.util.Objects;

public record CslRelationCS(String kind, String name, List<CslEndpointCS> endpoints,
                            CslSourceLocationCS location) {
    public CslRelationCS {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(name, "name");
        endpoints = List.copyOf(endpoints);
        Objects.requireNonNull(location, "location");
    }
}
