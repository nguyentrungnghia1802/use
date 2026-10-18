package org.vnu.sme.goal.dsl.csl.ast;

import java.util.Objects;
import java.util.Optional;

public record CslCardinalityCS(String min, Optional<String> max, CslSourceLocationCS location) {
    public CslCardinalityCS {
        Objects.requireNonNull(min, "min");
        max = Objects.requireNonNull(max, "max");
        Objects.requireNonNull(location, "location");
    }
}
