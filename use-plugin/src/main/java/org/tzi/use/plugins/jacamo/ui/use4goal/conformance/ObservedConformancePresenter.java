package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.JaCaMoFacade;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationSnapshot;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Evaluation;

/** Presentation adapter only. It never creates or changes a model, state or engine. */
public final class ObservedConformancePresenter {
    private final JaCaMoFacade facade;
    public ObservedConformancePresenter(JaCaMoFacade facade) { this.facade = Objects.requireNonNull(facade); }

    public List<Cut> capture() {
        // This immutable snapshot is the ownership anchor. History may advance
        // concurrently, so do not join evidence from a different epoch/revision.
        var snapshot = facade.verificationSnapshot();
        var latest = snapshot.result();
        var results = new LinkedHashMap<String, RuntimeVerificationResult>();
        for (var result : facade.runtimeVerificationHistory()) {
            if (latest == null || sameOwner(latest, result) && result.stateVersion() <= latest.stateVersion())
                results.put(boundaryKey(result), result);
        }
        if (latest != null) results.put(boundaryKey(latest), latest);
        var images=new HashMap<String,VerificationSnapshot.StateImage>();
        var checkpoints=new HashMap<String,String>();
        for(var retained:facade.retainedVerificationSnapshots()){rememberImage(images,retained);rememberCheckpoint(checkpoints,retained);}
        rememberImage(images,snapshot);
        rememberCheckpoint(checkpoints,snapshot);
        var cuts=new ArrayList<Cut>();String previousHash="";
        for(var result:results.values()){
            String kind=checkpoints.getOrDefault(result.resultHash(),"HISTORY_RECORD");
            String category=Set.of("OPERATION_PRE","OPERATION_POST","TASK_PRE","TASK_POST").contains(kind)
                    ?(result.stateHash().equals(previousHash)?"Execution-only boundary":"Execution and state boundary"):
                    result.stateHash().equals(previousHash)?"Evidence boundary (state unchanged)":"State boundary";
            cuts.add(cut(result,images.get(result.resultHash()),category+" / "+kind));previousHash=result.stateHash();
        }
        return List.copyOf(cuts);
    }
    private static String boundaryKey(RuntimeVerificationResult result){return result.sessionId()+":"+result.generation()+":"+result.modelRevision()+":"+result.stateVersion()+":"+result.eventId()+":"+result.checkpointId();}
    private static void rememberCheckpoint(Map<String,String> checkpoints,VerificationSnapshot cut){
        if(cut!=null&&cut.result()!=null&&cut.metadata()!=null)checkpoints.put(cut.result().resultHash(),cut.metadata().checkpoint().name());
    }
    private static void rememberImage(Map<String,VerificationSnapshot.StateImage> images,VerificationSnapshot cut) {
        if(cut!=null&&cut.result()!=null&&cut.image()!=null&&cut.currentVersion()==cut.result().stateVersion())
            images.put(cut.result().resultHash(),cut.image());
    }

    private static boolean sameOwner(RuntimeVerificationResult a, RuntimeVerificationResult b) {
        return a.sessionId().equals(b.sessionId()) && a.generation() == b.generation()
                && a.modelRevision().equals(b.modelRevision());
    }

    private static Cut cut(RuntimeVerificationResult result, VerificationSnapshot.StateImage image,String category) {
        var rows = new ArrayList<Evaluation>();
        boolean failed = false;
        if (result.semantic() != null) {
            var semantic = result.semantic();
            if (semantic.observation().get("originalEngine") instanceof Map<?,?> raw) {
                var core = CanonicalJson.object(raw);
                for (Object item : (List<?>) core.getOrDefault("contexts", List.of())) {
                    var context = CanonicalJson.object(item);
                    var occurrences = CanonicalJson.object(context.getOrDefault("occurrences", Map.of()));
                    for (var entry : new TreeMap<>(occurrences).entrySet()) {
                        var occurrence = CanonicalJson.object(entry.getValue());
                        String quality = Objects.toString(core.getOrDefault("qualityOverlay", occurrence.getOrDefault("quality", "UNAVAILABLE")));
                        String propagated = Objects.toString(occurrence.getOrDefault("aggregateStatus", "UNAVAILABLE"));
                        boolean known = quality.equals("KNOWN") && "OBSERVED_PREFIX".equals(core.get("historyQuality"))
                                && "PINNED_NATIVE_CUT".equals(core.get("evaluated"));
                        String qualified = known ? propagated : "UNKNOWN";
                        failed |= qualified.equals("VIOLATED");
                        String identity = "context=" + context.getOrDefault("context", "UNAVAILABLE")
                                + "; occurrence=" + entry.getKey() + "; player=" + occurrence.getOrDefault("player", "UNAVAILABLE")
                                + "; role=" + occurrence.getOrDefault("role", "UNAVAILABLE")
                                + "; raw propagated=" + propagated;
                        rows.add(new Evaluation(Objects.toString(occurrence.getOrDefault("sourceElement", entry.getKey())),
                                Objects.toString(occurrence.getOrDefault("leafStatus", "UNAVAILABLE")), qualified, quality, identity));
                    }
                }
            }
            for (var constraint : semantic.constraints()) {
                failed |= constraint.verdict() == org.tzi.use.plugins.jacamo.semantics.evidence.SemanticVerdict.FAIL;
                rows.add(new Evaluation(constraint.id(), constraint.backendValue(), constraint.verdict().name(),
                        constraint.diagnostic(), constraint.contextObject() + " / " + constraint.invocation() + " / " + constraint.witness()));
            }
        }
        for (var outcome : result.outcomes()) {
            failed |= outcome.outcome() == org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL;
            rows.add(new Evaluation(outcome.constraintId(), "native OCL", outcome.outcome().name(),
                    outcome.diagnostic(), outcome.contextObject()));
        }
        String state = "Accepted state hash: " + result.stateHash() + "\nState image not retained for this cut.";
        if(image!=null)state=new String(CanonicalJson.encode(image.toMap()),StandardCharsets.UTF_8);
        String trace = category+"\nObserved prefix | state " + result.stateVersion() + " | event " + result.eventId()
                + " | source " + result.sourceId() + ":" + result.sourceSequence()
                + " | checkpoint " + result.checkpointId() + "\n" + result.diagnostic();
        return new Cut(boundaryKey(result), trace+"\nResult hash: "+result.resultHash(), state, rows, result.coverage(), result.freshness(), failed);
    }
}
