package org.tzi.use.plugins.jacamo.codegrounded;

import cartago.Artifact;
import cartago.OPERATION;

/** Generic real CArtAgO fixture, not a production case-study binding. */
public class LiveRuntimePropertyArtifact extends Artifact {
    public void init() { defineObsProperty("status", "A"); }
    @OPERATION public void set(String value) { getObsProperty("status").updateValue(value); }
    @OPERATION public void remove() { removeObsProperty("status"); }
    @OPERATION public void add(String value) { defineObsProperty("status", value); }
}
