package org.tzi.use.plugins.jacamo.semantics.istar;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict;
import static org.tzi.use.plugins.jacamo.semantics.istar.IntentionalMarking.Status.*;
import static org.tzi.use.plugins.jacamo.semantics.istar.IntentionalMarking.Lifecycle.*;

/** Versioned recurrence on accepted observed boundaries; no JaCaMo scheduling or liveness proof is inferred. */
public final class TemporalGoalMonitor {
    public record Input(SemanticVerdict activation, SemanticVerdict condition, String bindingToken, String diagnostic) {
        public Input {Objects.requireNonNull(activation);Objects.requireNonNull(condition);bindingToken=Objects.requireNonNullElse(bindingToken,"");diagnostic=Objects.requireNonNullElse(diagnostic,"");}
    }
    private record Episode(IntentionalMarking marking,long activationNumber) { }
    public record Snapshot(long boundary,Map<String,Episode> episodes,Map<String,IntentionalMarking> output,Map<String,Input> inputs,boolean lost) { }
    private final RequirementModel model;
    private Map<String,Episode> episodes=new LinkedHashMap<>();
    private Map<String,IntentionalMarking> output=Map.of();
    private long boundary=-1;
    private Map<String,Input> lastInputs=Map.of();private boolean lastLost;
    public TemporalGoalMonitor(RequirementModel model) {this.model=model;}
    public Snapshot savepoint() {return new Snapshot(boundary,Map.copyOf(episodes),output,lastInputs,lastLost);}
    public void restore(Snapshot image) {boundary=image.boundary();episodes=new LinkedHashMap<>(image.episodes());output=image.output();lastInputs=image.inputs();lastLost=image.lost();}
    public void clear() {episodes.clear();output=Map.of();boundary=-1;lastInputs=Map.of();lastLost=false;}
    public Map<String,IntentionalMarking> evaluate(long nextBoundary,Map<String,Input> observations,boolean historyLost) {
        if(nextBoundary==boundary && observations.equals(lastInputs) && historyLost==lastLost)return output;
        if(nextBoundary<boundary)throw new IllegalArgumentException("TEMPORAL_BOUNDARY_NOT_MONOTONIC");
        var nodes=new HashMap<String,RequirementModel.Node>();model.nodes().forEach(n->nodes.put(n.id(),n));
        var next=new LinkedHashMap<String,IntentionalMarking>();
        for(var node:model.nodes())evaluate(node,nodes,observations,next,nextBoundary,historyLost);
        boundary=nextBoundary;lastInputs=Map.copyOf(observations);lastLost=historyLost;output=Collections.unmodifiableMap(next);return output;
    }
    private IntentionalMarking evaluate(RequirementModel.Node node,Map<String,RequirementModel.Node> nodes,Map<String,Input> inputs,
            Map<String,IntentionalMarking> next,long version,boolean lost) {
        if(next.containsKey(node.id()))return next.get(node.id());
        var input=inputs.getOrDefault(node.id(),new Input(SemanticVerdict.SKIPPED,SemanticVerdict.SKIPPED,"","REQUIREMENT_OBSERVATION_UNAVAILABLE"));
        var raw=truth(input.condition());var quality=input.condition();
        if(!node.children().isEmpty()) {
            var children=node.children().stream().map(id->evaluate(nodes.get(id),nodes,inputs,next,version,lost)).toList();
            raw=node.refinement()==RequirementModel.Refinement.AND?and(children.stream().map(IntentionalMarking::evaluated).toList()):or(children.stream().map(IntentionalMarking::evaluated).toList());
            quality=raw==SATISFIED?SemanticVerdict.PASS:raw==VIOLATED?SemanticVerdict.FAIL:SemanticVerdict.INCONCLUSIVE;
        } else if(!node.dependency().isBlank()) {
            var dependee=evaluate(nodes.get(node.dependency()),nodes,inputs,next,version,lost);raw=dependee.evaluated();quality=dependee.quality();
        }
        var previous=episodes.get(node.id());
        boolean same=previous!=null && previous.marking().bindingToken().equals(input.bindingToken());
        var old=same?previous.marking():null;
        long activation=same?previous.activationNumber():0;
        if(input.bindingToken().isBlank() || input.activation()!=SemanticVerdict.PASS && input.activation()!=SemanticVerdict.FAIL) {
            var unknown=new IntentionalMarking(node.id(),UNDETERMINED,UNDETERMINED,UNKNOWN,input.activation(),
                old==null?node.binding().episodeId():old.episodeIdentity(),input.bindingToken(),-1,-1,true,input.diagnostic());
            // Do not associate an old latch with an unconfirmed context. Retain it internally for a confirmed same binding only.
            if(previous!=null) {var retained=previous.marking();episodes.put(node.id(),new Episode(new IntentionalMarking(retained.occurrenceId(),retained.raw(),retained.evaluated(),retained.lifecycle(),retained.quality(),retained.episodeIdentity(),retained.bindingToken(),retained.firstSatisfied(),retained.firstViolated(),true,retained.diagnostic()),previous.activationNumber()));}
            next.put(node.id(),unknown);return unknown;
        }
        if(input.activation()==SemanticVerdict.FAIL) {
            var inactive=new IntentionalMarking(node.id(),UNDETERMINED,UNDETERMINED,INACTIVE,SemanticVerdict.SKIPPED,
                old==null?node.binding().episodeId():old.episodeIdentity(),input.bindingToken(),old==null?-1:old.firstSatisfied(),old==null?-1:old.firstViolated(),old!=null && old.historyGap(),"ACTIVATION_FALSE");
            episodes.put(node.id(),new Episode(inactive,activation));next.put(node.id(),inactive);return inactive;
        }
        boolean fresh=old==null || old.lifecycle()!=ACTIVE;
        if(fresh){old=null;activation++;}
        String episode=node.binding().episodeId()+":"+activation;
        long satisfied=old==null?-1:old.firstSatisfied(),violated=old==null?-1:old.firstViolated();
        boolean gap=(old!=null && old.historyGap()) || lost || raw==UNDETERMINED;
        IntentionalMarking.Status evaluated;
        if(node.kind()==RequirementModel.Kind.TASK) {
            evaluated=raw; satisfied=raw==SATISFIED?version:-1;violated=raw==VIOLATED?version:-1;gap=raw==UNDETERMINED;
        } else switch(node.temporal()) {
            case NONE -> {evaluated=raw;satisfied=raw==SATISFIED?version:-1;violated=raw==VIOLATED?version:-1;gap=raw==UNDETERMINED;}
            case ACHIEVE -> {if(satisfied<0 && raw==SATISFIED)satisfied=version;evaluated=satisfied>=0?SATISFIED:UNDETERMINED;}
            case MAINTAIN -> {
                if(violated<0 && raw==VIOLATED)violated=version;
                evaluated=violated>=0?VIOLATED:raw==SATISFIED && !gap?SATISFIED:UNDETERMINED;
                if(satisfied<0 && evaluated==SATISFIED)satisfied=version;
            }
            case SUSTAIN -> {
                if(satisfied<0 && raw==SATISFIED)satisfied=version;
                if(satisfied>=0 && violated<0 && raw==VIOLATED)violated=version;
                evaluated=violated>=0?VIOLATED:satisfied>=0 && raw==SATISFIED && !gap?SATISFIED:UNDETERMINED;
            }
            default -> throw new IllegalArgumentException("TEMPORAL_TYPE_UNSUPPORTED");
        }
        if((node.temporal()==RequirementModel.Temporal.MAINTAIN || node.temporal()==RequirementModel.Temporal.SUSTAIN) && gap && evaluated==UNDETERMINED)quality=SemanticVerdict.INCONCLUSIVE;
        var marking=new IntentionalMarking(node.id(),raw,evaluated,ACTIVE,quality,episode,input.bindingToken(),satisfied,violated,gap,input.diagnostic());
        episodes.put(node.id(),new Episode(marking,activation));next.put(node.id(),marking);return marking;
    }
    public static IntentionalMarking.Status truth(SemanticVerdict v) {return v==SemanticVerdict.PASS?SATISFIED:v==SemanticVerdict.FAIL?VIOLATED:UNDETERMINED;}
    public static IntentionalMarking.Status and(List<IntentionalMarking.Status> values) {if(values.isEmpty())throw new IllegalArgumentException("EMPTY_REFINEMENT");return values.contains(VIOLATED)?VIOLATED:values.stream().allMatch(v->v==SATISFIED)?SATISFIED:UNDETERMINED;}
    public static IntentionalMarking.Status or(List<IntentionalMarking.Status> values) {if(values.isEmpty())throw new IllegalArgumentException("EMPTY_REFINEMENT");return values.contains(SATISFIED)?SATISFIED:values.stream().allMatch(v->v==VIOLATED)?VIOLATED:UNDETERMINED;}
}
