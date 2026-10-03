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
            "ExactBindingEvidence", "BackingJavaOperation", "ArtifactInfo",
            "WorkspaceDeclaration", "ArtifactDeclaration", "OrganizationDeployment",
            "GroupDeployment", "SchemeDeployment", "InstitutionDeployment",
            "AgentProgram", "Trigger", "Action", "Belief", "AgentGoal", "BeliefRule",
            "Signal", "Guard", "Operation");
    private static final Set<String> AUTO_PROFILE_EXCLUDED_CLASSES = Set.of(
            "LiveObservableProperty", "A17PlanOrderEntry", "A19BodyOrderEntry");
    private static final Map<String, String> RATIONALES = Map.ofEntries(
            Map.entry("ExactBindingEvidence", "exact binding remains in trace and binding links; no verification target needs a duplicate evidence object"),
            Map.entry("BackingJavaOperation", "C06 reflection evidence and native MOperation remain available; source descriptor is trace-only in AUTO"),
            Map.entry("ArtifactInfo", "C10 runtime snapshot helper is retained in semantic evidence; no native OCL target requires a duplicate object"),
            Map.entry("WorkspaceDeclaration", "JCM deployment declaration is retained in semantic trace; runtime verification uses the resolved C02 Workspace instead"),
            Map.entry("ArtifactDeclaration", "JCM artifact declaration is retained in semantic trace; runtime verification uses the resolved C04 Artifact instead"),
            Map.entry("OrganizationDeployment", "JCM organization deployment is configuration evidence, not a current OCL or runtime mutation target"),
            Map.entry("GroupDeployment", "JCM group deployment is configuration evidence, not a current OCL or runtime mutation target"),
            Map.entry("SchemeDeployment", "JCM scheme deployment is configuration evidence, not a current OCL or runtime mutation target"),
            Map.entry("InstitutionDeployment", "JCM institution deployment is configuration evidence, not a current OCL or runtime mutation target"),
            Map.entry("AgentProgram", "program wrapper is retained in Jason semantic trace; AUTO constraints target PlanLibrary/Plan/PlanBodyElement directly"),
            Map.entry("Trigger", "trigger is retained in Jason semantic trace; current native OCL does not target trigger instances"),
            Map.entry("Action", "action is retained in Jason semantic trace; runtime action events are evidence-only and no native OCL targets Action"),
            Map.entry("Belief", "initial belief is retained in Jason semantic trace; current native OCL and runtime mutation do not target Belief"),
            Map.entry("AgentGoal", "agent goal is retained in Jason semantic trace; current native OCL and runtime mutation do not target AgentGoal"),
            Map.entry("BeliefRule", "belief rule is retained in Jason semantic trace; current native OCL and runtime mutation do not target BeliefRule"),
            Map.entry("Signal", "signal is retained in exact cross-dimension evidence; C08 live property capability is unavailable and no native OCL targets Signal"),
            Map.entry("Guard", "guard is retained in Cartago semantic evidence; current native OCL and runtime mutation do not target Guard"),
            Map.entry("Operation", "operation descriptor remains exact C06/MOperation evidence; AUTO has no OCL/runtime MObject dependency on a duplicate Operation object"),
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
                "RuntimeEvidenceHistory", "SnapshotOnlyHelpers", "WorkspaceDeclaration", "ArtifactDeclaration",
                "OrganizationDeployment", "GroupDeployment", "SchemeDeployment", "InstitutionDeployment",
                "AgentProgram", "Trigger", "Action", "Belief", "AgentGoal", "BeliefRule", "Signal", "Guard",
                "Operation");
        for (String concept : concepts) statuses.put(concept, statusFor(concept));
        LinkedHashMap<String, String> selectedRationales = new LinkedHashMap<>();
        for (String concept : MoiseDomainProjection.INSPECTION_RULES.keySet().stream().sorted().toList()) {
            statuses.put(concept, statusFor(concept));
            selectedRationales.put(concept, "Moise definition graph is FULL inspection data; AUTO specializes it into domain schema/policies, not static enactment objects");
        }
        concepts.forEach(concept -> selectedRationales.put(concept, RATIONALES.get(concept)));
        conceptStatuses = Collections.unmodifiableMap(statuses);
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
        if (MoiseDomainProjection.INSPECTION_RULES.containsKey(concept)) return NativeProjectionStatus.EVIDENCE_ONLY;
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
        MoiseDomainProjection.INSPECTION_RULES.keySet().forEach(value -> {
            if (statusFor(value) == NativeProjectionStatus.EVIDENCE_ONLY) result.add(value);
        });
        return Collections.unmodifiableSet(result);
    }
}
