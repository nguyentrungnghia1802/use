package org.tzi.use.plugins.jacamo.session;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.CheckedInputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;
import org.tzi.use.plugins.jacamo.session.VerificationSession.Configuration;

/** Unpacks the inputs of an MRV experiment while preserving project-relative paths. */
public final class ExperimentBundle {
    private static final String MANIFEST = "mrv.json";
    private static final int MAX_MANIFEST_BYTES = 16 * 1024;
    private static final int MAX_ENTRIES = 4096;
    private static final long MAX_CONTENT_BYTES = 128L * 1024 * 1024;
    private static final Pattern WINDOWS_DEVICE = Pattern.compile(
            "(?i)(?:con|prn|aux|nul|com[1-9¹²³]|lpt[1-9¹²³])(?:\\..*)?");

    private ExperimentBundle() { }

    public static boolean hasBundleExtension(Path file) {
        return file.getFileName() != null && file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mrv");
    }

    public static Configuration unpack(Path bundle, Path workspace) throws IOException {
        if (!hasBundleExtension(bundle) || !Files.isRegularFile(bundle)) {
            throw new IOException("Select an MRV (.mrv) experiment file.");
        }
        if (Files.size(bundle) > MAX_CONTENT_BYTES) throw new IOException("MRV file exceeds 128 MiB.");
        Files.createDirectories(workspace);
        Path root = Files.createTempDirectory(workspace.toAbsolutePath().normalize(), "use-mrv-");
        try (var zip = new ZipFile(bundle.toFile())) {
            if (zip.size() > MAX_ENTRIES) throw new IOException("MRV contains too many entries.");
            var manifestEntry = zip.getEntry(MANIFEST);
            if (manifestEntry == null || manifestEntry.isDirectory() || manifestEntry.getSize() > MAX_MANIFEST_BYTES) {
                throw new IOException("MRV needs a root " + MANIFEST + " of at most 16 KiB.");
            }
            unpackEntries(zip, root);
            var manifest = readManifest(root.resolve(MANIFEST));
            if (!BigDecimal.ONE.equals(manifest.get("version"))) {
                throw new IOException("Unsupported MRV version. Expected version 1.");
            }
            var configuration = new Configuration(requiredFile(root, manifest, "project", ".jcm"),
                    requiredFile(root, manifest, "mapping", ".xml"),
                    requiredFile(root, manifest, "csl", ".csl"),
                    requiredFile(root, manifest, "istar", ".istar"));
            configuration.validate();
            return configuration;
        } catch (IOException | RuntimeException failure) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static void unpackEntries(ZipFile zip, Path root) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            String name = entry.getName();
            Path target = resolveEntry(root, entry.isDirectory() ? name.substring(0, name.length() - 1) : name);
            if (entry.isDirectory()) {
                if (entry.getSize() != 0) throw new IOException("Invalid MRV directory entry: " + name);
                Files.createDirectories(target);
                continue;
            }
            Files.createDirectories(target.getParent());
            long size = 0;
            try (var input = new CheckedInputStream(zip.getInputStream(entry), new CRC32());
                 var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                for (int read; (read = input.read(buffer)) != -1; ) {
                    total += read;
                    size += read;
                    if (total > MAX_CONTENT_BYTES) throw new IOException("Unpacked MRV exceeds 128 MiB.");
                    if (size > entry.getSize()) throw new IOException("Invalid MRV entry size: " + name);
                    output.write(buffer, 0, read);
                }
                if (size != entry.getSize() || input.getChecksum().getValue() != entry.getCrc()) {
                    throw new IOException("Corrupt MRV entry: " + name);
                }
            }
        }
    }

    private static Map<String, Object> readManifest(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            return CanonicalJson.object(CanonicalJson.decode(bytes, MAX_MANIFEST_BYTES, 4, 4096));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid MRV manifest: " + invalid.getMessage(), invalid);
        }
    }

    private static Path requiredFile(Path root, Map<String, Object> manifest, String role, String extension)
            throws IOException {
        if (!(manifest.get(role) instanceof String name) || !name.endsWith(extension)) {
            throw new IOException("MRV manifest must name a " + extension + " file for " + role + ".");
        }
        Path file = resolveEntry(root, name);
        if (!Files.isRegularFile(file)) throw new IOException("File missing in MRV: " + name);
        return file;
    }

    private static Path resolveEntry(Path root, String name) throws IOException {
        if (name.indexOf('\\') >= 0 || name.indexOf(':') >= 0) {
            throw new IOException("Invalid MRV path: " + name);
        }
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.endsWith(".") || part.endsWith(" ")
                    || WINDOWS_DEVICE.matcher(part).matches()) {
                throw new IOException("Invalid MRV path: " + name);
            }
        }
        try {
            Path target = root.resolve(name).normalize();
            if (target.startsWith(root) && !target.equals(root)) return target;
        } catch (InvalidPathException invalid) {
            throw new IOException("Invalid MRV path: " + name, invalid);
        }
        throw new IOException("Invalid MRV path: " + name);
    }
}
