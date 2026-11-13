package org.vnu.sme.goal.dsl.istar.mm;

import java.util.List;

/** Project extension: an intentional actor whose Agent/Role specialization is unspecified. */
public record GenericActor(String name,List<IntentionalElement> elements,List<Refinement> refinements,
        List<Contribution> contributions,List<Qualification> qualifications,List<NeededBy> neededBys,
        List<Association> associations) implements Actor {
    public GenericActor {
        elements=List.copyOf(elements);refinements=List.copyOf(refinements);
        contributions=List.copyOf(contributions);qualifications=List.copyOf(qualifications);
        neededBys=List.copyOf(neededBys);associations=List.copyOf(associations);
    }
}
