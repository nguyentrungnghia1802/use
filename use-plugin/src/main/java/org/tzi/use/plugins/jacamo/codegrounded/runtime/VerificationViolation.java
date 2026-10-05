package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.time.Instant;
import java.util.*;
import org.jacamo.bridge.contract.RuntimeControlContract.State;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.RuntimeConstraintPolicy;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;

/** Immutable FAIL diagnostics; original cut/identity remain unchanged by pause confirmation. */
public record VerificationViolation(String id,String constraintId,String constraintName,VerificationOutcome result,
        RuntimeConstraintPolicy.Severity severity,RuntimeConstraintPolicy.Enforcement enforcement,CheckpointType checkpoint,
        String failingSnapshotId,long boundarySequence,long generation,Instant timestamp,String contextObject,
        Map<String,VerificationSnapshot.ObjectState> involvedObjects,Map<String,String> expected,Map<String,String> actual,
        List<SourceTrace> traces,State pauseState,Confirmation confirmation,String confirmationSnapshotId,String diagnostic) {
    public enum Confirmation { PENDING, REPORT_ONLY, CONFIRMED, TRANSIENT_NOT_REPRODUCED, CONFIRMATION_ERROR }
    public record SourceTrace(String file,int line,String semanticId,List<String> runtimeIdentities,String mappingRule,String target) {
        public SourceTrace {runtimeIdentities=List.copyOf(runtimeIdentities);}
        public String location(){return file+(line>0?":"+line:" (line unavailable)");}
    }
    public VerificationViolation {
        if(result!=VerificationOutcome.FAIL || failingSnapshotId==null || failingSnapshotId.isBlank())throw new IllegalArgumentException("VIOLATION_REQUIRES_FAILING_CUT");
        involvedObjects=Map.copyOf(involvedObjects);expected=Map.copyOf(expected);actual=Map.copyOf(actual);traces=List.copyOf(traces);
    }
    public VerificationViolation update(State state,Confirmation confirmed,String snapshot,String message) {
        return new VerificationViolation(id,constraintId,constraintName,result,severity,enforcement,checkpoint,failingSnapshotId,boundarySequence,
                generation,timestamp,contextObject,involvedObjects,expected,actual,traces,state,confirmed,snapshot,message);
    }
}
