package org.tzi.use.plugins.jacamo.ui.use4goal.csl;

import java.util.Objects;

public record CslEdge(String fromId, String toId, CslEdgeKind kind,
                      String sourceCardinality, String targetCardinality, String label,
                      boolean bidirectional, boolean interGroup, int routeIndex, int routeCount) {
    public CslEdge {
        Objects.requireNonNull(fromId, "fromId");
        Objects.requireNonNull(toId, "toId");
        Objects.requireNonNull(kind, "kind");
        if (routeIndex < 0 || routeCount < 1 || routeIndex >= routeCount) {
            throw new IllegalArgumentException("Invalid CSL edge route");
        }
    }

    public static CslEdge of(String from, String to, CslEdgeKind kind,
                             String sourceCardinality, String targetCardinality,
                             String label, boolean bidirectional, boolean interGroup) {
        return new CslEdge(from, to, kind, sourceCardinality, targetCardinality,
                label, bidirectional, interGroup, 0, 1);
    }

    public CslEdge withRoute(int index, int count) {
        return new CslEdge(fromId, toId, kind, sourceCardinality, targetCardinality,
                label, bidirectional, interGroup, index, count);
    }
}
