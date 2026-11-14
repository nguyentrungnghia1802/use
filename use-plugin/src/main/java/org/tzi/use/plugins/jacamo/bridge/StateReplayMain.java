package org.tzi.use.plugins.jacamo.bridge;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;

/** Evaluate the same recorded source states with another specification, without changing the producer. */
public final class StateReplayMain {
    public static void main(String[] args)throws Exception {
        if(args.length!=5)throw new IllegalArgumentException("mapping.xml model.csl model.istar history.jsonl output.json");
        var events=new ArrayList<Map<String,Object>>();String last="NO_INPUT";long firstHold=0;
        try(var verifier=new CslStateVerifier(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]));var reader=Files.newBufferedReader(Path.of(args[3]))) {
            String line;
            while((line=reader.readLine())!=null) {
                var record=CanonicalJson.object(CanonicalJson.decode(line.getBytes(StandardCharsets.UTF_8)));
                var request=CanonicalJson.object(record.get("request"));
                if(!StateWire.VERSION.equals(request.get("version")))throw new IllegalArgumentException("REPLAY_REQUIRES_CURRENT_TRACE_VERSION:"+StateWire.VERSION);
                var snapshot=StateWire.snapshot(CanonicalJson.object(request.get("snapshot")));
                try {
                    var result=verifier.update(snapshot);last=result.decision();
                    events.add(Map.of("sequence",snapshot.sequence(),"decision",last,
                            "failedInvariants",result.checks().stream().filter(c->c.kind().equals("CSL_INVARIANT")&&c.verdict().equals("FAIL")).map(CslStateVerifier.Check::id).toList(),
                            "violatedGoals",result.elements().stream().filter(e->e.status().equals("VIOLATED")).map(CslStateVerifier.ElementStatus::element).toList(),
                            "completedAuctions",result.elements().stream().filter(e->e.element().equals("AuctionCompleted")&&e.status().equals("FULFILLED")).count()));
                }catch(Exception failure){last="HOLD_UNVERIFIED";events.add(Map.of("sequence",snapshot.sequence(),"decision",last,"diagnostic",failure.toString()));}
                if(!last.equals("APPROVE")){firstHold=snapshot.sequence();break;}
            }
        }
        var result=Map.of("evaluatedCheckpoints",events.size(),"decision",last,"firstHoldSequence",firstHold,"events",events,
                "scope","Recorded-state prefix only; does not predict later JaCaMo behavior after a changed decision");
        Files.writeString(Path.of(args[4]),new String(CanonicalJson.encode(result),StandardCharsets.UTF_8));
        System.out.println("REPLAY checkpoints="+events.size()+" decision="+last+" firstHold="+firstHold);
    }
}
