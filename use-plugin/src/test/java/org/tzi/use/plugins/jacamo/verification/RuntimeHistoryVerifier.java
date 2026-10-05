package org.tzi.use.plugins.jacamo.verification;

import java.util.*;
import org.tzi.use.plugins.jacamo.runtime.*;

/** Finite recorded-history checks, deliberately distinct from OCL and liveness claims. */
public final class RuntimeHistoryVerifier {
    public record Result(String ruleId, VerificationOutcome outcome, long generation,
                         List<String> eventIds, String diagnostic) {
        public Result { eventIds = List.copyOf(eventIds); }
    }
    public List<Result> verify(RuntimeTrace trace) {
        List<Result> results = new ArrayList<>();
        Map<String,RuntimeEvent> starts = new HashMap<>();
        Set<String> completed = new HashSet<>();
        Map<Long,Long> last = new HashMap<>();
        Set<Long> generations = new HashSet<>();
        trace.boundaries().forEach(b -> generations.add(b.generation()));
        for (var entry : trace.entries()) {
            var e = entry.event();
            if (entry.disposition().equals("REJECTED")) {
                results.add(new Result("STREAM_ADMISSION",VerificationOutcome.ERROR,entry.generation(),List.of(e.eventId()),entry.diagnostic()));
                continue;
            }
            if (!entry.disposition().equals("ACCEPTED")) continue;
            boolean ordered = generations.contains(entry.generation()) && e.sequence() > last.getOrDefault(entry.generation(),-1L);
            results.add(new Result("HAPPENED_BEFORE", ordered ? VerificationOutcome.PASS : VerificationOutcome.FAIL,
                entry.generation(),List.of(e.eventId()),ordered ? "Strict sequence within recorded generation" : "Unknown generation or non-increasing sequence"));
            last.put(entry.generation(),e.sequence());
            String key=entry.generation()+":"+e.correlationId();
            if(e.kind()==RuntimeEventKind.OP_ENTER) {
                boolean fresh=!starts.containsKey(key) && !completed.contains(key);
                results.add(new Result("CORRELATION_UNIQUE",fresh ? VerificationOutcome.PASS : VerificationOutcome.FAIL,entry.generation(),List.of(e.eventId()),"One start per invocation and generation"));
                if(fresh) starts.put(key,e);
            } else if(e.kind()==RuntimeEventKind.OP_EXIT || e.kind()==RuntimeEventKind.OP_FAIL) {
                var start=starts.get(key);
                boolean valid=start!=null && start.sequence()<e.sequence() && start.runtimeSourceId().equals(e.runtimeSourceId())
                    && Objects.equals(start.semanticSourceId(),e.semanticSourceId())
                    && (!e.payload().containsKey("operation") || Objects.equals(start.payload().get("operation"),e.payload().get("operation")));
                results.add(new Result("START_BEFORE_TERMINAL",valid ? VerificationOutcome.PASS : VerificationOutcome.FAIL,entry.generation(),
                    start==null ? List.of(e.eventId()) : List.of(start.eventId(),e.eventId()),"Exact correlation, identity, operation and generation required"));
                if(valid) { starts.remove(key); completed.add(key); }
            }
        }
        // A finite prefix cannot prove that an unfinished operation will eventually terminate.
        starts.forEach((key,e)->results.add(new Result("TERMINAL_NOT_OBSERVED",VerificationOutcome.SKIPPED,
            Long.parseLong(key.substring(0,key.indexOf(':'))),List.of(e.eventId()),"No eventual-completion claim from a finite trace")));
        results.sort(Comparator.comparingLong(Result::generation).thenComparing(r -> String.join("|",r.eventIds())).thenComparing(Result::ruleId));
        return List.copyOf(results);
    }
    public Result happenedBefore(RuntimeTrace trace, long generation, String first, String second) {
        var a=trace.byEventId(first).stream().filter(e->e.generation()==generation && e.disposition().equals("ACCEPTED")).findFirst();
        var b=trace.byEventId(second).stream().filter(e->e.generation()==generation && e.disposition().equals("ACCEPTED")).findFirst();
        var outcome=a.isEmpty() || b.isEmpty() ? VerificationOutcome.SKIPPED : a.get().event().sequence()<b.get().event().sequence() ? VerificationOutcome.PASS : VerificationOutcome.FAIL;
        return new Result("EXPLICIT_HAPPENED_BEFORE",outcome,generation,List.of(first,second),"Exact event IDs; missing evidence is SKIPPED");
    }
}
