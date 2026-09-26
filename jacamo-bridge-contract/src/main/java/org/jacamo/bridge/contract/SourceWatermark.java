package org.jacamo.bridge.contract;

public record SourceWatermark(String sourceId, long sequence) {
    public SourceWatermark {
        sourceId = ContractSupport.required(sourceId, "sourceId");
        if (sequence < 0) throw new ContractException("watermark sequence must be non-negative");
    }
}
