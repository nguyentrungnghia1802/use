package org.tzi.use.plugins.jacamo.extraction;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.tools.JavaCompiler;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.tzi.use.plugins.jacamo.diagnostics.Phase;
import org.tzi.use.plugins.jacamo.diagnostics.Severity;
import org.tzi.use.plugins.jacamo.project.SourceKind;
import org.tzi.use.plugins.jacamo.semantic.AttributeValue;
import org.tzi.use.plugins.jacamo.semantic.MetamodelKind;
import org.tzi.use.plugins.jacamo.semantic.SemanticReference;

/** Uses the JDK Java parser only; project classes are never loaded or executed. */
final class CartagoSourceExtractor {
    void parse(ExtractionContext context) {
        List<ElementDraft> artifacts = context.elements.stream().filter(element -> element.kind == MetamodelKind.Artifact).toList();
        for (ElementDraft artifact : artifacts) {
            AttributeValue value = artifact.attributes.get("className");
            if (!(value instanceof AttributeValue.Text type)) continue;
            String suffix = type.value().replace('.', '/') + ".java";
            List<Path> matches = context.graph.sources().stream().filter(source -> source.kind() == SourceKind.JAVA
                    && source.path().toString().replace('\\', '/').endsWith(suffix)).map(source -> source.path()).toList();
            if (matches.size() == 1) parseArtifact(context, artifact, matches.getFirst());
            else if (matches.size() > 1) context.diagnostic("CARTAGO_SOURCE_AMBIGUOUS", Severity.ERROR,
                    Phase.PARSING, artifact.provenance.getFirst().span(), artifact.id.value(),
                    "Artifact Java source is ambiguous", matches.toString(), "Configure a unique java-path");
            else if (context.graph.sources().stream().anyMatch(source ->
                    source.kind() == SourceKind.CLASS || source.kind() == SourceKind.JAR)) {
                context.diagnostic("CARTAGO_BYTECODE_UNSUPPORTED", Severity.WARNING, Phase.PARSING,
                        artifact.provenance.getFirst().span(), artifact.id.value(),
                        "Artifact type resolves only to bytecode; static members remain unresolved",
                        type.value(), "Provide matching Java source or keep runtime-dependent semantics unresolved");
            }
        }
    }

