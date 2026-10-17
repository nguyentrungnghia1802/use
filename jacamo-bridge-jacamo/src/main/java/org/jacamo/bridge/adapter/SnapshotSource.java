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
    record LocalCut(SourceWatermark start,SourceWatermark end,List<RuntimeFact> facts) {
        public LocalCut { facts=List.copyOf(facts); }
    }
    /** Override only when immutable observation publication and its source sequence share a lock. */
    default LocalCut captureCut() throws Exception {
        var start=watermark();var facts=capture();return new LocalCut(start,watermark(),facts);
    }
    /** Publish changed supported observations for APIs with no lifecycle listener. */
    default void poll() throws Exception { }
    /** Immutable diagnostics/scope evidence recorded with the validated cut. */
    default java.util.Map<String,Object> captureMetadata(){return java.util.Map.of();}
    Completeness completeness();
    @Override void close() throws Exception;
}
