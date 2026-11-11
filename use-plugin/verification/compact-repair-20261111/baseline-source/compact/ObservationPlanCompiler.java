package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.jacamo.bridge.contract.ObservationCapabilityRequest;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointPolicySelection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Compiles a CompactMappingSpecification and admitted catalogs into an immutable CompiledObservationPlan.
 * Generates necessary capability demands, checkpoint rules, type conversions, and transparent explanations.
 */
public final class ObservationPlanCompiler {

    public static CompiledObservationPlan compile(
            CompactMappingSpecification spec,
            SourceSymbolCatalog sourceCatalog,
            NativeTargetCatalog targetCatalog) {
        return compile(spec, sourceCatalog, targetCatalog, "", "", "", "");
    }

    public static CompiledObservationPlan compile(
            CompactMappingSpecification spec,
            SourceSymbolCatalog sourceCatalog,
            NativeTargetCatalog targetCatalog,
            String cslModelSha,
            String istarModelSha) {
        return compile(spec, sourceCatalog, targetCatalog, cslModelSha, istarModelSha, "", "");
    }

    public static CompiledObservationPlan compile(
            CompactMappingSpecification spec,
            SourceSymbolCatalog sourceCatalog,
            NativeTargetCatalog targetCatalog,
            String cslModelSha,
            String istarModelSha,
            String oclSha,
            String engineProfile) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(sourceCatalog, "sourceCatalog");
        Objects.requireNonNull(targetCatalog, "targetCatalog");

        // 1. Strict semantic validation
        CorrespondenceValidator.requireValid(spec, sourceCatalog, targetCatalog);

        List<CslBinding> cslBindings = new ArrayList<>();
        List<IStarBinding> istarBindings = new ArrayList<>();
        List<ActorRuntimeBinding> actorRuntimes = new ArrayList<>();
        List<BindingExplanation> explanations = new ArrayList<>();

        Map<String, Set<String>> artifactFieldsToObserve = new LinkedHashMap<>();
        Map<String, Set<String>> artifactOpsToObserve = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> aslPlanStepsToObserve = new LinkedHashMap<>();
        boolean hasMoiseSchemes = false;

        // Collect all mapped artifact classes and entity-to-artifact mappings
        Set<String> mappedArtifactClasses = new LinkedHashSet<>();
        Map<String, String> entityToArtifactClass = new LinkedHashMap<>();
        for (CompactMappingSpecification.Correspondence c : spec.cslMappings()) {
            SourceReference sRef = SourceReference.parse(c.source());
            TargetReference tRef = TargetReference.parse(c.target());
            if (sRef instanceof SourceReference.ArtifactRef ar) {
                mappedArtifactClasses.add(ar.className());
                entityToArtifactClass.put(tRef.qualifier(), ar.className());
            } else if (sRef instanceof SourceReference.ArtifactPropertyRef pr) {
                mappedArtifactClasses.add(pr.className());
                entityToArtifactClass.putIfAbsent(tRef.qualifier(), pr.className());
            } else if (sRef instanceof SourceReference.ArtifactFieldRef fr) {
                mappedArtifactClasses.add(fr.className());
                entityToArtifactClass.putIfAbsent(tRef.qualifier(), fr.className());
            } else if (sRef instanceof SourceReference.ArtifactOpRef or) {
                mappedArtifactClasses.add(or.className());
                entityToArtifactClass.putIfAbsent(tRef.qualifier(), or.className());
            }
        }

