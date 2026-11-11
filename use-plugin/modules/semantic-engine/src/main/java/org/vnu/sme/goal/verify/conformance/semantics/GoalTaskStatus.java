package org.vnu.sme.goal.verify.conformance.semantics;

/**
 * Status of a Goal/Task element in a Goal Model Marking — Definition 3.1
 * (Caballero-Villalobos): value in Delta x Delta, restricted to {(?,?), (T,F), (T,T)}.
 */
public enum GoalTaskStatus { UNKNOWN, PENDING, FULFILLED, VIOLATED }
