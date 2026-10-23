package org.tzi.use.plugins.jacamo.semantics.evidence;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.auction.*;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;

/** Versioned portable program inputs; original mutable M1 is rebuilt and fingerprinted.
 * No Java serialization, MSystem, connection configuration or authentication material. */
public final class SemanticProgramManifest {
    public record Decoded(ModelSnapshot source,SemanticProgram program) { }
    private static final Set<String> FIELDS=Set.of("schemaVersion","profile","source","rules","programHash","cslHash","goalHash","bindingHash","traceHash","implementation");
    private static final Set<String> CASE_FIELDS=Set.of("version","manifest","assets","derivedJcm","jcmDerivation","requiredCapabilities");
    private static final Set<String> CASE_CAPABILITIES=Set.of(VerificationObservation.CAPABILITY,"runtime.observation.jason-step.v1","runtime.observation.goal-transition.v1","runtime.observation.clock.v1");
    public byte[] encode(SemanticProgram program) {
        program.assertUnchanged();
        var assets=program.complement().assets().stream().filter(a->a.id().equals("official-model")&&a.format().equals("OFFICIAL_MODEL_SNAPSHOT_JSON")).toList();
        if(assets.size()!=1)throw new IllegalArgumentException("REPLAY_OFFICIAL_MODEL_ASSET_REQUIRED");
        var source=CanonicalJson.object(CanonicalJson.decode(assets.getFirst().bytes()));
        program.requireSourceHash(SemanticJson.hash(source));
        if(program.xmlPlan()!=null) {
            var inputs=program.xmlPlan().inputs();var body=new TreeMap<String,Object>();
            body.put("schemaVersion","1.2.0");body.put("profile",SemanticJson.tree(program.profile()));body.put("source",source);body.put("rules",CanonicalJson.decode(program.rules().bytes()));
            body.put("programHash",program.hash());body.put("cslHash",program.cslHash());body.put("goalHash",program.goalHash());body.put("bindingHash",program.bindings().hash());body.put("traceHash",SemanticJson.hash(SemanticJson.tree(program.complement())));body.put("implementation",declarativeImplementation());
            body.put("declarative",Map.of("version","JACAMO_MAPPING_XML/2.0","location",inputs.specification().root().location().source(),"xml",SemanticJson.tree(inputs.xml()),"ocl",SemanticJson.tree(inputs.ocl()),"sources",SemanticJson.tree(inputs.sources().values())));
            return CanonicalJson.encode(body);
        }
        if(program.casePlan()!=null) {
            var plan=program.casePlan();var derived=program.complement().assets().stream().filter(a->a.id().equals("src-jcm:derived")).toList();
            if(derived.size()!=1)throw new IllegalArgumentException("REPLAY_CASE_DERIVED_JCM_REQUIRED");
            var body=new TreeMap<String,Object>();body.put("schemaVersion","1.1.0");body.put("profile",SemanticJson.tree(program.profile()));body.put("source",source);body.put("rules",CanonicalJson.decode(program.rules().bytes()));
            body.put("programHash",program.hash());body.put("cslHash",program.cslHash());body.put("goalHash",program.goalHash());body.put("bindingHash",program.bindings().hash());body.put("traceHash",SemanticJson.hash(SemanticJson.tree(program.complement())));body.put("implementation",caseImplementation());
            body.put("case",Map.of("version","AUCTION_CASE/1.0","manifest",SemanticJson.tree(plan.bundle().manifest()),"assets",SemanticJson.tree(plan.bundle().assets().values()),"derivedJcm",SemanticJson.tree(derived.getFirst()),"jcmDerivation",plan.evidence().get("jcmDerivation"),"requiredCapabilities",CASE_CAPABILITIES.stream().sorted().toList()));
            return CanonicalJson.encode(body);
        }
        return CanonicalJson.encode(Map.of("schemaVersion","1.0.0","profile",SemanticJson.tree(program.profile()),"source",source,"rules",CanonicalJson.decode(program.rules().bytes()),
                "programHash",program.hash(),"cslHash",program.cslHash(),"goalHash",program.goalHash(),"bindingHash",program.bindings().hash(),
                "traceHash",SemanticJson.hash(SemanticJson.tree(program.complement())),"implementation",implementation()));
    }
    public Decoded decode(byte[] bytes) {
        var body=CanonicalJson.object(CanonicalJson.decode(bytes));
        if("1.2.0".equals(body.get("schemaVersion")))return decodeDeclarative(body);
        if("1.1.0".equals(body.get("schemaVersion")))return decodeCase(body);
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
    private Decoded decodeDeclarative(Map<String,Object> body) {
        var expected=new HashSet<>(FIELDS);expected.add("declarative");if(!body.keySet().equals(expected))throw new IllegalArgumentException("REPLAY_DECLARATIVE_FIELDS");
        if(!SemanticJson.hash(declarativeImplementation()).equals(SemanticJson.hash(body.get("implementation"))))throw new IllegalArgumentException("REPLAY_DECLARATIVE_IMPLEMENTATION_INCOMPATIBLE");
        var spec=CanonicalJson.object(body.get("declarative"));
        if(!spec.keySet().equals(Set.of("version","location","xml","ocl","sources"))||!"JACAMO_MAPPING_XML/2.0".equals(spec.get("version"))||!(spec.get("location") instanceof String location)||!(spec.get("sources") instanceof List<?> rows)||rows.size()>128)throw new IllegalArgumentException("REPLAY_DECLARATIVE_SCHEMA_UNSUPPORTED");
        var xml=asset(spec.get("xml"));var ocl=asset(spec.get("ocl"));
        if(!xml.id().equals("mapping-specification")||!xml.format().equals("JACAMO_MAPPING_XML/2.0")||!ocl.id().equals("authored-ocl")||!ocl.format().equals("NATIVE_USE_OCL"))throw new IllegalArgumentException("REPLAY_DECLARATIVE_ASSET_TYPE");
        var sources=new LinkedHashMap<String,TraceComplement.Asset>();for(var row:rows){var value=asset(row);if(sources.putIfAbsent(value.id(),value)!=null)throw new IllegalArgumentException("REPLAY_DECLARATIVE_SOURCE_DUPLICATE");}
        var inputs=org.tzi.use.plugins.jacamo.semantics.mapping.xml.MappingInputs.fromFrozen(xml.bytes(),ocl.bytes(),sources,location);
        var source=ContractPayloads.model(CanonicalJson.object(body.get("source")));var program=new org.tzi.use.plugins.jacamo.semantics.mapping.xml.GenericMappingEngine().map(source,inputs);
        if(!program.profile().hash().equals(SemanticJson.hash(body.get("profile")))||!Arrays.equals(program.rules().bytes(),CanonicalJson.encode(body.get("rules")))
                ||!program.hash().equals(body.get("programHash"))||!program.cslHash().equals(body.get("cslHash"))||!program.goalHash().equals(body.get("goalHash"))||!program.bindings().hash().equals(body.get("bindingHash"))||!SemanticJson.hash(SemanticJson.tree(program.complement())).equals(body.get("traceHash")))throw new IllegalArgumentException("REPLAY_DECLARATIVE_FINGERPRINT_MISMATCH");
        return new Decoded(source,program);
    }
    private static Map<String,Object> declarativeImplementation() {
        var result=new TreeMap<>(implementation());
        for(String name:List.of("MappingInputs","MappingXmlReader","MappingSpecification","MappingVocabulary","GenericMappingEngine","GenericBindingPlan","SourceSelectors","SourceSelections","CslRuleExecutor","IStarRuleExecutor","CapabilityRequirements")) {
            String fqcn="org.tzi.use.plugins.jacamo.semantics.mapping.xml."+name;result.put(fqcn,resource("/"+fqcn.replace('.','/')+".class"));
        }
        for(String name:List.of("RuntimeTables","RuntimeFactSources","RuntimeValueConverters","M0RuleExecutor","XmlRuntimeBatch","XmlRuntimeStateMapping","OccurrenceProjection","TaskDemandProjection","XmlTaskCheckpoints","RuntimeObservationCut","JasonStepCutDecoder","MoiseSchemeCutDecoder","ArtifactFieldCutDecoder","OperationHistoryCutDecoder","CartagoPhysicalReference","ObserverProjection","NativeObserverExpression","NplLifecycleCut","NplObserverHistory","MoiseFactQualification")) {
            String fqcn="org.tzi.use.plugins.jacamo.semantics.mapping.xml.runtime."+name;result.put(fqcn,resource("/"+fqcn.replace('.','/')+".class"));
        }
        for(String name:List.of("org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlTaskOclBindings","org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlIntentionalOclBindings","org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlNativePreparation","org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlOperationBindings","org.tzi.use.plugins.jacamo.semantics.mapping.ocl.ObserverSchema","org.tzi.use.plugins.jacamo.semantics.mapping.ocl.CslScopeModelAdapter","org.tzi.use.plugins.jacamo.semantics.mapping.ocl.AuthoredOclSpecificationLoader","org.tzi.use.plugins.jacamo.semantics.runtime.OccurrenceGoalModelExpander","org.tzi.use.plugins.jacamo.semantics.runtime.SemanticResultAdapter","org.jacamo.bridge.contract.ObservationCapabilityRequest","org.jacamo.bridge.contract.CapabilityObservation","org.jacamo.bridge.contract.VerificationObservation"))result.put(name,resource("/"+name.replace('.','/')+".class"));
        return Collections.unmodifiableMap(result);
    }
    private Decoded decodeCase(Map<String,Object> body) {
        var expected=new HashSet<>(FIELDS);expected.add("case");if(!body.keySet().equals(expected))throw new IllegalArgumentException("REPLAY_CASE_PROGRAM_FIELDS");
        if(!SemanticJson.hash(caseImplementation()).equals(SemanticJson.hash(body.get("implementation"))))throw new IllegalArgumentException("REPLAY_CASE_IMPLEMENTATION_INCOMPATIBLE");
        var spec=CanonicalJson.object(body.get("case"));
        if(!spec.keySet().equals(CASE_FIELDS)||!"AUCTION_CASE/1.0".equals(spec.get("version"))||!(spec.get("requiredCapabilities") instanceof List<?> required)||!new HashSet<>(required).equals(CASE_CAPABILITIES)||required.size()!=CASE_CAPABILITIES.size())throw new IllegalArgumentException("REPLAY_CASE_SCHEMA_OR_CAPABILITIES_UNSUPPORTED");
        var manifest=asset(spec.get("manifest"));if(!manifest.id().equals(AuctionCaseBundle.MANIFEST)||!manifest.format().equals("AUCTION_CASE_MANIFEST/1.0"))throw new IllegalArgumentException("REPLAY_CASE_MANIFEST_TYPE");
        if(!(spec.get("assets") instanceof List<?> rawAssets))throw new IllegalArgumentException("REPLAY_CASE_ASSETS_REQUIRED");
        var assets=new LinkedHashMap<String,TraceComplement.Asset>();for(var raw:rawAssets){var asset=asset(raw);if(assets.putIfAbsent(asset.id(),asset)!=null)throw new IllegalArgumentException("REPLAY_CASE_ASSET_DUPLICATE");}
        var bundle=AuctionCaseBundle.fromFrozen(manifest.bytes(),assets);var source=ContractPayloads.model(CanonicalJson.object(body.get("source")));
        var derivation=AuctionJcmDerivation.fromFrozen(bundle,source,asset(spec.get("derivedJcm")),CanonicalJson.object(spec.get("jcmDerivation")));
        var selection=new AuctionStaticMappingAdapter.Selection(bundle,new AuctionMappingXmlLoader().load(bundle));
        var program=new AuctionStaticMappingAdapter().restore(source,selection,derivation);
        if(!program.profile().hash().equals(SemanticJson.hash(body.get("profile")))||!Arrays.equals(program.rules().bytes(),CanonicalJson.encode(body.get("rules")))
                ||!program.hash().equals(body.get("programHash"))||!program.cslHash().equals(body.get("cslHash"))||!program.goalHash().equals(body.get("goalHash"))
                ||!program.bindings().hash().equals(body.get("bindingHash"))||!SemanticJson.hash(SemanticJson.tree(program.complement())).equals(body.get("traceHash")))throw new IllegalArgumentException("REPLAY_CASE_PROGRAM_FINGERPRINT_MISMATCH");
        return new Decoded(source,program);
    }
    private static TraceComplement.Asset asset(Object raw) {
        var value=CanonicalJson.object(raw);if(!value.keySet().equals(Set.of("id","format","sha256","contentBase64")))throw new IllegalArgumentException("REPLAY_CASE_ASSET_FIELDS");
        for(var field:value.values())if(!(field instanceof String))throw new IllegalArgumentException("REPLAY_CASE_ASSET_STRING_REQUIRED");
        return new TraceComplement.Asset((String)value.get("id"),(String)value.get("format"),(String)value.get("sha256"),(String)value.get("contentBase64"));
    }
    private static Map<String,Object> caseImplementation() {
        var result=new TreeMap<>(implementation());
        for(String name:List.of("AuctionCaseBundle","AuctionJcmDerivation","AuctionMappingXmlLoader","AuctionMappingValidator","AuctionStaticMappingAdapter","AuctionSourceSelector","AuctionBindingPlan","AuctionUseModelAdapter","AuctionIStarSourceAdapter","AuctionGoalInstanceExpander","AuctionOccurrenceBindingAdapter","AuctionRuntimeStateMapping","AuctionOclBindings","AuctionOclSpecificationLoader","AuctionTaskOclBindings","AuctionTaskDemand","AuctionTaskCheckpoints","AuctionStepObservationDecoder","AuctionSchemeObservationDecoder","AuctionObligationDecoder","AuctionObligationProjection","AuctionMonitorProjection","AuctionSourceClock","AuctionObservationCut","AuctionLifecycleMapping")) {
            String fqcn="org.tzi.use.plugins.jacamo.semantics.mapping.auction."+name;result.put(fqcn,resource("/"+fqcn.replace('.','/')+".class"));
        }
        for(String name:List.of("org.tzi.use.semantic.engine.runtime.SingleOwnerContextTopology","org.tzi.use.semantic.engine.runtime.EngineTopologyPolicy"))result.put(name,resource("/"+name.replace('.','/')+".class"));
        for(String name:List.of("org.tzi.use.plugins.jacamo.semantics.evidence.AuctionEvidenceEncoder","org.tzi.use.plugins.jacamo.semantics.runtime.SemanticResultAdapter","org.tzi.use.plugins.jacamo.semantics.ocl.SemanticInvariantEvaluator"))result.put(name,resource("/"+name.replace('.','/')+".class"));
        return Collections.unmodifiableMap(result);
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
                "org.tzi.use.plugins.jacamo.semantics.mapping.CaseRuntimeMapping",
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
    public static Map<String,String> currentClassOrigins() {
        var result=new TreeMap<String,String>();
        for(Class<?> type:List.of(org.vnu.sme.goal.verify.conformance.semantics.GoalMarking.class,org.vnu.sme.goal.verify.conformance.semantics.TaskMarking.class,org.vnu.sme.goal.verify.conformance.semantics.IStarMarking.class,org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation.class)) {
            var source=type.getProtectionDomain().getCodeSource();result.put(type.getName(),source==null?"UNAVAILABLE":source.getLocation().toExternalForm());
        }
        return Map.copyOf(result);
    }
    private static String resource(String path) {
        try(var input=SemanticProgramManifest.class.getResourceAsStream(path)) {
            if(input==null)throw new IllegalArgumentException("REPLAY_IMPLEMENTATION_RESOURCE_MISSING:"+path);
            return ExternalOclConstraintService.sha256(input.readAllBytes());
        }catch(java.io.IOException error){throw new IllegalArgumentException("REPLAY_IMPLEMENTATION_HASH_UNAVAILABLE:"+path,error);}
    }
}
