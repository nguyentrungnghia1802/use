package org.vnu.sme.goal.dsl.csl.ast;

import java.util.List;
import java.util.Objects;

public record CslCompatibilityCS(String fromRole, String toRole, boolean bidirectionalArrow,
                                 String groupName, List<CslLinkOptionCS> options, CslSourceLocationCS location) {
    public CslCompatibilityCS {
        Objects.requireNonNull(fromRole, "fromRole");
        Objects.requireNonNull(toRole, "toRole");
        options = List.copyOf(options);
        Objects.requireNonNull(location, "location");
    }
}
