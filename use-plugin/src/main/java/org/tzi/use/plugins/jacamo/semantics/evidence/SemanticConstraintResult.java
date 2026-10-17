package org.tzi.use.plugins.jacamo.semantics.evidence;

import java.util.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

public record SemanticConstraintResult(String id,String kind,String contextClass,String contextObject,String sourceIdentity,
        String expression,SemanticVerdict verdict,String backendValue,String diagnostic,long stateVersion,String invocation,
        Map<String,String> witness) {
    public SemanticConstraintResult {witness=Map.copyOf(witness);}
    public ExternalOclConstraintService.Outcome legacy() {return new ExternalOclConstraintService.Outcome(id,contextClass,
        verdict.legacyView(),diagnostic,expression,contextObject);}
}
