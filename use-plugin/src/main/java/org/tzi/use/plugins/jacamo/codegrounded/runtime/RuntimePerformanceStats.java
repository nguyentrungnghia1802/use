package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.*;

/** Constant-size observational timing counters; no event/state history or runtime control. */
public final class RuntimePerformanceStats {
    public enum Metric { SNAPSHOT_BUILD, USE_MUTATION, OCL_EVALUATION, PAUSE_REQUEST, PAUSE_ACK, RESUME_REQUEST, RESUME_ACK, AUTHORITATIVE_RESYNC }
    private record Timing(long count,long total,long maximum,long last) { }
    private final EnumMap<Metric,Timing> timings=new EnumMap<>(Metric.class);
    private final long started=System.nanoTime();
    public synchronized void record(Metric metric,long elapsedNanos) {
        long elapsed=Math.max(0,elapsedNanos);var old=timings.getOrDefault(metric,new Timing(0,0,0,0));
        timings.put(metric,new Timing(old.count()+1,old.total()+elapsed,Math.max(old.maximum(),elapsed),elapsed));
    }
    public synchronized Map<String,Object> snapshot() {
        var result=new LinkedHashMap<String,Object>();result.put("elapsedNanos",Math.max(1,System.nanoTime()-started));
        var values=new TreeMap<String,Object>();
        timings.forEach((metric,t)->values.put(metric.name(),Map.of("samples",t.count(),"totalNanos",t.total(),"meanNanos",t.total()/t.count(),
                "maxNanos",t.maximum(),"lastNanos",t.last())));result.put("timings",values);
        result.put("scope","Observed verifier/control wall-time cost, not an uninstrumented JaCaMo slowdown comparison");
        return Map.copyOf(result);
    }
}
