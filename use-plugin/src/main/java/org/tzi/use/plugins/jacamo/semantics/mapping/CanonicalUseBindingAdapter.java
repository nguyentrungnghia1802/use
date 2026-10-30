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
    /** Explicit M1 possibilities only; never proof that a runtime player enacted a role. */
    public static Set<String> enactedRoles(org.tzi.use.uml.mm.MClass cls) {
        if(cls.getAnnotation("CslEnactmentView")==null)return Set.of();
        if(!"SINGLE_AGENT_ENACTMENT/1.0".equals(cls.getAnnotationValue("CslEnactmentView","version")))throw new IllegalArgumentException("ENACTMENT_VIEW_VERSION");
        return Set.copyOf(Arrays.asList(cls.getAnnotationValue("CslEnactmentView","roles").split("\\s+")));
    }
    public void install(SemanticProgram program,Use75ModelBinding backend,CodeGroundedTraceCollector trace) {
        program.assertUnchanged();if(!program.cslHash().equals(CslModelFingerprint.of(backend.cslModel())))throw new IllegalArgumentException("PROGRAM_BACKEND_M1_MISMATCH");
        for(var cls:backend.model().classes())if(cls.getAnnotation(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.HELPER)!=null)
            DomainProjection.annotate(cls,"observer-helper",org.tzi.use.semantic.engine.use75.Use75CslModelBackend.decode(cls.getAnnotationValue(org.tzi.use.semantic.engine.use75.Use75CslModelBackend.HELPER,"id64")),"");
        var agents=program.source().nodes().stream().filter(n->n.sourceKind().equals("JACAMO_AGENT_DECLARATION")).map(n->Map.of("id",n.id(),"instances",n.features().get("instances").value())).toList();
        backend.model().addAnnotation(new MElementAnnotation("CslAgentDeclarations",Map.of("payload64",DomainProjection.encode(NativeUseModelBuilder.canonicalJson(agents)))));
        for(var binding:program.bindings().bindings()) {
            if(binding.metadata().containsKey("enactsRoles")&&binding.kind()!=ORG_CONTEXT)throw new IllegalArgumentException("ENACTMENT_REQUIRES_SINGLE_AGENT_CONTEXT");
            if(!Set.of(ENTITY,ROLE,ORG_CONTEXT).contains(binding.kind()))continue;
            var cls=backend.classifier(binding.targetId());var source=program.source().require(binding.sourceId());
            if(!binding.nativeSymbol().isBlank()&&!binding.nativeSymbol().equals(cls.name()))throw new IllegalArgumentException("STATIC_NATIVE_SYMBOL_MISMATCH");
            String javaClass=binding.kind()==ENTITY?JaCaMoCslMappingRules.javaClass(program.source(),binding):"";
            DomainProjection.annotate(cls,switch(binding.kind()){case ENTITY->"artifact";case ROLE->"csl-role";default->"csl-org_context";},binding.sourceId(),javaClass);
            cls.addAnnotation(new MElementAnnotation("CanonicalSemanticBinding",Map.of("kind",binding.kind().name(),"source64",DomainProjection.encode(binding.sourceId()),"target64",DomainProjection.encode(binding.targetId()),
                    "rule64",DomainProjection.encode(binding.ruleId()),"reason64",DomainProjection.encode(binding.abstractionReason()),"metadata64",DomainProjection.encode(NativeUseModelBuilder.canonicalJson(binding.metadata())))));
            String enacted=binding.metadata().getOrDefault("enactsRoles", "").trim();
            if(binding.metadata().containsKey("enactsRoles")&&enacted.isEmpty())throw new IllegalArgumentException("ENACTMENT_ROLES_EMPTY");
            if(!enacted.isEmpty()) {
                if(binding.kind()!=ORG_CONTEXT||!"SINGLE_AGENT_CONTEXT/1.0".equals(binding.metadata().get("contextAbstraction")))throw new IllegalArgumentException("ENACTMENT_REQUIRES_SINGLE_AGENT_CONTEXT");
                var roles=Arrays.asList(enacted.split("\\s+"));
                if(new HashSet<>(roles).size()!=roles.size())throw new IllegalArgumentException("ENACTMENT_DUPLICATE_ROLE");
                for(String role:roles)if(backend.cslModel().findRole(role).isEmpty()||program.bindings().bindings().stream().noneMatch(b->b.kind()==ROLE&&b.targetId().equals(role)))throw new IllegalArgumentException("ENACTMENT_ROLE_BINDING_UNRESOLVED:"+role);
                if(cls.getAnnotation("CslEnactmentView")!=null&&!enactedRoles(cls).equals(Set.copyOf(roles)))throw new IllegalArgumentException("ENACTMENT_CONFLICTING_BINDINGS");
                cls.addAnnotation(new MElementAnnotation("CslEnactmentView",Map.of("version","SINGLE_AGENT_ENACTMENT/1.0","roles",String.join(" ",new TreeSet<>(roles)))));
            }
            trace.add(new CodeGroundedTraceRecord("CSL4-M1-"+binding.kind(),TracePhase.MODEL_DECLARATION,source.sourceKind(),source.sourceJavaFqcn(),source.id(),"MClass","class:"+cls.name(),
                    EvidenceAuthority.EXPLICIT_BINDING,Fidelity.CONDITIONAL,CapabilityStatus.COMPLETE,List.of("ENGINE_PROFILE="+program.profile().engine(),"MAPPING_RULE="+binding.ruleId(),"NO_M0_POPULATION")));
        }
        backend.model().addAnnotation(new MElementAnnotation("CanonicalSemanticProgram",Map.of("version","2.0","programHash",program.hash(),"sourceHash",program.source().sourceHash(),"mappingProfile",program.profile().id(),
                "mappingVersion",program.profile().version(),"ruleHash",program.rules().hash(),"cslHash",program.cslHash(),"goalHash",program.goalHash())));
    }
}
