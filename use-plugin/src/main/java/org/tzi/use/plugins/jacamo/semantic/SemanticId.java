package org.tzi.use.plugins.jacamo.semantic;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Stable six-part identity: jacamo:project:dimension:kind:ownerPath:localId. */
public final class SemanticId {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private final String value;
    private final String projectId;
    private final Dimension dimension;
    private final String metamodelKind;

    private SemanticId(String value, String projectId, Dimension dimension, String metamodelKind) {
        this.value = value;
        this.projectId = projectId;
        this.dimension = dimension;
        this.metamodelKind = metamodelKind;
    }

    public static SemanticId of(String projectId, Dimension dimension, String metamodelKind,
                                List<String> ownerPath, String localId) {
        Objects.requireNonNull(dimension, "dimension");
        if (ownerPath == null || ownerPath.isEmpty()) {
            throw new IllegalArgumentException("ownerPath must contain at least one segment");
        }
        String owner = ownerPath.stream().map(SemanticId::encode).collect(Collectors.joining("/"));
        return new SemanticId("jacamo:" + encode(projectId) + ":" + dimension.key() + ":"
                + encode(metamodelKind) + ":" + owner + ":" + encode(localId),
                projectId, dimension, metamodelKind);
    }

    private static String encode(String segment) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException("semantic identity segment must not be blank");
        }
        StringBuilder encoded = new StringBuilder();
        for (byte b : segment.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = b & 0xff;
            if ((unsigned >= 'A' && unsigned <= 'Z') || (unsigned >= 'a' && unsigned <= 'z')
                    || (unsigned >= '0' && unsigned <= '9') || unsigned == '-' || unsigned == '_'
                    || unsigned == '.' || unsigned == '~') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%').append(HEX[unsigned >>> 4]).append(HEX[unsigned & 0xf]);
            }
        }
        return encoded.toString();
    }

    public String value() {
        return value;
    }

    public String projectId() { return projectId; }
    public Dimension dimension() { return dimension; }
    public String metamodelKind() { return metamodelKind; }

    @Override public boolean equals(Object other) {
        return other instanceof SemanticId id && value.equals(id.value);
    }

    @Override public int hashCode() {
        return value.hashCode();
    }

    @Override public String toString() {
        return value;
    }
}
