package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection;

/** Functional definitions remain a separate observer vocabulary, never CSL Entities. */
public final class FunctionalMappingRules {
    public record Threshold(boolean all,int minimum) {
        public Threshold {if(minimum < -1 || all != (minimum == -1))throw new IllegalArgumentException("FUNCTIONAL_THRESHOLD_INVALID");}
        public static Threshold of(int source){return new Threshold(source == -1,source);}
    }
    public record Goal(OrganizationalGoalSemantic source,Threshold threshold) { }
    public record Scheme(SchemeSemantic source,List<Goal> goals,List<SchemeMissionCardinalitySemantic> cardinalities) {
        public Scheme {goals=List.copyOf(goals);cardinalities=List.copyOf(cardinalities);}
    }
    public List<Scheme> derive(OrganizationSemantic organization) {
        MoiseDomainProjection.validate(organization);
        return organization.functionalSpecification().schemes().stream().map(s->new Scheme(s,s.goals().stream().map(g->new Goal(g,Threshold.of(g.minAgentsToSatisfy()))).toList(),
            organization.functionalSpecification().schemeMissionCardinalities().stream().filter(c->c.schemeId().equals(s.metadata().semanticId())).toList())).toList();
    }
}
