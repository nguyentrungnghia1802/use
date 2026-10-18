package org.vnu.sme.goal.dsl.iscn.mm;

/** One statement inside a scenario body: fires/assigns an element's state, or asserts a cross-instance property. */
public sealed interface ScenarioStmt permits ScenarioStmt.Fire, ScenarioStmt.Assign, ScenarioStmt.Aggregate {

    /** {@code instanceId} is null for the legacy/default (no 'instance' declared) form. */
    record Fire(String instanceId, String elementId, String objectInstanceId) implements ScenarioStmt {}

    /** {@code statusValue} is still raw text here — Goal/Task vs. Quality isn't known until resolved against the model. */
    record Assign(String instanceId, String elementId, String statusValue) implements ScenarioStmt {}

    /** {@code actorType} (null if omitted) restricts the check to only that actor type's declared instances. */
    record Aggregate(String label, AggregateMode mode, String actorType, String elementId) implements ScenarioStmt {}
}
