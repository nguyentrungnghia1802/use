package org.jacamo.bridge.adapter;
/** A distinct test case; no corresponding production mapping/provider class. */
public class FieldProbeArtifact extends cartago.Artifact {
    private String token="initial";
    private int changes;
    public void init(){defineObsProperty("p",0);}
    @cartago.OPERATION public void bump(){changes++;token="change-"+changes;getObsProperty("p").updateValue(changes);}
}
