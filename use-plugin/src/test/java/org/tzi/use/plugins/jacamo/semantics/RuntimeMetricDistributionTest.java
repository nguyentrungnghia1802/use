package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimePerformanceStats;

class RuntimeMetricDistributionTest {
    @Test void recentPercentilesAreExactAndRetentionIsBoundedAfterOneHundredThousandSamples() {
        var stats=new RuntimePerformanceStats();for(int i=1;i<=100000;i++)stats.record(RuntimePerformanceStats.Metric.USE_MUTATION,i);var snapshot=stats.snapshot();var timing=(Map<?,?>)((Map<?,?>)snapshot.get("timings")).get("USE_MUTATION");assertEquals(100000L,timing.get("samples"));assertEquals(1024,timing.get("recentSamples"));assertEquals(99488L,timing.get("p50Nanos"));assertEquals(99949L,timing.get("p95Nanos"));assertEquals(99990L,timing.get("p99Nanos"));assertEquals(100000L,timing.get("maxNanos"));assertEquals("EXACT_NEAREST_RANK_RECENT_1024_SAMPLES_PER_METRIC",snapshot.get("percentileScope"));
    }
}
