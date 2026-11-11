package org.jacamo.bridge.adapter;

/** Neutral framework fixture: successful operation is not equivalent to improving its value. */
public class OperationReceiptArtifact extends cartago.Artifact {
    public void init(){defineObsProperty("best",10.0);}
    @cartago.OPERATION public void offer(double value){if(value<0)failed("negative");if(value<(double)getObsProperty("best").getValue())getObsProperty("best").updateValue(value);}
}
