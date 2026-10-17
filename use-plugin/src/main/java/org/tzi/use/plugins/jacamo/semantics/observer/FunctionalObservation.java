package org.tzi.use.plugins.jacamo.semantics.observer;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.jacamo.bridge.contract.semantic.MoiseSemanticContract.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** One COMPLETE public SchemeBoard cut. Counts are observations, not a replacement for NPL satisfaction. */
public record FunctionalObservation(String schemeIdentity,String declarationIdentity,String organizationIdentity,
        String name,String artifactUuid,OrganizationSemantic organization,SchemeSemantic definition,
        List<String> responsibleContexts,List<Commitment> commitments,List<Goal> goals,String cutHash) {
    public enum State { WAITING, ENABLED, SATISFIED }
    public record Commitment(String player,String mission) { }
    public record Goal(String definition,State state,Map<String,Object> arguments,List<String> committed,List<String> achieved) {
        public Goal {arguments=SemanticJson.object(arguments);committed=unique(committed);achieved=unique(achieved);}
    }
    public FunctionalObservation {responsibleContexts=unique(responsibleContexts);commitments=List.copyOf(commitments);goals=List.copyOf(goals);}
    public static String goalIdentity(String scheme,String goal,Map<String,Object> arguments) {
        return "functional-goal:"+SemanticJson.hash(List.of(scheme,goal,arguments));
    }
    public static String missionIdentity(String scheme,String mission){return "functional-mission:"+SemanticJson.hash(List.of(scheme,mission));}
    public static FunctionalObservation decode(BridgeEntityId runtime,Map<String,Object> data) {
        if(!"1.0.0".equals(data.get("goalObservationVersion")))throw new IllegalArgumentException("FUNCTIONAL_PUBLIC_OBSERVATION_VERSION_REQUIRED");
        if(!runtime.canonical().equals(text(data,"runtimeIdentity")))throw new IllegalArgumentException("FUNCTIONAL_BOARD_IDENTITY_MISMATCH");
        String uuid=text(data,"boardArtifactIdentity");
        if(!runtime.incarnation().endsWith(":"+uuid))throw new IllegalArgumentException("FUNCTIONAL_BOARD_UUID_MISMATCH");
        UUID.fromString(uuid);
        var os=SemanticContractCodec.organizationFromTree(data.get("organisationDefinition"));MoiseDomainProjection.validate(os);
        if(!os.metadata().semanticId().equals(text(data,"organisationSpecSemanticId")))throw new IllegalArgumentException("FUNCTIONAL_OS_IDENTITY_MISMATCH");
        var scheme=os.functionalSpecification().schemes().stream().filter(s->s.metadata().semanticId().equals(text(data,"schemeSpecSemanticId"))).findFirst().orElseThrow(()->new IllegalArgumentException("FUNCTIONAL_SPEC_UNRESOLVED"));
        var knownGoals=new HashSet<String>();scheme.goals().forEach(g->knownGoals.add(g.metadata().semanticId()));
        var goals=new ArrayList<Goal>();var seen=new HashSet<String>();
        for(Object raw:list(data,"goalStates")) {
            var map=CanonicalJson.object(raw);String goal=text(map,"goalSemanticId");
            if(!knownGoals.contains(goal) || !seen.add(goal))throw new IllegalArgumentException("FUNCTIONAL_GOAL_SCOPE_OR_DUPLICATE:"+goal);
            var arguments=CanonicalJson.object(map.get("arguments"));
            if(arguments.values().stream().anyMatch(v->!(v instanceof String)))throw new IllegalArgumentException("FUNCTIONAL_RAW_TERM_ARGUMENT_REQUIRED");
            goals.add(new Goal(goal,State.valueOf(text(map,"state")),arguments,strings(map,"committedAgentSemanticIds"),strings(map,"achievedAgentSemanticIds")));
        }
        if(!seen.equals(knownGoals))throw new IllegalArgumentException("FUNCTIONAL_GOAL_POPULATION_INCOMPLETE");
        var arguments=CanonicalJson.object(data.get("schemeArguments"));
        for(var goal:scheme.goals()) {
            Object supplied=arguments.getOrDefault(goal.goalId(),Map.of());var observed=goals.stream().filter(g->g.definition().equals(goal.metadata().semanticId())).findFirst().orElseThrow();
            if(!SemanticJson.hash(supplied).equals(SemanticJson.hash(observed.arguments())))throw new IllegalArgumentException("FUNCTIONAL_ARGUMENT_CUT_MISMATCH");
        }
        if(!scheme.goals().stream().map(OrganizationalGoalSemantic::goalId).toList().containsAll(arguments.keySet()))throw new IllegalArgumentException("FUNCTIONAL_UNKNOWN_ARGUMENT_GOAL");
        var commitments=new ArrayList<Commitment>();var pairs=new HashSet<Commitment>();
        for(Object raw:list(data,"commitments")) {
            var map=CanonicalJson.object(raw);var pair=new Commitment(text(map,"agentSemanticId"),text(map,"missionSemanticId"));
            if(scheme.missions().stream().noneMatch(m->m.metadata().semanticId().equals(pair.mission())) || !pairs.add(pair))throw new IllegalArgumentException("FUNCTIONAL_COMMITMENT_SCOPE_OR_DUPLICATE");
            commitments.add(pair);
        }
        return new FunctionalObservation(runtime.canonical(),text(data,"semanticId"),text(data,"organisationSemanticId"),text(data,"name"),uuid,os,scheme,
            strings(data,"responsibleGroupSemanticIds"),commitments,goals,SemanticJson.hash(data));
    }
    private static List<String> unique(List<String> values){var sorted=new TreeSet<>(values);if(sorted.size()!=values.size())throw new IllegalArgumentException("FUNCTIONAL_DUPLICATED_ID");return List.copyOf(sorted);}
    private static String text(Map<String,Object> map,String key){if(!(map.get(key) instanceof String value)||value.isBlank())throw new IllegalArgumentException("FUNCTIONAL_REQUIRED_TEXT:"+key);return value;}
    private static List<?> list(Map<String,Object> map,String key){if(!(map.get(key) instanceof List<?> value))throw new IllegalArgumentException("FUNCTIONAL_REQUIRED_LIST:"+key);return value;}
    private static List<String> strings(Map<String,Object> map,String key){return list(map,key).stream().map(v->{if(!(v instanceof String s)||s.isBlank())throw new IllegalArgumentException("FUNCTIONAL_REQUIRED_ID:"+key);return s;}).toList();}
}
