package org.tzi.use.plugins.jacamo.semantics.profile;

/** A loss/admission decision always identifies the source; absence is not success. */
public record UnsupportedFeature(String sourceId, String concept, String code, String explanation) {
    public UnsupportedFeature {
        if (sourceId == null || sourceId.isBlank() || concept == null || concept.isBlank()
                || code == null || code.isBlank() || explanation == null || explanation.isBlank())
            throw new IllegalArgumentException("UNSUPPORTED_FEATURE_EVIDENCE_REQUIRED");
    }
}
