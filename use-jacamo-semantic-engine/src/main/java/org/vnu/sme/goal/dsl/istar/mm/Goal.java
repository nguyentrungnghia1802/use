package org.vnu.sme.goal.dsl.istar.mm;

/** iStar 2.0 Goal (oval) with an optional satisfaction predicate. */
public final class Goal implements GoalTaskElement {

    private String id;
    private GoalType goalType;
    private IStarOclConstraint condition;

    public Goal(String id, GoalType goalType) {
        this(id, goalType, null);
    }

    public Goal(String id, GoalType goalType, IStarOclConstraint condition) {
        this.id = id;
        this.goalType = goalType;
        this.condition = condition;
    }

    @Override public String id() { return id; }
    public GoalType goalType() { return goalType; }

    @Override
    public java.util.List<IStarOclConstraint> constraints() {
        return conditions();
    }

    public java.util.List<IStarOclConstraint> conditions() {
        return condition == null ? java.util.List.of() : java.util.List.of(condition);
    }

    @Override
    public String oclSource() {
        return condition == null ? null : condition.oclBody();
    }

    public void setId(String id) { this.id = id; }
    public void setGoalType(GoalType goalType) { this.goalType = goalType; }
    public void setCondition(IStarOclConstraint condition) { this.condition = condition; }
    public void setOclSource(String oclSource) {
        condition = oclSource == null ? null : new IStarOclConstraint(oclSource);
    }
}
