package org.tzi.use.plugins.jacamo.semantic;

/** Exact EClass names from the frozen JaCaMo Ecore. */
public enum MetamodelKind {
    MAS(Dimension.PROJECT),
    Agent(Dimension.AGENT), Belief(Dimension.AGENT), Rule(Dimension.AGENT),
    Goal(Dimension.AGENT), Context(Dimension.AGENT), Plan(Dimension.AGENT),
    Body(Dimension.AGENT), BodyTerm(Dimension.AGENT), Action(Dimension.AGENT),
    ExternalAction(Dimension.AGENT), InternalAction(Dimension.AGENT),
    MentalNotes(Dimension.AGENT), Message(Dimension.AGENT), TriggeringEvent(Dimension.AGENT),
    Workspace(Dimension.ENVIRONMENT), Artifact(Dimension.ENVIRONMENT),
    Port(Dimension.ENVIRONMENT), ObsProperty(Dimension.ENVIRONMENT),
    AbsOperation(Dimension.ENVIRONMENT), LinkedOperation(Dimension.ENVIRONMENT),
    InternalOperation(Dimension.ENVIRONMENT), GuardOperation(Dimension.ENVIRONMENT),
    Operation(Dimension.ENVIRONMENT),
    Organisation(Dimension.ORGANISATION), NormativeSpecification(Dimension.ORGANISATION),
    StructuralSpecification(Dimension.ORGANISATION), FunctionalSpecification(Dimension.ORGANISATION),
    Norm(Dimension.ORGANISATION), Group(Dimension.ORGANISATION), Role(Dimension.ORGANISATION),
    FormationConstraints(Dimension.ORGANISATION), Link(Dimension.ORGANISATION),
    Scheme(Dimension.ORGANISATION), OPlan(Dimension.ORGANISATION),
    OGoal(Dimension.ORGANISATION), Mission(Dimension.ORGANISATION);

    private final Dimension dimension;

    MetamodelKind(Dimension dimension) { this.dimension = dimension; }

    public Dimension dimension() { return dimension; }
}
