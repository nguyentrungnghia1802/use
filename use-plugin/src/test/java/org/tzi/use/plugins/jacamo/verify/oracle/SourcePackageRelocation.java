package org.tzi.use.plugins.jacamo.verify.oracle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
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
        source = restoreExtractedAlgorithms(upstream, source);
        source = source.replaceAll("(?m)^import org\\.vnu\\.sme\\.goal\\.verify\\.csl\\.validation\\.CslSemanticValidator;\\r?\\n", "");
        if (upstream.endsWith("/CslSemanticValidator.java")) {
            source = source.replaceAll("(?m)^import org\\.vnu\\.sme\\.goal\\.dsl\\.csl\\.mm\\.\\*;\\r?\\n", "");
        }
        if (upstream.endsWith("/IStarPropagation.java")) {
            source = source.replaceAll("(?m)^import org\\.vnu\\.sme\\.goal\\.verify\\.istar\\.marking\\.(?:GoalTaskStatus|IStarMarking|QualityStatus);\\r?\\n", "");
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

    /** Reverse only checked delegations and mechanically lifted method bodies.
     * The algorithm is read from its new source, never substituted from the oracle. */
    private static String restoreExtractedAlgorithms(String upstream, String source) {
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        try {
            if (upstream.endsWith("/GoalMarking.java")) {
                String lifted = extractedSource("istar/temporal/GoalTemporalEvaluator.java", newline);
                String aliases = ("        GoalType type = marking.type();\n"
                        + "        boolean active = marking.active();\n"
                        + "        boolean condition = marking.condition();\n"
                        + "        boolean stable = marking.stable();\n").replace("\n", newline);
                String update = method(lifted, "    public static GoalMarking update(")
                        .replace("public static GoalMarking update(GoalMarking marking, boolean nextActive, boolean predicate)",
                                "public GoalMarking update(boolean nextActive, boolean predicate)")
                        .replace(aliases, "").replace("return GoalMarking.initial(type);", "return initial(type);");
                String status = method(lifted, "    public static GoalTaskStatus status(")
                        .replace("public static GoalTaskStatus status(GoalMarking marking)", "public GoalTaskStatus status()")
                        .replace(aliases, "");
                source = restoreDelegation(source, "    public GoalMarking update(",
                        "    public GoalMarking update(boolean nextActive, boolean predicate) {\n"
                                + "        return GoalTemporalEvaluator.update(this, nextActive, predicate);\n    }", update, newline);
                source = restoreDelegation(source, "    public GoalTaskStatus status()",
                        "    public GoalTaskStatus status() {\n        return GoalTemporalEvaluator.status(this);\n    }", status, newline);
                String baseline = method(lifted, "    private static boolean baseline(");
                int end = source.lastIndexOf('}');
                source = source.substring(0, end) + newline + baseline + newline + source.substring(end);
                source = source.replaceAll("(?m)^import org\\.vnu\\.sme\\.goal\\.verify\\.istar\\.temporal\\.GoalTemporalEvaluator;\\r?\\n", "");
            } else if (upstream.endsWith("/GoalModelInstanceTranslator.java")) {
                String lifted = extractedSource("istar/propagation/IStarDependencyPropagation.java", newline);
                String algorithm = method(lifted, "    public static IStarMarking propagate(")
                        .replace("public static IStarMarking propagate(", "private static IStarMarking saturateDependencies(");
                source = restoreDelegation(source, "    private static IStarMarking saturateDependencies(",
                        "    private static IStarMarking saturateDependencies(GoalModel model, IStarMarking marking) {\n"
                                + "        return IStarDependencyPropagation.propagate(model, marking);\n    }", algorithm, newline);
                var limit = Pattern.compile("(?m)^    private static final int MAX_DEPENDENCY_STEPS = [^;]+;").matcher(lifted);
                if (!limit.find()) throw new IllegalStateException("Dependency limit source is missing");
                String constructor = "    private GoalModelInstanceTranslator() {}";
                source = source.replace(constructor, limit.group() + newline + newline + constructor);
                source = source.replaceAll("(?m)^import org\\.vnu\\.sme\\.goal\\.verify\\.istar\\.propagation\\.IStarDependencyPropagation;\\r?\\n", "");
            }
            return source;
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }

    private static String extractedSource(String relative, String newline) throws IOException {
        return Files.readString(Path.of("src/main/java/org/vnu/sme/goal/verify").resolve(relative))
                .replace("\r\n", "\n").replace("\n", newline);
    }

    private static String restoreDelegation(String source, String declaration, String expected,
                                            String original, String newline) {
        String actual = method(source, declaration);
        if (!actual.equals(expected.replace("\n", newline)))
            throw new IllegalStateException("Unrecorded delegation change: " + declaration);
        return source.replace(actual, original);
    }

    private static String method(String source, String declaration) {
        int start = source.indexOf(declaration);
        if (start < 0) throw new IllegalStateException("Extracted method missing: " + declaration);
        int at = source.indexOf('{', start), depth = 1;
        for (at++; depth != 0; at++) {
            char token = source.charAt(at);
            if (token == '{') depth++;
            else if (token == '}') depth--;
        }
        return source.substring(start, at);
    }
}
