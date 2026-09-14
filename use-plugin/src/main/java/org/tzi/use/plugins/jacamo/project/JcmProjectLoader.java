package org.tzi.use.plugins.jacamo.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.tzi.use.plugins.jacamo.diagnostics.Diagnostic;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;

/** Static JCM source discovery. It never loads or executes project classes. */
public final class JcmProjectLoader {
    private static final Set<String> TOP_LEVEL = Set.of("agent", "workspace", "organisation",
            "asl-path", "org-path", "java-path", "class-path", "platform", "mas");
    private static final Map<String, List<String>> DEFAULT_PATHS = Map.of(
            "asl-path", List.of(".", "src/agt", "src/agt/inc"),
            "org-path", List.of(".", "src/org"),
            "java-path", List.of(".", "src/env", "src/agt"),
            "class-path", List.of("lib"));

    private final Map<Path, SourceFile> sources = new HashMap<>();
    private final List<ProjectEdge> edges = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Set<Path> visited = new HashSet<>();
    private final Set<Path> active = new HashSet<>();
    private Path root;

    public synchronized ProjectDiscoveryResult discover(Path entryPath) {
        sources.clear(); edges.clear(); diagnostics.clear(); visited.clear(); active.clear();
        if (entryPath == null) {
            error("JCM_ENTRY_MISSING", null, "No .jcm entry was selected", "null path", "Select an existing .jcm file");
            return new ProjectDiscoveryResult(null, diagnostics);
        }
        Path entry = entryPath.toAbsolutePath().normalize();
        if (!entry.getFileName().toString().endsWith(".jcm")) {
            error("JCM_ENTRY_TYPE", null, "Entry must be a .jcm file", entry.toString(), "Select a .jcm file");
            return new ProjectDiscoveryResult(null, diagnostics);
        }
        if (!Files.isRegularFile(entry)) {
            error("JCM_ENTRY_MISSING", null, "JCM entry file is missing", entry.toString(), "Select an existing .jcm file");
            return new ProjectDiscoveryResult(null, diagnostics);
        }
        try {
            entry = entry.toRealPath();
            root = entry.getParent();
            Document entryDocument = parse(entry);
            if (entryDocument == null) return new ProjectDiscoveryResult(null, diagnostics);
            visit(entry, entryDocument);
            ProjectGraph graph = new ProjectGraph(new ProjectRoot(root, entryDocument.name()), entry,
                    List.copyOf(sources.values()), edges);
            return new ProjectDiscoveryResult(graph, diagnostics);
        } catch (IOException exception) {
            error("JCM_IO", null, "Cannot read JCM entry", entry + ": " + exception.getMessage(),
                    "Check the file path and permissions");
            return new ProjectDiscoveryResult(null, diagnostics);
        }
    }

    private void visit(Path file, Document document) {
        if (!visited.add(file)) return;
        active.add(file);
        addSource(file, SourceKind.JCM, null);
        for (JcmLexer.Token include : document.includes()) {
            Path target = resolveInclude(file, include);
            if (target == null) continue;
            edges.add(new ProjectEdge(file, target, ProjectEdgeKind.INCLUDE, include.span(file)));
            if (active.contains(target)) {
                error("JCM_INCLUDE_CYCLE", include.span(file), "JCM include cycle detected",
                        file + " -> " + target, "Remove the cyclic uses declaration");
                continue;
            }
            if (!visited.contains(target)) {
                Document included = parse(target);
                if (included != null) visit(target, included);
            }
        }
        for (Declaration declaration : document.declarations()) {
            switch (declaration.kind()) {
                case AGENT_SOURCE -> resolveDeclaredSource(file, document, declaration,
                        "asl-path", SourceKind.ASL, false);
                case ORGANISATION_SOURCE -> resolveDeclaredSource(file, document, declaration,
                        "org-path", SourceKind.MOISE_XML, false);
                case ARTIFACT_SOURCE -> resolveDeclaredSource(file, document, declaration,
                        "java-path", SourceKind.JAVA, true);
                default -> throw new IllegalStateException("unhandled source declaration");
            }
        }
        active.remove(file);
    }

