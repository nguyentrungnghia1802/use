package org.tzi.use.plugins.jacamo.semantics;

/** Test application artifact: real CArtAgO operation and property callbacks. */
public class CslCounterArtifact extends cartago.Artifact {
    public void init(){defineObsProperty("p",0);}
    @cartago.OPERATION public void increment(){getObsProperty("p").updateValue(getObsProperty("p").intValue()+1);}
    @cartago.OPERATION public void incrementTwo(){getObsProperty("p").updateValue(getObsProperty("p").intValue()+2);}
    @cartago.OPERATION public void set(int value){getObsProperty("p").updateValue(value);}
    @cartago.OPERATION public void remove(){removeObsProperty("p");}
    @cartago.OPERATION public void add(int value){defineObsProperty("p",value);}
}
