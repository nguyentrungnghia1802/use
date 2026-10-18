package org.vnu.sme.goal.dsl.iscn.goalmodelinstance.mm;

import java.util.Map;

import org.vnu.sme.goal.dsl.istar.mm.Actor;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;
import org.vnu.sme.goal.dsl.istar.mm.IntentionalElement;

/**
 * Instance-level projection of a type-level i* {@link GoalModel}.
 *
 * <p>The contained {@code model} is still a normal {@link GoalModel} so the existing iStar
 * view and propagation code can use it unchanged. The metadata maps keep the real
 * instance-of relation that the view model ids alone cannot express.
 */
public record GoalModelInstance(GoalModel model,
                                Map<String, Actor> actorInstanceOf,
                                Map<String, IntentionalElement> elementInstanceOf) {
    public GoalModelInstance {
        actorInstanceOf = Map.copyOf(actorInstanceOf);
        elementInstanceOf = Map.copyOf(elementInstanceOf);
    }
}
