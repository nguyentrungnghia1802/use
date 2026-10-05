package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jacamo.bridge.contract.CanonicalJson;
import org.jacamo.bridge.contract.Completeness;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;

/** One consistent read of a committed workspace. Profile interval survives history-tail eviction. */
public record VerificationSnapshot(long currentVersion, RuntimeVerificationResult result, ProfileInterval profile,
                                   Metadata metadata, StateImage image) {
    public static final String CONTRACT_VERSION="1.1.0";
    public enum SynchronizationState { OFFLINE, MODEL_READY, CONNECTING, SYNCING, LIVE, STALE, ERROR }
    public record Metadata(String snapshotId,long boundarySequence,Instant capturedAt,long generation,
                           CheckpointType checkpoint,Set<String> correlations,SynchronizationState lifecycle,
                           Map<String,String> capabilities,Map<String,String> sourceVersions,
                           Completeness completeness,Map<String,Completeness> sourceCompleteness,
                           Map<String,String> evidence) {
        public Metadata {
            if(snapshotId==null || snapshotId.isBlank() || boundarySequence<0 || capturedAt==null || generation<0
                    || checkpoint==null || lifecycle==null || completeness==null) throw new IllegalArgumentException("VERIFICATION_SNAPSHOT_METADATA_INVALID");
            correlations=Set.copyOf(correlations);capabilities=Map.copyOf(capabilities);sourceVersions=Map.copyOf(sourceVersions);
            sourceCompleteness=Map.copyOf(sourceCompleteness);evidence=Map.copyOf(evidence);
        }
    }
    public record ObjectState(String name,String className,String semanticId,List<String> exactIdentities,
                              Map<String,String> attributes,Map<String,String> valueTypes) {
        public ObjectState {exactIdentities=List.copyOf(exactIdentities);attributes=Map.copyOf(attributes);valueTypes=Map.copyOf(valueTypes);}
    }
    public record LinkState(String association,List<String> participants,List<List<String>> qualifiers) {
        public LinkState {participants=List.copyOf(participants);qualifiers=qualifiers.stream().map(List::copyOf).toList();}
    }
    /** Copies values and identities only; no MObject/MSystemState references are retained. */
    public record StateImage(Map<String,ObjectState> objects,List<LinkState> links,String soil) {
        public StateImage {objects=Map.copyOf(objects);links=List.copyOf(links);java.util.Objects.requireNonNull(soil);}
        public Map<String,Object> toMap() {
            return Map.of("objects",objects.values().stream().sorted(java.util.Comparator.comparing(ObjectState::name)).map(o->Map.of(
                    "name",o.name(),"className",o.className(),"semanticId",o.semanticId(),"exactIdentities",o.exactIdentities(),
                    "attributes",o.attributes(),"valueTypes",o.valueTypes())).toList(),"links",links.stream().map(l->Map.of(
                    "association",l.association(),"participants",l.participants(),"qualifiers",l.qualifiers())).toList(),"soil",soil);
        }
    }
    public record ProfileInterval(long loadedVersion, String intervalId, Instant installedAt, String sessionId,
            long generation, String installedModelRevision, ExternalOclConstraintService.Profile profile) { }
    public VerificationSnapshot(long version,RuntimeVerificationResult result,ProfileInterval profile) {this(version,result,profile,null,null);}
    public String snapshotId() {return metadata==null ? "" : metadata.snapshotId();}
    /** Complete portable diagnostic evidence, including OCL/profile and cut metadata. */
    public Map<String,Object> toMap() {
        var values=new java.util.LinkedHashMap<String,Object>();values.put("schemaVersion",CONTRACT_VERSION);
        values.put("currentVersion",currentVersion);values.put("result",result==null?null:result.toMap());
        values.put("metadata",metadata==null?null:Map.ofEntries(Map.entry("snapshotId",metadata.snapshotId()),
                Map.entry("boundarySequence",metadata.boundarySequence()),Map.entry("capturedAt",metadata.capturedAt().toString()),
                Map.entry("generation",metadata.generation()),Map.entry("checkpoint",metadata.checkpoint().name()),
                Map.entry("correlations",metadata.correlations().stream().sorted().toList()),Map.entry("lifecycle",metadata.lifecycle().name()),
                Map.entry("capabilities",metadata.capabilities()),Map.entry("sourceVersions",metadata.sourceVersions()),
                Map.entry("completeness",metadata.completeness().name()),Map.entry("sourceCompleteness",metadata.sourceCompleteness().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().name()))),Map.entry("evidence",metadata.evidence())));
        values.put("image",image==null?null:image.toMap());
        values.put("profile",profile==null?null:Map.ofEntries(Map.entry("loadedVersion",profile.loadedVersion()),Map.entry("intervalId",profile.intervalId()),
                Map.entry("installedAt",profile.installedAt().toString()),Map.entry("sessionId",profile.sessionId()),Map.entry("generation",profile.generation()),
                Map.entry("installedModelRevision",profile.installedModelRevision()),Map.entry("sourceFile",profile.profile().sourceFile()),
                Map.entry("sourceHash",profile.profile().sourceHash()),Map.entry("source",profile.profile().source()),Map.entry("constraints",profile.profile().constraints().stream()
                        .map(c->Map.ofEntries(Map.entry("id",c.constraintId()),Map.entry("context",c.contextClass()),Map.entry("enabled",c.enabled()),Map.entry("negated",c.negated()),
                                Map.entry("requiredClasses",c.requiredClasses().stream().sorted().toList()),Map.entry("requiredRules",c.requiredRules().stream().sorted().toList()),
                                Map.entry("requiredSources",c.requiredSources().stream().sorted().toList()))).toList())));
        return java.util.Collections.unmodifiableMap(values);
    }
    /** Serialized bytes, a conservative retention budget unit; this is not a JVM heap measurement. */
    public long estimatedBytes() {return image==null ? 0 : CanonicalJson.encode(toMap()).length;}
    public static VerificationSnapshot empty() { return new VerificationSnapshot(0, null, null,null,null); }
}