        // 2. Compile CSL correspondences
        for (CompactMappingSpecification.Correspondence c : spec.cslMappings()) {
            SourceReference sRef = SourceReference.parse(c.source());
            TargetReference tRef = TargetReference.parse(c.target());
            SourceSymbol sSymbol = sourceCatalog.resolve(sRef);
            TargetSymbol tSymbol = targetCatalog.resolve(tRef, "CSL");

            CslBinding.BindingKind bKind;
            CslBinding.TypeConversion conv = CslBinding.TypeConversion.DIRECT;
            String frameworkRule;
            String evidenceReq;

            switch (sSymbol.kind()) {
                case ARTIFACT -> {
                    bKind = CslBinding.BindingKind.ENTITY_MAPPING;
                    frameworkRule = "CSL_ENTITY_FROM_ARTIFACT";
                    evidenceReq = "CArtAgO workspace artifact instance snapshot";
                }
                case ARTIFACT_PROPERTY -> {
                    bKind = CslBinding.BindingKind.ATTRIBUTE_MAPPING;
                    conv = inferTypeConversion(tSymbol.declaredType());
                    frameworkRule = "CSL_ATTRIBUTE_FROM_PROPERTY";
                    evidenceReq = "CArtAgO live observable property value";
                }
                case ARTIFACT_FIELD -> {
                    bKind = CslBinding.BindingKind.ATTRIBUTE_MAPPING;
                    conv = inferTypeConversion(tSymbol.declaredType());
                    frameworkRule = "CSL_ATTRIBUTE_FROM_FIELD";
                    evidenceReq = "CArtAgO internal field snapshot via reflection";
                    if (sRef instanceof SourceReference.ArtifactFieldRef afr) {
                        artifactFieldsToObserve.computeIfAbsent(afr.className(), k -> new LinkedHashSet<>()).add(afr.fieldName());
                    }
                }
                case ARTIFACT_OPERATION -> {
                    bKind = CslBinding.BindingKind.ENTITY_MAPPING;
                    frameworkRule = "CSL_ENTITY_FROM_OPERATION";
                    evidenceReq = "CArtAgO operation execution receipt";
                    if (sRef instanceof SourceReference.ArtifactOpRef aor) {
                        artifactOpsToObserve.computeIfAbsent(aor.className(), k -> new LinkedHashSet<>()).add(aor.operationName());
                    }
                }
                case MOISE_GROUP -> {
                    bKind = CslBinding.BindingKind.GROUP_MAPPING;
                    frameworkRule = "CSL_GROUP_FROM_MOISE_GROUP";
                    evidenceReq = "Moise group instance snapshot";
                }
                case MOISE_ROLE -> {
                    bKind = CslBinding.BindingKind.ROLE_MAPPING;
                    frameworkRule = "CSL_ROLE_FROM_MOISE_ROLE";
                    evidenceReq = "Moise role adoption snapshot";
                }
                default -> {
                    bKind = CslBinding.BindingKind.ENTITY_MAPPING;
                    frameworkRule = "GENERIC_CSL_MAPPING";
                    evidenceReq = "Admitted source snapshot fact";
                }
            }

            cslBindings.add(new CslBinding(sRef, tRef, sSymbol, tSymbol, bKind, conv));
            explanations.add(new BindingExplanation(c.source(), c.target(), frameworkRule, evidenceReq));
        }

