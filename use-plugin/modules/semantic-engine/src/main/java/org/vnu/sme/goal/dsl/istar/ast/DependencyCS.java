package org.vnu.sme.goal.dsl.istar.ast;

/**
 * Cross-actor dependency in concrete syntax: depender → dependum → dependee.
 * {@code dependerElmt}/{@code dependeeElmt} are the optional "boundary opening"
 * (the SR element inside that actor's boundary the dependency attaches to) —
 * null when the source text only names the actor (no {@code '.'IDENT} suffix).
 */
public record DependencyCS(
        String depender,
        String dependerElmt,
        String dependumKind,
        String dependum,
        String dependee,
        String dependeeElmt
) {}
