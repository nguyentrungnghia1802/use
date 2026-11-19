package org.tzi.use.plugins.jacamo.tool.main;

import org.tzi.use.plugins.jacamo.bridge.protocol.CheckpointProtocol;
import org.tzi.use.plugins.jacamo.session.SnapshotVerifier;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;

/** Evaluate the same recorded source states with another specification, without changing the producer. */
public final class ReplayMain {
    public static void main(String[] args)throws Exception {
        if(args.length!=5)throw new IllegalArgumentException("mapping.xml model.csl model.istar history.jsonl output.json");
        var events=new ArrayList<Map<String,Object>>();String last="NO_INPUT";long firstHold=0;
        try(var verifier=new SnapshotVerifier(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]));var reader=Files.newBufferedReader(Path.of(args[3]))) {
            String line;
            while((line=reader.readLine())!=null) {
                var record=CanonicalJson.object(CanonicalJson.decode(line.getBytes(StandardCharsets.UTF_8)));
                var request=CanonicalJson.object(record.get("request"));
                if (!CheckpointProtocol.supportsReplay(request.get("version"))) {
                    throw new IllegalArgumentException("REPLAY_UNSUPPORTED_TRACE_VERSION:" + request.get("version"));
                }
                var snapshot=CheckpointProtocol.decodeSnapshot(CanonicalJson.object(request.get("snapshot")));
                try {
                    var result=verifier.update(snapshot);last=result.decision();
                    events.add(summarize(result));
                }catch(Exception failure){last="HOLD_UNVERIFIED";events.add(Map.of("sequence",snapshot.sequence(),"decision",last,"diagnostic",failure.toString()));}
                if(!last.equals("APPROVE")){firstHold=snapshot.sequence();break;}
            }
        }
        var result=Map.of("evaluatedCheckpoints",events.size(),"decision",last,"firstHoldSequence",firstHold,"events",events,
                "scope","Recorded-state prefix only; does not predict later JaCaMo behavior after a changed decision");
        Files.writeString(Path.of(args[4]),new String(CanonicalJson.encode(result),StandardCharsets.UTF_8));
        System.out.println("REPLAY checkpoints="+events.size()+" decision="+last+" firstHold="+firstHold);
    }

    private static Map<String, Object> summarize(SnapshotVerifier.Result result) {
        var failedInvariants = result.checks().stream()
                .filter(c -> c.kind().equals("CSL_INVARIANT") && c.verdict().equals("FAIL"))
                .map(SnapshotVerifier.Check::id).toList();
        var violatedGoals = result.elements().stream()
                .filter(e -> e.status().equals("VIOLATED"))
                .map(SnapshotVerifier.ElementStatus::element).toList();
        var fulfilledElements = result.elements().stream()
                .filter(e -> e.status().equals("FULFILLED"))
                .map(e -> Map.of("context", e.context(), "actor", e.actor(), "element", e.element()))
                .toList();
        return Map.of("sequence", result.sequence(), "decision", result.decision(),
                "failedInvariants", failedInvariants, "violatedGoals", violatedGoals,
                "fulfilledElements", fulfilledElements);
    }
}