    private Path resolveInclude(Path from, JcmLexer.Token include) {
        String text = include.text().endsWith(".jcm") ? include.text() : include.text() + ".jcm";
        try {
            Path candidate = from.getParent().resolve(text).normalize();
            if (!candidate.startsWith(root)) {
                error("JCM_PATH_ESCAPE", include.span(from), "Include escapes project root", text,
                        "Keep uses targets within the project root");
                return null;
            }
            if (!Files.isRegularFile(candidate)) {
                error("JCM_INCLUDE_MISSING", include.span(from), "Included JCM file is missing", text,
                        "Add the included .jcm file or correct its name");
                return null;
            }
            Path actual = candidate.toRealPath();
            if (!actual.startsWith(root)) {
                error("JCM_PATH_ESCAPE", include.span(from), "Include resolves outside project root", text,
                        "Use an included file inside the project root");
                return null;
            }
            return actual;
        } catch (IOException | InvalidPathException exception) {
            error("JCM_INCLUDE_INVALID", include.span(from), "Cannot resolve include", text + ": " + exception.getMessage(),
                    "Correct the uses path");
            return null;
        }
    }

    private void resolveDeclaredSource(Path file, Document document, Declaration declaration,
                                       String pathKey, SourceKind kind, boolean javaType) {
        String reference = declaration.reference();
        if (javaType) reference = reference.replace('.', '/') + ".java";
        List<String> configured = document.paths().getOrDefault(pathKey, DEFAULT_PATHS.get(pathKey));
        boolean explicit = document.paths().containsKey(pathKey);
        Set<Path> matches = new HashSet<>();
        boolean escaped = false;
        for (String directory : configured) {
            try {
                Path base = root.resolve(directory).normalize();
                if (!explicit && !base.startsWith(root)) continue;
                Path candidate = base.resolve(reference).normalize();
                if (!candidate.startsWith(base)) { escaped = true; continue; }
                if (!Files.isRegularFile(candidate)) continue;
                Path actualBase = Files.isDirectory(base) ? base.toRealPath() : base;
                if (!explicit && !actualBase.startsWith(root)) { escaped = true; continue; }
                Path actual = candidate.toRealPath();
                if (!actual.startsWith(actualBase)) { escaped = true; continue; }
                matches.add(actual);
            } catch (IOException | InvalidPathException exception) {
                error("JCM_SOURCE_INVALID", declaration.token().span(file), "Cannot resolve source path",
                        reference + ": " + exception.getMessage(), "Correct the source path");
                return;
            }
        }
        if (escaped) {
            error("JCM_PATH_ESCAPE", declaration.token().span(file), "Source path leaves its search root",
                    reference, "Use a path inside the project root or an explicit source-path directory");
            return;
        }
        if (matches.size() > 1) {
            error("JCM_SOURCE_AMBIGUOUS", declaration.token().span(file), "Source resolves to multiple files",
                    matches.stream().map(Path::toString).sorted().toList().toString(),
                    "Use an explicit, unique source path");
            return;
        }
        if (matches.isEmpty()) {
            if (javaType) {
                if (resolveClassArtifact(file, document, declaration)) return;
                diagnostics.add(new Diagnostic("JCM_JAVA_UNRESOLVED", Severity.WARNING, Phase.PROJECT_DISCOVERY,
                        declaration.token().span(file), null, null, "Artifact Java source is not available",
                        declaration.reference(), "Add a Java source path or resolve the class in Phase 3"));
            } else {
                error("JCM_SOURCE_MISSING", declaration.token().span(file), "Declared source file is missing",
                        declaration.reference(), "Add the file or correct its source path");
            }
            return;
        }
        Path target = matches.iterator().next();
        edges.add(new ProjectEdge(file, target, declaration.kind(), declaration.token().span(file)));
        addSource(target, kind, declaration.token().span(file));
    }

