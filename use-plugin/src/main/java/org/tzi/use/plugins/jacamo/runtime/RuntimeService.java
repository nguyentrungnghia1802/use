package org.tzi.use.plugins.jacamo.runtime;

import java.net.URI;

/** Contract for future runtime connectivity; no implementation exists in Phase 1. */
public interface RuntimeService {
    void connect(URI endpoint);
    void disconnect();
}
