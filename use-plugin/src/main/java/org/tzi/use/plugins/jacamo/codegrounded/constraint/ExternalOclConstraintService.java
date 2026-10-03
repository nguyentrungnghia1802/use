package org.tzi.use.plugins.jacamo.codegrounded.constraint;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.Token;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.analysis.coverage.CoverageCalculationVisitor;
import org.tzi.use.parser.ParseErrorHandler;
import org.tzi.use.parser.generator.ASSLCompiler;
import org.tzi.use.parser.generator.GeneratorLexer;
import org.tzi.use.plugins.jacamo.verification.VerificationOutcome;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MInvalidModelException;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.ExpAllInstances;
import org.tzi.use.uml.ocl.expr.ExpExists;
import org.tzi.use.uml.ocl.expr.ExpStdOp;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.events.ClassInvariantsLoadedEvent;
import org.tzi.use.uml.sys.events.ClassInvariantsUnloadedEvent;
import org.tzi.use.uml.sys.events.tags.EventContext;

/** Uses USE's invariant compiler without installing any declaration until the whole profile is valid.
 * All calls in production are serialized by RuntimeVerificationCoordinator. */
public final class ExternalOclConstraintService {
    public record RegisteredConstraint(String constraintId, String contextClass, String sourceFile,
            String sourceHash, String modelRevision, boolean enabled, boolean negated, Set<String> requiredClasses,
            Set<String> requiredRules, Set<String> requiredSources) {
        public RegisteredConstraint {
            requiredClasses = Set.copyOf(requiredClasses);
            requiredRules = Set.copyOf(requiredRules);
            requiredSources = Set.copyOf(requiredSources);
        }
        public RegisteredConstraint(String constraintId,String contextClass,String sourceFile,String sourceHash,
                String modelRevision,boolean enabled,Set<String> requiredClasses,Set<String> requiredRules,
                Set<String> requiredSources) {
            this(constraintId,contextClass,sourceFile,sourceHash,modelRevision,enabled,false,requiredClasses,requiredRules,requiredSources);
        }
    }
    public record Profile(String sourceFile, String sourceHash, String source, String modelRevision,
                          List<RegisteredConstraint> constraints) {
        public Profile { constraints = List.copyOf(constraints); }
    }
    public record Outcome(String constraintId, String contextClass, VerificationOutcome outcome,
                          String diagnostic, String expression) { }

    private static final Map<String, String> RUNTIME_RULES = Map.of(
            "Environment", "C01", "Workspace", "C02", "ArtifactType", "C03", "Artifact", "C04",
            "LiveObservableProperty", "C08", "ObservablePropertySnapshot", "C09",
            "CartagoAgentIdentity", "C12", "Agent", "J02");
    private final MSystem system;
    private final Map<String, MClassInvariant> owned = new LinkedHashMap<>();
    private Profile profile;

    public ExternalOclConstraintService(MSystem system) { this.system = java.util.Objects.requireNonNull(system); }
    public MSystem system() { return system; }
    public Profile profile() {
        if (profile != null && profile.constraints().stream().anyMatch(item ->
                owned.get(item.constraintId()).isActive() != item.enabled()
                        || owned.get(item.constraintId()).isNegated() != item.negated()))
            profile = new Profile(profile.sourceFile(), profile.sourceHash(), profile.source(), profile.modelRevision(),
                    owned.values().stream().map(inv -> registration(inv, profile.sourceFile(), profile.sourceHash(), profile.modelRevision())).toList());
        return profile;
    }

    public Profile install(Path path, String revision) {
        try {
            Path source = path.toAbsolutePath().normalize();
            if (!Files.isRegularFile(source) || Files.size(source) > 1024 * 1024)
                throw new IllegalArgumentException("EXTERNAL_OCL_FILE_INVALID_OR_TOO_LARGE:" + source);
            return installSource(source.toString(), Files.readString(source, StandardCharsets.UTF_8), revision);
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("EXTERNAL_OCL_IO:" + path, error);
        }
    }

    public Profile installSource(String sourceFile, String source, String revision) {
        return installSource(sourceFile, source, revision, Map.of());
    }

    public Profile installSource(String sourceFile, String source, String revision, Map<String, Boolean> enabled) {
        return installSource(sourceFile, source, revision, enabled, Map.of());
    }

