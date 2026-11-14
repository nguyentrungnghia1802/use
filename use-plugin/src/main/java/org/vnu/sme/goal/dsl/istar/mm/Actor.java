package org.vnu.sme.goal.dsl.istar.mm;

import java.util.List;

/** Actor supertype; GenericActor is the project's explicit unspecified-specialization extension. */
public sealed interface Actor permits Agent, Role, GenericActor {

    String name();
    List<IntentionalElement> elements();
    List<Refinement>         refinements();
    List<Contribution>       contributions();
    List<Qualification>      qualifications();
    List<NeededBy>           neededBys();
    List<Association>        associations();
}
