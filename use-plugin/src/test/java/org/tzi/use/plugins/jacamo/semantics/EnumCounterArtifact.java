package org.tzi.use.plugins.jacamo.semantics;

public class EnumCounterArtifact extends cartago.Artifact {
    public enum Color { RED, BLUE }
    public void init(){defineObsProperty("color",Color.RED);}
    @cartago.OPERATION public void blue(){getObsProperty("color").updateValue(Color.BLUE);}
}
