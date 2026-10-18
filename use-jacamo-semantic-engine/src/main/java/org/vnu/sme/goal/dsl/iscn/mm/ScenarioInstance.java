package org.vnu.sme.goal.dsl.iscn.mm;

/**
 * One concrete named object declared in the scenario — e.g. "amr : Participant",
 * "matilda : Organizer". The MM-level counterpart of a UML object: istar's mm has only
 * type-level classes (Actor, Role, Agent, Goal, Task, ...); this is the corresponding
 * instance-level object, addressable on its own rather than folded into the syntactic
 * grouping of an 'instance a, b : Type;' declaration.
 */
public record ScenarioInstance(String name, String actorType) {}
