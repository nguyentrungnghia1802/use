package org.tzi.use.plugins.jacamo.bridge;

import java.net.InetAddress;

@FunctionalInterface
public interface BridgeTransportFactory {
    BridgeTransport open(BridgeConnectionConfig configuration);

    static BridgeTransportFactory localTcp() {
        return configuration -> {
            try {
                return new LocalTcpBridgeTransport(InetAddress.getByName(configuration.endpoint().getHost()),
                        configuration.endpoint().getPort(), configuration.readSecret(),
                        configuration.maxFrameBytes(), configuration.timeoutMillis());
            } catch (java.net.UnknownHostException error) {
                throw new IllegalArgumentException("BRIDGE_ENDPOINT_INVALID", error);
            }
        };
    }
}
