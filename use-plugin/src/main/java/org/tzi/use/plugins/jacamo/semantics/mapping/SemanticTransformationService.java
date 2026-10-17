package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;
import org.tzi.use.plugins.jacamo.semantics.profile.CslMonitorConfiguration;

/** Explicit projection specification plus source adapters; no source condition is presumed to be OCL. */
public final class SemanticTransformationService {
    private final MappingRuleCatalog catalog=new MappingRuleCatalog();
    public MappingBundle fromOfficial(ModelSnapshot input,CslMonitorConfiguration configuration) {
        var source=new SourceModelNormalizer().normalize(input);
        return bundle(source,configuration,List.of(TraceComplement.Asset.of("official-model","OFFICIAL_MODEL_SNAPSHOT_JSON",CanonicalJson.encode(ContractPayloads.model(input)))),Map.of());
    }
    public MappingBundle fromEcore(NormalizedSourceModel emf,byte[] originalXmi,ModelSnapshot supplements,
            Map<String,String> correspondences,CslMonitorConfiguration configuration) {
        if(supplements==null)throw new IllegalArgumentException("SOURCE_ABSTRACTION_INCOMPLETE:OFFICIAL_TYPE_AND_DEFINITION_SUPPLEMENTS_REQUIRED");
        if(!emf.frontend().equals("EMF") || !emf.sourceHash().equals(org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService.sha256(originalXmi)))throw new IllegalArgumentException("EMF_SOURCE_BYTES_MISMATCH");
        var official=new SourceModelNormalizer().normalize(supplements);
        for(var binding:correspondences.entrySet()) {
            var original=emf.require(binding.getKey());var authoritative=official.require(binding.getValue());
            if(!original.concept().equals(authoritative.concept()))throw new IllegalArgumentException("SOURCE_CORRESPONDENCE_KIND_MISMATCH:"+binding);
            compareNames(original,authoritative);
        }
        for(var type:configuration.schema().classifiers())if(!correspondences.containsValue(type.sourceId()))throw new IllegalArgumentException("SOURCE_ABSTRACTION_INCOMPLETE:EXPLICIT_CLASSIFIER_CORRESPONDENCE_REQUIRED:"+type.sourceId());
        String hash=SemanticJson.hash(Map.of("emf",emf.sourceHash(),"official",official.sourceHash(),"correspondences",correspondences));
        var nodes=new ArrayList<>(official.nodes());nodes.addAll(emf.nodes());
        nodes.add(new NormalizedSourceModel.Node("correspondence:"+hash,"Supplement",NormalizedSourceModel.Layer.MODEL_DEFINITION,"EXPLICIT_FRONTEND_CORRESPONDENCE","", "",
            Map.of("bindings",new NormalizedSourceModel.Feature(correspondences,true,"EXPLICIT_BINDING"))));
        var diagnostics=new ArrayList<>(official.diagnostics());diagnostics.addAll(emf.diagnostics());diagnostics.add("EMF_IS_A_SUPPLEMENTED_DEFINITION_FRONTEND_NOT_A_RUNTIME_SOURCE");
        var source=new NormalizedSourceModel("EMF+OFFICIAL",hash,emf.metamodelHash(),nodes,diagnostics);
        return bundle(source,configuration,List.of(TraceComplement.Asset.of("official-model","OFFICIAL_MODEL_SNAPSHOT_JSON",CanonicalJson.encode(ContractPayloads.model(supplements))),
            TraceComplement.Asset.of("emf-model","SOURCE_XMI_BYTES",originalXmi)),correspondences);
    }
    private MappingBundle bundle(NormalizedSourceModel source,CslMonitorConfiguration config,List<TraceComplement.Asset> assets,Map<String,String> correspondences) {
        var decisions=new ArrayList<TraceComplement.Decision>();
        for(var node:source.nodes()) {
            String bound=correspondences.getOrDefault(node.id(),node.id());
            var classifiers=config.schema().classifiers().stream().filter(c->c.sourceId().equals(bound)).toList();
            if(classifiers.size()>1)throw new IllegalArgumentException("SOURCE_TARGET_TYPE_BINDING_AMBIGUOUS:"+bound);
            var rule=node.concept().equals("Supplement")?null:catalog.require(node.concept());
            for(var entry:node.features().entrySet()) {
                String feature=entry.getKey();var value=entry.getValue();
                var targets=new ArrayList<String>();String reason="SOURCE_FEATURE_RETAINED_IN_TRACE_COMPLEMENT";
                var disposition=MappingRuleCatalog.Disposition.TRACE_ONLY;
                if(!classifiers.isEmpty()) {
                    var classifier=classifiers.getFirst();
                    if(feature.equals("metadata")) {targets.add("class:"+classifier.symbol()+":sourceId");disposition=MappingRuleCatalog.Disposition.PARTIAL;reason="SOURCE_ID_BOUND; OTHER_METADATA_RETAINED";}
                    if(node.sourceKind().equals("DERIVED_ARTIFACT_CLASS_ALIAS") && feature.equals("javaClass")) {targets.add("class:"+classifier.symbol()+":javaClass");disposition=MappingRuleCatalog.Disposition.DIRECT;reason="EXACT_REGISTERED_ARTIFACT_TYPE_VIEW";}
                    if(feature.equals("superRoleSemanticIds") && !classifier.parentId().isBlank()) {
                        targets.add("generalization:"+classifier.symbol()+":"+config.schema().classifier(classifier.parentId()).symbol());disposition=MappingRuleCatalog.Disposition.PARTIAL;reason="SINGLE_PARENT_PROFILE; ORIGINAL_PARENTS_PRESERVED";
                    }
                }
                if(config.schema().policy().cardinalities().stream().anyMatch(c->c.id().equals(node.id())) && Set.of("min","max","groupId","roleId").contains(feature)) {
                    targets.add("constraint:CslPolicy_"+org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection.hash(node.id()));disposition=MappingRuleCatalog.Disposition.DIRECT;reason="EXACT_LEAF_ROLE_CONTEXTUAL_BOUND";
                }
                if(config.schema().policy().nesting().stream().anyMatch(c->c.id().equals(node.id())) && Set.of("min","max","parentGroupId","subGroupId").contains(feature)) {
                    targets.add("context-declaration-and-bound:"+node.id());disposition=MappingRuleCatalog.Disposition.PARTIAL;reason="M1_NESTING_ONLY; RUNTIME_POPULATION_REQUIRES_OBSERVATION";
                }
                if(node.concept().equals("Plan") && feature.equals("context"))reason="SOURCE_CONDITION_LANGUAGE_AND_QUERY_SEMANTICS_NOT_AUTOMATICALLY_OCL";
                if(node.concept().equals("Norm"))reason="NORM_LIFECYCLE_NOT_AN_INVARIANT";
                if(node.concept().equals("OGoal") && feature.equals("type"))reason="SOURCE_GOAL_TYPE_PRESERVED; NO_TEMPORAL_ENUM_CAST";
                if(node.layer()==NormalizedSourceModel.Layer.RUNTIME_OBSERVATION && targets.isEmpty())reason="MODEL_ENVELOPE_OBSERVATION_REQUIRES_SEPARATE_RUNTIME_ADMISSION";
                if(correspondences.containsKey(node.id()))reason+="; EXPLICIT_EMF_CORRESPONDENCE="+bound;
                decisions.add(new TraceComplement.Decision(node.id(),feature,rule==null?"SUPPLEMENT":rule.id(),disposition,targets,reason,value.value(),value.origin()));
            }
        }
        return new MappingBundle(source,config,new TraceComplement("1.0",MappingRuleCatalog.VERSION,source.sourceHash(),source.metamodelHash(),assets,decisions));
    }
    private static void compareNames(NormalizedSourceModel.Node original,NormalizedSourceModel.Node authoritative) {
        String sourceName=switch(original.concept()){case "Role","Group","Organization","Scheme","Mission","OGoal","Norm"->"id";case "Agent","Artifact","Workspace","Operation","Property","Signal"->"name";default->"";};
        String targetName=switch(original.concept()){case "Role"->"roleId";case "Group"->"groupId";case "Scheme"->"schemeId";case "Mission"->"missionId";case "OGoal"->"goalId";case "Norm"->"normId";case "Organization"->"name";default->"name";};
        if(original.features().containsKey(sourceName) && authoritative.features().containsKey(targetName)
                && !Objects.equals(original.features().get(sourceName).value(),authoritative.features().get(targetName).value()))throw new IllegalArgumentException("SOURCE_CORRESPONDENCE_VALUE_MISMATCH:"+original.id()+":"+sourceName);
        if(original.concept().equals("Artifact") && original.features().containsKey("type")) {
            var actual=authoritative.features().getOrDefault("javaClass",authoritative.features().get("javaClassName"));
            if(actual!=null && !Objects.equals(original.features().get("type").value(),actual.value()))throw new IllegalArgumentException("SOURCE_ARTIFACT_TYPE_CORRESPONDENCE_MISMATCH");
        }
    }
}
