from pathlib import Path
p=Path('use-plugin/src/main/java/org/tzi/use/plugins/jacamo/semantics/mapping/compact/ObservationPlanCompiler.java')
s=p.read_text(encoding='utf-8')
a=s.index('        try {\n            MessageDigest md',s.index('    private static String computePlanFingerprint('))
b=s.index('    private ObservationPlanCompiler()',a)
s=s[:a]+'''        var identity=new TreeMap<String,Object>();
        identity.put("compiler",CompiledObservationPlan.COMPILER_VERSION);
        identity.put("mapping",spec.sha256());
        identity.put("mappingId",spec.id());
        identity.put("csl",Objects.toString(cslSha,""));
        identity.put("istar",Objects.toString(istarSha,""));
        identity.put("ocl",Objects.toString(oclSha,""));
        identity.put("engineProfile",Objects.toString(engineProfile,""));
        identity.put("sources",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(sourceCatalog.allSymbols()));
        identity.put("cslBindings",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(cslBindings));
        identity.put("istarBindings",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(istarBindings));
        identity.put("actorBindings",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(actorRuntimes));
        identity.put("runtime",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(spec.actorRuntimes()));
        identity.put("goalViews",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(spec.goalRealizations()));
        identity.put("dependencyPopulations",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(spec.dependencyPopulations()));
        identity.put("requirements",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(capabilityRequests));
        identity.put("checkpoints",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(checkpointRules));
        return org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(identity);
    }

'''+s[b:]
s=s.replace('import java.security.MessageDigest;\n','').replace('import java.security.NoSuchAlgorithmException;\n','').replace('import java.nio.charset.StandardCharsets;\n','')
# The simple compiler overloads still bind the actual models, not empty hash arguments.
s=s.replace('        // 7. Compute Deterministic Plan Fingerprint','''        if (cslModelSha == null || cslModelSha.isBlank()) cslModelSha = targetCatalog.cslModel()
                .map(org.tzi.use.semantic.engine.api.CslModelFingerprint::of).orElse("");
        if (istarModelSha == null || istarModelSha.isBlank()) istarModelSha = targetCatalog.goalModel()
                .map(org.tzi.use.semantic.engine.api.OwnedGoalModel::fingerprint).orElse("");
        // 7. Compute Deterministic Plan Fingerprint''')
p.write_text(s,encoding='utf-8')
