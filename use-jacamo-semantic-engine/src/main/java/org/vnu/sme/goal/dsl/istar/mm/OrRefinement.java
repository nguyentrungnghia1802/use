package org.vnu.sme.goal.dsl.istar.mm;

/** OR-refinement — {@code child} alone is sufficient to satisfy {@code parent}. */
public final class OrRefinement implements Refinement {

    private String parent;
    private String child;

    public OrRefinement(String parent, String child) {
        this.parent = parent;
        this.child  = child;
    }

    @Override public String parent() { return parent; }
    public String child()            { return child; }

    public void setParent(String parent) { this.parent = parent; }
    public void setChild(String child)   { this.child = child; }
}