        // 3. Compile iStar correspondences
        for (CompactMappingSpecification.Correspondence c : spec.istarMappings()) {
            SourceReference sRef = SourceReference.parse(c.source());
            TargetReference tRef = TargetReference.parse(c.target());
            SourceSymbol sSymbol = sourceCatalog.resolve(sRef);
            TargetSymbol tSymbol = targetCatalog.resolve(tRef, "ISTAR");

            IStarBinding.BindingKind bKind;
            Optional<IStarBinding.StepRange> stepRange = Optional.empty();
            String frameworkRule;
            String evidenceReq;

            switch (sSymbol.kind()) {
                case AGENT -> {
                    bKind = IStarBinding.BindingKind.ACTOR_MAPPING;
                    frameworkRule = "ISTAR_ACTOR_FROM_AGENT";
                    evidenceReq = "Jason agent declaration / physical player";
                }
                case MOISE_ROLE -> {
                    bKind = IStarBinding.BindingKind.ACTOR_MAPPING;
                    frameworkRule = "ISTAR_ACTOR_FROM_MOISE_ROLE";
                    evidenceReq = "Moise role adoption / enactment";
                }
                case ASL_GOAL -> {
                    bKind = IStarBinding.BindingKind.GOAL_MAPPING;
                    frameworkRule = "ISTAR_GOAL_FROM_ASL_GOAL";
                    evidenceReq = "Jason agent goal trigger";
                }
                case MOISE_GOAL -> {
                    bKind = IStarBinding.BindingKind.GOAL_MAPPING;
                    hasMoiseSchemes = true;
                    frameworkRule = "ISTAR_GOAL_FROM_MOISE_GOAL";
                    evidenceReq = "Moise scheme organizational goal";
                }
                case ASL_PLAN_STEP -> {
                    bKind = IStarBinding.BindingKind.TASK_MAPPING;
                    if (sRef instanceof SourceReference.AslPlanStepRef psr) {
                        stepRange = Optional.of(new IStarBinding.StepRange(psr.firstIndex(), psr.afterIndex()));
                        frameworkRule = "ISTAR_TASK_FROM_PLAN_STEP";
                        evidenceReq = "Jason plan body step execution bounds [" + psr.firstIndex() + ".." + psr.afterIndex() + "]";

                        String stepArtifactClass = resolveStepArtifactClass(
                                c, tSymbol, spec, mappedArtifactClasses, entityToArtifactClass);

                        Map<String, Object> stepMap = new LinkedHashMap<>();
                        stepMap.put("first", psr.firstIndex());
                        stepMap.put("after", psr.afterIndex());
                        stepMap.put("task", tSymbol.name());
                        stepMap.put("artifactClass", stepArtifactClass);

                        aslPlanStepsToObserve.computeIfAbsent(psr.aslPath() + ":" + psr.planTrigger(), k -> new ArrayList<>()).add(stepMap);
                    } else {
                        frameworkRule = "ISTAR_TASK_FROM_PLAN_STEP";
                        evidenceReq = "Jason plan step execution";
                    }
                }
                default -> {
                    bKind = tSymbol.kind() == TargetSymbol.TargetKind.ISTAR_ACTOR
                            ? IStarBinding.BindingKind.ACTOR_MAPPING
                            : IStarBinding.BindingKind.GOAL_MAPPING;
                    frameworkRule = "GENERIC_ISTAR_MAPPING";
                    evidenceReq = "Admitted intentional element evidence";
                }
            }

            istarBindings.add(new IStarBinding(sRef, tRef, sSymbol, tSymbol, bKind, stepRange));
            explanations.add(new BindingExplanation(c.source(), c.target(), frameworkRule, evidenceReq));
        }

        // 4. Compile Actor Runtimes
        for (CompactMappingSpecification.ActorRuntime ar : spec.actorRuntimes()) {
            TargetSymbol tSymbol = targetCatalog.resolve(TargetReference.parse(ar.actor()), "ISTAR");
            actorRuntimes.add(new ActorRuntimeBinding(ar.actor(), tSymbol, ar.activationPolicy(), ar.episodePolicy()));
        }

        // 5. Synthesize Observation Capability Requests
        List<ObservationCapabilityRequest> capabilityRequests = new ArrayList<>();

        // 5.1 Artifact Fields
        for (var entry : artifactFieldsToObserve.entrySet()) {
            String className = entry.getKey();
            List<String> fields = new ArrayList<>(entry.getValue());
            String reqId = "req_field_" + sanitizeId(className);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("sourceRef", "artifact:" + className);
            params.put("class", className);
            params.put("field", fields.getFirst()); // primary field
            params.put("fields", fields);
            capabilityRequests.add(new ObservationCapabilityRequest(reqId, "cartago.artifact-field.v1", true, params));
        }

        // 5.2 Artifact Operations
        for (var entry : artifactOpsToObserve.entrySet()) {
            String className = entry.getKey();
            List<String> ops = new ArrayList<>(entry.getValue());
            String reqId = "req_op_" + sanitizeId(className);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("sourceRef", "artifact:" + className);
            params.put("class", className);
            params.put("operations", ops);
            capabilityRequests.add(new ObservationCapabilityRequest(reqId, "cartago.operation-history.v1", true, params));
        }

