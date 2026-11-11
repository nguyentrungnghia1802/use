package org.vnu.sme.goal.dsl.istar.mm;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Conservative temporal compatibility for an explicitly typed goal refining
 * another explicitly typed goal.
 *
 * <p>The child contract must be at least strong enough to establish the
 * parent's temporal class. Untyped goals and tasks remain structural and are
 * intentionally outside this policy.</p>
 */
public final class GoalTypeRefinementPolicy {
    private static final Map<GoalType, Set<GoalType>> ALLOWED_CHILDREN = Map.of(
            GoalType.ACHIEVE, EnumSet.allOf(GoalType.class),
            GoalType.MAINTAIN, EnumSet.of(GoalType.MAINTAIN, GoalType.NONE),
            GoalType.SUSTAIN, EnumSet.of(GoalType.MAINTAIN, GoalType.SUSTAIN, GoalType.NONE),
            GoalType.NONE, EnumSet.allOf(GoalType.class));

    private GoalTypeRefinementPolicy() {}

    public static boolean permits(GoalType parent, GoalType child) {
        return ALLOWED_CHILDREN.get(parent).contains(child);
    }

    public static Set<GoalType> allowedChildren(GoalType parent) {
        return Set.copyOf(ALLOWED_CHILDREN.get(parent));
    }
}
