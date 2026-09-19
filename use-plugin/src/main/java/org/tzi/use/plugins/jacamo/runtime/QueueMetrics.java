package org.tzi.use.plugins.jacamo.runtime;

public record QueueMetrics(int depth, int highWatermark, long processed, long rejected, long failed, long dropped) { }
