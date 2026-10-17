package org.tzi.use.plugins.jacamo.semantics.exploration;

/** All limits are explicit. Reaching one before a terminal scenario is a cutoff, never success. */
public record ExplorationBounds(int steps,long nodes,long completeScenarios,int visitsPerState,int population,long logicalClock,long timeoutMillis) {
    public ExplorationBounds {if(steps<0 || nodes<1 || completeScenarios<1 || visitsPerState<1 || population<1 || logicalClock<0 || timeoutMillis<1)throw new IllegalArgumentException("EXPLORATION_BOUNDS_INVALID");}
}
