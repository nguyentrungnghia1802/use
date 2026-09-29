package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic native projection policy.  The semantic contract is built before this policy is
 * applied; the policy only decides which already-extracted concepts receive a normal USE target.
 */
public final class NativeProjectionProfile {
    private static final Set<String> AUTO_EVIDENCE_ONLY_CLASSES = Set.of(
            "ExactBindingEvidence", "BackingJavaOperation", "ArtifactInfo");
    private static final Set<String> AUTO_PROFILE_EXCLUDED_CLASSES = Set.of(
            "LiveObservableProperty", "A17PlanOrderEntry", "A19BodyOrderEntry");
    private static final Map<String, String> RATIONALES = Map.ofEntries(
            Map.entry("ExactBindingEvidence", "exact binding remains in trace and binding links; no verification target needs a duplicate evidence object"),
            Map.entry("BackingJavaOperation", "C06 reflection evidence and native MOperation remain available; source descriptor is trace-only in AUTO"),
            Map.entry("ArtifactInfo", "C10 runtime snapshot helper is retained in semantic evidence; no native OCL target requires a duplicate object"),
            Map.entry("LiveObservableProperty", "C08 audited API does not expose live ObsProperty; keep the capability diagnostic explicit"),
            Map.entry("A17PlanOrderEntry", "A17 order is represented by Plan.ordinal and the existing ordered PlanLibrary.plans association"),
            Map.entry("A19BodyOrderEntry", "A19 order is represented by PlanBodyElement.ordinal and the existing ordered Plan.bodyElements association"),
            Map.entry("SourceImportProvenance", "provenance is trace/evidence, not a verification-domain object"),
            Map.entry("RuntimeEvidenceHistory", "runtime evidence/history is owned by the Bridge trace and runtime projector"),
            Map.entry("SnapshotOnlyHelpers", "snapshot helper payloads remain in the typed semantic contract and trace"));

    private final NativeProjectionMode mode;
    private final Map<String, NativeProjectionStatus> conceptStatuses;
    private final Map<String, String> rationales;

    private NativeProjectionProfile(NativeProjectionMode mode) {
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        LinkedHashMap<String, NativeProjectionStatus> statuses = new LinkedHashMap<>();
        List<String> concepts = List.of(
                "ExactBindingEvidence", "BackingJavaOperation", "ArtifactInfo", "LiveObservableProperty",
                "A17PlanOrderEntry", "A19BodyOrderEntry", "SourceImportProvenance",
                "RuntimeEvidenceHistory", "SnapshotOnlyHelpers");
        for (String concept : concepts) statuses.put(concept, statusFor(concept));
        conceptStatuses = Collections.unmodifiableMap(statuses);
        LinkedHashMap<String, String> selectedRationales = new LinkedHashMap<>();
        concepts.forEach(concept -> selectedRationales.put(concept, RATIONALES.get(concept)));
        rationales = Collections.unmodifiableMap(selectedRationales);
    }

    public static NativeProjectionProfile forMode(NativeProjectionMode mode) {
        return new NativeProjectionProfile(mode == null ? NativeProjectionMode.AUTO : mode);
    }

    public NativeProjectionMode mode() { return mode; }

    public NativeProjectionStatus statusFor(String concept) {
        if (concept == null || concept.isBlank()) throw new IllegalArgumentException("PROJECTION_CONCEPT_REQUIRED");
        if ("SourceImportProvenance".equals(concept) || "RuntimeEvidenceHistory".equals(concept)
                || "SnapshotOnlyHelpers".equals(concept)) return NativeProjectionStatus.EVIDENCE_ONLY;
        if (mode == NativeProjectionMode.FULL) return NativeProjectionStatus.MATERIALIZED;
        if (AUTO_EVIDENCE_ONLY_CLASSES.contains(concept)) return NativeProjectionStatus.EVIDENCE_ONLY;
        if (AUTO_PROFILE_EXCLUDED_CLASSES.contains(concept)) return NativeProjectionStatus.PROFILE_EXCLUDED;
        return NativeProjectionStatus.MATERIALIZED;
    }

    public boolean materializesClass(String className) {
        return statusFor(className) == NativeProjectionStatus.MATERIALIZED;
    }

    public boolean materializesOrderEntries() {
        return materializesClass("A17PlanOrderEntry") && materializesClass("A19BodyOrderEntry");
    }

    public Map<String, NativeProjectionStatus> conceptStatuses() { return conceptStatuses; }
    public Map<String, String> rationales() { return rationales; }

    public Set<String> profileExcludedClasses() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        AUTO_PROFILE_EXCLUDED_CLASSES.forEach(value -> {
            if (statusFor(value) == NativeProjectionStatus.PROFILE_EXCLUDED) result.add(value);
        });
        return Collections.unmodifiableSet(result);
    }

    public Set<String> evidenceOnlyClasses() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        AUTO_EVIDENCE_ONLY_CLASSES.forEach(value -> {
            if (statusFor(value) == NativeProjectionStatus.EVIDENCE_ONLY) result.add(value);
        });
        return Collections.unmodifiableSet(result);
    }
}
