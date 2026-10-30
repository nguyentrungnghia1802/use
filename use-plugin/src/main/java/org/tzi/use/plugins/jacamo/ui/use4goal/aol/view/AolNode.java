package org.tzi.use.plugins.jacamo.ui.use4goal.aol.view;

import java.util.List;

public final class AolNode {
    public final String id;
    public final String label;
    public final AolNodeKind kind;
    public final String subtitle;
    public final List<String> details;
    public int x, y, w, h;

    public AolNode(String id, String label, AolNodeKind kind, String subtitle, List<String> details) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.subtitle = subtitle;
        this.details = List.copyOf(details);
    }
}
