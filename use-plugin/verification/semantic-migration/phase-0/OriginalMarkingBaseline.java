import org.vnu.sme.goal.dsl.istar.mm.GoalType;
import org.vnu.sme.goal.verify.conformance.semantics.*;

/** Compiled with exact read-only upstream source, outside production classpath. */
public final class OriginalMarkingBaseline {
    public static void main(String[] args) {
        GoalMarking none = GoalMarking.initial(GoalType.NONE).update(true, false);
        require(none.status() == GoalTaskStatus.PENDING, "NONE false is PENDING");
        GoalMarking achieve = GoalMarking.initial(GoalType.ACHIEVE)
                .update(true, false).update(true, true).update(true, false);
        require(achieve.status() == GoalTaskStatus.FULFILLED, "ACHIEVE latches truth");
        GoalMarking maintain = GoalMarking.initial(GoalType.MAINTAIN)
                .update(true, true).update(true, false).update(true, true);
        require(maintain.status() == GoalTaskStatus.VIOLATED, "MAINTAIN latches violation");
        GoalMarking sustain = GoalMarking.initial(GoalType.SUSTAIN)
                .update(true, false).update(true, true).update(true, false);
        require(sustain.status() == GoalTaskStatus.VIOLATED, "SUSTAIN fails after truth falls");
        require(!achieve.update(false, false).active(), "deactivation resets episode");
        TaskMarking task = TaskMarking.initial().update(true, true, false)
                .update(true, false, true);
        require(task.preSeen() && task.postSeen() && task.status() == GoalTaskStatus.FULFILLED,
                "historical task spans complete known tuples");
        boolean unsupported = false;
        try { TaskMarking.fromStatus(GoalTaskStatus.VIOLATED); }
        catch (IllegalArgumentException expected) { unsupported = true; }
        require(unsupported, "Task VIOLATED not representable");
        System.out.println("{\"source\":\"READ_ONLY_UPSTREAM_SOURCE_COMPILED_TO_USE_TARGET\","
                + "\"checks\":7,\"noneFalse\":\"PENDING\",\"achieveLatch\":\"FULFILLED\","
                + "\"maintainLatch\":\"VIOLATED\",\"sustainAfterTruthFalls\":\"VIOLATED\","
                + "\"taskHistory\":\"FULFILLED\",\"taskViolated\":\"UNREPRESENTABLE\"}");
    }
    private static void require(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
