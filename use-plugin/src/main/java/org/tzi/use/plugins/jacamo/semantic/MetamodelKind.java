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
}
