package org.tzi.use.plugins.jacamo.extraction;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
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
            AttributeValue value = artifact.attributes.get("type");
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
            String source = context.read(path);
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
                            artifact.attributes.get("type")))) {
                        boolean confirmed = type.getExtendsClause() != null
                                && type.getExtendsClause().toString().endsWith("Artifact");
                        artifact.sourceFacts.put("artifactTypeConfirmed", new AttributeValue.Bool(confirmed));
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
        String operationKind = annotationKind(method.getModifiers().getAnnotations());
        int line = line(unit, positions, method);
        ElementDraft operation = null;
        if (operationKind != null && !operationKind.equals("GUARD")) {
            String name = method.getName().toString();
            List<String> owner = new ArrayList<>(artifact.id.ownerPath());
            owner.add(artifact.name);
            operation = context.element(MetamodelKind.Operation, name, owner, path, line, column(unit, positions, method),
                    "jdk-java-parser", snippet(context, path, unit, positions, method));
            operation.sourceFacts.put("className", artifact.attributes.get("type"));
            operation.attributes.put("name", new AttributeValue.Text(name));
            operation.attributes.put("arity", new AttributeValue.IntegerNumber(method.getParameters().size()));
            operation.sourceFacts.put("operationCategory", new AttributeValue.Text(operationKind));
            operation.sourceFacts.put("parameters", new AttributeValue.Text(parameters(method.getParameters())));
            operation.sourceFacts.put("returnType", new AttributeValue.Text(
                    method.getReturnType() == null ? "void" : method.getReturnType().toString()));
            artifact.references.add(new SemanticReference("operations", name, operation.id));
            String guard = annotationValue(method.getModifiers().getAnnotations(), "OPERATION", "guard");
            if (guard != null) operation.sourceFacts.put("guardedBy", new AttributeValue.Text(guard));
        }
        if ("GUARD".equals(operationKind)) {
            String prefix = "guard:" + method.getName() + ":";
            artifact.sourceFacts.put(prefix + "parameters", new AttributeValue.Text(parameters(method.getParameters())));
            artifact.sourceFacts.put(prefix + "returnType", new AttributeValue.Text(method.getReturnType().toString()));
            artifact.sourceFacts.put(prefix + "source", new AttributeValue.Text(method.toString()));
            context.addProvenance(artifact, path, line, column(unit, positions, method), "jdk-java-parser", snippet(context, path, unit, positions, method));
            if (method.getBody() != null && method.getBody().getStatements().size() == 1
                    && method.getBody().getStatements().getFirst() instanceof ReturnTree statement
                    && pureGuardExpression(statement.getExpression())) {
                artifact.sourceFacts.put(prefix + "expression", new AttributeValue.Text(statement.getExpression().toString()));
            } else {
                artifact.sourceFacts.put(prefix + "unsupported", new AttributeValue.Text("UNSUPPORTED_GUARD_BODY: requires one pure return expression"));
                context.diagnostic("CARTAGO_GUARD_BODY_UNSUPPORTED", Severity.WARNING, Phase.PARSING,
                        artifact.provenance.getFirst().span(), artifact.id.value(), "Guard body cannot be reduced to one expression",
                        method.toString(), "Retain source; provide an explicit contract if needed");
            }
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
                    owningOperation.sourceFacts.put("signalExpression", new AttributeValue.Text(invocation.toString()));
                    extractSignal(context, artifact, owningOperation, path, unit, positions, invocation);
                } else if (owningOperation != null && (name.equals("await") || name.equals("await_time"))) {
                    owningOperation.sourceFacts.put(name.equals("await") ? "awaitExpression" : "await_timeExpression",
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
        ElementDraft property = context.element(MetamodelKind.Property, name, owner, path,
                line(unit, positions, invocation), column(unit, positions, invocation), "jdk-java-parser", snippet(context, path, unit, positions, invocation));
        property.attributes.put("name", new AttributeValue.Text(name));
        property.attributes.put("arity", new AttributeValue.IntegerNumber(invocation.getArguments().size() - 1));
        if (invocation.getArguments().size() > 1) {
            ExpressionTree initial = invocation.getArguments().get(1);
            property.sourceFacts.put("initialExpression", new AttributeValue.Text(initial.toString()));
            property.sourceFacts.put("resolvedType", new AttributeValue.Text(literalType(initial)));
        }
        artifact.references.add(new SemanticReference("properties", name, property.id));
    }

    private void extractPort(ExtractionContext context, ElementDraft artifact, Path path,
                             CompilationUnitTree unit, SourcePositions positions, MethodInvocationTree invocation) {
        if (invocation.getArguments().isEmpty() || !(invocation.getArguments().getFirst() instanceof LiteralTree literal)
                || !(literal.getValue() instanceof String name)) return;
        artifact.sourceFacts.put("port@" + line(unit, positions, invocation), new AttributeValue.Text(invocation.toString()));
        context.addProvenance(artifact, path, line(unit, positions, invocation), column(unit, positions, invocation), "jdk-java-parser", snippet(context, path, unit, positions, invocation));
    }

    private void extractSignal(ExtractionContext context, ElementDraft artifact, ElementDraft operation, Path path,
                               CompilationUnitTree unit, SourcePositions positions, MethodInvocationTree invocation) {
        if (invocation.getArguments().isEmpty() || !(invocation.getArguments().getFirst() instanceof LiteralTree literal)
                || !(literal.getValue() instanceof String name)) {
            context.diagnostic("CARTAGO_DYNAMIC_SIGNAL", Severity.WARNING, Phase.PARSING, operation.provenance.getFirst().span(),
                    operation.id.value(), "Dynamic signal name remains source-only", invocation.toString(), "Provide literal source evidence");
            return;
        }
        List<String> owner = new ArrayList<>(artifact.id.ownerPath()); owner.add(artifact.name);
        int arity = invocation.getArguments().size() - 1;
        ElementDraft signal = context.elements.stream().filter(e -> e.kind == MetamodelKind.Signal && e.name.equals(name)
                && e.id.ownerPath().equals(owner)).findFirst().orElse(null);
        if (signal != null && !new AttributeValue.IntegerNumber(arity).equals(signal.attributes.get("arity"))) {
            context.diagnostic("CARTAGO_SIGNAL_ARITY_CONFLICT", Severity.ERROR, Phase.PARSING, operation.provenance.getFirst().span(),
                    operation.id.value(), "Signal name has conflicting source arities", invocation.toString(), "Resolve signal identity explicitly");
            return;
        }
        if (signal == null) {
            signal = context.element(MetamodelKind.Signal, name, owner, path, line(unit, positions, invocation), column(unit, positions, invocation), "jdk-java-parser", snippet(context, path, unit, positions, invocation));
            signal.attributes.put("name", new AttributeValue.Text(name));
            signal.attributes.put("arity", new AttributeValue.IntegerNumber(arity));
        }
        final var target = signal;
        if (operation.references.stream().noneMatch(r -> target.id.equals(r.targetId())))
            operation.references.add(new SemanticReference("signals", name, signal.id));
    }

    private String annotationKind(List<? extends AnnotationTree> annotations) {
        for (AnnotationTree annotation : annotations) {
            String name = annotation.getAnnotationType().toString();
            name = name.substring(name.lastIndexOf('.') + 1);
            if (name.equals("OPERATION")) return "OPERATION";
            if (name.equals("GUARD")) return "GUARD";
            if (name.equals("INTERNAL_OPERATION")) return "INTERNAL_OPERATION";
            if (name.equals("LINKED_OPERATION")) return "LINKED_OPERATION";
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

    private boolean pureGuardExpression(ExpressionTree expression) {
        if (expression == null) return false;
        return switch (expression.getKind()) {
            case IDENTIFIER, INT_LITERAL, BOOLEAN_LITERAL -> true;
            case PARENTHESIZED -> pureGuardExpression(((com.sun.source.tree.ParenthesizedTree) expression).getExpression());
            case LOGICAL_COMPLEMENT, UNARY_MINUS -> pureGuardExpression(((com.sun.source.tree.UnaryTree) expression).getExpression());
            case CONDITIONAL_AND, CONDITIONAL_OR, AND, OR, EQUAL_TO, NOT_EQUAL_TO,
                 LESS_THAN, GREATER_THAN, LESS_THAN_EQUAL, GREATER_THAN_EQUAL,
                 PLUS, MINUS, MULTIPLY, DIVIDE -> {
                var binary = (com.sun.source.tree.BinaryTree) expression;
                yield pureGuardExpression(binary.getLeftOperand()) && pureGuardExpression(binary.getRightOperand());
            }
            default -> false;
        };
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

    private int column(CompilationUnitTree unit, SourcePositions positions, com.sun.source.tree.Tree tree) {
        long start = positions.getStartPosition(unit, tree);
        return start < 0 ? 1 : Math.toIntExact(unit.getLineMap().getColumnNumber(start));
    }
    private String snippet(ExtractionContext context, Path path, CompilationUnitTree unit, SourcePositions positions, com.sun.source.tree.Tree tree) {
        try {
            return context.read(path).substring(Math.toIntExact(positions.getStartPosition(unit, tree)), Math.toIntExact(positions.getEndPosition(unit, tree)));
        } catch (IOException e) { throw new IllegalArgumentException("Cannot preserve Java source span", e); }
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
