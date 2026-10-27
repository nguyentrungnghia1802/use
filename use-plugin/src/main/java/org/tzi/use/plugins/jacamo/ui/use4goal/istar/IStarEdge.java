package org.tzi.use.plugins.jacamo.ui.use4goal.istar;

import org.vnu.sme.goal.dsl.istar.mm.ContributionType;

/**
 * Adapter: one relationship edge between two {@link IStarNode} ids, already
 * resolved from the MM (e.g. Dependency's depender/dependee are resolved to
 * the actor's first element id here, not re-resolved at paint time).
 */
public record IStarEdge(String fromId, String toId, IStarEdgeKind kind, String label, ContributionType contribType) {}
