package org.tzi.use.plugins.jacamo.bridge;

import java.util.function.Consumer;

/** Process-neutral byte transport. Implementations never expose platform objects. */
public interface BridgeTransport extends AutoCloseable {
    byte[] handshake();
    byte[] modelSnapshot();
    byte[] runtimeSnapshot();
    Subscription subscribe(String resumeToken, Consumer<byte[]> receiver);
    default Subscription subscribe(String resumeToken, Consumer<byte[]> receiver,
                                   Consumer<RuntimeException> failure) {
        return subscribe(resumeToken, receiver);
    }
    void acknowledge(String resumeToken);
    interface Subscription extends AutoCloseable { @Override void close(); }
    @Override void close();
}
