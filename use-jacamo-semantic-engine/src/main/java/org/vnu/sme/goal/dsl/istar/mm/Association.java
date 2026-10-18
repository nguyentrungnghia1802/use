package org.vnu.sme.goal.dsl.istar.mm;

/** actor is-a / participates-in another actor */
public final class Association {

    private String    actor;
    private AssocKind kind;
    private String    target;

    public Association(String actor, AssocKind kind, String target) {
        this.actor  = actor;
        this.kind   = kind;
        this.target = target;
    }

    public String actor()      { return actor; }
    public AssocKind kind()    { return kind; }
    public String target()     { return target; }

    public void setActor(String actor)     { this.actor = actor; }
    public void setKind(AssocKind kind)     { this.kind = kind; }
    public void setTarget(String target)   { this.target = target; }
}
