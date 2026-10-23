package org.jacamo.bridge.adapter;
/** Alternate case for neutral receipt consumers; successful does not imply a value change. */
public class NeutralOperationArtifact extends cartago.Artifact {
    void init(){defineObsProperty("best",10.0);}
    @cartago.OPERATION void offer(double value){if(value<0)failed("negative");if(value<(double)getObsProperty("best").getValue())getObsProperty("best").updateValue(value);}
}
