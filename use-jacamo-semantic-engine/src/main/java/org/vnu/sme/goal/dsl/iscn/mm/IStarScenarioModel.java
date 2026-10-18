package org.vnu.sme.goal.dsl.iscn.mm;

import java.util.List;

/**
 * Runtime MM for an i* Scenario: the concrete objects it declares (one {@link ScenarioInstance}
 * per name — "instance a, b : Type;" is just how they're written, each name is still its own
 * object here, the same way a class with 3 fields of the same type is 3 Field objects, not one
 * FieldGroup) plus an ordered statement script targeting one .istar model. This is the
 * un-executed "soil script" — {@code modelFile} is still the raw path text from the source
 * (resolved to an actual GoalModel by the compiler, not here: MM must not know about file I/O).
 */
public final class IStarScenarioModel {

    private final String                 name;
    private final String                 modelFile;
    private final List<ScenarioInstance> instances;
    private final List<ScenarioStmt>     statements;

    public IStarScenarioModel(String name, String modelFile,
                               List<ScenarioInstance> instances, List<ScenarioStmt> statements) {
        this.name       = name;
        this.modelFile  = modelFile;
        this.instances  = List.copyOf(instances);
        this.statements = List.copyOf(statements);
    }

    public String                 name()       { return name; }
    public String                 modelFile()  { return modelFile; }
    public List<ScenarioInstance> instances()  { return instances; }
    public List<ScenarioStmt>     statements() { return statements; }
}
