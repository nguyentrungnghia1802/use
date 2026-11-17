package org.tzi.use.plugins.jacamo.verify.oracle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Test-only inverse of the recorded source relocation; the pinned oracle stays untouched. */
public final class SourcePackageRelocation {
    private static final Map<String, String> CLASSES = load();
    private static final Map<String, String> ORIGINALS = CLASSES.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));
    private static final Pattern CURRENT_NAMES = Pattern.compile(ORIGINALS.keySet().stream()
            .sorted((a, b) -> Integer.compare(b.length(), a.length()))
            .map(Pattern::quote).collect(Collectors.joining("|")) + "(?![\\w])");

    private SourcePackageRelocation() { }

    private static Map<String, String> load() {
        var properties = new Properties();
        try (var input = SourcePackageRelocation.class.getResourceAsStream("/META-INF/dsl-class-relocations.properties")) {
            if (input == null) throw new IllegalStateException("DSL relocation audit is missing");
            properties.load(input);
        } catch (IOException error) { throw new UncheckedIOException(error); }
        var result = new TreeMap<String, String>();
        properties.forEach((key, value) -> result.put(key.toString(), value.toString()));
        return Map.copyOf(result);
    }

    public static Map<String, String> classes() { return CLASSES; }

    public static Path sourcePath(Path root, String upstream) {
        String relative = upstream.substring("goal/src/main/java/".length());
        String name = relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
        return root.resolve("src/main/java/" + CLASSES.getOrDefault(name, name).replace('.', '/') + ".java");
    }

    public static String originalNames(String source) {
        return CURRENT_NAMES.matcher(source).replaceAll(match -> ORIGINALS.get(match.group()));
    }

    /** Restore only package declarations, qualified names and recorded split-package imports. */
    public static byte[] originalSource(String upstream, byte[] current) {
        String source = new String(current, StandardCharsets.UTF_8);
        if (upstream.endsWith("/IStarPropagation.java")) {
            source = source.replaceAll("(?m)^import org\\.vnu\\.sme\\.goal\\.dsl\\.istar_marking\\.mm\\.(?:GoalTaskStatus|IStarMarking|QualityStatus);\\r?\\n", "");
        }
        if (upstream.endsWith("/GoalModelInstanceTranslator.java")) {
            source = source.replaceAll("(?m)^import org\\.vnu\\.sme\\.goal\\.dsl\\.istar_marking\\.mm\\.GoalModelInstance(?:Evaluation|Topology);\\r?\\n", "");
            // The new imports occupy the pre-existing blank line after the package.
            source = source.replaceFirst("(package [\\w.]+;)(\\r?\\n)\\r?\\n\\r?\\n", "$1$2$2");
        }
        String relative = upstream.substring("goal/src/main/java/".length());
        String originalPackage = relative.substring(0, relative.lastIndexOf('/')).replace('/', '.');
        source = source.replaceFirst("(?m)^package [\\w.]+;", "package " + originalPackage + ";");
        return originalNames(source).getBytes(StandardCharsets.UTF_8);
    }
}
