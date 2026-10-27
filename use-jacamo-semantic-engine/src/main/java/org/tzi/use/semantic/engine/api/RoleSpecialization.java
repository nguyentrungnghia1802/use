package org.tzi.use.semantic.engine.api;

import org.vnu.sme.goal.dsl.istar.mm.*;

/** Definition-only capability roles have no independent intentional occurrence.
 * Inherited mutable domain attributes stay in the same CSL role instance.
 * Nonempty intentional inheritance is deliberately outside this profile. */
public final class RoleSpecialization {
    private RoleSpecialization() { }
    public static boolean definitionOnly(GoalModel model,Actor actor) {
        return actor instanceof Role && actor.elements().isEmpty() && actor.refinements().isEmpty()
                && actor.neededBys().isEmpty() && actor.contributions().isEmpty() && actor.qualifications().isEmpty()
                && actor.associations().stream().allMatch(a->a.kind()==AssocKind.IS_A)
                && model.getActors().stream().flatMap(a->a.associations().stream()).anyMatch(a->a.kind()==AssocKind.IS_A&&a.target().equals(actor.name()));
    }
    public static boolean admitted(GoalModel model,Actor owner,Association relation) {
        return owner instanceof Role && relation.actor().equals(owner.name()) && relation.kind()==AssocKind.IS_A
                && model.findActor(relation.target()).filter(a->definitionOnly(model,a)).isPresent();
    }
}
