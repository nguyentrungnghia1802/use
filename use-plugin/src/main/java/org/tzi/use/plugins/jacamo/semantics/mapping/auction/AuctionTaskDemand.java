package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.plugins.jacamo.semantics.runtime.SemanticObservationMapper.Port;
import org.tzi.use.uml.sys.MObject;

/** Source-qualified demand/episode metadata on the existing monitor savepoint.
 * State enablement is distinct from its unavailable internal transition time. */
public final class AuctionTaskDemand {
    public record Read(Boolean required,String episode,String diagnostic,Map<String,String> witness) {public Read{witness=Map.copyOf(witness);}}
    private final SemanticProgram program;
    private final AuctionSchemeObservationDecoder decoder;
    public AuctionTaskDemand(SemanticProgram program){this.program=program;decoder=new AuctionSchemeObservationDecoder(program);}
    static void observe(Port port,MObject monitor,AuctionSchemeObservationDecoder.Cut source) {
        for(var goal:source.goals().values()) {
            String key="auctionTaskDemand:"+goal.name(),stored=port.metadata(monitor,key);
            var prior=stored.isBlank()?Map.<String,Object>of():json(stored);String oldState=Objects.toString(prior.get("state"),""),episode=Objects.toString(prior.get("episode"),"");
            if(!source.continuous()){port.metadata(monitor,key,encode(Map.of("state",goal.state(),"episode","","quality","UNAVAILABLE","sourceCut",source.hash())));continue;}
            if(goal.state().equals("ENABLED")&&!oldState.equals("ENABLED"))episode=SemanticJson.hash(List.of(source.scheme(),source.uuid(),goal.definition(),source.sequence()));
            else if(episode.isBlank()&&goal.publicationTimes().containsKey("ENABLED"))
                episode=SemanticJson.hash(List.of(source.scheme(),source.uuid(),goal.definition(),"OBSERVED_ENABLEMENT_PUBLICATION_IDENTITY",goal.publicationTimes().get("ENABLED")));
            port.metadata(monitor,key,encode(Map.of("state",goal.state(),"episode",episode,"quality","OBSERVED_SOURCE_STATE","sourceCut",source.hash())));
        }
    }
    public static Read read(SemanticProgram program,NativeRuntimeMutationEngine engine,AuctionOccurrenceBindingAdapter.Scope scope,AuctionOccurrenceBindingAdapter.Player actor,String task,AuctionArtifactBindingResolver.Run run) {
        return new AuctionTaskDemand(program).read(engine,scope,actor,task,run);
    }
    /** Reuse only frozen source preparation. Live evidence and epoch/hash guards
     * are still decoded at every accepted boundary. */
    public Read read(NativeRuntimeMutationEngine engine,AuctionOccurrenceBindingAdapter.Scope scope,AuctionOccurrenceBindingAdapter.Player actor,String task,AuctionArtifactBindingResolver.Run run) {
        var witness=new TreeMap<String,String>();witness.put("demandAuthority","EXACT_SCHEME_ENABLEMENT_AND_MISSION_COMMITMENT_NOT_ROLE_MEMBERSHIP");
        if(!scope.complete())return new Read(null,"","DEMAND_CAST_UNAVAILABLE",witness);
        var monitor=engine.objectForSemanticId(scope.id());if(monitor==null||!"true".equals(engine.metadata(monitor,"auctionMonitorLive")))return new Read(null,"","DEMAND_MONITOR_UNAVAILABLE",witness);
        var source=decoder.decode(json(engine.metadata(monitor,"auctionMonitorSource")),run.id(),run.generation(),run.modelRevision());
        String goalName=AuctionStepObservationDecoder.organizationalGoal(task);var goal=source.goal(goalName);var anchor=json(engine.metadata(monitor,"auctionTaskDemand:"+goalName));
        witness.put("demandGoal",goalName);witness.put("demandSourceCut",source.hash());witness.put("demandSourceState",goal.state());witness.put("demandPlayer",actor.name());witness.put("demandEpochAuthority","OBSERVED_SOURCE_ENABLEMENT_NOT_INTERNAL_TRANSITION_TIME");
        if(!source.continuous()||!source.hash().equals(anchor.get("sourceCut")))return new Read(null,"","DEMAND_SOURCE_HISTORY_UNAVAILABLE",witness);
        String episode=Objects.toString(anchor.get("episode"),"");witness.put("demandEpisode",episode);
        if(goal.state().equals("WAITING"))return new Read(false,episode,"GOAL_NOT_CURRENTLY_ENABLED",witness);
        if(episode.isBlank())return new Read(null,"","ACTUAL_GOAL_ENABLEMENT_UNPROVEN",witness);
        // Frozen first-satisfaction membership defines the all-committed cast;
        // current execution demand still requires this player's actual mission.
        var players=goal.committed();
        if(players==null)return new Read(null,episode,"DEMAND_COMMITMENT_POPULATION_UNAVAILABLE",witness);
        return new Read(players.contains(actor.name()),episode,"",witness);
    }
    private static Map<String,Object> json(String text){return CanonicalJson.object(CanonicalJson.decode(text.getBytes(StandardCharsets.UTF_8)));}
    private static String encode(Object value){return new String(CanonicalJson.encode(value),StandardCharsets.UTF_8);}
}
