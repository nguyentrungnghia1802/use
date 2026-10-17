package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.*;

/** Constant-size observational timing counters; no event/state history or runtime control. */
public final class RuntimePerformanceStats {
    public enum Metric { SNAPSHOT_BUILD, USE_MUTATION, OCL_EVALUATION, PAUSE_REQUEST, PAUSE_ACK, RESUME_REQUEST, RESUME_ACK, AUTHORITATIVE_RESYNC, WRITER_WAIT, USE_EXPORT, SEMANTIC_EVALUATION, ISTAR_PROPAGATION, JOURNAL_WRITE }
    private record Timing(long count,long total,long maximum,long last) { }
    private final EnumMap<Metric,Timing> timings=new EnumMap<>(Metric.class);
    private final long started=System.nanoTime();
    private static final int WINDOW=1024;
    private final EnumMap<Metric,long[]> samples=new EnumMap<>(Metric.class);
    public synchronized void record(Metric metric,long elapsedNanos) {
        long elapsed=Math.max(0,elapsedNanos);var old=timings.getOrDefault(metric,new Timing(0,0,0,0));
        timings.put(metric,new Timing(old.count()+1,old.total()+elapsed,Math.max(old.maximum(),elapsed),elapsed));samples.computeIfAbsent(metric,k->new long[WINDOW])[(int)(old.count()%WINDOW)]=elapsed;
    }
    private static long percentile(long[] sorted,double p){return sorted[Math.max(0,(int)Math.ceil(sorted.length*p)-1)];}
    public synchronized Map<String,Object> snapshot() {
        var result=new LinkedHashMap<String,Object>();result.put("elapsedNanos",Math.max(1,System.nanoTime()-started));
        var values=new TreeMap<String,Object>();
        timings.forEach((metric,t)->{var window=Arrays.copyOf(samples.get(metric),(int)Math.min(WINDOW,t.count()));Arrays.sort(window);values.put(metric.name(),Map.of("samples",t.count(),"totalNanos",t.total(),"meanNanos",t.total()/t.count(),"maxNanos",t.maximum(),"lastNanos",t.last(),"recentSamples",window.length,"p50Nanos",percentile(window,.50),"p95Nanos",percentile(window,.95),"p99Nanos",percentile(window,.99)));});result.put("timings",values);result.put("percentileScope","EXACT_NEAREST_RANK_RECENT_1024_SAMPLES_PER_METRIC");
        result.put("scope","Observed verifier/control wall-time cost, not an uninstrumented JaCaMo slowdown comparison");
        return Map.copyOf(result);
    }
}
