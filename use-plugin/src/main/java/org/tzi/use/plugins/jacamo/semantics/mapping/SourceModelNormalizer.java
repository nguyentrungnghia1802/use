package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.SemanticContractCodec;
import org.tzi.use.plugins.jacamo.codegrounded.model.JacamoSpecificationModel;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

public final class SourceModelNormalizer {
    private final MappingRuleCatalog catalog=new MappingRuleCatalog();
    public NormalizedSourceModel normalize(ModelSnapshot input) {
        input.semanticContract().moiseOrganizations().forEach(org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection::validate);
        new JacamoSpecificationModel(input.semanticContract()); // existing identity/reference validation is authoritative
        Map<String,Object> tree=ContractPayloads.model(input);String hash=SemanticJson.hash(tree),root="source-envelope:"+hash;
        var nodes=new TreeMap<String,NormalizedSourceModel.Node>();var diagnostics=new TreeSet<String>();
        walk(tree,root,nodes,diagnostics);
        var features=new TreeMap<String,NormalizedSourceModel.Feature>();tree.forEach((k,v)->features.put(k,new NormalizedSourceModel.Feature(references(v),true,"OFFICIAL_CONTRACT_ENVELOPE")));
        nodes.put(root,new NormalizedSourceModel.Node(root,"Supplement",NormalizedSourceModel.Layer.OPAQUE_EVIDENCE,"MODEL_SNAPSHOT_ENVELOPE","org.jacamo.bridge.contract.ModelSnapshot","",features));
        var aliases=new TreeMap<String,List<String>>();
        input.semanticContract().artifactDeclarations().forEach(a->aliases.computeIfAbsent(a.javaClass(),k->new ArrayList<>()).add(a.metadata().semanticId()));
        input.semanticContract().cartagoEnvironments().forEach(e->e.artifactTypes().forEach(a->aliases.computeIfAbsent(a.javaClassName(),k->new ArrayList<>()).add(a.metadata().semanticId())));
        var actionOnlyClasses=new HashSet<String>();
        for(var reference:SourceArtifactTypeReferences.fromActions(input)) {
            if(!aliases.containsKey(reference.javaClass()))actionOnlyClasses.add(reference.javaClass());
            aliases.computeIfAbsent(reference.javaClass(),k->new ArrayList<>()).add(reference.sourceId());
        }
        aliases.forEach((fqcn,origins)->{
            String id=DomainProjection.artifactId(fqcn);var values=new TreeMap<String,NormalizedSourceModel.Feature>();
            values.put("javaClass",new NormalizedSourceModel.Feature(fqcn,true,actionOnlyClasses.contains(fqcn)
                    ?"EXACT_PRODUCER_CLASS_REFERENCE_ALIAS":"EXACT_DECLARED_OR_OBSERVED_CLASS_ALIAS"));
            values.put("sourceDefinitions",new NormalizedSourceModel.Feature(origins.stream().distinct().sorted().map(origin->Map.of("reference",origin)).toList(),true,"EXACT_SOURCE_BINDINGS"));
            var alias=new NormalizedSourceModel.Node(id,"Artifact",NormalizedSourceModel.Layer.MODEL_DEFINITION,"DERIVED_ARTIFACT_CLASS_ALIAS",fqcn,root,values);
            if(nodes.putIfAbsent(id,alias)!=null)throw new IllegalArgumentException("SOURCE_DERIVED_ID_COLLISION:"+id);
        });
        return new NormalizedSourceModel("OFFICIAL_CONTRACT",hash,"",List.copyOf(nodes.values()),List.copyOf(diagnostics));
    }
    private void walk(Object raw,String container,Map<String,NormalizedSourceModel.Node> nodes,Set<String> diagnostics) {
        if(raw instanceof List<?> list){list.forEach(v->walk(v,container,nodes,diagnostics));return;}
        if(!(raw instanceof Map<?,?>))return;var map=CanonicalJson.object(raw);String owner=container;
        if(map.get("metadata") instanceof Map<?,?>) {
            var metadata=CanonicalJson.object(map.get("metadata"));String id=(String)metadata.get("semanticId"),kind=(String)metadata.get("sourceKind"),java=(String)metadata.get("sourceJavaFqcn");
            var values=new TreeMap<String,NormalizedSourceModel.Feature>();map.forEach((k,v)->values.put(k,new NormalizedSourceModel.Feature(references(v),true,"OFFICIAL_TYPED_CONTRACT")));
            String concept=catalog.concept(kind);
            if(concept.equals("Supplement"))diagnostics.add("SOURCE_SUPPLEMENT_TRACE_ONLY:"+kind);
            var node=new NormalizedSourceModel.Node(id,concept,layer(kind),kind,java,container,values);
            var old=nodes.putIfAbsent(id,node);if(old!=null && !old.equals(node))throw new IllegalArgumentException("SOURCE_SEMANTIC_ID_CONTENT_COLLISION:"+id);
            owner=id;
        }
        String parent=owner;map.values().forEach(v->walk(v,parent,nodes,diagnostics));
    }
    private static NormalizedSourceModel.Layer layer(String kind) {
        return Set.of("CARTAGO_ENVIRONMENT","CARTAGO_WORKSPACE","CARTAGO_ARTIFACT","CARTAGO_ARTIFACT_INFO","CARTAGO_AGENT_IDENTITY","CARTAGO_OBSERVABLE_PROPERTY_SNAPSHOT","CARTAGO_FOCUS_OBSERVATION").contains(kind)
            ?NormalizedSourceModel.Layer.RUNTIME_OBSERVATION:NormalizedSourceModel.Layer.MODEL_DEFINITION;
    }
    private static Object references(Object raw) {
        if(raw instanceof List<?> list)return list.stream().map(SourceModelNormalizer::references).toList();
        if(raw instanceof Map<?,?>){var map=CanonicalJson.object(raw);if(map.get("metadata") instanceof Map<?,?> metadata)return Map.of("reference",CanonicalJson.object(metadata).get("semanticId"));
            var result=new TreeMap<String,Object>();map.forEach((k,v)->result.put(k,references(v)));return result;}
        return raw;
    }
}
