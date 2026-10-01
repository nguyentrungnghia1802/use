package org.jacamo.bridge.adapter;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import jason.asSyntax.Term;
import org.jacamo.bridge.contract.Evidence;

/** Resolves only the exact location supplied by Jason SourceInfo, never a filename search. */
final class JasonSourceEvidence {
    private final Path projectRoot;
    private final Map<String, Evidence> cache = new HashMap<>();

    JasonSourceEvidence(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    Evidence of(Term term, Evidence containingSource) throws Exception {
        // Trigger/body nodes without their own SourceInfo inherit the containing Plan's file.
        if (term.getSrcInfo() == null) return containingSource;
        return resolve(term.getSrcInfo().getSrcFile());
    }

    Evidence resolve(String location) throws Exception {
        if (location == null || location.isBlank()) throw new IOException("JASON_SOURCE_LOCATION_MISSING");
        Evidence known = cache.get(location);
        if (known != null) return known;
        String exact = location.replace('\\', '/');
        if (exact.startsWith("ClassResource:")) {
            var resource = jason.asSemantics.Agent.class.getResource(exact.substring("ClassResource:".length()));
            if (resource == null) throw new IOException("JASON_SOURCE_RESOURCE_MISSING:" + location);
            Evidence result = resolve(resource.toString());
            cache.put(location, result);
            return result;
        }
        Evidence result;
        if (exact.startsWith("jar:file:")) {
            int split = exact.indexOf("!/");
            if (split < 0) throw new IOException("JASON_SOURCE_JAR_ENTRY_MISSING:" + location);
            Path archive = filePath(exact.substring(4, split));
            String entryName = URI.create("/" + exact.substring(split + 2).replace(" ", "%20")).getPath().substring(1);
            try (var jar = new JarFile(archive.toFile())) {
                var entry = jar.getJarEntry(entryName);
                if (entry == null) throw new IOException("JASON_SOURCE_JAR_ENTRY_MISSING:" + location);
                try (var input = jar.getInputStream(entry)) {
                    String entryUri = new URI(null, null, "/" + entryName, null).getRawPath().substring(1);
                    result = evidence("jar:" + archive.toUri() + "!/" + entryUri, input.readAllBytes());
                }
            }
        } else {
            Path file;
            if (exact.startsWith("file:")) file = filePath(exact);
            else {
                if (exact.contains(":") && !exact.matches("^[A-Za-z]:/.*"))
                    throw new IOException("JASON_SOURCE_SCHEME_UNSUPPORTED:" + location);
                // Official Include can return an exact absolute filesystem path, but not a guessed basename.
                file = Path.of(location);
                if (!file.isAbsolute()) throw new IOException("JASON_SOURCE_LOCATION_UNRESOLVED:" + location);
            }
            file = file.toAbsolutePath().normalize();
            result = file.startsWith(projectRoot)
                    ? AdapterEvidence.file("jason-parser", projectRoot, file, "Jason SourceInfo exact file")
                    : evidence(file.toUri().toString(), Files.readAllBytes(file));
        }
        cache.put(location, result);
        return result;
    }

    private static Path filePath(String location) {
        String path = location.substring("file:".length());
        // JaCaMo's package roots can emit file:C:/... instead of hierarchical file:/C:/....
        if (!path.startsWith("/")) path = "/" + path;
        return Path.of(URI.create("file:" + path.replace(" ", "%20"))).toAbsolutePath().normalize();
    }

    private static Evidence evidence(String uri, byte[] content) {
        String hash = AdapterEvidence.digest(content);
        return new Evidence("jason-parser:" + hash.substring(0, 16), "jason-parser", uri, hash,
                "Jason SourceInfo exact file/resource content");
    }
}
