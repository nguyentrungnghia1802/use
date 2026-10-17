package org.tzi.use.plugins.jacamo.semantics.profile;

import java.util.Set;

/** Versioned observation/requirement contract; this is not an interpreter or a global MAS clock. */
public record SemanticsProfile(String id, String version, Set<String> capabilities,
        String stateBoundary, String transitionBoundary) {
    public SemanticsProfile {
        if (id == null || id.isBlank() || version == null || version.isBlank()
                || stateBoundary == null || transitionBoundary == null)
            throw new IllegalArgumentException("SEMANTICS_PROFILE_INVALID");
        capabilities = Set.copyOf(capabilities);
    }

    public static SemanticsProfile v1() {
        return new SemanticsProfile("JACAMO_CSL_MONITOR", "1.0", Set.of(
                "csl.context", "csl.atomic-player", "csl.role-occurrence", "csl.enactment",
                "domain.scalar", "ocl.invariant", "ocl.invocation-at-pre", "istar.achieve"),
                "SOURCE_WATERMARKED_OBSERVED_CUT", "CORRELATED_INVOCATION_INTERVAL");
    }

    public static SemanticsProfile v2() {
        var capabilities = new java.util.HashSet<>(v1().capabilities());
        capabilities.addAll(Set.of("istar.none", "istar.maintain", "istar.sustain",
                "istar.task", "istar.and", "istar.or", "istar.dependency", "csl.structural-policy", "moise.functional-observer", "jason.mental-observer", "jason.execution-observer", "jason.ground-stored-query", "npl.reported-lifecycle"));
        return new SemanticsProfile(v1().id(), "2.0", capabilities,
                v1().stateBoundary(), v1().transitionBoundary());
    }

    public void require(String capability) {
        if (!capabilities.contains(capability))
            throw new IllegalArgumentException("SEMANTIC_CAPABILITY_UNSUPPORTED:" + capability + ":" + version);
    }
}
