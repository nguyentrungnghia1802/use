package org.tzi.use.semantic.engine.runtime;

/** Explicit topology identity, independent of source language and transport. */
public enum EngineTopologyPolicy {
    ORIGINAL_CONTEXT_PATHS,
    /** Exactly one proved instance per actor in one context; no path copies. */
    SINGLE_OWNER_CONTEXT_V1
}
