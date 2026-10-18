package org.vnu.sme.goal.dsl.istar.mm;

/** element --[type]--> quality */
public final class Contribution {

    private String           element;
    private ContributionType type;
    private String           quality;

    public Contribution(String element, ContributionType type, String quality) {
        this.element = element;
        this.type    = type;
        this.quality = quality;
    }

    public String element()           { return element; }
    public ContributionType type()   { return type; }
    public String quality()           { return quality; }

    public void setElement(String element)          { this.element = element; }
    public void setType(ContributionType type)      { this.type = type; }
    public void setQuality(String quality)           { this.quality = quality; }
}
