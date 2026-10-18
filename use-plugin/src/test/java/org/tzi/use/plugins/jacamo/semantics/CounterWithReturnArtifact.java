package org.tzi.use.plugins.jacamo.semantics;

/** Actual reflected Method; the CArtAgO logger does not report Java return values. */
public class CounterWithReturnArtifact extends CslCounterArtifact {
    @cartago.OPERATION public int returnP(){return getObsProperty("p").intValue();}
}
