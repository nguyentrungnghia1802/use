package org.tzi.use.plugins.jacamo.semantics.mapping.auction;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram;
import org.tzi.use.semantic.engine.api.OwnedGoalModel;
import org.vnu.sme.goal.dsl.istar.mm.*;
import org.vnu.sme.goal.dsl.iscn.mm.ScenarioInstance;

/** Explicit case execution M1. The canonical original model is never changed.
 * Each committed player gets one distinct type/cast; universal D_bid is an
 * original AND over separate dependency receivers, not repeated assignment to
 * one depender (which would give the original algorithm last-writer credit). */
public final class AuctionGoalInstanceExpander {
    public static final String VERSION="AUCTION_COMMITTED_CAST/1.0";
    public record Trace(String expanded,String source,String actor,String player,String role,String kind) { }
    public record Expansion(OwnedGoalModel owned,List<ScenarioInstance> cast,Map<String,Trace> trace,AuctionOccurrenceBindingAdapter.Scope scope,String hash) {
        public Expansion {cast=List.copyOf(cast);trace=Map.copyOf(trace);}
        public GoalModel model(){return owned.model();}
    }
    private final SemanticProgram program;
    public AuctionGoalInstanceExpander(SemanticProgram program){
        this.program=program;var policy=program.casePlan().specification().root().child("verificationBindings").child("goalEvaluation");
        if(!policy.attr("perBidScope").equals("committed-Participant-enactment-in-this-scheme")||!policy.attr("aggregateBid").equals("all-relevant-committed-agents"))throw new IllegalArgumentException("AUCTION_EXPANSION_UNREVIEWED_QUANTIFICATION");
    }
    public Expansion expand(AuctionOccurrenceBindingAdapter.Scope scope) {
        program.assertUnchanged();if(!program.hash().equals(scope.program())||!scope.complete())throw new IllegalArgumentException("AUCTION_EXPANSION_REQUIRES_OWNED_COMPLETE_SCOPE");
        var policy=Map.of("D_bid",org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.Quantifier.ALL);
        var core=new org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander(program.goalModel(),program.profile().engine(),policy);
        var players=scope.actors().stream().map(p->new org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander.Player(p.sourceActor(),p.actorType(),p.instance(),p.player(),p.role(),p.roleInterval())).toList();
        var expanded=core.expand(players,program.goalModel().getName()+"_Execution_"+SemanticJson.hash(List.of(scope.scheme(),scope.artifact())).substring(0,24));
        var traces=new TreeMap<String,Trace>();expanded.trace().forEach((id,t)->traces.put(id,new Trace(t.expanded(),t.source(),t.actor(),t.player(),t.role(),t.kind().replace("ALL_DEPENDENCY","ALL_COMMITTED_DEPENDENCY"))));
        String hash=SemanticJson.hash(List.of(VERSION,program.goalHash(),scope.revision(),expanded.owned().hash(),SemanticJson.tree(expanded.cast()),SemanticJson.tree(traces)));
        program.assertUnchanged();return new Expansion(expanded.owned(),expanded.cast(),traces,scope,hash);
    }
}
