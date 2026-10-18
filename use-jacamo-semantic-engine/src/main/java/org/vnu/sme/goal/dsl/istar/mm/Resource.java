package org.vnu.sme.goal.dsl.istar.mm;

/** iStar 2.0 Resource (rectangle) — needed-by a {@link Task}, may be qualified by a Quality. */
public final class Resource implements ConcreteIntentionalElement {

    private String id;

    public Resource(String id) { this.id = id; }

    @Override public String id() { return id; }
    public void setId(String id) { this.id = id; }
}
