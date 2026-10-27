package org.tzi.use.plugins.jacamo.ui.use4goal.csl;

import java.util.List;

public final class CslNode {
    public final String id;
    public final String label;
    public final CslNodeKind kind;
    public final String subtitle;
    public final List<String> details;
    public int x, y, w, h;

    public CslNode(String id, String label, CslNodeKind kind, String subtitle, List<String> details) {
        this.id = id;
        this.label = label;
        this.kind = kind;
        this.subtitle = subtitle;
        this.details = List.copyOf(details);
    }
}
