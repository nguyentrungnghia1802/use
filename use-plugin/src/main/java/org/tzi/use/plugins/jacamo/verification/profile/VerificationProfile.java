package org.tzi.use.plugins.jacamo.verification.profile;

import java.util.List;

/** Declarative verification-only semantic decisions layered over, never written into, Mapping V1. */
public record VerificationProfile(String profileId, String version, String status, String baselineMappingId,
                                  List<Decision> decisions) {
    public VerificationProfile { decisions = List.copyOf(decisions); }

    public record Decision(String id, Classification classification, Kind kind, String subclass,
                           String superclass, String mappingRuleId, String from, String to, String rationale) { }
    public enum Classification { OUR_EXT, SEMANTIC_CLARIFICATION }
    public enum Kind { REMOVE_INHERITANCE, OVERRIDE_FORWARD_MULTIPLICITY }
}
