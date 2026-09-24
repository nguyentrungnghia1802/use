package org.tzi.use.plugins.jacamo.semantic;

/** Named handles for parser-specific behavior; the active inventory is descriptor-derived. */
public final class MetamodelKind {
    private final String name;
    private final Dimension dimension;
    MetamodelKind(String name, Dimension dimension) { this.name = name; this.dimension = dimension; }
    private static final SemanticKindRegistry ACTIVE = new SemanticKindRegistry(new org.tzi.use.plugins.jacamo.mapping.ActiveBaseline().packaged());
    public static SemanticKindRegistry registry() { return ACTIVE; }
    public static MetamodelKind valueOf(String name) { return ACTIVE.require(name); }
    public static MetamodelKind[] values() { return ACTIVE.kinds().toArray(MetamodelKind[]::new); }
    public String name() { return name; }
    public Dimension dimension() { return dimension; }
    @Override public String toString() { return name; }
    @Override public boolean equals(Object other) { return other instanceof MetamodelKind k && name.equals(k.name) && dimension == k.dimension; }
    @Override public int hashCode() { return java.util.Objects.hash(name, dimension); }
    public static final MetamodelKind Organization = ACTIVE.require("Organization");
    public static final MetamodelKind Group = ACTIVE.require("Group");
    public static final MetamodelKind Role = ACTIVE.require("Role");
    public static final MetamodelKind Link = ACTIVE.require("Link");
    public static final MetamodelKind Norm = ACTIVE.require("Norm");
    public static final MetamodelKind Scheme = ACTIVE.require("Scheme");
    public static final MetamodelKind Mission = ACTIVE.require("Mission");
    public static final MetamodelKind OGoal = ACTIVE.require("OGoal");
    public static final MetamodelKind OPlan = ACTIVE.require("OPlan");
    public static final MetamodelKind Agent = ACTIVE.require("Agent");
    public static final MetamodelKind Plan = ACTIVE.require("Plan");
    public static final MetamodelKind Event = ACTIVE.require("Event");
    public static final MetamodelKind Action = ACTIVE.require("Action");
    public static final MetamodelKind Belief = ACTIVE.require("Belief");
    public static final MetamodelKind AGoal = ACTIVE.require("AGoal");
    public static final MetamodelKind Environment = ACTIVE.require("Environment");
    public static final MetamodelKind Workspace = ACTIVE.require("Workspace");
    public static final MetamodelKind Artifact = ACTIVE.require("Artifact");
    public static final MetamodelKind Operation = ACTIVE.require("Operation");
    public static final MetamodelKind Property = ACTIVE.require("Property");
    public static final MetamodelKind Signal = ACTIVE.require("Signal");
    // Transitional parser handles only; excluded from ACTIVE and rejected by V2 IR validation.
    @Deprecated(forRemoval = true) public static final MetamodelKind MAS = new MetamodelKind("MAS", Dimension.PROJECT);
    @Deprecated(forRemoval = true) public static final MetamodelKind Rule = new MetamodelKind("Rule", Dimension.AGENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind Goal = new MetamodelKind("Goal", Dimension.AGENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind Context = new MetamodelKind("Context", Dimension.AGENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind Body = new MetamodelKind("Body", Dimension.AGENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind BodyTerm = new MetamodelKind("BodyTerm", Dimension.AGENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind ExternalAction = new MetamodelKind("ExternalAction", Dimension.AGENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind InternalAction = new MetamodelKind("InternalAction", Dimension.AGENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind MentalNotes = new MetamodelKind("MentalNotes", Dimension.AGENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind Message = new MetamodelKind("Message", Dimension.AGENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind TriggeringEvent = new MetamodelKind("TriggeringEvent", Dimension.AGENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind Port = new MetamodelKind("Port", Dimension.ENVIRONMENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind ObsProperty = new MetamodelKind("ObsProperty", Dimension.ENVIRONMENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind AbsOperation = new MetamodelKind("AbsOperation", Dimension.ENVIRONMENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind LinkedOperation = new MetamodelKind("LinkedOperation", Dimension.ENVIRONMENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind InternalOperation = new MetamodelKind("InternalOperation", Dimension.ENVIRONMENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind GuardOperation = new MetamodelKind("GuardOperation", Dimension.ENVIRONMENT);
    @Deprecated(forRemoval = true) public static final MetamodelKind Organisation = new MetamodelKind("Organisation", Dimension.ORGANISATION);
    @Deprecated(forRemoval = true) public static final MetamodelKind NormativeSpecification = new MetamodelKind("NormativeSpecification", Dimension.ORGANISATION);
    @Deprecated(forRemoval = true) public static final MetamodelKind StructuralSpecification = new MetamodelKind("StructuralSpecification", Dimension.ORGANISATION);
    @Deprecated(forRemoval = true) public static final MetamodelKind FunctionalSpecification = new MetamodelKind("FunctionalSpecification", Dimension.ORGANISATION);
    @Deprecated(forRemoval = true) public static final MetamodelKind FormationConstraints = new MetamodelKind("FormationConstraints", Dimension.ORGANISATION);
}
