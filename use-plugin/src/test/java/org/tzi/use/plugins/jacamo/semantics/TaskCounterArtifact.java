package org.tzi.use.plugins.jacamo.semantics;
public class TaskCounterArtifact extends cartago.Artifact {
    protected void init(){defineObsProperty("p0",0);}
    @cartago.OPERATION void increment(){getObsProperty("p0").updateValue(getObsProperty("p0").intValue()+1);}
}
