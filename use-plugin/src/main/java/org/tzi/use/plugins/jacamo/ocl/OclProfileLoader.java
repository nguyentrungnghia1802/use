package org.tzi.use.plugins.jacamo.ocl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/** Loads authored OCL without changing it; compilation is performed against the generated USE model. */
public final class OclProfileLoader {
    public LoadedProfile loadCase(Path projectRoot, Path profile) {
        return loadWithin(projectRoot, profile, "OCL_PROFILE");
    }
    public LoadedProfile loadUser(Path allowedRoot, Path profile) {
        return loadWithin(allowedRoot, profile, "OCL_USER_PROFILE");
    }
    private LoadedProfile loadWithin(Path projectRoot, Path profile, String diagnosticPrefix) {
        try {
            Path root = projectRoot.toAbsolutePath().normalize();
            Path resolved = profile.isAbsolute() ? profile.normalize() : root.resolve(profile).normalize();
            if (!resolved.startsWith(root)) throw new IllegalArgumentException(diagnosticPrefix + "_PATH_ESCAPE");
            return loaded(resolved, Files.readString(resolved, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalArgumentException(diagnosticPrefix + "_IO: " + exception.getMessage(), exception);
        }
    }
    public LoadedProfile loadCore() {
        String resource = "/org/tzi/use/plugins/jacamo/ocl/jacamo-core.ocl";
        try (InputStream input = OclProfileLoader.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalArgumentException("OCL_CORE_PROFILE_MISSING");
            return loaded(Path.of(resource), new String(input.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) { throw new IllegalArgumentException("OCL_CORE_PROFILE_IO", exception); }
    }
    private LoadedProfile loaded(Path origin, String content) {
        if (content.isBlank()) throw new IllegalArgumentException("OCL_PROFILE_EMPTY");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return new LoadedProfile(origin, content, java.util.HexFormat.of().formatHex(digest));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    public record LoadedProfile(Path origin, String content, String sha256) { }
}
