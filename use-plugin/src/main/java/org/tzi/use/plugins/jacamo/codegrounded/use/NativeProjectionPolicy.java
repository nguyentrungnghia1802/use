package org.tzi.use.plugins.jacamo.codegrounded.use;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned exposed vocabulary; the rich/frozen source contracts are not modified. */
public final class NativeProjectionPolicy {
    public static final String VERSION = "3.0.0";
    public enum Decision {
        EXPOSE_CLASS, EXPOSE_CONCRETE_SUBCLASS, EXPOSE_ASSOCIATION,
        EXPOSE_ASSOCIATION_CLASS, FLATTEN_TO_ATTRIBUTE, FLATTEN_TO_OPERATION,
        FLATTEN_TO_RELATION, CONSTRAINT_ONLY, TRACE_ONLY, UNSUPPORTED
    }
    public static final List<String> BASE_CLASSES = List.of("Agent", "Belief", "AgentGoal", "Workspace",
            "Artifact", "Organization", "Group", "Scheme", "OrganizationalGoal", "Mission");
    public static final Map<String, String> BASE_KINDS = Map.ofEntries(
            Map.entry("Agent", "agent-base"), Map.entry("Belief", "belief"),
            Map.entry("AgentGoal", "agent-goal"), Map.entry("Workspace", "workspace"),
            Map.entry("Artifact", "artifact-base"), Map.entry("Organization", "organisation-base"),
            Map.entry("Group", "group-base"), Map.entry("Scheme", "scheme-base"),
            Map.entry("OrganizationalGoal", "organisational-goal"), Map.entry("Mission", "mission"));
    private static final Set<String> EXECUTION = Set.of("Plan", "PlanLibrary", "PlanBody", "PlanBodyElement",
            "Trigger", "Event", "Option", "Intention", "IntendedMeans", "Action", "ActionExec",
            "Circumstance", "TransitionSystem", "InternalAction", "OrganizationalPlan", "OPlan");
    private static final Set<String> TRACE = Set.of("Environment", "ArtifactType", "BeliefRule", "Soc",
            "CartagoAgentIdentity", "ArtifactInfo", "Signal", "Guard", "BackingJavaOperation",
            "LiveObservableProperty", "InstitutionDeployment", "ExactBindingEvidence", "RoleEnactment",
            "GroupInstance", "SchemeInstance", "MissionCommitment", "SourceImportProvenance",
            "RuntimeEvidenceHistory", "SnapshotOnlyHelpers", "A17PlanOrderEntry", "A19BodyOrderEntry",
            "StructuralSpecification", "FunctionalSpecification", "NormativeSpecification");
    private NativeProjectionPolicy() { }

    /** Compiler-derived demand, including Agent.beliefs navigation; comments/text are not selectors. */
    public static boolean verificationUses(org.tzi.use.uml.mm.MModel model, String className) {
        return model.classInvariants().stream().filter(org.tzi.use.uml.mm.MClassInvariant::isActive)
                .anyMatch(inv -> org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService
                        .requiredClasses(inv).contains(className));
    }

    public static boolean exposesBelief(org.tzi.use.uml.mm.MModel model, Map<String,Object> literal) {
        return Boolean.TRUE.equals(literal.get("domainAuthored"))
                && Boolean.FALSE.equals(literal.get("authoritativeElsewhere"))
                && !Boolean.TRUE.equals(literal.get("hidden")) && verificationUses(model, "Belief");
    }

    public static Decision decide(String concept) {
        if (concept == null || concept.isBlank()) throw new IllegalArgumentException("PROJECTION_CONCEPT_REQUIRED");
        if (BASE_CLASSES.contains(concept) || concept.equals("AGoal") || concept.equals("OGoal")) return Decision.EXPOSE_CLASS;
        if (EXECUTION.contains(concept)) return concept.equals("OrganizationalPlan") || concept.equals("OPlan")
                ? Decision.FLATTEN_TO_RELATION : Decision.TRACE_ONLY;
        if (TRACE.contains(concept)) return Decision.TRACE_ONLY;
        return switch (concept) {
            case "AgentProgram", "JavaArtifactType", "OrganizationSpecification", "GroupSpecification", "SchemeSpecification" -> Decision.EXPOSE_CONCRETE_SUBCLASS;
            case "Role", "GroupRoleCardinality" -> Decision.EXPOSE_ASSOCIATION_CLASS;
            case "Property", "ObsProperty", "ObservablePropertySnapshot" -> Decision.FLATTEN_TO_ATTRIBUTE;
            case "Operation" -> Decision.FLATTEN_TO_OPERATION;
            case "Norm", "Link", "Compatibility", "RoleRelation" -> Decision.CONSTRAINT_ONLY;
            case "SubGroupCardinality", "SchemeMissionCardinality", "AgentDeclaration", "WorkspaceDeclaration",
                    "ArtifactDeclaration", "OrganizationDeployment", "GroupDeployment", "SchemeDeployment" -> Decision.FLATTEN_TO_RELATION;
            case "Focus", "WorkspaceJoin", "ResponsibleFor", "MissionGoal" -> Decision.EXPOSE_ASSOCIATION;
            default -> Decision.UNSUPPORTED;
        };
    }
    public static boolean executionOnly(String concept) { return EXECUTION.contains(concept) && !Set.of("OPlan", "OrganizationalPlan").contains(concept); }
    public static boolean allowsClass(org.tzi.use.uml.mm.MClass cls) {
        String kind=DomainProjection.kind(cls);
        if(BASE_KINDS.containsValue(kind)) return BASE_KINDS.get(cls.name())!=null && BASE_KINDS.get(cls.name()).equals(kind);
        if(kind.equals("role-association")) return cls instanceof org.tzi.use.uml.mm.MAssociationClass && cls.getAnnotation(MoiseDomainProjection.ROLE_ASSOCIATION)!=null;
        return Set.of("agent-program","artifact","organisation","group","scheme").contains(kind)
            && !cls.getAnnotationValue(DomainProjection.ANNOTATION,"sourceId64").isBlank();
    }
    public static Set<String> knownConcepts() {
        var result = new java.util.TreeSet<>(BASE_CLASSES); result.addAll(EXECUTION); result.addAll(TRACE);
        result.addAll(Set.of("AGoal", "OGoal", "AgentProgram", "JavaArtifactType", "OrganizationSpecification",
                "GroupSpecification", "SchemeSpecification", "Role", "GroupRoleCardinality", "Property", "ObsProperty",
                "ObservablePropertySnapshot", "Operation", "Norm", "Link", "Compatibility", "RoleRelation",
                "SubGroupCardinality", "SchemeMissionCardinality", "AgentDeclaration", "WorkspaceDeclaration",
                "ArtifactDeclaration", "OrganizationDeployment", "GroupDeployment", "SchemeDeployment", "Focus",
                "WorkspaceJoin", "ResponsibleFor", "MissionGoal"));
        return Set.copyOf(result);
    }
}
