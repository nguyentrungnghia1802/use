package org.jacamo.bridge.adapter;

/** Top-level fixture: the official JCM grammar does not accept JVM '$' names. */
public class SelectiveMeterArtifact extends cartago.Artifact {
    public void init(){defineObsProperty("reading",0);defineObsProperty("ignored",new SelectiveCartagoCaptureTest.Payload());}
    @cartago.OPERATION public void noise(){getObsProperty("ignored").updateValue(new SelectiveCartagoCaptureTest.Payload());}
    @cartago.OPERATION public void bump(){getObsProperty("reading").updateValue(getObsProperty("reading").intValue()+1);}
    @cartago.OPERATION public void reset(){getObsProperty("reading").updateValue(0);}
    @cartago.OPERATION public void keepSame(){ }
    @cartago.OPERATION public void reject(){failed("expected rejection");}
}
