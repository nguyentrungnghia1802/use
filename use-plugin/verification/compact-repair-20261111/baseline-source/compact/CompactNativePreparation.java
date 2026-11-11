package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.SourceEvidence;
import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.ocl.*;
import org.tzi.use.semantic.engine.use75.*;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.Type;
import org.vnu.sme.goal.dsl.istar.mm.*;

/**
 * Prepares the authoritative USE MModel for a compact mapping specification (v3),
 * installing CSL scopes, observer views, artifact operations, CSL invariants,
 * authored OCL invariants/pre/postconditions, and iStar Goal/Task OCL contracts.
 */
public final class CompactNativePreparation {

    private static final Pattern ALL_INSTANCES_PATTERN =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\.allInstances\\s*\\(\\s*\\)");
    private static final Pattern ITERATOR_VAR_PATTERN =
            Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\.allInstances\\s*\\(\\s*\\)\\s*->\\s*[A-Za-z]+\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(?::[^|)]+)?\\|");

    public JaCaMoOclContractMapping.Result prepare(
            ModelSnapshot source,
            SemanticProgram program,
            Use75ModelBinding binding,
            CodeGroundedTraceCollector trace) {
        program.assertUnchanged();
        program.requireSourceHash(SemanticJson.hash(ContractPayloads.model(source)));

        var scopes = new CslScopeModelAdapter().compile(program.cslModel());
        new CanonicalUseBindingAdapter().install(program, binding, trace);

        var api = new UseModelApi(binding.model());
        var compactPlan = program.compactPlan();
        var authoredOpt = compactPlan.mappingInputs().map(ModelNativeContracts::read);

        try {
            installInferredObserverClasses(api, program, authoredOpt.orElse(null));
            Map<String, MOperation> contextOperations = new LinkedHashMap<>();
            Map<String, List<Object>> operationSignatures = new LinkedHashMap<>();
            if (authoredOpt.isPresent()) {
                installOperations(source, program, api, authoredOpt.get(), contextOperations, operationSignatures);
            }

            var contracts = new LinkedHashMap<String, JaCaMoOclContractMapping.BoundContract>();
            var canonicalBodies = new HashMap<String, String>();

            try (var compiler = new NativeOclBackend(binding.model(), program.hash(), program.profile().engine().name())) {
                // 1. Canonical CSL invariants
                for (var inv : program.cslModel().invariants()) {
                    var spec = new OclContractSpec(
                            "CSL:" + inv.qualifiedName(),
                            OclContractSpec.Kind.INVARIANT,
                            binding.classifier(inv.contextType()),
                            null,
                            inv.expression());
                    var compiled = compiler.compile(spec);
                    var created = api.createInvariantEx(
                            "CSL_" + inv.name() + "_" + DomainProjection.hash(inv.qualifiedName()),
                            inv.contextType(),
                            compiled.expression(),
                            false);
                    created.addAnnotation(new MElementAnnotation("CslConstraint", Map.of(
                            "id64", DomainProjection.encode(spec.id()),
                            "canonical", "true",
                            "bodyHash", SemanticJson.hash(inv.expression()),
                            "origin64", DomainProjection.encode("CANONICAL_CSL_INVARIANT"))));
                    canonicalBodies.put(inv.contextType() + "::" + inv.name(), compiled.expression().toString());
                    contracts.put(spec.id(), new JaCaMoOclContractMapping.BoundContract(
                            spec.id(), "", spec, SemanticJson.hash(spec.body()), "CANONICAL_CSL_INVARIANT", List.of()));
                }

                // 2. Authored OCL invariants and operation PRE/POST clauses
                if (authoredOpt.isPresent()) {
                    var authored = authoredOpt.get();
                    Map<String, String> opToTask = mapOperationToTask(compactPlan, program);
                    for (var clause : authored.clauses()) {
                        if (clause.kind() == AuthoredOclSpecificationLoader.Kind.INVARIANT) {
                            String canonicalKey = clause.context().owner() + "::" + clause.name();
                            if (canonicalBodies.containsKey(canonicalKey)) {
                                continue;
                            }
                            var exp = AuthoredOclSpecificationLoader.compileInvariant(
                                    binding.model(), clause.context().owner(), clause.body(), clause.name());
                            String id = "XML:OCL:INV:" + clause.name();
                            var created = api.createInvariantEx(
                                    clause.name() + "_" + DomainProjection.hash(id),
                                    clause.context().owner(),
                                    exp,
                                    false);
                            created.addAnnotation(new MElementAnnotation("CslConstraint", Map.of(
                                    "id64", DomainProjection.encode(id),
                                    "clauseName64", DomainProjection.encode(clause.name()),
                                    "bodyHash", SemanticJson.hash(clause.body()),
                                    "origin64", DomainProjection.encode("AUTHORED_OCL_SPECIFICATION"))));
                            var spec = new OclContractSpec(
                                    id, OclContractSpec.Kind.INVARIANT,
                                    binding.model().getClass(clause.context().owner()),
                                    null, clause.body());
                            contracts.put(id, new JaCaMoOclContractMapping.BoundContract(
                                    id, "", spec, SemanticJson.hash(clause.body()), "AUTHORED_OCL_SPECIFICATION", List.of()));
                        } else {
                            String opKey = clause.context().owner() + "::" + clause.context().operation() + "/" + clause.context().parameters().size();
                            MOperation op = contextOperations.get(opKey);
                            if (op == null) continue;
                            boolean pre = clause.kind() == AuthoredOclSpecificationLoader.Kind.PRE;
                            var exp = AuthoredOclSpecificationLoader.compileOperationClause(
                                    binding.model(), op, clause.body(), pre, clause.name());
                            String id = "XML:OCL:" + clause.kind().name() + ":" + clause.name();
                            var created = api.createPrePostConditionEx(
                                    (pre ? "Pre_" : "Post_") + clause.name() + "_" + DomainProjection.hash(id),
                                    op, pre, exp);
                            var annotations = new TreeMap<String, String>();
                            annotations.put("id64", DomainProjection.encode(id));
                            annotations.put("clauseName64", DomainProjection.encode(clause.name()));
                            annotations.put("bodyHash", SemanticJson.hash(clause.body()));
                            annotations.put("origin64", DomainProjection.encode("AUTHORED_OCL_SPECIFICATION"));
                            String taskId = opToTask.getOrDefault(opKey, "");
                            if (!taskId.isBlank()) {
                                annotations.put("targetTask64", DomainProjection.encode(taskId));
                                annotations.put("contractBase64", DomainProjection.encode("XML:OP:" + taskId));
                            }
                            created.addAnnotation(new MElementAnnotation("CslConstraint", annotations));
                            var spec = new OclContractSpec(
                                    id,
                                    pre ? OclContractSpec.Kind.TASK_PRE : OclContractSpec.Kind.TASK_POST,
                                    op.cls(), op, clause.body());
                            contracts.put(id, new JaCaMoOclContractMapping.BoundContract(
                                    id, taskId, spec, SemanticJson.hash(clause.body()),
                                    "AUTHORED_OCL_SPECIFICATION",
                                    operationSignatures.getOrDefault(opKey, List.of())));
                        }
                    }
                }

                // 3. Intentional Goal and Task OCL contracts from .istar
                for (var template : program.goalBindings().elements().values()) {
                    var elementOpt = program.goalModel().findElement(template.elementId());
                    if (elementOpt.isEmpty()) continue;
                    var element = elementOpt.get();
                    var selfCls = binding.model().getClass(template.selfDeclaration());
                    if (selfCls == null) continue;

                    if (element instanceof Goal goal && !template.derived() && goal.condition().isPresent()) {
                        String expr = goal.oclSource();
                        if (expr != null && !expr.isBlank()) {
                            var vars = new LinkedHashMap<String, Type>();
                            if (binding.model().getClass("RuntimePlayer") != null) {
                                vars.put("player", binding.model().getClass("RuntimePlayer"));
                            }
                            if (binding.model().getClass("OrgContext") != null) {
                                vars.put("orgContext", binding.model().getClass("OrgContext"));
                            }
                            String id = "GOAL:" + goal.id();
                            var spec = new OclContractSpec(id, OclContractSpec.Kind.GOAL, selfCls, null, expr, vars);
                            compiler.compile(spec);
                            contracts.put(id, new JaCaMoOclContractMapping.BoundContract(
                                    id, goal.id(), spec, SemanticJson.hash(expr), "CANONICAL_ISTAR_GOAL_CONDITION", List.of()));
                        }
                    } else if (element instanceof Task task) {
                        compileTaskSlot(api, compiler, contracts, task, selfCls, true, contextOperations);
                        compileTaskSlot(api, compiler, contracts, task, selfCls, false, contextOperations);
                    }
                }
            }

            binding.model().addAnnotation(new MElementAnnotation("CslContextScopes",
                    Map.of("payload64", DomainProjection.encode(NativeUseModelBuilder.canonicalJson(scopes.contexts())))));
            binding.model().addAnnotation(new MElementAnnotation("CslEntityScopes",
                    Map.of("payload64", DomainProjection.encode(NativeUseModelBuilder.canonicalJson(scopes.entities())))));
            binding.model().addAnnotation(new MElementAnnotation("CslAgentContexts",
                    Map.of("payload64", DomainProjection.encode(NativeUseModelBuilder.canonicalJson(scopes.agentContexts())))));
            binding.model().addAnnotation(new MElementAnnotation("XmlNativeContractPreparation", Map.of(
                    "version", "3.0",
                    "programHash", program.hash(),
                    "mappingPlanHash", compactPlan.planFingerprint(),
                    "contractCount", Integer.toString(contracts.size()))));

            return new JaCaMoOclContractMapping.Result(List.copyOf(contracts.values()));
        } catch (UseApiException | IllegalArgumentException failure) {
            if (failure instanceof MappingAdmissionException mapped) throw mapped;
            var diagnostic = new MappingDiagnostic(
                    MappingDiagnostic.Kind.ERROR,
                    "COMPACT_NATIVE_PREPARATION_FAILED",
                    "",
                    program.hash(),
                    "COMPACT_NATIVE_OCL_3.0",
                    "AUTHORED_MODELS_AND_CONTRACTS",
                    0,
                    failure.getMessage() == null ? failure.getClass().getName() : failure.getMessage());
            var mapped = new MappingAdmissionException(List.of(diagnostic));
            mapped.initCause(failure);
            throw mapped;
        }
    }

    private static void compileTaskSlot(
            UseModelApi api,
            NativeOclBackend compiler,
            Map<String, JaCaMoOclContractMapping.BoundContract> contracts,
            Task task,
            MClass selfCls,
            boolean pre,
            Map<String, MOperation> contextOperations) throws UseApiException {
        var slots = pre ? task.preconditions() : task.postconditions();
        if (slots.isEmpty()) return;
        String body = slots.getFirst().oclBody();
        if (body == null || body.isBlank()) return;

        var vars = new LinkedHashMap<String, Type>();
        if (body.contains("step.")) {
            vars.put("step", api.getType("Tuple(enabled:Boolean,succeeded:Boolean,completed:Boolean)"));
        }
        for (MOperation op : contextOperations.values()) {
            if (op.cls().equals(selfCls)) {
                for (int i = 0; i < op.paramList().size(); i++) {
                    var decl = op.paramList().varDecl(i);
                    if (Pattern.compile("\\b" + Pattern.quote(decl.name()) + "\\b").matcher(body).find()) {
                        vars.putIfAbsent(decl.name(), decl.type());
                    }
                }
            }
        }
        String id = "TASK:" + task.id() + (pre ? ":pre" : ":post");
        var spec = new OclContractSpec(
                id,
                pre ? OclContractSpec.Kind.TASK_PRE : OclContractSpec.Kind.TASK_POST,
                selfCls,
                null,
                body,
                vars);
        compiler.compile(spec);
        contracts.put(id, new JaCaMoOclContractMapping.BoundContract(
                id, task.id(), spec, SemanticJson.hash(body), "CANONICAL_ISTAR_TASK_CONDITION", List.of()));
    }

    private static void installInferredObserverClasses(
            UseModelApi api,
            SemanticProgram program,
            AuthoredOclSpecificationLoader.Specification authored) throws UseApiException {
        List<Map.Entry<String, String>> allExpressions = new ArrayList<>();
        for (var inv : program.cslModel().invariants()) {
            allExpressions.add(Map.entry(inv.contextType(), inv.expression()));
        }
        for (var actor : program.goalModel().getActors()) {
            for (var elem : actor.elements()) {
                if (elem instanceof Goal g && g.condition().isPresent()) {
                    allExpressions.add(Map.entry("", g.condition().get().expression()));
                } else if (elem instanceof Task t) {
                    t.preCondition().ifPresent(e -> allExpressions.add(Map.entry("", e.expression())));
                    t.postCondition().ifPresent(e -> allExpressions.add(Map.entry("", e.expression())));
                }
            }
        }
        if (authored != null) {
            for (var clause : authored.clauses()) {
                allExpressions.add(Map.entry(clause.context().owner(), clause.body()));
            }
        }

        Set<String> observerClasses = new LinkedHashSet<>();
        for (var entry : allExpressions) {
            String ctx = entry.getKey();
            if (!ctx.isBlank() && api.getModel().getClass(ctx) == null) {
                observerClasses.add(ctx);
            }
            Matcher m = ALL_INSTANCES_PATTERN.matcher(entry.getValue());
            while (m.find()) {
                String clsName = m.group(1);
                if (api.getModel().getClass(clsName) == null) {
                    observerClasses.add(clsName);
                }
            }
        }

        for (String obsClass : observerClasses) {
            var cls = api.createClass(obsClass, false);
            DomainProjection.annotate(cls, "external-observer", "compact-observer:" + obsClass, "");
            cls.addAnnotation(new MElementAnnotation("ObserverRole", Map.of(
                    "role", "EXTERNAL_OBSERVER_VIEW",
                    "category", "CLASS",
                    "domainState", "false")));

            Map<String, String> inferredFields = new LinkedHashMap<>();
            for (var entry : allExpressions) {
                String ctx = entry.getKey();
                String expr = entry.getValue();
                Set<String> aliases = new LinkedHashSet<>();
                if (obsClass.equals(ctx)) {
                    aliases.add("self");
                }
                Matcher vm = ITERATOR_VAR_PATTERN.matcher(expr);
                while (vm.find()) {
                    if (obsClass.equals(vm.group(1))) {
                        aliases.add(vm.group(2));
                    }
                }
                for (String alias : aliases) {
                    Pattern propPat = Pattern.compile("\\b" + Pattern.quote(alias) + "\\.([A-Za-z_][A-Za-z0-9_]*)\\b");
                    Matcher pm = propPat.matcher(expr);
                    while (pm.find()) {
                        String field = pm.group(1);
                        if ("allInstances".equals(field)) continue;
                        String type = inferFieldType(expr, alias, field);
                        inferredFields.merge(field, type, CompactNativePreparation::mergeFieldType);
                    }
                }
            }

            for (var fieldEntry : inferredFields.entrySet()) {
                var attr = api.createAttribute(obsClass, fieldEntry.getKey(), fieldEntry.getValue());
                attr.addAnnotation(new MElementAnnotation("ObserverField", Map.of(
                        "origin64", DomainProjection.encode("compact-inferred-observer-field"))));
            }
        }
    }

    private static String inferFieldType(String expr, String alias, String field) {
        String ref = Pattern.quote(alias) + "\\." + Pattern.quote(field);
        if (Pattern.compile("\\b" + ref + "\\s*->").matcher(expr).find()) {
            return "Set(String)";
        }
        if (Pattern.compile("\\b" + ref + "\\s*(?:<=|>=|<|>|\\+|-)").matcher(expr).find()
                || Pattern.compile("(?:<=|>=|<|>|\\+|-)\\s*" + ref + "\\b").matcher(expr).find()
                || Pattern.compile("\\b" + ref + "\\s*=\\s*-?\\d+").matcher(expr).find()) {
            return "Integer";
        }
        if (Pattern.compile("\\b" + ref + "\\s*(?:=|<>)" + "\\s*(?:[A-Za-z_][A-Za-z0-9_]*\\.(?:workspace|name|id|scheme)|'[^']*')").matcher(expr).find()
                || field.equals("workspace") || field.equals("scheme") || field.equals("goal")) {
            return "String";
        }
        return "Boolean";
    }

    private static String mergeFieldType(String a, String b) {
        if (a.equals(b)) return a;
        if (a.equals("Set(String)") || b.equals("Set(String)")) return "Set(String)";
        if (a.equals("Integer") || b.equals("Integer")) return "Integer";
        if (a.equals("String") || b.equals("String")) return "String";
        return "Boolean";
    }

    private static void installOperations(
            ModelSnapshot source,
            SemanticProgram program,
            UseModelApi api,
            AuthoredOclSpecificationLoader.Specification authored,
            Map<String, MOperation> contextOperations,
            Map<String, List<Object>> operationSignatures) throws UseApiException {
        for (var context : authored.operations().values()) {
            var entityBindingOpt = program.bindings().find(SemanticBindingIndex.Kind.ENTITY, context.owner());
            String fqcn = entityBindingOpt.map(b -> JaCaMoCslMappingRules.javaClass(program.source(), b)).orElse("");
            String opKey = context.owner() + "::" + context.operation() + "/" + context.parameters().size();

            var exposure = new DomainProjection.OperationExposure(
                    context.operation(),
                    context.parameters().stream().map(AuthoredOclSpecificationLoader.Parameter::name).toList());

            MOperation installed = tryInstallWithProducerProof(source, api, context, fqcn, exposure, operationSignatures, opKey);
            if (installed == null) {
                String[][] params = new String[context.parameters().size()][2];
                List<String> javaParamTypes = new ArrayList<>();
                for (int i = 0; i < context.parameters().size(); i++) {
                    var p = context.parameters().get(i);
                    params[i][0] = p.name();
                    params[i][1] = p.type();
                    javaParamTypes.add(oclToJavaType(p.type()));
                }
                String result = context.returnType().isBlank() ? null : context.returnType();
                String javaReturn = context.returnType().isBlank() ? "void" : oclToJavaType(context.returnType());
                List<Object> sig = List.of(fqcn.isBlank() ? context.owner() : fqcn, context.operation(), javaParamTypes, javaReturn);
                operationSignatures.put(opKey, sig);
                String canonicalSig = NativeUseModelBuilder.canonicalJson(sig);
                var cls = api.getModel().getClass(context.owner());
                installed = cls.operation(context.operation(), false);
                if (installed == null) {
                    installed = api.createOperation(context.owner(), context.operation(), params, result);
                }
                var identity = new LinkedHashMap<String, String>();
                identity.put("signature64", DomainProjection.encode(canonicalSig));
                identity.put("javaName64", DomainProjection.encode(context.operation()));
                identity.put("policy", NativeProjectionPolicy.VERSION);
                identity.put("exposure", "AUTHORED_NAMES_V1");
                identity.put("parameterAliases64", DomainProjection.encode(NativeUseModelBuilder.canonicalJson(installed.paramNames())));
                installed.addAnnotation(new MElementAnnotation("CartagoOperation", identity));
            }
            contextOperations.put(opKey, installed);
        }
    }

    private static MOperation tryInstallWithProducerProof(
            ModelSnapshot source,
            UseModelApi api,
            AuthoredOclSpecificationLoader.Context context,
            String fqcn,
            DomainProjection.OperationExposure exposure,
            Map<String, List<Object>> operationSignatures,
            String opKey) {
        if (fqcn == null || fqcn.isBlank()) return null;
        try {
            var declarations = source.semanticContract().artifactDeclarations().stream()
                    .filter(d -> d.javaClass().equals(fqcn))
                    .toList();
            var references = SourceArtifactTypeReferences.fromActions(source).stream()
                    .filter(r -> r.javaClass().equals(fqcn))
                    .toList();
            for (var decl : declarations) {
                for (SourceEvidence ev : decl.metadata().evidence()) {
                    if (!ev.sourceSemanticId().equals(decl.metadata().semanticId())
                            || !ev.diagnostics().contains("DECLARED_CARTAGO_ARTIFACT_OPERATION")) continue;
                    var sigOpt = matchOperationSignature(ev, context);
                    if (sigOpt.isPresent()) {
                        operationSignatures.put(opKey, sigOpt.get());
                        return DomainProjection.declaredOperation(api, context.owner(), sigOpt.get(), decl.metadata().semanticId(), ev, exposure);
                    }
                }
            }
            for (var ref : references) {
                SourceEvidence ev = ref.evidence();
                if (!ev.diagnostics().contains(SourceArtifactTypeReferences.OPERATION)) continue;
                var sigOpt = matchOperationSignature(ev, context);
                if (sigOpt.isPresent()) {
                    operationSignatures.put(opKey, sigOpt.get());
                    return DomainProjection.declaredOperation(api, context.owner(), sigOpt.get(), ref.sourceId(), ev, exposure);
                }
            }
        } catch (Exception ignored) {
            // Fall back to direct operation installation if producer proof is absent
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Optional<List<Object>> matchOperationSignature(
            SourceEvidence ev,
            AuthoredOclSpecificationLoader.Context context) {
        for (String diag : ev.diagnostics()) {
            if (!diag.startsWith("SIGNATURE:")) continue;
            String json = diag.substring("SIGNATURE:".length());
            Object raw = CanonicalJson.decode(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!(raw instanceof List<?> list) || list.size() != 4) continue;
            String methodName = String.valueOf(list.get(1));
            if (!methodName.equals(context.operation())) continue;
            if (!(list.get(2) instanceof List<?> paramTypes) || paramTypes.size() != context.parameters().size()) continue;
            boolean paramsMatch = true;
            for (int i = 0; i < paramTypes.size(); i++) {
                String oclType = DomainProjection.operationType(String.valueOf(paramTypes.get(i)));
                if (!oclType.equals(context.parameters().get(i).type())) {
                    paramsMatch = false;
                    break;
                }
            }
            if (!paramsMatch) continue;
            String retJava = String.valueOf(list.get(3));
            String retOcl = "void".equals(retJava) ? "" : DomainProjection.operationType(retJava);
            if (!retOcl.equals(context.returnType())) continue;
            return Optional.of((List<Object>) list);
        }
        return Optional.empty();
    }

    private static Map<String, String> mapOperationToTask(
            CompiledObservationPlan compactPlan,
            SemanticProgram program) {
        Map<String, String> result = new LinkedHashMap<>();
        for (var window : compactPlan.stepWindows()) {
            String taskId = window.istarTaskId();
            int dot = taskId.lastIndexOf('.');
            String simpleTaskId = dot >= 0 ? taskId.substring(dot + 1) : taskId;
            var template = program.goalBindings().elements().get(simpleTaskId);
            String owner = template != null ? template.selfDeclaration() : "";
            for (String functor : window.expectedActionFunctors()) {
                for (int arity = 0; arity <= 4; arity++) {
                    result.putIfAbsent(owner + "::" + functor + "/" + arity, simpleTaskId);
                }
            }
        }
        return result;
    }

    private static String oclToJavaType(String oclType) {
        return switch (oclType) {
            case "Integer" -> "int";
            case "Real" -> "double";
            case "Boolean" -> "boolean";
            default -> "java.lang.String";
        };
    }
}

