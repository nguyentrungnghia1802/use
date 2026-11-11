package org.vnu.sme.goal.dsl.istar.mm;

/** iStar 2.0 Quality (cloud) — the target of contributesTo, source of qualifies. */
public final class Quality implements IntentionalElement {

    private String id;

    public Quality(String id) { this.id = id; }

    @Override public String id() { return id; }
    public void setId(String id) { this.id = id; }
}
