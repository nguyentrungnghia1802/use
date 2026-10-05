package org.tzi.use.plugins.jacamo.runtime;

import org.tzi.use.plugins.jacamo.trace.TraceRecord;

/** Exact target lookup using the existing trace, independent of EClass names. */
public interface RuntimeTargetResolver {
    record Request(String runtimeId, String semanticId, String targetKind) { }
    record Target(String semanticId, String useId, TraceRecord provenance) { }
    Target resolve(Request request);
}