    private void parseArtifact(ExtractionContext context, ElementDraft artifact, Path path) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            context.diagnostic("CARTAGO_JAVA_COMPILER_MISSING", Severity.ERROR, Phase.PARSING,
                    artifact.provenance.getFirst().span(), artifact.id.value(), "JDK Java parser is unavailable",
                    System.getProperty("java.home"), "Run the plugin with a full JDK"); return;
        }
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            var file = new StringSource(path.toUri(), source);
            DiagnosticCollector<JavaFileObject> compilerDiagnostics = new DiagnosticCollector<>();
            JavacTask task = (JavacTask) compiler.getTask(null, null, compilerDiagnostics,
                    List.of("-proc:none"), null, List.of(file));
            CompilationUnitTree unit = task.parse().iterator().next();
            if (compilerDiagnostics.getDiagnostics().stream().anyMatch(diagnostic ->
                    diagnostic.getKind() == javax.tools.Diagnostic.Kind.ERROR)) {
                context.diagnostic("CARTAGO_JAVA_INVALID", Severity.ERROR, Phase.PARSING,
                        artifact.provenance.getFirst().span(), artifact.id.value(), "Artifact Java syntax is invalid",
                        compilerDiagnostics.getDiagnostics().toString(), "Correct Java syntax before importing");
                return;
            }
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            new TreeScanner<Void, Void>() {
                @Override public Void visitClass(ClassTree type, Void unused) {
                    if (type.getSimpleName().toString().equals(simpleName((AttributeValue.Text)
                            artifact.attributes.get("className")))) {
                        boolean confirmed = type.getExtendsClause() != null
                                && type.getExtendsClause().toString().endsWith("Artifact");
                        artifact.attributes.put("artifactTypeConfirmed", new AttributeValue.Bool(confirmed));
                        if (!confirmed) context.diagnostic("CARTAGO_TYPE_UNCONFIRMED", Severity.WARNING,
                                Phase.PARSING, artifact.provenance.getFirst().span(), artifact.id.value(),
                                "Java type is not statically confirmed as a CArtAgO Artifact subtype",
                                type.getSimpleName().toString(), "Extend cartago.Artifact or provide explicit parser evidence");
                    }
                    return super.visitClass(type, unused);
                }
                @Override public Void visitMethod(MethodTree method, Void unused) {
                    extractMethod(context, artifact, path, unit, positions, method);
                    return null;
                }
            }.scan(unit, null);
        } catch (IOException | RuntimeException exception) {
            context.diagnostic("CARTAGO_JAVA_INVALID", Severity.ERROR, Phase.PARSING,
                    artifact.provenance.getFirst().span(), artifact.id.value(), "Cannot parse Artifact Java source",
                    path + ": " + exception.getMessage(), "Correct Java syntax or source encoding");
        }
    }

    private void extractMethod(ExtractionContext context, ElementDraft artifact, Path path,
                               CompilationUnitTree unit, SourcePositions positions, MethodTree method) {
        MetamodelKind operationKind = annotationKind(method.getModifiers().getAnnotations());
        int line = line(unit, positions, method);
        ElementDraft operation = null;
        if (operationKind != null) {
            String name = method.getName().toString();
            List<String> owner = new ArrayList<>(artifact.id.ownerPath());
            owner.add(artifact.name);
            operation = context.element(operationKind, name, owner, path, line, 1,
                    "jdk-java-parser", method.toString());
            operation.attributes.put("className", artifact.attributes.get("className"));
            operation.attributes.put("parameters", new AttributeValue.Text(parameters(method.getParameters())));
            operation.attributes.put("returnType", new AttributeValue.Text(
                    method.getReturnType() == null ? "void" : method.getReturnType().toString()));
            artifact.references.add(new SemanticReference("operation", name, operation.id));
            String guard = annotationValue(method.getModifiers().getAnnotations(), "OPERATION", "guard");
            if (guard != null) operation.references.add(new SemanticReference("guardedBy", guard, null));
        }
        ElementDraft owningOperation = operation;
        new TreeScanner<Void, Void>() {
            @Override public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
                String select = invocation.getMethodSelect().toString();
                String name = select.substring(select.lastIndexOf('.') + 1);
                if (name.equals("defineObsProperty") || name.equals("defineObservableProperty")) {
                    extractProperty(context, artifact, path, unit, positions, invocation);
                } else if (name.equals("definePort")) {
                    extractPort(context, artifact, path, unit, positions, invocation);
                } else if (owningOperation != null && name.equals("signal")) {
                    owningOperation.attributes.put("signalExpression", new AttributeValue.Text(invocation.toString()));
                } else if (owningOperation != null && (name.equals("await") || name.equals("await_time"))) {
                    owningOperation.attributes.put(name.equals("await") ? "awaitExpression" : "await_timeExpression",
                            new AttributeValue.Text(invocation.toString()));
                }
                return super.visitMethodInvocation(invocation, unused);
            }
        }.scan(method.getBody(), null);
    }

    private void extractProperty(ExtractionContext context, ElementDraft artifact, Path path,
                                 CompilationUnitTree unit, SourcePositions positions, MethodInvocationTree invocation) {
        if (invocation.getArguments().isEmpty() || !(invocation.getArguments().getFirst() instanceof LiteralTree literal)
                || !(literal.getValue() instanceof String name)) {
            int line = line(unit, positions, invocation);
            context.diagnostic("CARTAGO_DYNAMIC_PROPERTY", Severity.WARNING, Phase.PARSING,
                    new org.tzi.use.plugins.jacamo.project.SourceSpan(path, line, 1, line, 1), artifact.id.value(),
                    "Dynamic observable property cannot be identified statically", invocation.toString(),
                    "Provide a literal property name or resolve it from runtime evidence"); return;
        }
        List<String> owner = new ArrayList<>(artifact.id.ownerPath()); owner.add(artifact.name);
        ElementDraft property = context.element(MetamodelKind.ObsProperty, name, owner, path,
                line(unit, positions, invocation), 1, "jdk-java-parser", invocation.toString());
        property.attributes.put("Name", new AttributeValue.Text(name));
        if (invocation.getArguments().size() > 1) {
            ExpressionTree initial = invocation.getArguments().get(1);
            property.attributes.put("initialExpression", new AttributeValue.Text(initial.toString()));
            property.attributes.put("resolvedType", new AttributeValue.Text(literalType(initial)));
        }
        artifact.references.add(new SemanticReference("obsProperty", name, property.id));
    }

    private void extractPort(ExtractionContext context, ElementDraft artifact, Path path,
                             CompilationUnitTree unit, SourcePositions positions, MethodInvocationTree invocation) {
        if (invocation.getArguments().isEmpty() || !(invocation.getArguments().getFirst() instanceof LiteralTree literal)
                || !(literal.getValue() instanceof String name)) return;
        List<String> owner = new ArrayList<>(artifact.id.ownerPath()); owner.add(artifact.name);
        ElementDraft port = context.element(MetamodelKind.Port, name, owner, path,
                line(unit, positions, invocation), 1, "jdk-java-parser", invocation.toString());
        port.attributes.put("Name", new AttributeValue.Text(name));
        artifact.references.add(new SemanticReference("port", name, port.id));
    }

    private MetamodelKind annotationKind(List<? extends AnnotationTree> annotations) {
        for (AnnotationTree annotation : annotations) {
            String name = annotation.getAnnotationType().toString();
            name = name.substring(name.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
            if (name.equals("OPERATION")) return MetamodelKind.Operation;
            if (name.equals("GUARD")) return MetamodelKind.GuardOperation;
            if (name.equals("INTERNAL_OPERATION")) return MetamodelKind.InternalOperation;
            if (name.equals("LINKED_OPERATION")) return MetamodelKind.LinkedOperation;
        }
        return null;
    }

    private String annotationValue(List<? extends AnnotationTree> annotations, String annotationName, String key) {
        for (AnnotationTree annotation : annotations) {
            String name = annotation.getAnnotationType().toString();
            if (!name.substring(name.lastIndexOf('.') + 1).equalsIgnoreCase(annotationName)) continue;
            for (ExpressionTree argument : annotation.getArguments()) {
                if (argument instanceof AssignmentTree assignment && assignment.getVariable().toString().equals(key)
                        && assignment.getExpression() instanceof LiteralTree literal
                        && literal.getValue() instanceof String value) return value;
            }
        }
        return null;
    }

    private String parameters(List<? extends VariableTree> parameters) {
        return parameters.stream().map(parameter -> parameter.getType() + " " + parameter.getName())
                .reduce((left, right) -> left + "," + right).orElse("");
    }

    private String literalType(ExpressionTree tree) {
        if (tree instanceof LiteralTree literal) {
            Object value = literal.getValue();
            return value == null ? "undefined" : value.getClass().getSimpleName();
        }
        return "unresolved";
    }

    private String simpleName(AttributeValue.Text type) {
        String name = type.value();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private int line(CompilationUnitTree unit, SourcePositions positions, com.sun.source.tree.Tree tree) {
        long position = positions.getStartPosition(unit, tree);
        return position < 0 ? 1 : Math.toIntExact(unit.getLineMap().getLineNumber(position));
    }

    private static final class StringSource extends SimpleJavaFileObject {
        private final String content;
        StringSource(URI uri, String content) { super(uri, Kind.SOURCE); this.content = content; }
        @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) { return content; }
    }
}
