package org.tzi.use.plugins.jacamo.bridge;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

/** Validated local production endpoint configuration. Secrets are read only when opening a transport. */
public record BridgeConnectionConfig(URI endpoint, Path secretFile, String distributionSha256,
                                     Set<String> requiredCapabilities, int maxFrameBytes,
                                     int timeoutMillis, int maxBufferedEvents) {
    public static final String ENDPOINT_PROPERTY = "use.jacamo.bridge.endpoint";
    public static final String SECRET_FILE_PROPERTY = "use.jacamo.bridge.secret-file";
    public static final String DISTRIBUTION_PROPERTY = "use.jacamo.bridge.distribution-sha256";
    public static final String CAPABILITIES_PROPERTY = "use.jacamo.bridge.required-capabilities";

    public BridgeConnectionConfig {
        if (endpoint == null || !"tcp".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getPort() < 1 || endpoint.getPort() > 65535 || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null)
            throw new IllegalArgumentException("BRIDGE_ENDPOINT_INVALID");
        try {
            if (!InetAddress.getByName(endpoint.getHost()).isLoopbackAddress())
                throw new IllegalArgumentException("BRIDGE_REMOTE_MODE_UNSUPPORTED");
        } catch (java.net.UnknownHostException error) {
            throw new IllegalArgumentException("BRIDGE_ENDPOINT_INVALID", error);
        }
        if (secretFile == null) throw new IllegalArgumentException("BRIDGE_SECRET_FILE_REQUIRED");
        secretFile = secretFile.toAbsolutePath().normalize();
        if (distributionSha256 == null || !distributionSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("BRIDGE_DISTRIBUTION_SHA256_REQUIRED");
        requiredCapabilities = Set.copyOf(requiredCapabilities == null ? Set.of() : requiredCapabilities);
        if (requiredCapabilities.isEmpty() || requiredCapabilities.stream().anyMatch(value -> value == null || value.isBlank()))
            throw new IllegalArgumentException("BRIDGE_CAPABILITIES_REQUIRED");
        if (maxFrameBytes < 1024 || timeoutMillis < 100 || maxBufferedEvents < 1)
            throw new IllegalArgumentException("BRIDGE_LIMITS_INVALID");
    }

    public static BridgeConnectionConfig fromSystemProperties() {
        String endpoint = requiredProperty(ENDPOINT_PROPERTY);
        String secret = requiredProperty(SECRET_FILE_PROPERTY);
        String distribution = requiredProperty(DISTRIBUTION_PROPERTY);
        String configuredCapabilities = System.getProperty(CAPABILITIES_PROPERTY,
                "official.model,runtime.snapshot");
        Set<String> capabilities = new LinkedHashSet<>();
        for (String value : configuredCapabilities.split(",")) {
            String capability = value.trim();
            if (!capability.isEmpty()) capabilities.add(capability);
        }
        return new BridgeConnectionConfig(URI.create(endpoint), Path.of(secret), distribution, capabilities,
                4 * 1024 * 1024, 5_000, 1_024);
    }

    byte[] readSecret() {
        try {
            if (!Files.isRegularFile(secretFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(secretFile))
                throw new IllegalArgumentException("BRIDGE_SECRET_FILE_INVALID");
            long size = Files.size(secretFile);
            if (size < 64 || size > 4_096) throw new IllegalArgumentException("BRIDGE_SECRET_FILE_INVALID");
            String encoded = Files.readString(secretFile, StandardCharsets.US_ASCII).trim();
            if (!encoded.matches("[0-9a-fA-F]{64,}" ) || (encoded.length() & 1) != 0)
                throw new IllegalArgumentException("BRIDGE_SECRET_FILE_INVALID");
            byte[] value = HexFormat.of().parseHex(encoded);
            if (value.length < 32) throw new IllegalArgumentException("BRIDGE_SECRET_TOO_SHORT");
            return value;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("BRIDGE_SECRET_FILE_INVALID", error);
        }
    }

    public String displayEndpoint() {
        return "tcp://" + endpoint.getHost() + ":" + endpoint.getPort();
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("BRIDGE_CONFIGURATION_REQUIRED:" + name);
        return value.trim();
    }
}
