package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson;

/** Definition/observation origins remain explicit; this graph never asserts a live enactment. */
public record NormalizedSourceModel(String frontend,String sourceHash,String metamodelHash,List<Node> nodes,List<String> diagnostics) {
    public enum Layer { MODEL_DEFINITION, RUNTIME_OBSERVATION, OPAQUE_EVIDENCE }
    public record Feature(Object value,boolean explicitlySet,String origin) {public Feature{value=SemanticJson.freeze(value);Objects.requireNonNull(origin);}}
    public record Node(String id,String concept,Layer layer,String sourceKind,String sourceJavaFqcn,String container,
            Map<String,Feature> features) {public Node{features=Collections.unmodifiableMap(new TreeMap<>(features));}}
    public NormalizedSourceModel {nodes=List.copyOf(nodes);diagnostics=List.copyOf(diagnostics);var ids=new HashSet<String>();for(var node:nodes)if(!ids.add(node.id()))throw new IllegalArgumentException("SOURCE_SEMANTIC_ID_COLLISION:"+node.id());}
    public Node require(String id){return nodes.stream().filter(n->n.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("SOURCE_REFERENCE_UNRESOLVED:"+id));}
}
