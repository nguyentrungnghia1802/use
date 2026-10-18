package org.tzi.use.plugins.jacamo.semantics.runtime;

import org.tzi.use.semantic.engine.use75.NativeOclOutcome;

/** Quality is an overlay; unavailable observations never become Boolean false in the original engine. */
public final class ObservationQualityGate {
    public record Decision(Boolean known,String reason) {public boolean admitted(){return known!=null;}}
    public Decision booleanInput(NativeOclOutcome outcome,boolean exactContext,boolean coverageLost) {
        if(coverageLost)return new Decision(null,"COVERAGE_LOST");
        if(!exactContext)return new Decision(null,"EXACT_CONTEXT_CUT_UNAVAILABLE");
        if(outcome==null)return new Decision(null,"OCL_OUTCOME_UNAVAILABLE");
        return switch(outcome.status()) {
            case PASS->new Decision(true,"");case FAIL->new Decision(false,"");
            case UNDEFINED,ERROR,SKIPPED->new Decision(null,outcome.status()+":"+outcome.diagnostic());
        };
    }
}
