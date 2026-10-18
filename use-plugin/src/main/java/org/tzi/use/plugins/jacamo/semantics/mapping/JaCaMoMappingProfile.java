package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.tzi.use.semantic.engine.api.EngineSemanticsProfile;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Frozen mapping policy, distinct from engine semantics and independent of transport/UI. */
public record JaCaMoMappingProfile(String id,String version,EngineSemanticsProfile engine,Set<String> capabilities) {
    public JaCaMoMappingProfile {
        Objects.requireNonNull(id);Objects.requireNonNull(version);Objects.requireNonNull(engine);capabilities=Collections.unmodifiableSet(new TreeSet<>(capabilities));
        if(id.isBlank()||version.isBlank())throw new IllegalArgumentException("MAPPING_PROFILE_ID_REQUIRED");
    }
    public static JaCaMoMappingProfile observedV1(){return new JaCaMoMappingProfile("JACAMO_MAPPING_V1","1.0",EngineSemanticsProfile.USE4GOAL_OBSERVED_V1,
            Set.of("csl.v4","istar.original","ocl.native75","authored.domain-view","source.complement"));}
    public String hash(){return SemanticJson.hash(SemanticJson.tree(this));}
}
