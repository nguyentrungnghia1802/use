package org.tzi.use.plugins.jacamo.semantics.exploration;

import java.util.*;

/** Matches a bounded abstract witness to supplied complete observation vectors; never creates a live JaCaMo run. */
public final class WitnessConcretizer {
    public enum Status{OBSERVED_VALUE_TRACE_MATCH,REJECTED_SPURIOUS,INCONCLUSIVE}
    public record ObservedStep(String sourceId,String actor,TransitionProfile.State before,TransitionProfile.State after,boolean complete,String observationDigest) {
        public ObservedStep{Objects.requireNonNull(sourceId);Objects.requireNonNull(actor);Objects.requireNonNull(before);Objects.requireNonNull(after);if(observationDigest==null || !observationDigest.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("CONCRETE_OBSERVATION_DIGEST_REQUIRED");}
    }
    public record Result(Status status,String scope,String diagnostic,List<String> observationDigests){public Result{observationDigests=List.copyOf(observationDigests);}}
    public Result match(TransitionProfile profile,ConformanceSummary.Witness witness,List<ObservedStep> observed) {
        String scope="SUPPLIED_OBSERVATION_VECTORS_ONLY_NOT_ALL_JACAMO_BEHAVIOR";var digests=observed.stream().map(ObservedStep::observationDigest).toList();
        if(observed.size()!=witness.transitions().size() || observed.stream().anyMatch(s->!s.complete()))return new Result(Status.INCONCLUSIVE,scope,"TRACE_LENGTH_OR_COVERAGE_INCOMPLETE",digests);
        String previous=null;
        for(int i=0;i<witness.transitions().size();i++) {String id=witness.transitions().get(i);var edge=profile.transitions().stream().filter(t->t.id().equals(id)).findFirst().orElse(null);var step=observed.get(i);
            if(edge==null || i==0 && !profile.initial().contains(edge.from()) || previous!=null && !previous.equals(edge.from()) || !edge.sourceId().equals(step.sourceId()) || !edge.actor().equals(step.actor()) || !profile.states().get(edge.from()).equals(step.before()) || !profile.states().get(edge.to()).equals(step.after()))return new Result(Status.REJECTED_SPURIOUS,scope,"SOURCE_ACTOR_OR_FULL_STATE_VECTOR_MISMATCH_AT:"+i,digests);
            previous=edge.to();
        }
        if(previous==null || !previous.equals(witness.state()) || !profile.states().get(previous).equals(witness.values()))return new Result(Status.INCONCLUSIVE,scope,"NO_NONEMPTY_MATCHING_WITNESS",digests);
        return new Result(Status.OBSERVED_VALUE_TRACE_MATCH,scope,"COMPLETE_SUPPLIED_TRACE_MATCHES_ABSTRACT_WITNESS",digests);
    }
}
