package org.tzi.use.plugins.jacamo.bridge;

import java.util.*;

/** Frozen source observations shared by the producer, protocol and verifier. */
public record SourceSnapshot(String run,long sequence,boolean complete,List<Artifact> artifacts,List<Node> nodes,List<Enactment> enactments,List<Membership> memberships) {
    public SourceSnapshot(String run,long sequence,boolean complete,List<Artifact> artifacts,List<Node> nodes,List<Enactment> enactments){this(run,sequence,complete,artifacts,nodes,enactments,List.of());}
    public SourceSnapshot(String run,long sequence,boolean complete,List<Artifact> artifacts){this(run,sequence,complete,artifacts,List.of(),List.of());}
    public SourceSnapshot {artifacts=List.copyOf(artifacts);nodes=List.copyOf(nodes);enactments=List.copyOf(enactments);memberships=List.copyOf(memberships);if(run==null||run.isBlank()||sequence<1)throw new IllegalArgumentException("STATE_SNAPSHOT_IDENTITY_REQUIRED");}


    /** Physical organization/group/agent instance; parent is a physical node identity, not a CSL class. */
    public record Node(String identity,String kind,String type,String name,String workspace,String parent,Map<String,Object> properties) {
        public Node {properties=Map.copyOf(properties);if(!Set.of("organization","group","agent").contains(kind)||identity.isBlank()||type.isBlank()||name.isBlank())throw new IllegalArgumentException("STRUCTURAL_NODE_INVALID");}
    }
    public record Enactment(String context,String player,String role) { }
    public record Membership(String member,String group) { }
    public record Artifact(String identity,String javaClass,String name,String workspace,Map<String,Object> properties,Set<String> observers) {
        public Artifact(String identity,String javaClass,String name,String workspace,Map<String,Object> properties){this(identity,javaClass,name,workspace,properties,Set.of());}
        public Artifact {properties=Collections.unmodifiableMap(new LinkedHashMap<>(properties));observers=Set.copyOf(observers);for(String value:List.of(identity,javaClass,name,workspace))if(value.isBlank())throw new IllegalArgumentException("PHYSICAL_IDENTITY_REQUIRED");}
    }
}
