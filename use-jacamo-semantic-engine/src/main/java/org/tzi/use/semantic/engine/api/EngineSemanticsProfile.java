package org.tzi.use.semantic.engine.api;

/** Versioned semantic choices. No mapping rules or mutable runtime are owned here. */
public enum EngineSemanticsProfile {
    USE4GOAL_OBSERVED_V1;

    public String upstreamCommit() { return "6f3e73801239fd8bfb94ca8d04ba531b903c0ac9"; }
    public String hostUseVersion() { return "7.5.0"; }
    public String antlrVersion() { return "4.9.3"; }
    public int maxOccurrences() { return 4096; }
    public int maxDefinitionDepth() { return 256; }
    public int maxDefinitionEdges() { return 16384; }
    public int maxPendingInputs() { return 1024; }
    public int originalPropagationStepLimit() { return 10_000; }
}
