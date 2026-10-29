package org.tzi.use.plugins.jacamo.codegrounded;

import org.jacamo.bridge.contract.ModelSnapshot;
import org.tzi.use.plugins.jacamo.codegrounded.model.JacamoSpecificationModel;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceCollector;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceIndex;
import org.tzi.use.plugins.jacamo.codegrounded.trace.CodeGroundedTraceRecord;
import org.tzi.use.plugins.jacamo.codegrounded.trace.TracePhase;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseModelBuilder;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionMode;
import org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseStateBuilder;

/** Atomic Phase-1A/1B native build. No historical mapping/profile service is reachable from this class. */
public final class CodeGroundedNativePipeline {
    public record CanonicalBuildResult(Result nativeResult,org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram program,
            org.tzi.use.semantic.engine.use75.Use75ModelBinding binding,org.tzi.use.plugins.jacamo.semantics.mapping.JaCaMoOclContractMapping.Result contracts,
            org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlNativePreparation.Result xmlPreparation) {
        public CanonicalBuildResult(Result result,org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram program,
                org.tzi.use.semantic.engine.use75.Use75ModelBinding binding,org.tzi.use.plugins.jacamo.semantics.mapping.JaCaMoOclContractMapping.Result contracts) {
            this(result,program,binding,contracts,null);
        }
    }
    /** Explicit canonical profile route, additive to all existing native/legacy routes. */
    public CanonicalBuildResult buildCanonicalProgram(ModelSnapshot snapshot,NativeProjectionMode mode,
            org.tzi.use.plugins.jacamo.semantics.mapping.SemanticProgram program) {
        program.assertUnchanged();program.requireSourceHash(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(org.jacamo.bridge.contract.ContractPayloads.model(snapshot)));
        var source=new JacamoSpecificationModel(snapshot.semanticContract());var trace=new CodeGroundedTraceCollector();
        var xmlPreparation=program.xmlPlan()==null?null:new org.tzi.use.plugins.jacamo.semantics.mapping.ocl.XmlNativePreparation().prepare(snapshot,program,trace);
        var binding=xmlPreparation!=null?xmlPreparation.binding():new org.tzi.use.semantic.engine.use75.Use75CslModelBackend().compile(program.cslModel(),org.tzi.use.semantic.engine.use75.M0ExtensionSpec.observedPlayers());
        if(xmlPreparation==null)new org.tzi.use.plugins.jacamo.semantics.mapping.CanonicalUseBindingAdapter().install(program,binding,trace);
        var contracts=xmlPreparation!=null?xmlPreparation.intentional():new org.tzi.use.plugins.jacamo.semantics.mapping.JaCaMoOclContractMapping().install(snapshot,program,binding);
        boolean tupleWriter="EXPLICIT_RUNTIME_MODEL_BINDING".equals(program.rules().options().get("associationTupleAuthority"))&&!program.rules().options().getOrDefault("associationTupleSourceId","").isBlank();
        if(tupleWriter)binding.model().addAnnotation(new org.tzi.use.uml.mm.MElementAnnotation("CanonicalTupleAuthority",java.util.Map.of("authority","EXPLICIT_RUNTIME_MODEL_BINDING","sourceId",program.rules().options().get("associationTupleSourceId"))));
        var model=new NativeUseModelBuilder().buildCsl(binding,trace,mode,tupleWriter);var state=new NativeUseStateBuilder().build(source,model,trace);
        var export=new NativeUseExporter().export(model.model());
        if(state.system().model()!=model.model()||!export.originalStructuralHash().equals(export.recompiledStructuralHash()))throw new IllegalStateException("CANONICAL_NATIVE_BUILD_IDENTITY_OR_EXPORT_DIVERGED");
        return new CanonicalBuildResult(new Result(source,model,state,export,trace.index(),model.profile()),program,binding,contracts,xmlPreparation);
    }
    public Result build(ModelSnapshot snapshot) {
        return build(snapshot, NativeProjectionMode.AUTO);
    }

    public Result build(ModelSnapshot snapshot, NativeProjectionMode mode) {
        var profile = org.tzi.use.plugins.jacamo.semantics.profile.SemanticProjectionProfile.configured(
                System.getProperty(org.tzi.use.plugins.jacamo.semantics.profile.SemanticProjectionProfile.PROPERTY));
        if(profile==org.tzi.use.plugins.jacamo.semantics.profile.SemanticProjectionProfile.USE4GOAL_OBSERVED_V1)throw new IllegalArgumentException("CANONICAL_PROFILE_REQUIRES_BUILD_CANONICAL_PROGRAM");
        return build(snapshot, mode, profile == org.tzi.use.plugins.jacamo.semantics.profile.SemanticProjectionProfile.NATIVE_CURRENT
                ? null : org.tzi.use.plugins.jacamo.semantics.profile.CslMonitorConfiguration.configured());
    }

