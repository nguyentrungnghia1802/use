package org.jacamo.bridge.adapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.jacamo.bridge.contract.Evidence;

final class AdapterEvidence {
    private AdapterEvidence() { }
    static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    static Evidence file(String authority, Path projectRoot, Path file, String detail) throws IOException {
        Path exact = file.toAbsolutePath().normalize();
        String relative;
        try { relative = projectRoot.toAbsolutePath().normalize().relativize(exact).toString().replace('\\','/'); }
        catch (IllegalArgumentException outside) { relative = "external/" + exact.getFileName(); }
        String hash = digest(Files.readAllBytes(exact));
        return new Evidence(authority + ":" + hash.substring(0,16), authority, "project:/" + relative, hash, detail);
    }
}
