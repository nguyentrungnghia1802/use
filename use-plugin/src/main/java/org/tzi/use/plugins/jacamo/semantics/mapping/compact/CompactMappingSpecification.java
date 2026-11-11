package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;

/**
 * Immutable specification of a compact source-target mapping (v3).
 * Namespace: urn:use:jacamo-mapping:3
 */
public record CompactMappingSpecification(
        String id,
        String sha256,
        List<Correspondence> cslMappings,
        List<Correspondence> istarMappings,
        List<ActorRuntime> actorRuntimes,
        List<Requirement> requirements,
        List<DependencyPopulation> dependencyPopulations,
        List<GoalRealization> goalRealizations,
        List<VerificationRequirement> verificationRequirements,
        Milestones milestones,
        Location location) {

    public static final String NAMESPACE = "urn:use:jacamo-mapping:3";
    public static final String VERSION = "3.0";
    public CompactMappingSpecification(String id,String sha256,List<Correspondence> csl,List<Correspondence> istar,List<ActorRuntime> actors,List<Requirement> requirements,
            List<DependencyPopulation> populations,List<GoalRealization> realizations,Milestones milestones,Location location) {
        this(id,sha256,csl,istar,actors,requirements,populations,realizations,List.of(),milestones,location);
    }

    public static final Set<String> VALID_ACTIVATION_POLICIES = Set.of(
            "EXPLICIT_ENACTMENT",
            "ROLE_ADOPTION",
            "MODEL_ONLY_CAPABILITY_ROLE",
            "OBSERVED_SCHEME_COMMITMENT",
            "PROJECT_EXECUTION_VIEW",
            "AGENT_ENACTMENT"
    );

    public static final Set<String> VALID_EPISODE_POLICIES = Set.of(
            "STATIC_LIFETIME",
            "DYNAMIC_EPISODE",
            "MODEL_REVISION",
            "RUN_SCHEME_PLAYER_INVOCATION",
            "SCHEME_EPISODE"
    );

    public static final Set<String> VALID_QUANTIFIERS = Set.of(
            "ALL",
            "ANY",
            "SAME_PLAYER"
    );

    public static final Set<String> VALID_MILESTONE_KINDS = Set.of(
            "DOMAIN",
            "CHECKPOINT",
            "ALL"
    );

    public CompactMappingSpecification(
            String id,
            String sha256,
            List<Correspondence> cslMappings,
            List<Correspondence> istarMappings,
            List<ActorRuntime> actorRuntimes,
            List<Requirement> requirements,
            Milestones milestones,
            Location location) {
        this(id, sha256, cslMappings, istarMappings, actorRuntimes, requirements, List.of(), List.of(), milestones, location);
    }

    public String version() { return VERSION; }

    public CompactMappingSpecification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sha256, "sha256");
        cslMappings = List.copyOf(cslMappings == null ? List.of() : cslMappings);
        istarMappings = List.copyOf(istarMappings == null ? List.of() : istarMappings);
        actorRuntimes = List.copyOf(actorRuntimes == null ? List.of() : actorRuntimes);
        requirements = List.copyOf(requirements == null ? List.of() : requirements);
        dependencyPopulations = List.copyOf(dependencyPopulations == null ? List.of() : dependencyPopulations);
        goalRealizations = List.copyOf(goalRealizations == null ? List.of() : goalRealizations);
        verificationRequirements=List.copyOf(verificationRequirements==null?List.of():verificationRequirements);
        Objects.requireNonNull(location, "location");
    }

    public record Location(String sourceName, int line, int column) {
        public Location {
            sourceName = Objects.requireNonNullElse(sourceName, "unknown");
        }
        @Override
        public String toString() {
            return sourceName + ":" + line + ":" + column;
        }
        public IllegalArgumentException error(String message) {
            return new IllegalArgumentException("COMPACT_MAPPING:" + this + ":" + message);
        }
    }

    public record Correspondence(String source, String target, Map<String, String> attributes, Location location) {
        public Correspondence(String source, String target, Location location) {
            this(source, target, Map.of(), location);
        }
        public Correspondence {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
            Objects.requireNonNull(location, "location");
            if (source.isBlank()) throw location.error("SOURCE_EMPTY");
            if (target.isBlank()) throw location.error("TARGET_EMPTY");
        }
    }

    public record ActorRuntime(String actor, String activationPolicy, String episodePolicy, Map<String, String> attributes, Location location) {
        public ActorRuntime(String actor, String activationPolicy, String episodePolicy, Location location) {
            this(actor, activationPolicy, episodePolicy, Map.of(), location);
        }
        public ActorRuntime {
            Objects.requireNonNull(actor, "actor");
            activationPolicy = Objects.requireNonNullElse(activationPolicy, "EXPLICIT_ENACTMENT").trim();
            episodePolicy = Objects.requireNonNullElse(episodePolicy, "STATIC_LIFETIME").trim();
            attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
            Objects.requireNonNull(location, "location");
            if (actor.isBlank()) throw location.error("ACTOR_RUNTIME_EMPTY");
            if (!VALID_ACTIVATION_POLICIES.contains(activationPolicy)) {
                throw location.error("INVALID_ACTIVATION_POLICY: '" + activationPolicy + "'");
            }
            if (!VALID_EPISODE_POLICIES.contains(episodePolicy)) {
                throw location.error("INVALID_EPISODE_POLICY: '" + episodePolicy + "'");
            }
        }
    }

    public record DependencyPopulation(String target, String quantifier, Location location) {
        public DependencyPopulation {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(quantifier, "quantifier");
            Objects.requireNonNull(location, "location");
            if (target.isBlank()) throw location.error("DEPENDENCY_TARGET_EMPTY");
            if (!VALID_QUANTIFIERS.contains(quantifier.trim())) {
                throw location.error("INVALID_DEPENDENCY_QUANTIFIER: '" + quantifier + "'");
            }
            quantifier = quantifier.trim();
        }
    }

    public record GoalRealization(String target, String source, String sourceGoal, String sourceRef, String scheme, String policy, String reason, Location location) {
        public GoalRealization {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(source, "source");
            policy = (policy == null || policy.isBlank()) ? "SAME_SCOPE_PLAYER_REFINEMENT_VIEW/1.0" : policy.trim();
            reason = (reason == null || reason.isBlank()) ? "Mapped same-scope player refinement view" : reason.trim();
            sourceGoal = Objects.requireNonNullElse(sourceGoal, "");
            sourceRef = Objects.requireNonNullElse(sourceRef, "");
            scheme = Objects.requireNonNullElse(scheme, "");
            Objects.requireNonNull(location, "location");
            if (target.isBlank() || source.isBlank()) throw location.error("GOAL_REALIZATION_ENDPOINT_EMPTY");
        }
    }

    public record Requirement(String id, String provider, boolean required, Map<String, Object> parameters, Location location) {
        public Requirement {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(provider, "provider");
            parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
            Objects.requireNonNull(location, "location");
            if (id.isBlank()) throw location.error("REQUIREMENT_ID_EMPTY");
            if (provider.isBlank()) throw location.error("REQUIREMENT_PROVIDER_EMPTY");
        }
    }
    public record VerificationRequirement(String target,String scope,Location location) {
        public VerificationRequirement {Objects.requireNonNull(target);Objects.requireNonNull(location);scope=Objects.requireNonNullElse(scope,"");if(target.isBlank())throw location.error("VERIFICATION_TARGET_REQUIRED");}
    }

    public record Milestones(String kind, Location location) {
        public Milestones {
            kind = Objects.requireNonNullElse(kind, "DOMAIN").trim();
            Objects.requireNonNull(location, "location");
            if (!VALID_MILESTONE_KINDS.contains(kind)) {
                throw location.error("INVALID_MILESTONE_KIND: '" + kind + "'");
            }
        }
    }
}