    private boolean resolveClassArtifact(Path file, Document document, Declaration declaration) {
        String classEntry = declaration.reference().replace('.', '/') + ".class";
        boolean explicit = document.paths().containsKey("class-path");
        List<String> configured = document.paths().getOrDefault("class-path", DEFAULT_PATHS.get("class-path"));
        Map<Path, SourceKind> matches = new HashMap<>();
        for (String entry : configured) {
            try {
                Path configuredPath = root.resolve(entry).normalize();
                if (!explicit && !configuredPath.startsWith(root)) continue;
                if (Files.isDirectory(configuredPath)) {
                    Path realDir = configuredPath.toRealPath();
                    if (!explicit && !realDir.startsWith(root)) {
                        error("JCM_PATH_ESCAPE", declaration.token().span(file), "Classpath leaves project root",
                                entry, "Use a classpath inside the project root or configure an explicit external path");
                        return true;
                    }
                    Path classFile = configuredPath.resolve(classEntry).normalize();
                    if (Files.isRegularFile(classFile)) {
                        Path actual = classFile.toRealPath();
                        if (!actual.startsWith(realDir)) {
                            error("JCM_PATH_ESCAPE", declaration.token().span(file), "Class file escapes classpath directory",
                                    classFile.toString(), "Remove the path traversal or symlink");
                            return true;
                        }
                        matches.put(actual, SourceKind.CLASS);
                    }
                    try (Stream<Path> files = Files.list(configuredPath)) {
                        for (Path jar : files.filter(path -> path.toString().endsWith(".jar")).toList()) {
                            addJarMatch(jar, classEntry, explicit, matches, file, declaration);
                        }
                    }
                } else if (Files.isRegularFile(configuredPath) && configuredPath.toString().endsWith(".jar")) {
                    addJarMatch(configuredPath, classEntry, explicit, matches, file, declaration);
                }
            } catch (IOException | InvalidPathException exception) {
                error("JCM_CLASSPATH_INVALID", declaration.token().span(file), "Cannot inspect classpath",
                        entry + ": " + exception.getMessage(), "Correct the class-path entry");
                return true;
            }
        }
        if (matches.size() > 1) {
            error("JCM_SOURCE_AMBIGUOUS", declaration.token().span(file), "Artifact class is present in multiple classpath entries",
                    matches.keySet().stream().map(Path::toString).sorted().toList().toString(),
                    "Use a class-path with one definition of this class");
            return true;
        }
        if (matches.isEmpty()) return false;
        Map.Entry<Path, SourceKind> selected = matches.entrySet().iterator().next();
        edges.add(new ProjectEdge(file, selected.getKey(), ProjectEdgeKind.ARTIFACT_SOURCE,
                declaration.token().span(file)));
        addSource(selected.getKey(), selected.getValue(), declaration.token().span(file));
        return true;
    }

    private void addJarMatch(Path jar, String classEntry, boolean explicit, Map<Path, SourceKind> matches,
                             Path file, Declaration declaration) throws IOException {
        Path actual = jar.toRealPath();
        if (!explicit && !actual.startsWith(root)) {
            error("JCM_PATH_ESCAPE", declaration.token().span(file), "Classpath JAR leaves project root",
                    jar.toString(), "Keep classpath JARs inside the project root");
            return;
        }
        try (JarFile archive = new JarFile(actual.toFile())) {
            if (archive.getJarEntry(classEntry) != null) matches.put(actual, SourceKind.JAR);
        }
    }

    private void addSource(Path file, SourceKind kind, SourceSpan location) {
        if (sources.containsKey(file)) return;
        try {
            sources.put(file, SourceFile.read(file, kind));
        } catch (IOException exception) {
            error("JCM_IO", location, "Cannot read discovered source", file + ": " + exception.getMessage(),
                    "Check source file permissions");
        }
    }

