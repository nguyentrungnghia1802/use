package org.vnu.sme.goal.dsl.istar.mm;

/** iStar 2.0 Task (hexagon) with separate optional pre- and postconditions. */
public final class Task implements GoalTaskElement {

    private String id;
    private IStarOclConstraint precondition;
    private IStarOclConstraint postcondition;

    public Task(String id) {
        this(id, null, null);
    }

    /** Compatibility constructor: the OCL expression is the Task postcondition. */
    public Task(String id, String oclSource) {
        this(id, null, oclSource == null ? null : new IStarOclConstraint(oclSource));
    }

    public Task(String id, IStarOclConstraint precondition, IStarOclConstraint postcondition) {
        this.id = id;
        this.precondition = precondition;
        this.postcondition = postcondition;
    }

    @Override public String id() { return id; }

    @Override
    public java.util.List<IStarOclConstraint> constraints() {
        java.util.ArrayList<IStarOclConstraint> result = new java.util.ArrayList<>(2);
        if (precondition != null) result.add(precondition);
        if (postcondition != null) result.add(postcondition);
        return java.util.List.copyOf(result);
    }

    @Override
    public java.util.List<IStarOclConstraint> preconditions() {
        return precondition == null ? java.util.List.of() : java.util.List.of(precondition);
    }

    @Override
    public java.util.List<IStarOclConstraint> postconditions() {
        return postcondition == null ? java.util.List.of() : java.util.List.of(postcondition);
    }

    public void setId(String id) { this.id = id; }
    public void setPrecondition(IStarOclConstraint precondition) { this.precondition = precondition; }
    public void setPostcondition(IStarOclConstraint postcondition) { this.postcondition = postcondition; }
    public void setOclSource(String oclSource) {
        postcondition = oclSource == null ? null : new IStarOclConstraint(oclSource);
    }
}