    public Profile installSource(String sourceFile, String source, String revision, Map<String, Boolean> enabled,
                                 Map<String, Boolean> negated) {
        if (sourceFile == null || source == null || revision == null || revision.isBlank())
            throw new IllegalArgumentException("EXTERNAL_OCL_SOURCE_REQUIRED");
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 1024 * 1024) throw new IllegalArgumentException("EXTERNAL_OCL_TOO_LARGE");
        StringWriter diagnostics = new StringWriter();
        PrintWriter errors = new PrintWriter(diagnostics, true);
        requireNamedDeclarations(source, sourceFile, errors);
        Collection<MClassInvariant> compiled = ASSLCompiler.compileInvariants(system.model(),
                new ByteArrayInputStream(bytes), sourceFile, errors);
        if (compiled == null || compiled.isEmpty() || !diagnostics.toString().isBlank())
            throw new IllegalArgumentException("EXTERNAL_OCL_COMPILE_REJECTED:" + diagnostics);
        Map<String, MClassInvariant> candidate = new LinkedHashMap<>();
        for (MClassInvariant invariant : compiled) {
            String id = invariant.qualifiedName();
            if (candidate.putIfAbsent(id, invariant) != null)
                throw new IllegalArgumentException("EXTERNAL_OCL_DUPLICATE:" + id);
            boolean conflicts = system.model().classInvariants().stream().anyMatch(existing ->
                    existing.qualifiedName().equals(id) && !owned.containsKey(id));
            if (conflicts) throw new IllegalArgumentException("EXTERNAL_OCL_NAME_CONFLICT:" + id);
        }
        String hash = sha256(bytes);
        candidate.forEach((id, invariant) -> {
            var existing = owned.get(id);
            if (existing != null) { invariant.setActive(existing.isActive()); invariant.setNegated(existing.isNegated()); }
            if (enabled.containsKey(id)) invariant.setActive(enabled.get(id));
            if (negated.containsKey(id)) invariant.setNegated(negated.get(id));
        });
        List<RegisteredConstraint> registry = candidate.values().stream().map(invariant ->
                registration(invariant, sourceFile, hash, revision)).toList();
        // Compilation and conflict checking above do not mutate MModel. Only owned loaded invariants
        // may be removed; on an unexpected installation failure restore the original profile.
        Map<String, MClassInvariant> previous = new LinkedHashMap<>(owned);
        previous.keySet().forEach(system.model()::removeClassInvariant);
        List<String> added = new ArrayList<>();
        try {
            for (var entry : candidate.entrySet()) {
                system.model().addClassInvariant(entry.getValue());
                added.add(entry.getKey());
            }
        } catch (MInvalidModelException error) {
            added.forEach(system.model()::removeClassInvariant);
            try { for (MClassInvariant old : previous.values()) system.model().addClassInvariant(old); }
            catch (MInvalidModelException rollback) { error.addSuppressed(rollback); }
            throw new IllegalStateException("EXTERNAL_OCL_ATOMIC_INSTALL_FAILED", error);
        }
        owned.clear(); owned.putAll(candidate);
        profile = new Profile(sourceFile, hash, source, revision, registry);
        if (!previous.isEmpty()) system.getEventBus().post(new ClassInvariantsUnloadedEvent(
                EventContext.NORMAL_EXECUTION, List.copyOf(previous.values()), true));
        system.getEventBus().post(new ClassInvariantsLoadedEvent(EventContext.NORMAL_EXECUTION,
                List.copyOf(candidate.values()), true));
        return profile;
    }

    public void rebind(String revision) {
        Profile current = profile();
        if (current != null) installSource(current.sourceFile(), current.source(), revision);
    }

    public String constraintSetHash() {
        return sha256(system.model().classInvariants().stream().sorted(Comparator.comparing(MClassInvariant::qualifiedName))
                .map(inv -> inv.qualifiedName() + "|" + inv.isActive() + "|" + inv.isNegated() + "|" + inv.bodyExpression())
                .collect(java.util.stream.Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8));
    }

    public List<Outcome> evaluate(Map<String, Completeness> sources, boolean coverageLost) {
        List<Outcome> outcomes = new ArrayList<>();
        for (MClassInvariant invariant : system.model().classInvariants().stream()
                .sorted(Comparator.comparing(MClassInvariant::qualifiedName)).toList()) {
            String id = (owned.containsKey(invariant.qualifiedName()) ? "EXTERNAL:" : "NATIVE:") + invariant.qualifiedName();
            var dependencies = registration(invariant, "", "", "");
            String skip = !invariant.isActive() ? "CONSTRAINT_DISABLED" : coverageLost ? "COVERAGE_LOST"
                    : dependencies.requiredClasses().contains("LiveObservableProperty") ? "C08_UNAVAILABLE_BY_API"
                    : dependencies.requiredClasses().stream().anyMatch(cls -> "EVIDENCE_ONLY".equals(system.model()
                            .getClass(cls).getAnnotationValue(org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.ANNOTATION,
                                    "runtimeProjection"))) ? "MOISE_DOMAIN_RUNTIME_EVIDENCE_ONLY"
                    : owned.containsKey(invariant.qualifiedName()) && dependencies.requiredSources().stream()
                            .anyMatch(source -> sources.get(source) != Completeness.COMPLETE)
                            ? "REQUIRED_RUNTIME_SOURCE_INCOMPLETE:" + dependencies.requiredSources() : "";
            VerificationOutcome outcome;
            String diagnostic = skip;
            if (!skip.isEmpty()) outcome = VerificationOutcome.SKIPPED;
            else try {
                // USE's expanded invariant converts undefined bodies to false. Inspect the
                // compiled body with USE expressions to retain ERROR versus a genuine violation.
                var variables = ((org.tzi.use.uml.ocl.expr.ExpQuery) invariant.expandedExpression()).getVariableDeclarations();
                var invalidBody = new ExpExists(variables, new ExpAllInstances(invariant.cls()),
                        ExpStdOp.create("oclIsUndefined", new Expression[] { invariant.bodyExpression() }));
                var undefined = new Evaluator().eval(invalidBody, system.state());
                var value = new Evaluator().eval(invariant.flaggedExpression(), system.state());
                if (undefined instanceof BooleanValue hasUndefined && hasUndefined.isTrue()) {
                    outcome = VerificationOutcome.ERROR;
                    diagnostic = "USE_OCL_UNDEFINED_BODY";
                } else if (value instanceof BooleanValue booleanValue) {
                    outcome = booleanValue.isTrue() ? VerificationOutcome.PASS : VerificationOutcome.FAIL;
                    diagnostic = booleanValue.isTrue() ? "true" : "false";
                } else {
                    outcome = VerificationOutcome.ERROR;
                    diagnostic = "USE_OCL_UNDEFINED_OR_INVALID:" + value;
                }
            } catch (Exception error) {
                outcome = VerificationOutcome.ERROR;
                diagnostic = error.getClass().getSimpleName() + ":" + error.getMessage();
            }
            outcomes.add(new Outcome(id, invariant.cls().name(), outcome, diagnostic,
                    invariant.bodyExpression().toString()));
        }
        return List.copyOf(outcomes);
    }

    private RegisteredConstraint registration(MClassInvariant invariant, String file, String hash, String revision) {
        CoverageCalculationVisitor visitor = new CoverageCalculationVisitor(true) {
            // Complete the existing USE visitor's intentionally empty cast/tuple hooks.
            // Dependencies come from compiler AST objects, never names parsed from OCL text.
            @Override public void visitAsType(org.tzi.use.uml.ocl.expr.ExpAsType expression) {
                expression.getSourceExpr().processWithVisitor(this);
                if (expression.getTargetType() instanceof org.tzi.use.uml.mm.MClass cls) addClassCoverage(cls);
            }
            @Override public void visitTupleSelectOp(org.tzi.use.uml.ocl.expr.ExpTupleSelectOp expression) {
                expression.getTupleExp().processWithVisitor(this);
            }
            @Override public void visitObjRef(org.tzi.use.uml.ocl.expr.ExpObjRef expression) {
                if (expression.type() instanceof org.tzi.use.uml.mm.MClass cls) addClassCoverage(cls);
            }
        };
        invariant.flaggedExpression().processWithVisitor(visitor);
        var coverage = visitor.getCoverageData();
        Set<String> classes = new TreeSet<>(); classes.add(invariant.cls().name());
        coverage.getCompleteClassCoverage().keySet().forEach(cls -> classes.add(cls.name()));
        coverage.getAssociationCoverage().keySet().forEach(association ->
                association.associatedClasses().forEach(cls -> classes.add(cls.name())));
        Set<String> rules = new TreeSet<>(), sources = new TreeSet<>();
        for (String cls : classes) {
            var modelClass = system.model().getClass(cls);
            if (modelClass != null && "moise".equals(modelClass.getAnnotationValue(
                    org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.ANNOTATION, "runtimeSource"))) {
                rules.add(modelClass.getAnnotationValue(org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.ANNOTATION, "ruleId"));
                sources.add("moise");
            }
            String rule = RUNTIME_RULES.get(cls);
            if (rule != null) { rules.add(rule); sources.add(rule.startsWith("C") ? "cartago" : "jason"); }
        }
        return new RegisteredConstraint(invariant.qualifiedName(), invariant.cls().name(), file, hash,
                revision, invariant.isActive(), invariant.isNegated(), classes, rules, sources);
    }

    private static void requireNamedDeclarations(String source, String name, PrintWriter errors) {
        // Token validation uses USE's lexer, including its comments/literals and keyword rules.
        // The existing ASSLCompiler remains the sole syntax/type/context compiler.
        GeneratorLexer lexer = new GeneratorLexer(new ANTLRStringStream(source));
        lexer.init(new ParseErrorHandler(name, errors));
        CommonTokenStream stream = new CommonTokenStream(lexer); stream.fill();
        List<Token> tokens = stream.getTokens().stream().map(value -> (Token) value)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL).toList();
        for (int i = 0; i < tokens.size(); i++) if ("inv".equals(tokens.get(i).getText())) {
            if (i + 2 >= tokens.size() || tokens.get(i + 1).getType() != GeneratorLexer.IDENT
                    || tokens.get(i + 2).getType() != GeneratorLexer.COLON)
                throw new IllegalArgumentException("EXTERNAL_OCL_NAMED_INVARIANT_REQUIRED:" + tokens.get(i).getLine());
        }
    }

    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
