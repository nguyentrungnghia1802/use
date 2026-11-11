package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.Objects;

/**
 * Resolved actor runtime policy binding in the compiled observation plan.
 */
public record ActorRuntimeBinding(
        String actorName,
        TargetSymbol actorSymbol,
        String activationPolicy,
        String episodePolicy) {

    public ActorRuntimeBinding {
        Objects.requireNonNull(actorName, "actorName");
        Objects.requireNonNull(actorSymbol, "actorSymbol");
        activationPolicy = Objects.requireNonNullElse(activationPolicy, "EXPLICIT_ENACTMENT");
        episodePolicy = Objects.requireNonNullElse(episodePolicy, "STATIC_LIFETIME");
    }
}