    public Result build(ModelSnapshot snapshot, NativeProjectionMode mode,
            org.tzi.use.plugins.jacamo.semantics.profile.CslMonitorConfiguration semantics) {
        JacamoSpecificationModel source = new JacamoSpecificationModel(snapshot.semanticContract());
        CodeGroundedTraceCollector trace = new CodeGroundedTraceCollector();
        NativeUseModelBuilder.Result model = semantics == null ? new NativeUseModelBuilder().build(source, trace, mode)
                : new NativeUseModelBuilder().buildCsl(source, trace, mode, semantics);
        NativeUseStateBuilder.Result state = new NativeUseStateBuilder().build(source, model, trace);
        NativeUseExporter.Result export = new NativeUseExporter().export(model.model());
        trace.add(new CodeGroundedTraceRecord("J01", TracePhase.EXPORT,
                source.project().metadata().sourceKind(), source.project().metadata().sourceJavaFqcn(),
                source.project().metadata().semanticId(), "USE_FILE", model.model().name() + ".use",
                source.project().metadata().evidenceAuthority(), source.project().metadata().fidelity(),
                source.project().metadata().capabilityStatus(), java.util.List.of(), source.project().metadata().evidence()));
        if (state.system().model() != model.model()) throw new IllegalStateException("NATIVE_SYSTEM_MODEL_IDENTITY_DIVERGED");
        if (!export.originalStructuralHash().equals(export.recompiledStructuralHash()))
            throw new IllegalStateException("NATIVE_EXPORT_HASH_MISMATCH");
        return new Result(source, model, state, export, trace.index(), model.profile());
    }

    public record SemanticBuildResult(Result nativeResult,org.tzi.use.plugins.jacamo.semantics.mapping.MappingBundle bundle) { }
    /** Both frontends reach this same production builder and one native model/system. */
    public SemanticBuildResult buildSemanticBundle(ModelSnapshot snapshot,NativeProjectionMode mode,
            org.tzi.use.plugins.jacamo.semantics.mapping.MappingBundle bundle) {
        String expected=org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(org.jacamo.bridge.contract.ContractPayloads.model(snapshot));
        var inputs=bundle.complement().assets().stream().filter(a->a.format().equals("OFFICIAL_MODEL_SNAPSHOT_JSON")).toList();
        if(inputs.size()!=1 || !inputs.getFirst().sha256().equals(expected))throw new IllegalArgumentException("MAPPING_BUNDLE_SOURCE_SNAPSHOT_MISMATCH");
        var expectedGraph=new org.tzi.use.plugins.jacamo.semantics.mapping.SourceModelNormalizer().normalize(snapshot);
        var actualNodes=new java.util.HashMap<String,org.tzi.use.plugins.jacamo.semantics.mapping.NormalizedSourceModel.Node>();bundle.source().nodes().forEach(n->actualNodes.put(n.id(),n));
        for(var node:expectedGraph.nodes()) {
            var actual=actualNodes.get(node.id());
            if(actual==null)throw new IllegalArgumentException("MAPPING_BUNDLE_SOURCE_GRAPH_MISMATCH:"+node.id());
            String expectedNode=org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(node));
            String actualNode=org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.hash(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson.tree(actual));
            if(!expectedNode.equals(actualNode))throw new IllegalArgumentException("MAPPING_BUNDLE_SOURCE_GRAPH_MISMATCH:"+node.id());
        }
        var result=build(snapshot,mode,bundle.configuration());
        org.tzi.use.plugins.jacamo.semantics.evidence.SemanticMappingAttachment.attach(result.model().model(),bundle);
        var exported=new NativeUseExporter().export(result.model().model());
        return new SemanticBuildResult(new Result(result.source(),result.model(),result.state(),exported,result.trace(),result.projectionProfile()),bundle);
    }
    public record Result(JacamoSpecificationModel source, NativeUseModelBuilder.Result model,
                         NativeUseStateBuilder.Result state, NativeUseExporter.Result export,
                         CodeGroundedTraceIndex trace,
                         org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionProfile projectionProfile) {
        public NativeProjectionMode projectionMode() { return projectionProfile.mode(); }
        public org.tzi.use.plugins.jacamo.semantics.binding.UseProjectionBinding binding(){return org.tzi.use.plugins.jacamo.semantics.binding.UseProjectionBinding.of(model.model());}
    }
}
