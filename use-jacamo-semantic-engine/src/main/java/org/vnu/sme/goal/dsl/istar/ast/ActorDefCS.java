package org.vnu.sme.goal.dsl.istar.ast;

import java.util.List;

/** Actor definition in concrete syntax. */
public record ActorDefCS(
        String              id,
        String              kind,     // "role" | "agent"
        List<ElementBodyCS> body
) {}