        // 5.3 ASL Plan Boundaries
        if (!aslPlanStepsToObserve.isEmpty()) {
            List<Map<String, Object>> plansList = new ArrayList<>();
            for (var entry : aslPlanStepsToObserve.entrySet()) {
                String key = entry.getKey();
                String[] parts = key.split(":", 2);
                String trigger = parts.length > 1 ? parts[1] : key;
                Map<String, Object> planEntry = new LinkedHashMap<>();
                planEntry.put("trigger", trigger);
                planEntry.put("steps", entry.getValue());
                plansList.add(planEntry);
            }
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("sourceRef", "asl:plan-boundaries");
            params.put("plans", plansList);
            capabilityRequests.add(new ObservationCapabilityRequest("req_plan_bounds", "jason.plan-boundaries.v1", true, params));
        }

        // 5.4 Moise Schemes
        if (hasMoiseSchemes) {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("sourceRef", "moise");
            capabilityRequests.add(new ObservationCapabilityRequest("req_moise_schemes", "moise.scheme-lifecycle.v1", true, params));
        }

        // 5.5 Explicit Requirements from <runtime>
        for (CompactMappingSpecification.Requirement req : spec.requirements()) {
            if (!req.provider().matches("^[A-Za-z0-9_.-]+\\.v[0-9]+$")) {
                throw new IllegalArgumentException(
                        "UNVERSIONED_CAPABILITY_PROVIDER: '" + req.provider() + "' on requirement '" + req.id() + "' must end with a version suffix like '.v1'");
            }
            capabilityRequests.add(new ObservationCapabilityRequest(req.id(), req.provider(), req.required(), req.parameters()));
        }

        // 6. Synthesize Checkpoint Rules
        List<VerifiedCheckpointProtocol.Rule> checkpointRules = CheckpointPolicySelection.rules(capabilityRequests, task -> true);

        // 7. Compute Deterministic Plan Fingerprint
        String fingerprint = computePlanFingerprint(
                spec, sourceCatalog, cslBindings, istarBindings, actorRuntimes,
                capabilityRequests, checkpointRules, cslModelSha, istarModelSha, oclSha, engineProfile);

