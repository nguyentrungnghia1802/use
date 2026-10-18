package org.vnu.sme.goal.dsl.csl.mm;

import java.util.Arrays;
import java.util.Optional;

/** The only Role/Group -> Entity relation kind: ownership (aggregation). */
public enum CslRoleEntityRelationType {
    OWNS("owns");

    private final String sourceName;
    CslRoleEntityRelationType(String sourceName) { this.sourceName = sourceName; }
    public String sourceName() { return sourceName; }
    public static Optional<CslRoleEntityRelationType> fromSource(String source) {
        return Arrays.stream(values()).filter(v -> v.sourceName.equals(source)).findFirst();
    }
}
