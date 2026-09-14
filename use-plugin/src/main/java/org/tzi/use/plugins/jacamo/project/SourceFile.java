package org.tzi.use.plugins.jacamo.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Source metadata; hash is computed from exact bytes, with no normalization. */
public record SourceFile(Path path, SourceKind kind, String sha256, long byteLength) {
    public SourceFile {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sha256, "sha256");
        if (byteLength < 0) throw new IllegalArgumentException("negative byte length");
    }

    public static SourceFile read(Path path, SourceKind kind) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return new SourceFile(path, kind, HexFormat.of().formatHex(digest), bytes.length);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK lacks SHA-256", impossible);
        }
    }
}
