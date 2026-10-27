package org.tzi.use.plugins.jacamo.ui.use4goal.istar;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Layout result: nodes + edges with computed coordinates. Pure data, no
 * Swing/AWT — a renderer other than {@link IStarView} (e.g. SVG export)
 * could consume this without depending on the View at all.
 */
public final class IStarLayout {

    public final Map<String, IStarNode> nodes;
    public final List<IStarEdge>        edges;
    public final int width, height;

    public IStarLayout(Map<String, IStarNode> nodes, List<IStarEdge> edges, int width, int height) {
        this.nodes  = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.edges  = List.copyOf(edges);
        this.width  = width;
        this.height = height;
    }
}
