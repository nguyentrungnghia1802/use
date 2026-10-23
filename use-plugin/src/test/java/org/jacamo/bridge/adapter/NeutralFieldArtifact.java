package org.jacamo.bridge.adapter;
/** Alternate-case fixture for request-owned internal-field receipts; never shipped in the plugin. */
public class NeutralFieldArtifact extends cartago.Artifact {
    private String token="initial";
    void init(){defineObsProperty("p",1);defineObsProperty("ready",false);defineObsProperty("weight",Double.MAX_VALUE);}
    @cartago.OPERATION void bump(){getObsProperty("p").updateValue(2);token="changed";}
}
