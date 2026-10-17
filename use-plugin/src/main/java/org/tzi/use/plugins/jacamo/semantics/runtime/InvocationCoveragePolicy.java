package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.time.Duration;

/** Resource limits and source-boundary requirements are explicit monitoring policy. */
public record InvocationCoveragePolicy(int capacity,Duration timeout) {
    public InvocationCoveragePolicy {if(capacity<1 || timeout==null || timeout.isNegative() || timeout.isZero())throw new IllegalArgumentException("INVOCATION_POLICY_INVALID");}
    public static InvocationCoveragePolicy standard(){return new InvocationCoveragePolicy(32,Duration.ofMinutes(5));}
}
