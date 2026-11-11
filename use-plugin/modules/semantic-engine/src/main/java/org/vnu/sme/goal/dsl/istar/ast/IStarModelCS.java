package org.vnu.sme.goal.dsl.istar.ast;

import java.util.List;

/** Root AST node for an iStar 2.0 model (CS = Concrete Syntax). */
public record IStarModelCS(
        String            name,
        List<ActorDefCS>  actors,
        List<DependencyCS> dependencies
) {}
