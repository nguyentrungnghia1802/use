package org.jacamo.bridge.adapter;

import java.util.List;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.SourceWatermark;

/** Official subsystem snapshot/event source. Callbacks must remain bounded. */
public interface SnapshotSource extends AutoCloseable {
    String sourceId();
    void attach(Consumer<RuntimeEvent> observer) throws Exception;
    SourceWatermark watermark();
    String topologyFingerprint() throws Exception;
    List<RuntimeFact> capture() throws Exception;
    Completeness completeness();
    @Override void close() throws Exception;
}
