package org.tzi.use.plugins.jacamo.semantics.normative;

import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;

/** No deadline is re-decided using the replay computer clock. */
public final class NormLifecycleMonitor {
    public record Result(SemanticVerdict verdict,String diagnostic){}
    public Result observe(NormativeObservation.State previous,NormativeObservation current,boolean historyGap) {
        if(historyGap || !current.history().equals("CREATED_EVENT_OBSERVED"))return new Result(SemanticVerdict.INCONCLUSIVE,"NORM_ACTIVATION_HISTORY_UNPROVEN");
        if(previous==null)return current.state()==NormativeObservation.State.ACTIVE?new Result(SemanticVerdict.PASS,"CREATED_STATE_OBSERVED"):new Result(SemanticVerdict.INCONCLUSIVE,"CREATION_PREDECESSOR_UNOBSERVED");
        if(previous==current.state())return new Result(SemanticVerdict.PASS,"SOURCE_REPORTED_STATE_STUTTER");
        if(previous==NormativeObservation.State.ACTIVE)return new Result(SemanticVerdict.PASS,"SOURCE_REPORTED_TERMINAL_TRANSITION");
        return new Result(SemanticVerdict.ERROR,"NORM_TERMINAL_STATE_OR_ACTIVATION_TOKEN_CONFLICT");
    }
}
