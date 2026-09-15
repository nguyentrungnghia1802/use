package org.tzi.use.plugins.jacamo.runtime;

import java.net.URI;

public interface RuntimeService extends AutoCloseable {
    void connect(URI endpoint);
    void disconnect();
    void resync();
    MirrorState state();
    QueueMetrics metrics();
    @Override void close();
}
