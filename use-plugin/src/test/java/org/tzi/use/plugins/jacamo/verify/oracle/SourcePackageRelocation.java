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
    private static final Pattern CSL_OCL_STATE = Pattern.compile(
            "(?m)^    /\\*\\*[^\\r\\n]*\\*/\\R    public interface State \\{\\R(?:        [^\\r\\n]*\\R)*    }\\R");

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
        String owner = CLASSES.getOrDefault(name, name).split("\\$", 2)[0];
        return root.resolve("src/main/java/" + owner.replace('.', '/') + ".java");
    }

    public static String originalNames(String source) {
        return CURRENT_NAMES.matcher(source).replaceAll(match -> ORIGINALS.get(match.group()));
    }

    /** Restore recorded package changes and the CSL evaluator's state interface placement. */
    public static byte[] originalSource(String upstream, byte[] current) {
        String source = new String(current, StandardCharsets.UTF_8);
        if (upstream.endsWith("/NativeOclEvaluator.java") || upstream.endsWith("/CslOclState.java"))
            source = restoreCslOclState(source, upstream.endsWith("/CslOclState.java"));
        source = source.replaceAll("(?m)^import org\\.vnu\\.sme\\.goal\\.verify\\.csl\\.CslSemanticValidator;\\r?\\n", "");
        if (upstream.endsWith("/CslSemanticValidator.java")) {
            source = source.replaceAll("(?m)^import org\\.vnu\\.sme\\.goal\\.dsl\\.csl\\.mm\\.\\*;\\r?\\n", "");
        }
        String relative = upstream.substring("goal/src/main/java/".length());
        String originalPackage = relative.substring(0, relative.lastIndexOf('/')).replace('/', '.');
        source = source.replaceFirst("(?m)^package [\\w.]+;", "package " + originalPackage + ";");
        return originalNames(source).getBytes(StandardCharsets.UTF_8);
    }

    private static String restoreCslOclState(String source, boolean standalone) {
        var match = CSL_OCL_STATE.matcher(source);
        if (!match.find()) throw new IllegalStateException("Missing nested CSL OCL state interface");
        String block = match.group();
        String newline = block.endsWith("\r\n") ? "\r\n" : "\n";
        if (standalone) {
            String packageLine = source.substring(0, source.indexOf(newline));
            return packageLine + newline + newline + block.replaceAll("(?m)^    ", "")
                    .replace("interface State", "interface CslOclState");
        }
        String importsEnd = "import java.util.Objects;" + newline + newline;
        return source.replace(block + newline, "")
                .replace(importsEnd, importsEnd
                        + "import org.vnu.sme.goal.dsl.csl.ocl.CslOclState;" + newline + newline)
                .replaceAll("\\bState(?= (?:snapshot|state)\\b)", "CslOclState");
    }
}
