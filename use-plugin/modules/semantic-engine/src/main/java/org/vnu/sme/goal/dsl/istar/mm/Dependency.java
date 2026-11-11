package org.vnu.sme.goal.dsl.istar.mm;

/**
 * iStar 2.0 Social Dependency: depender --(dependum)--> dependee.
 *
 * <p>The Dependency composition-owns exactly one dependum. The dependum object itself is a
 * concrete {@link Goal}, {@link Task}, {@link Resource}, or {@link Quality}, so no duplicate
 * kind attribute exists. Optional boundary endpoints reference SR elements owned by the
 * depender and dependee actors.</p>
 */
public final class Dependency {

    private String depender;
    private String dependerElmt;
    private IntentionalElement dependum;
    private String dependee;
    private String dependeeElmt;

    public Dependency(String depender, String dependerElmt, IntentionalElement dependum,
                      String dependee, String dependeeElmt) {
        this.depender = depender;
        this.dependerElmt = dependerElmt;
        this.dependum = dependum;
        this.dependee = dependee;
        this.dependeeElmt = dependeeElmt;
    }

    public String depender() { return depender; }
    public String dependerElmt() { return dependerElmt; }
    public IntentionalElement dependum() { return dependum; }
    public String dependee() { return dependee; }
    public String dependeeElmt() { return dependeeElmt; }

    public void setDepender(String depender) { this.depender = depender; }
    public void setDependerElmt(String dependerElmt) { this.dependerElmt = dependerElmt; }
    public void setDependum(IntentionalElement dependum) { this.dependum = dependum; }
    public void setDependee(String dependee) { this.dependee = dependee; }
    public void setDependeeElmt(String dependeeElmt) { this.dependeeElmt = dependeeElmt; }
}
