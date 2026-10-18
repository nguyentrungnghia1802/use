package org.vnu.sme.goal.dsl.istar.mm;

import java.util.List;

/** AND-refinement — all {@code children} must be satisfied for {@code parent} to be satisfied. */
public final class AndRefinement implements Refinement {

    private String       parent;
    private List<String> children;

    public AndRefinement(String parent, List<String> children) {
        this.parent   = parent;
        this.children = List.copyOf(children);
    }

    @Override public String parent() { return parent; }
    public List<String> children()   { return children; }

    public void setParent(String parent)          { this.parent = parent; }
    public void setChildren(List<String> children) { this.children = List.copyOf(children); }
}
