package org.tzi.use.plugins.jacamo.semantics.observer;

import java.util.*;
import org.jacamo.bridge.contract.*;

/** Clock continuation on the projector's existing rollback-owned proof index. No cache/state owner. */
public final class SourceClockCut {
    private SourceClockCut(){ }
    public static void validateContinuation(Map<String,RuntimeFact> before,RuntimeEvent event) {
        if(!VerificationObservation.present(event))return;var observation=VerificationObservation.require(event);
        var old=before.get(event.entityId().canonical());
        if(observation.kind()==VerificationObservation.Kind.CLOCK&&old!=null)
            SourceClock.requireContinuation(VerificationObservation.require(previousEvent(old,event)),observation);
    }
    public static void validateContinuation(Map<String,RuntimeFact> before,RuntimeSnapshot cut,String run,long generation) {
        for(var fact:cut.facts())if(fact.values().containsKey(VerificationObservation.KEY)) {
            var next=VerificationObservation.require(fact,cut,run,generation);var old=before.get(fact.id().canonical());
            if(next.kind()==VerificationObservation.Kind.CLOCK&&old!=null)SourceClock.requireContinuation(VerificationObservation.require(old,cut,run,generation),next);
        }
    }
    private static RuntimeEvent previousEvent(RuntimeFact fact,RuntimeEvent current) {
        var scope=CanonicalJson.object(CanonicalJson.object(fact.values().get(VerificationObservation.KEY)).get("scope"));long sequence=((Number)scope.get("sourceSequence")).longValue();
        return new RuntimeEvent("prior-clock:"+sequence,current.sessionId(),current.generation(),current.modelRevision(),"observer",VerificationObservation.SOURCE_ID,sequence,current.observedAt(),RuntimeEventKind.CHANGED,
            fact.kind(),fact.projectionStatus(),fact.id(),null,"","",Map.of(),fact.values(),new SourceWatermark(VerificationObservation.SOURCE_ID,sequence),fact.completeness(),fact.evidence());
    }
}
