package org.tzi.use.plugins.jacamo.semantics.mapping.compact;

import org.jacamo.bridge.contract.ObservationCapabilityRequest;
import org.jacamo.bridge.contract.VerifiedCheckpointProtocol;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.CheckpointPolicySelection;

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
            SourceSymbol sSymbol = sourceCatalog.select(c);
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
                case ARTIFACT_PROPERTY, ARTIFACT_PROPERTY_SELECTION -> {
                    bKind = CslBinding.BindingKind.ATTRIBUTE_MAPPING;
                    conv = inferTypeConversion(tSymbol.declaredType());
                    frameworkRule = "CSL_ATTRIBUTE_FROM_PROPERTY";
                    evidenceReq = "CArtAgO live observable property value";
                }
                case ARTIFACT_IDENTITY,WORKSPACE_IDENTITY -> {
                    bKind=CslBinding.BindingKind.ATTRIBUTE_MAPPING;conv=CslBinding.TypeConversion.TO_STRING;
                    frameworkRule="CSL_ATTRIBUTE_FROM_PHYSICAL_IDENTITY";evidenceReq="Qualified actual artifact/workspace identity";
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
                case PROJECT,WORKSPACE,ORGANIZATION,MOISE_GROUP -> {
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
            SourceSymbol sSymbol = sourceCatalog.select(c);
            TargetSymbol tSymbol = targetCatalog.resolve(tRef, "ISTAR");

            IStarBinding.BindingKind bKind;
            Optional<IStarBinding.StepRange> stepRange = Optional.empty();
            String frameworkRule;
            String evidenceReq;

            switch (sSymbol.kind()) {
                case AGENT,PROJECT,WORKSPACE -> {
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

                        String stepArtifactClass = c.attributes().getOrDefault("artifactClass", "");

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

        // Provider contracts are typed and pinned; incomplete shorthand is never
        // forwarded as if it were an admitted producer request.
        var capabilityRequests=ObservationCapabilityPlanner.compile(spec,sourceCatalog,cslBindings,istarBindings);
        var nativeTasks=istarBindings.stream().filter(b->b.kind()==IStarBinding.BindingKind.TASK_MAPPING).map(b->b.targetSymbol().name()).collect(java.util.stream.Collectors.toSet());
        var checkpointRules=CheckpointPolicySelection.rules(capabilityRequests,nativeTasks::contains);

        if (cslModelSha == null || cslModelSha.isBlank()) cslModelSha = targetCatalog.cslModel()
                .map(org.tzi.use.semantic.engine.api.CslModelFingerprint::of).orElse("");
        if (istarModelSha == null || istarModelSha.isBlank()) istarModelSha = targetCatalog.goalModel()
                .map(org.tzi.use.semantic.engine.api.OwnedGoalModel::fingerprint).orElse("");
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
        if (!c.attributes().getOrDefault("scope", "").isBlank()) {
            String sc = c.attributes().get("scope").trim();
            if (entityToArtifactClass.containsKey(sc)) {
                return entityToArtifactClass.get(sc);
            }
            if (mappedArtifactClasses.contains(sc)) {
                return sc;
            }
            var candidates = mappedArtifactClasses.stream().filter(ac -> ac.endsWith("." + sc)).toList();
            if (candidates.size() == 1) return candidates.getFirst();
            throw c.location().error("STEP_ARTIFACT_SCOPE_NOT_FOUND_OR_AMBIGUOUS:" + sc);
        }
        if (mappedArtifactClasses.size() == 1) {
            return mappedArtifactClasses.iterator().next();
        }
        if (mappedArtifactClasses.size() > 1) {
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
        var identity=new TreeMap<String,Object>();
        identity.put("compiler",CompiledObservationPlan.COMPILER_VERSION);
        identity.put("mapping",spec.sha256());
        identity.put("mappingId",spec.id());
        identity.put("csl",Objects.toString(cslSha,""));
        identity.put("istar",Objects.toString(istarSha,""));
        identity.put("ocl",Objects.toString(oclSha,""));
        identity.put("engineProfile",Objects.toString(engineProfile,""));
        identity.put("sources",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(sourceCatalog.allSymbols()));
        identity.put("cslBindings",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(cslBindings));
        identity.put("istarBindings",istarBindings.stream().map(b->Map.<String,Object>of(
                "source",b.sourceRef().raw(),"target",b.targetRef().raw(),"kind",b.kind().name(),
                "range",b.stepRange().map(Record::toString).orElse(""),
                "sourceSymbol",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(b.sourceSymbol()),
                "targetSymbol",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(b.targetSymbol()))).toList());
        identity.put("actorBindings",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(actorRuntimes));
        identity.put("runtime",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(spec.actorRuntimes()));
        identity.put("goalViews",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(spec.goalRealizations()));
        identity.put("dependencyPopulations",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(spec.dependencyPopulations()));
        identity.put("requirements",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(capabilityRequests));
        identity.put("checkpoints",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(checkpointRules));
        return org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(identity);
    }

    private ObservationPlanCompiler() {}
}