    private Document parse(Path file) {
        try {
            List<JcmLexer.Token> tokens = JcmLexer.scan(Files.readString(file, StandardCharsets.UTF_8));
            if (tokens.size() < 4 || !tokens.getFirst().text().equals("mas")
                    || tokens.get(1).text().isBlank()) {
                error("JCM_HEADER", tokens.isEmpty() ? null : tokens.getFirst().span(file),
                        "Invalid JCM mas header", file.toString(), "Start the file with mas <name> { ... }");
                return null;
            }
            int open = -1;
            for (int i = 2; i < tokens.size(); i++) {
                if (tokens.get(i).text().equals("{")) { open = i; break; }
            }
            if (open < 0) {
                error("JCM_HEADER", tokens.getFirst().span(file), "JCM mas body is missing", file.toString(),
                        "Add an opening and closing brace");
                return null;
            }
            List<JcmLexer.Token> includes = new ArrayList<>();
            if (open > 2) {
                if (!tokens.get(2).text().equals("uses")) {
                    error("JCM_HEADER", tokens.get(2).span(file), "Unknown mas header clause",
                            tokens.get(2).text(), "Use mas <name> uses <project> { ... }");
                    return null;
                }
                for (int i = 3; i < open; i++) {
                    if (!tokens.get(i).text().equals(",")) includes.add(tokens.get(i));
                }
                if (includes.isEmpty()) {
                    error("JCM_HEADER", tokens.get(2).span(file), "uses requires an included project",
                            file.toString(), "Name at least one .jcm project after uses");
                    return null;
                }
            }
            Map<String, List<String>> paths = new LinkedHashMap<>();
            List<Declaration> declarations = new ArrayList<>();
            int depth = 1;
            boolean pendingWorkspace = false, inWorkspace = false;
            for (int i = open + 1; i < tokens.size(); i++) {
                JcmLexer.Token token = tokens.get(i);
                String text = token.text();
                if (text.equals("{")) {
                    if (depth == 1 && pendingWorkspace) inWorkspace = true;
                    depth++; pendingWorkspace = false; continue;
                }
                if (text.equals("}")) {
                    depth--;
                    if (depth == 1) inWorkspace = false;
                    if (depth < 0) break;
                    continue;
                }
                if (depth == 1 && text.equals("workspace")) pendingWorkspace = true;
                if (depth == 1 && (text.equals("asl-path") || text.equals("org-path")
                        || text.equals("java-path") || text.equals("class-path"))) {
                    if (i + 1 >= tokens.size() || !tokens.get(i + 1).text().equals(":")) {
                        error("JCM_PATH_INVALID", token.span(file), "Source path directive needs ':'",
                                text, "Write " + text + ": <directory>");
                        continue;
                    }
                    List<String> values = new ArrayList<>();
                    int j = i + 2;
                    while (j < tokens.size() && !tokens.get(j).text().equals("}")
                            && !TOP_LEVEL.contains(tokens.get(j).text())) {
                        if (!tokens.get(j).text().equals(",")) values.add(tokens.get(j).text());
                        j++;
                    }
                    if (values.isEmpty()) {
                        error("JCM_PATH_INVALID", token.span(file), "Source path directive has no directory",
                                text, "Provide at least one directory after " + text + ":");
                    }
                    paths.put(text, List.copyOf(values));
                    i = j - 1;
                    continue;
                }
                if (depth == 1 && (text.equals("agent") || text.equals("organisation"))) {
                    if (i + 1 >= tokens.size()) continue;
                    String name = tokens.get(i + 1).text();
                    String suffix = text.equals("agent") ? ".asl" : ".xml";
                    String source = name + suffix;
                    if (i + 3 < tokens.size() && tokens.get(i + 2).text().equals(":")) {
                        source = tokens.get(i + 3).text();
                    }
                    declarations.add(new Declaration(text.equals("agent") ? ProjectEdgeKind.AGENT_SOURCE
                            : ProjectEdgeKind.ORGANISATION_SOURCE, source, token));
                }
                if (depth == 2 && inWorkspace && text.equals("artifact") && i + 3 < tokens.size()
                        && tokens.get(i + 2).text().equals(":")) {
                    declarations.add(new Declaration(ProjectEdgeKind.ARTIFACT_SOURCE, tokens.get(i + 3).text(), token));
                }
            }
            if (depth != 0) {
                error("JCM_BRACES", tokens.getFirst().span(file), "Unbalanced JCM braces", file.toString(),
                        "Balance the mas and nested declaration braces");
            }
            return new Document(tokens.get(1).text(), includes, declarations, paths);
        } catch (IOException exception) {
            error("JCM_IO", null, "Cannot read JCM file", file + ": " + exception.getMessage(),
                    "Check file encoding and permissions");
            return null;
        }
    }

    private void error(String code, SourceSpan location, String message, String evidence, String remediation) {
        diagnostics.add(new Diagnostic(code, Severity.ERROR, Phase.PROJECT_DISCOVERY,
                location, null, null, message, evidence, remediation));
    }

    private record Declaration(ProjectEdgeKind kind, String reference, JcmLexer.Token token) { }
    private record Document(String name, List<JcmLexer.Token> includes,
                            List<Declaration> declarations, Map<String, List<String>> paths) { }
}
