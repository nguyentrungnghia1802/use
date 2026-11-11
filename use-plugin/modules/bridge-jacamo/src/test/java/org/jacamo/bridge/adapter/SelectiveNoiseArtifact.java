package org.jacamo.bridge.adapter;

public class SelectiveNoiseArtifact extends cartago.Artifact {
    public void init(){defineObsProperty("ignored",new SelectiveCartagoCaptureTest.Payload());}
}
