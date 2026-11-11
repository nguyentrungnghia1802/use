package org.vnu.sme.goal.dsl.istar.mm;

import java.util.List;

/** iStar 2.0 Agent — a concrete, physical actor (e.g. a person, organization, or system). */
public final class Agent implements Actor {

    private String                   name;
    private List<IntentionalElement> elements;
    private List<Refinement>         refinements;
    private List<Contribution>       contributions;
    private List<Qualification>      qualifications;
    private List<NeededBy>           neededBys;
    private List<Association>        associations;
    public Agent(String name,
                 List<IntentionalElement> elements,
                 List<Refinement>         refinements,
                 List<Contribution>       contributions,
                 List<Qualification>      qualifications,
                 List<NeededBy>           neededBys,
                 List<Association>        associations) {
        this.name           = name;
        this.elements       = List.copyOf(elements);
        this.refinements    = List.copyOf(refinements);
        this.contributions  = List.copyOf(contributions);
        this.qualifications = List.copyOf(qualifications);
        this.neededBys       = List.copyOf(neededBys);
        this.associations    = List.copyOf(associations);
    }

    @Override public String                   name()           { return name; }
    @Override public List<IntentionalElement> elements()       { return elements; }
    @Override public List<Refinement>         refinements()    { return refinements; }
    @Override public List<Contribution>       contributions()  { return contributions; }
    @Override public List<Qualification>      qualifications() { return qualifications; }
    @Override public List<NeededBy>           neededBys()      { return neededBys; }
    @Override public List<Association>        associations()   { return associations; }

    public void setName(String name)                                     { this.name = name; }
    public void setElements(List<IntentionalElement> elements)            { this.elements = List.copyOf(elements); }
    public void setRefinements(List<Refinement> refinements)               { this.refinements = List.copyOf(refinements); }
    public void setContributions(List<Contribution> contributions)         { this.contributions = List.copyOf(contributions); }
    public void setQualifications(List<Qualification> qualifications)      { this.qualifications = List.copyOf(qualifications); }
    public void setNeededBys(List<NeededBy> neededBys)                     { this.neededBys = List.copyOf(neededBys); }
    public void setAssociations(List<Association> associations)            { this.associations = List.copyOf(associations); }
}
