package org.tzi.use.plugins.jacamo.runtime;

import java.net.URI;
import java.util.Set;
import java.util.function.Consumer;

public interface RuntimeConnector {
    String connectorId();
    Set<ConnectorCapability> capabilities();
    ConnectorState state();
    void connect(URI endpoint);
    RuntimeSnapshot fullSnapshot();
    RuntimeSubscription subscribe(Consumer<RuntimeEvent> listener);
    void disconnect();
}
