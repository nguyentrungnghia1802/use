package org.tzi.use.plugins.jacamo.ui.use4goal.csl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CslLayout {
    public final Map<String, CslNode> nodes;
    public final List<CslEdge> edges;
    public final int width;
    public final int height;

    public CslLayout(Map<String, CslNode> nodes, List<CslEdge> edges, int width, int height) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.edges = List.copyOf(edges);
        this.width = width;
        this.height = height;
    }
}
