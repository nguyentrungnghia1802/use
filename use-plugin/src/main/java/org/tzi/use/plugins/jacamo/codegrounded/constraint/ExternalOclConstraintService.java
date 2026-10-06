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
                          String diagnostic, String expression, String contextObject) {
        public Outcome(String constraintId,String contextClass,VerificationOutcome outcome,String diagnostic,String expression) {
            this(constraintId,contextClass,outcome,diagnostic,expression,"");
        }
        public Outcome {contextObject=contextObject==null?"":contextObject;}
    }

    private static final Map<String, String> RUNTIME_RULES = Map.of(
            "Environment", "C01", "Workspace", "C02", "ArtifactType", "C03", "Artifact", "C04",
            "LiveObservableProperty", "C08", "ObservablePropertySnapshot", "C09",
            "CartagoAgentIdentity", "C12", "Agent", "J02");
    private final MSystem system;
    private final Map<String, MClassInvariant> owned = new LinkedHashMap<>();
    private Profile profile;
    private final Map<String,RuntimeConstraintPolicy> policies=new LinkedHashMap<>();
    private final Map<String,String> approvedExpressions=new LinkedHashMap<>();
    public record RuntimeConstraint(String id,String context,Set<String> requiredClasses,Set<String> features,
                                    Set<String> requiredSources,RuntimeConstraintPolicy policy,String sourceFile,
                                    String sourceHash,String modelRevision) {
        public RuntimeConstraint {requiredClasses=Set.copyOf(requiredClasses);features=Set.copyOf(features);requiredSources=Set.copyOf(requiredSources);}
    }
    private record CachedRegistration(RegisteredConstraint registration,Set<String> features) { }
    private final Map<String,CachedRegistration> dependencyCache=new LinkedHashMap<>();
    private String dependencyHash="";
    private java.util.function.Consumer<Object> notifications;

    public ExternalOclConstraintService(MSystem system) {
        this.system = java.util.Objects.requireNonNull(system);
        notifications=system.getEventBus()::post;
    }
    public void notifications(java.util.function.Consumer<Object> sink) { notifications=java.util.Objects.requireNonNull(sink); }
    /** Ephemeral failure guard, not a cached historical profile or another evaluator. */
    public record Savepoint(Map<String,MClassInvariant> owned, Profile profile, Map<String,RuntimeConstraintPolicy> policies,
                            Map<String,String> approvedExpressions) { }
    public Savepoint savepoint() { return new Savepoint(Map.copyOf(owned),profile(),Map.copyOf(policies),Map.copyOf(approvedExpressions)); }
    public void restore(Savepoint before) {
        owned.keySet().forEach(system.model()::removeClassInvariant);
        try { for (var inv:before.owned().values()) system.model().addClassInvariant(inv); }
        catch (MInvalidModelException error) { throw new IllegalStateException("EXTERNAL_OCL_RESTORE_FAILED",error); }
        owned.clear(); owned.putAll(before.owned()); profile=before.profile();
        policies.clear(); policies.putAll(before.policies());approvedExpressions.clear();approvedExpressions.putAll(before.approvedExpressions());dependencyHash="";
    }
    public MSystem system() { return system; }
    public void configurePolicy(String constraintId,RuntimeConstraintPolicy policy) {
        if(runtimeRegistry().stream().noneMatch(c->c.id().equals(constraintId))) throw new IllegalArgumentException("RUNTIME_CONSTRAINT_ID_UNKNOWN:"+constraintId);
        policies.put(constraintId,java.util.Objects.requireNonNull(policy));
        approvedExpressions.put(constraintId,expressionFingerprint(constraintId));
    }
    private String expressionFingerprint(String id) {
        for(var inv:system.model().classInvariants())if(runtimeId(inv).equals(id))
            return sha256((inv.qualifiedName()+"|"+inv.isNegated()+"|"+inv.isExistential()+"|"+inv.var()+"|"+inv.bodyExpression()).getBytes(StandardCharsets.UTF_8));
        for(var condition:system.model().prePostConditions())if(("COMPILED:"+condition).equals(id))
            return sha256((condition+"|"+condition.isPre()+"|"+condition.operation().paramList()+"|"+condition.expression()).getBytes(StandardCharsets.UTF_8));
        return "";
    }
    private RuntimeConstraintPolicy policyFor(String id,RuntimeConstraintPolicy defaultPolicy) {
        if(policies.containsKey(id) && !expressionFingerprint(id).equals(approvedExpressions.get(id))) {
            policies.remove(id);approvedExpressions.remove(id);
        }
        return policies.getOrDefault(id,defaultPolicy);
    }
    public List<RuntimeConstraint> runtimeRegistry() {
        ensureDependencies();var current=profile();
        var result=new ArrayList<>(system.model().classInvariants().stream().sorted(Comparator.comparing(MClassInvariant::qualifiedName)).map(inv->{
            String id=runtimeId(inv);var info=dependencyCache.get(inv.qualifiedName());boolean external=owned.containsKey(inv.qualifiedName());
            return new RuntimeConstraint(id,inv.cls().name(),info.registration().requiredClasses(),info.features(),info.registration().requiredSources(),
                    policyFor(id,defaultPolicy(inv,external,current)),
                    external && current!=null?current.sourceFile():"",external && current!=null?current.sourceHash():"",external && current!=null?current.modelRevision():"");
        }).toList());
        for(var condition:system.model().prePostConditions()) {
            String id="COMPILED:"+condition;
            var visitor=dependencyVisitor();condition.expression().processWithVisitor(visitor);
            var features=new TreeSet<String>();features.add("operation:"+condition.cls().name()+"."+condition.operation().name());
            visitor.getCoverageData().getAttributeCoverage().keySet().forEach(a->features.add("attribute:"+a.owner().name()+"."+a.name()));
            var policy=policyFor(id,new RuntimeConstraintPolicy(org.tzi.use.plugins.jacamo.verification.ConstraintOrigin.USER,
                    RuntimeConstraintPolicy.Severity.SOFT,RuntimeConstraintPolicy.Enforcement.REPORT_ONLY,
                    Set.of(condition.isPre()?org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointType.OPERATION_PRE:
                        org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointType.OPERATION_POST),Set.of(),false,"compiled USE operation condition; exact source line unavailable"));
            result.add(new RuntimeConstraint(id,condition.cls().name(),Set.of(condition.cls().name()),features,Set.of("cartago"),policy,"","",condition.expression().toString()));
        }
        return List.copyOf(result);
    }
    public boolean mayPause(String constraintId) {return runtimeRegistry().stream().anyMatch(c->c.id().equals(constraintId)
            && c.policy().approved() && c.policy().severity()==RuntimeConstraintPolicy.Severity.HARD
            && c.policy().enforcement()==RuntimeConstraintPolicy.Enforcement.PAUSE_ON_FAIL);}
    /** Exact false contexts of a compiled invariant in this same active state; never infer from names. */
    public Set<String> failingContexts(Outcome outcome) {
        if(outcome.outcome()!=VerificationOutcome.FAIL)return Set.of();
        if(!outcome.contextObject().isBlank())return system.state().objectByName(outcome.contextObject())==null?Set.of():Set.of(outcome.contextObject());
        var invariant=system.model().classInvariants().stream().filter(i->runtimeId(i).equals(outcome.constraintId())).findFirst().orElse(null);
        if(invariant==null || invariant.isExistential() || invariant.hasVar() && invariant.var().contains(","))return Set.of();
        var result=new TreeSet<String>();
        for(var object:system.state().objectsOfClassAndSubClasses(invariant.cls()))try {
            var bindings=new org.tzi.use.uml.ocl.value.VarBindings(system.state());bindings.push(invariant.hasVar()?invariant.var():"self",object.value());
            var value=new Evaluator().eval(invariant.bodyExpression(),system.state(),bindings,null);
            if(value instanceof BooleanValue truth && (invariant.isNegated()?truth.isTrue():truth.isFalse()))result.add(object.name());
        } catch(RuntimeException unavailable) { /* Unavailable individual context remains explicitly unknown. */ }
        return Set.copyOf(result);
    }
    public String conditionFingerprint(String id) {
        String value=expressionFingerprint(id);
        if(value.isBlank())throw new IllegalArgumentException("RUNTIME_CONSTRAINT_ID_UNKNOWN:"+id);
        return value;
    }
    private RuntimeConstraintPolicy defaultPolicy(MClassInvariant invariant,boolean external,Profile current) {
        String provenance=invariant.getAnnotationValue("RuntimeConstraint","provenance");
        if(!external && provenance.startsWith("CORE:GOAL:") && !invariant.isNegated()
                && sha256(invariant.bodyExpression().toString().getBytes(StandardCharsets.UTF_8)).equals(invariant.getAnnotationValue("RuntimeConstraint","bodyHash"))) {
            String capability=invariant.getAnnotationValue("RuntimeConstraint","capabilities");
            return new RuntimeConstraintPolicy(org.tzi.use.plugins.jacamo.verification.ConstraintOrigin.CORE,RuntimeConstraintPolicy.Severity.HARD,
                    RuntimeConstraintPolicy.Enforcement.REPORT_ONLY,RuntimeConstraintPolicy.reportOnly(org.tzi.use.plugins.jacamo.verification.ConstraintOrigin.CORE,provenance).checkpoints(),
                    capability.isBlank()?Set.of():Set.of(capability.split(",")),false,provenance);
        }
        return RuntimeConstraintPolicy.reportOnly(external?org.tzi.use.plugins.jacamo.verification.ConstraintOrigin.USER:
                org.tzi.use.plugins.jacamo.verification.ConstraintOrigin.CORE,external && current!=null?current.sourceFile():"native model declaration");
    }
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
        system.assertUserMutationAllowed();
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
        // An approval is bound to the compiled expression, never merely to a reusable name.
        for(var entry:candidate.entrySet()) {
            var old=previous.get(entry.getKey());
            if(old==null || !old.bodyExpression().toString().equals(entry.getValue().bodyExpression().toString()))
                policies.remove("EXTERNAL:"+entry.getKey());
        }
        owned.clear(); owned.putAll(candidate);
        profile = new Profile(sourceFile, hash, source, revision, registry);
        if (!previous.isEmpty()) notifications.accept(new ClassInvariantsUnloadedEvent(
                EventContext.NORMAL_EXECUTION, List.copyOf(previous.values()), true));
        notifications.accept(new ClassInvariantsLoadedEvent(EventContext.NORMAL_EXECUTION,
                List.copyOf(candidate.values()), true));
        return profile;
    }

    public void rebind(String revision) {
        Profile current = profile();
        if (current != null) installSource(current.sourceFile(), current.source(), revision);
    }

    public String constraintSetHash() {
        var invariants=system.model().classInvariants().stream().sorted(Comparator.comparing(MClassInvariant::qualifiedName))
                .map(inv -> inv.qualifiedName() + "|" + inv.isActive() + "|" + inv.isNegated() + "|" + inv.bodyExpression())
                .collect(java.util.stream.Collectors.joining("\n"));
        var conditions=system.model().prePostConditions().stream().sorted(Comparator.comparing(Object::toString))
                .map(c->c+"|"+c.isPre()+"|"+c.operation().paramList()+"|"+c.expression()).collect(java.util.stream.Collectors.joining("\n"));
        return sha256((invariants+(conditions.isEmpty()?"":"\n"+conditions)).getBytes(StandardCharsets.UTF_8));
    }
    public String enforcementHash() {
        return sha256(runtimeRegistry().stream().sorted(Comparator.comparing(RuntimeConstraint::id)).map(e->e.id()+"|"+e.policy().origin()
                +"|"+e.policy().severity()+"|"+e.policy().enforcement()+"|"+new TreeSet<>(e.policy().checkpoints())
                +"|"+new TreeSet<>(e.policy().requiredCapabilities())+"|"+e.policy().approved()+"|"+e.policy().provenance()+"|"+new java.util.TreeMap<>(e.policy().exactEvidenceTargets())+"|"+expressionFingerprint(e.id()))
                .collect(java.util.stream.Collectors.joining("\n")).getBytes(StandardCharsets.UTF_8));
    }

    public List<Outcome> evaluate(Map<String, Completeness> sources, boolean coverageLost) {
        return evaluate(sources,coverageLost,org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointType.SNAPSHOT,Set.of(),Map.of());
    }
    public List<Outcome> evaluate(Map<String,Completeness> sources,boolean coverageLost,
            org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointType checkpoint,Set<String> changedFeatures,Map<String,String> capabilities) {
        ensureDependencies();
        var registry=runtimeRegistry();
        var descriptors=registry.stream().map(c->new org.tzi.use.plugins.jacamo.verification.ConstraintDescriptor(c.id(),c.id(),c.context(),"",
                org.tzi.use.plugins.jacamo.verification.ConstraintKind.INV,c.policy().origin(),null,null,List.copyOf(c.features()),true,"")).toList();
        var selection=new org.tzi.use.plugins.jacamo.verification.ConstraintDependencyIndex(descriptors).select(changedFeatures);
        List<Outcome> outcomes = new ArrayList<>();
        for (MClassInvariant invariant : system.model().classInvariants().stream()
                .sorted(Comparator.comparing(MClassInvariant::qualifiedName)).toList()) {
            String id = runtimeId(invariant);
            var dependencies = dependencyCache.get(invariant.qualifiedName()).registration();
            var policy=registry.stream().filter(c->c.id().equals(id)).findFirst().orElseThrow().policy();
            String skip = !invariant.isActive() ? "CONSTRAINT_DISABLED" : coverageLost ? "COVERAGE_LOST"
                    : !policy.checkpoints().contains(checkpoint)?"CHECKPOINT_NOT_APPLICABLE"
                    : policy.requiredCapabilities().stream().anyMatch(c->!"COMPLETE".equals(capabilities.get(c)))?"REQUIRED_CAPABILITY_UNAVAILABLE"
                    : (checkpoint==org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointType.AFTER_MUTATION
                        || checkpoint==org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointType.OPERATION_POST) && !selection.fullCheckFallback()
                        && !selection.constraintIds().contains(id)?"CHECKPOINT_NOT_DEPENDENT"
                    : dependencies.requiredClasses().contains("LiveObservableProperty") ? "C08_UNAVAILABLE_BY_API"
                    : dependencies.requiredClasses().stream().anyMatch(cls -> "EVIDENCE_ONLY".equals(system.model()
                            .getClass(cls).getAnnotationValue(org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.ANNOTATION,
                                    "runtimeProjection"))) ? "MOISE_DOMAIN_RUNTIME_EVIDENCE_ONLY"
                    : dependencyCache.get(invariant.qualifiedName()).features().stream().anyMatch(feature->feature.equals("attribute:OrganizationalGoal.runtimeState")
                        || feature.equals("association:"+org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("goalCommitment","Agent","OrganizationalGoal"))
                        || feature.equals("association:"+org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.relation("goalAchievement","Agent","OrganizationalGoal")))
                        && !"COMPLETE".equals(capabilities.get("runtime.moise.goalState.v1"))?"REQUIRED_CAPABILITY_UNAVAILABLE:runtime.moise.goalState.v1"
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
    private String runtimeId(MClassInvariant inv) {return (owned.containsKey(inv.qualifiedName())?"EXTERNAL:":"NATIVE:")+inv.qualifiedName();}
    private void ensureDependencies() {
        String hash=constraintSetHash();if(hash.equals(dependencyHash)) return;
        dependencyCache.clear();
        for(var invariant:system.model().classInvariants()) {
            var visitor=dependencyVisitor();invariant.flaggedExpression().processWithVisitor(visitor);
            var coverage=visitor.getCoverageData();var features=new TreeSet<String>();
            coverage.getClassCoverage().keySet().forEach(c->features.add("class:"+c.name()));
            // Every context range is affected by creation/removal, including constant-body invariants.
            features.add("class:"+invariant.cls().name());
            coverage.getAttributeCoverage().keySet().forEach(a->features.add("attribute:"+a.owner().name()+"."+a.name()));
            coverage.getAssociationCoverage().keySet().forEach(a->features.add("association:"+a.name()));
            coverage.getOperationCoverage().keySet().forEach(o->features.add("operation:"+o.cls().name()+"."+o.name()));
            dependencyCache.put(invariant.qualifiedName(),new CachedRegistration(registration(invariant,"","",""),Set.copyOf(features)));
        }
        policies.keySet().removeIf(id->system.model().classInvariants().stream().noneMatch(inv->runtimeId(inv).equals(id))
                && system.model().prePostConditions().stream().noneMatch(c->("COMPILED:"+c).equals(id)));
        approvedExpressions.keySet().retainAll(policies.keySet());
        dependencyHash=hash;
    }

    private static CoverageCalculationVisitor dependencyVisitor() {
        return new CoverageCalculationVisitor(true) {
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
    }
    public static Set<String> requiredClasses(MClassInvariant invariant) {
        CoverageCalculationVisitor visitor = dependencyVisitor();
        invariant.flaggedExpression().processWithVisitor(visitor);
        var coverage = visitor.getCoverageData();
        Set<String> classes = new TreeSet<>(); classes.add(invariant.cls().name());
        coverage.getCompleteClassCoverage().keySet().forEach(cls -> classes.add(cls.name()));
        coverage.getAssociationCoverage().keySet().forEach(association ->
                association.associatedClasses().forEach(cls -> classes.add(cls.name())));
        return Set.copyOf(classes);
    }

    private RegisteredConstraint registration(MClassInvariant invariant, String file, String hash, String revision) {
        Set<String> classes = requiredClasses(invariant);
        Set<String> rules = new TreeSet<>(), sources = new TreeSet<>();
        for (String cls : classes) {
            var modelClass = system.model().getClass(cls);
            if (modelClass != null && "moise".equals(modelClass.getAnnotationValue(
                    org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.ANNOTATION, "runtimeSource"))) {
                rules.add(modelClass.getAnnotationValue(org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection.ANNOTATION, "ruleId"));
                sources.add("moise.domain");
            }
            String rule = RUNTIME_RULES.get(cls);
            if (rule != null) { rules.add(rule); sources.add(rule.startsWith("C") ? "cartago" : "jason"); }
            if (modelClass != null) switch (org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.kind(modelClass)) {
                case "agent-base", "agent-program" -> { rules.add("A01"); sources.add("jason"); }
                case "belief" -> { rules.add("A08"); sources.add("jason"); }
                case "artifact" -> { rules.add("C04"); sources.add("cartago"); }
                case "workspace" -> { rules.add("C02"); sources.add("cartago"); }
                default -> { }
            }
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
