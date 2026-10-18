package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.tzi.use.plugins.jacamo.codegrounded.trace.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.semantic.engine.api.CslModelFingerprint;
import org.tzi.use.semantic.engine.use75.Use75ModelBinding;
import org.tzi.use.uml.mm.MElementAnnotation;
import static org.tzi.use.plugins.jacamo.semantics.mapping.SemanticBindingIndex.Kind.*;

/** Joins source IDs to neutral backend symbols on the same MModel, without a local CSL schema. */
public final class CanonicalUseBindingAdapter {
    public void install(SemanticProgram program,Use75ModelBinding backend,CodeGroundedTraceCollector trace) {
        program.assertUnchanged();if(!program.cslHash().equals(CslModelFingerprint.of(backend.cslModel())))throw new IllegalArgumentException("PROGRAM_BACKEND_M1_MISMATCH");
        for(var cls:backend.model().classes())if(cls.getAnnotation(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.HELPER)!=null)
            DomainProjection.annotate(cls,"observer-helper",org.tzi.use.semantic.engine.use75.Use75CslModelBackend.decode(cls.getAnnotationValue(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.HELPER,"id64")),"");
        var agents=program.source().nodes().stream().filter(n->n.sourceKind().equals("JACAMO_AGENT_DECLARATION")).map(n->Map.of("id",n.id(),"instances",n.features().get("instances").value())).toList();
        backend.model().addAnnotation(new MElementAnnotation("CslAgentDeclarations",Map.of("payload64",DomainProjection.encode(NativeUseModelBuilder.canonicalJson(agents)))));
        for(var binding:program.bindings().bindings()) {
            if(!Set.of(ENTITY,ROLE,ORG_CONTEXT).contains(binding.kind()))continue;
            var cls=backend.classifier(binding.targetId());var source=program.source().require(binding.sourceId());
            if(!binding.nativeSymbol().isBlank()&&!binding.nativeSymbol().equals(cls.name()))throw new IllegalArgumentException("STATIC_NATIVE_SYMBOL_MISMATCH");
            String javaClass=binding.kind()==ENTITY?JaCaMoCslMappingRules.javaClass(program.source(),binding):"";
            DomainProjection.annotate(cls,switch(binding.kind()){case ENTITY->"artifact";case ROLE->"csl-role";default->"csl-org_context";},binding.sourceId(),javaClass);
            cls.addAnnotation(new MElementAnnotation("CanonicalSemanticBinding",Map.of("kind",binding.kind().name(),"source64",DomainProjection.encode(binding.sourceId()),"target64",DomainProjection.encode(binding.targetId()),
                    "rule64",DomainProjection.encode(binding.ruleId()),"reason64",DomainProjection.encode(binding.abstractionReason()),"metadata64",DomainProjection.encode(NativeUseModelBuilder.canonicalJson(binding.metadata())))));
            trace.add(new CodeGroundedTraceRecord("CSL4-M1-"+binding.kind(),TracePhase.MODEL_DECLARATION,source.sourceKind(),source.sourceJavaFqcn(),source.id(),"MClass","class:"+cls.name(),
                    EvidenceAuthority.EXPLICIT_BINDING,Fidelity.CONDITIONAL,CapabilityStatus.COMPLETE,List.of("ENGINE_PROFILE="+program.profile().engine(),"MAPPING_RULE="+binding.ruleId(),"NO_M0_POPULATION")));
        }
        backend.model().addAnnotation(new MElementAnnotation("CanonicalSemanticProgram",Map.of("version","2.0","programHash",program.hash(),"sourceHash",program.source().sourceHash(),"mappingProfile",program.profile().id(),
                "mappingVersion",program.profile().version(),"ruleHash",program.rules().hash(),"cslHash",program.cslHash(),"goalHash",program.goalHash())));
    }
}
