package org.vnu.sme.goal.dsl.istar.mm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves which ActorType a Goal/Task's {@code self} is bound to: the actor block the
 * element is declared in, or -- when the element is the dependee endpoint of a {@code depend}
 * relation -- the dependee's own owner prepended to the depender's context, so an OCL clause
 * on the dependee can still refer back to the depender via {@code self.outer}.
 */
public final class ContextResolution {

    private ContextResolution() {}

    public static ContextResolution of(GoalModel gm) {
        return new ContextResolution();
    }

    /** The actor type name self should be bound to when compiling/evaluating this element's ocl
     *  clause: the element's owning actor. */
    public String actorTypeOf(GoalModel gm, String elementId) {
        return contextTypesOf(gm, elementId).get(0);
    }

    /**
     * Context stack for an element, from the innermost binding ({@code self}) outward.
     * A dependency transfers the depender element's complete stack to its dependee endpoint
     * (reachable as {@code self.outer}, {@code self.outer.outer}, ...); the composition-owned
     * dependum is a dependency label/object, not an actor-owned context.
     */
    public List<String> contextTypesOf(GoalModel gm, String elementId) {
        return contextTypesOf(gm, elementId, new LinkedHashSet<>());
    }

    private List<String> contextTypesOf(GoalModel gm, String elementId, Set<String> visiting) {
        if (!visiting.add(elementId)) return baseContextTypes(gm, elementId);
        for (var dependency : gm.getDependencies()) {
            boolean receivesContext = elementId.equals(dependency.dependeeElmt());
            if (!receivesContext || dependency.dependerElmt() == null) continue;
            List<String> upstream = contextTypesOf(gm, dependency.dependerElmt(), visiting);
            String owner = gm.ownerOf(elementId).orElse(null);
            if (owner != null) {
                List<String> transferred = new ArrayList<>();
                transferred.add(owner);
                for (String type : upstream) if (!transferred.contains(type)) transferred.add(type);
                return List.copyOf(transferred);
            }
        }
        return baseContextTypes(gm, elementId);
    }

    private List<String> baseContextTypes(GoalModel gm, String elementId) {
        String owner = gm.ownerOf(elementId).orElseThrow(() ->
                new IllegalStateException("element '" + elementId + "' has no owning actor"));
        return List.of(owner);
    }
}
