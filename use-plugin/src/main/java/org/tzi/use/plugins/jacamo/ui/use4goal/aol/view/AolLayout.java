package org.tzi.use.plugins.jacamo.ui.use4goal.aol.view;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AolLayout {
    public final Map<String, AolNode> nodes;
    public final List<AolEdge> edges;
    public final int width;
    public final int height;

    public AolLayout(Map<String, AolNode> nodes, List<AolEdge> edges, int width, int height) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.edges = List.copyOf(edges);
        this.width = width;
        this.height = height;
    }
}