        return new CompiledObservationPlan(
                spec.id(),
                fingerprint,
                spec,
                cslBindings,
                istarBindings,
                actorRuntimes,
                capabilityRequests,
                checkpointRules,
                explanations,
                targetCatalog.cslModel(),
                targetCatalog.goalModel()
        );
    }

    private static String resolveStepArtifactClass(
            CompactMappingSpecification.Correspondence c,
            TargetSymbol tSymbol,
            CompactMappingSpecification spec,
            Set<String> mappedArtifactClasses,
            Map<String, String> entityToArtifactClass) {
        if (c.scope().isPresent() && !c.scope().get().isBlank()) {
            String sc = c.scope().get().trim();
            if (entityToArtifactClass.containsKey(sc)) {
                return entityToArtifactClass.get(sc);
            }
            if (mappedArtifactClasses.contains(sc)) {
                return sc;
            }
            for (String ac : mappedArtifactClasses) {
                if (ac.endsWith("." + sc)) return ac;
            }
            return sc;
        }
        if (mappedArtifactClasses.size() == 1) {
            return mappedArtifactClasses.iterator().next();
        }
        if (mappedArtifactClasses.size() > 1) {
            if (tSymbol.owner() != null) {
                for (CompactMappingSpecification.GoalRealization gr : spec.goalRealizations()) {
                    if (gr.actor().equals(tSymbol.owner()) && entityToArtifactClass.containsKey(gr.rootClass())) {
                        return entityToArtifactClass.get(gr.rootClass());
                    }
                }
            }
            throw new IllegalArgumentException(
                    "AMBIGUOUS_STEP_ARTIFACT_SCOPE: multiple artifact classes mapped " + mappedArtifactClasses
                            + "; specify scope on step mapping '" + c.source() + " -> " + c.target() + "'");
        }
        return "";
    }

    private static CslBinding.TypeConversion inferTypeConversion(String declaredType) {
        if (declaredType == null) return CslBinding.TypeConversion.DIRECT;
        return switch (declaredType.toLowerCase(Locale.ROOT)) {
            case "string" -> CslBinding.TypeConversion.TO_STRING;
            case "integer", "int" -> CslBinding.TypeConversion.TO_INTEGER;
            case "real", "double", "float" -> CslBinding.TypeConversion.TO_REAL;
            case "boolean", "bool" -> CslBinding.TypeConversion.TO_BOOLEAN;
            default -> CslBinding.TypeConversion.DIRECT;
        };
    }

    private static String sanitizeId(String name) {
        return name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String computePlanFingerprint(
            CompactMappingSpecification spec,
            SourceSymbolCatalog sourceCatalog,
            List<CslBinding> cslBindings,
            List<IStarBinding> istarBindings,
            List<ActorRuntimeBinding> actorRuntimes,
            List<ObservationCapabilityRequest> capabilityRequests,
            List<VerifiedCheckpointProtocol.Rule> checkpointRules,
            String cslSha,
            String istarSha,
            String oclSha,
            String engineProfile) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(CompiledObservationPlan.COMPILER_VERSION.getBytes(StandardCharsets.UTF_8));
            md.update(spec.id().getBytes(StandardCharsets.UTF_8));
            md.update(spec.sha256().getBytes(StandardCharsets.UTF_8));
            if (cslSha != null) md.update(cslSha.getBytes(StandardCharsets.UTF_8));
            if (istarSha != null) md.update(istarSha.getBytes(StandardCharsets.UTF_8));
            if (oclSha != null) md.update(oclSha.getBytes(StandardCharsets.UTF_8));
            if (engineProfile != null) md.update(engineProfile.getBytes(StandardCharsets.UTF_8));

            for (SourceSymbol sym : sourceCatalog.allSymbols()) {
                md.update((sym.kind() + ":" + sym.qualifiedName() + ":" + Objects.toString(sym.sourceDigest(), "")).getBytes(StandardCharsets.UTF_8));
            }
            for (CslBinding b : cslBindings) {
                md.update((b.sourceRef().raw() + "->" + b.targetRef().raw() + ":" + b.kind() + ":" + b.conversion() + ":" + Objects.toString(b.sourceSymbol().sourceDigest(), "")).getBytes(StandardCharsets.UTF_8));
            }
            for (IStarBinding b : istarBindings) {
                md.update((b.sourceRef().raw() + "->" + b.targetRef().raw() + ":" + b.kind() + ":" + b.stepRange().map(Record::toString).orElse("")).getBytes(StandardCharsets.UTF_8));
            }
            for (ActorRuntimeBinding ar : actorRuntimes) {
                md.update((ar.actorName() + ":" + ar.activationPolicy() + ":" + ar.episodePolicy()).getBytes(StandardCharsets.UTF_8));
            }
            for (CompactMappingSpecification.GoalRealization gr : spec.goalRealizations()) {
                md.update((gr.actor() + ":" + gr.rootClass() + ":" + gr.population() + ":" + gr.attributes()).getBytes(StandardCharsets.UTF_8));
            }
            for (CompactMappingSpecification.DependencyPopulation dp : spec.dependencyPopulations()) {
                md.update((dp.dependum() + ":" + dp.quantifier()).getBytes(StandardCharsets.UTF_8));
            }
            for (ObservationCapabilityRequest r : capabilityRequests) {
                md.update((r.id() + ":" + r.provider() + ":" + r.required() + ":" + r.parameters()).getBytes(StandardCharsets.UTF_8));
            }
            for (VerifiedCheckpointProtocol.Rule r : checkpointRules) {
                md.update((r.id() + ":" + r.selectorKind() + ":" + r.operations()).getBytes(StandardCharsets.UTF_8));
            }

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }

    private ObservationPlanCompiler() {}
}

