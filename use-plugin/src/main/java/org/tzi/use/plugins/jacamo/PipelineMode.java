package org.tzi.use.plugins.jacamo;

/** Atomic authority switch: a build uses either the historical V2 path or the native code-grounded path. */
public enum PipelineMode {
    LEGACY_V2,
    CODE_GROUNDED_NATIVE
}
