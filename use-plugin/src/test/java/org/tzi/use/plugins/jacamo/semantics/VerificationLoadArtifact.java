package org.tzi.use.plugins.jacamo.semantics;

/** Source interface fixture for synthetic adapter-boundary workloads. */
public class VerificationLoadArtifact extends cartago.Artifact {
    public void init(){for(int i=0;i<10;i++)defineObsProperty("p"+i,0);}
}
