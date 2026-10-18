package org.tzi.use.plugins.jacamo.semantics.evidence;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;

/** Versioned portable program inputs; original mutable M1 is rebuilt and fingerprinted.
 * No Java serialization, MSystem, connection configuration or authentication material. */
public final class SemanticProgramManifest {
    public record Decoded(ModelSnapshot source,SemanticProgram program) { }
    private static final Set<String> FIELDS=Set.of("schemaVersion","profile","source","rules","programHash","cslHash","goalHash","bindingHash","traceHash","implementation");
    public byte[] encode(SemanticProgram program) {
        program.assertUnchanged();
        var assets=program.complement().assets().stream().filter(a->a.id().equals("official-model")&&a.format().equals("OFFICIAL_MODEL_SNAPSHOT_JSON")).toList();
        if(assets.size()!=1)throw new IllegalArgumentException("REPLAY_OFFICIAL_MODEL_ASSET_REQUIRED");
        var source=CanonicalJson.object(CanonicalJson.decode(assets.getFirst().bytes()));
        program.requireSourceHash(SemanticJson.hash(source));
        return CanonicalJson.encode(Map.of("schemaVersion","1.0.0","profile",SemanticJson.tree(program.profile()),"source",source,"rules",CanonicalJson.decode(program.rules().bytes()),
                "programHash",program.hash(),"cslHash",program.cslHash(),"goalHash",program.goalHash(),"bindingHash",program.bindings().hash(),
                "traceHash",SemanticJson.hash(SemanticJson.tree(program.complement())),"implementation",implementation()));
    }
    public Decoded decode(byte[] bytes) {
        var body=CanonicalJson.object(CanonicalJson.decode(bytes));
        if(!body.keySet().equals(FIELDS)||!"1.0.0".equals(body.get("schemaVersion")))throw new IllegalArgumentException("REPLAY_PROGRAM_SCHEMA_UNSUPPORTED");
        if(!SemanticJson.hash(implementation()).equals(SemanticJson.hash(body.get("implementation"))))throw new IllegalArgumentException("REPLAY_ENGINE_GRAMMAR_BACKEND_INCOMPATIBLE");
        var profile=JaCaMoMappingProfile.observedV1();
        if(!profile.hash().equals(SemanticJson.hash(body.get("profile"))))throw new IllegalArgumentException("REPLAY_MAPPING_PROFILE_UNSUPPORTED");
        var source=ContractPayloads.model(CanonicalJson.object(body.get("source")));
        var rules=MappingRuleSet.parse(CanonicalJson.encode(body.get("rules")));
        var program=new JaCaMoStaticMappingAdapter().map(source,profile,rules);
        if(!program.hash().equals(body.get("programHash"))||!program.cslHash().equals(body.get("cslHash"))||!program.goalHash().equals(body.get("goalHash"))
                ||!program.bindings().hash().equals(body.get("bindingHash"))||!SemanticJson.hash(SemanticJson.tree(program.complement())).equals(body.get("traceHash")))
            throw new IllegalArgumentException("REPLAY_PROGRAM_FINGERPRINT_MISMATCH");
        return new Decoded(source,program);
    }
    /** Exact executable compatibility; a future engine upgrade needs an explicit
     * reader/profile, never silent reinterpretation of an old observed trace. */
    public static Map<String,Object> implementation() {
        var hashes=new TreeMap<String,Object>();
        for(String name:List.of(
                "org.vnu.sme.goal.dsl.csl.parser.CslCompiler","org.vnu.sme.goal.dsl.csl.parser.CSLParser","org.vnu.sme.goal.dsl.csl.parser.CSLLexer",
                "org.vnu.sme.goal.dsl.istar.parser.IStarCompiler","org.vnu.sme.goal.dsl.istar.parser.IStarParser","org.vnu.sme.goal.dsl.istar.parser.IStarLexer",
                "org.vnu.sme.goal.verify.conformance.semantics.GoalMarking","org.vnu.sme.goal.verify.conformance.semantics.TaskMarking",
                "org.vnu.sme.goal.verify.conformance.semantics.IStarMarking","org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation",
                "org.vnu.sme.goal.dsl.iscn.goalmodelinstance.parser.GoalModelInstanceTranslator",
                "org.tzi.use.semantic.engine.runtime.Use4GoalRuntimeSessionAdapter","org.tzi.use.semantic.engine.use75.Use75CslModelBackend",
                "org.tzi.use.semantic.engine.use75.NativeOclBackend","org.tzi.use.semantic.engine.use75.Use75ModelPrinter",
                "org.tzi.use.semantic.engine.api.EngineInstallationGuard","org.antlr.v4.runtime.RuntimeMetaData","org.antlr.v4.runtime.Parser",
                "org.tzi.use.plugins.jacamo.semantics.mapping.JaCaMoStaticMappingAdapter","org.tzi.use.plugins.jacamo.semantics.mapping.JaCaMoRuntimeStateMapping",
                "org.tzi.use.plugins.jacamo.semantics.mapping.JaCaMoCslMappingRules","org.tzi.use.plugins.jacamo.semantics.mapping.JaCaMoGoalMappingRules",
                "org.tzi.use.plugins.jacamo.semantics.mapping.JaCaMoOclContractMapping","org.tzi.use.plugins.jacamo.semantics.mapping.CanonicalUseBindingAdapter",
                "org.tzi.use.plugins.jacamo.semantics.mapping.MappingRuleSet","org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram",
                "org.tzi.use.plugins.jacamo.semantics.mapping.JaCaMoMappingProfile","org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex",
                "org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection","org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder",
                "org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade",
                "org.tzi.use.plugins.jacamo.semantics.runtime.RuntimeEngineSessionOwner","org.tzi.use.plugins.jacamo.semantics.runtime.JaCaMoOccurrenceBindingAdapter",
                "org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine","org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeOperationCheckpoints",
                "org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationCoordinator","org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeInvocationEvaluation",
                "org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay","org.tzi.use.plugins.jacamo.codegrounded.runtime.CanonicalReplaySupport",
                "org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeEventJournal","org.tzi.use.plugins.jacamo.semantics.evidence.SemanticProgramManifest",
                "org.tzi.use.parser.ocl.OCLCompiler","org.tzi.use.uml.ocl.expr.Evaluator","org.tzi.use.uml.sys.MSystem"))
            hashes.put(name,resource("/"+name.replace('.','/')+".class"));
        for(String path:List.of("/grammars/CSL.g4","/grammars/IStar.g4","/META-INF/use4goal-provenance.json"))hashes.put(path,resource(path));
        return Collections.unmodifiableMap(hashes);
    }
    private static String resource(String path) {
        try(var input=SemanticProgramManifest.class.getResourceAsStream(path)) {
            if(input==null)throw new IllegalArgumentException("REPLAY_IMPLEMENTATION_RESOURCE_MISSING:"+path);
            return ExternalOclConstraintService.sha256(input.readAllBytes());
        }catch(java.io.IOException error){throw new IllegalArgumentException("REPLAY_IMPLEMENTATION_HASH_UNAVAILABLE:"+path,error);}
    }
}
