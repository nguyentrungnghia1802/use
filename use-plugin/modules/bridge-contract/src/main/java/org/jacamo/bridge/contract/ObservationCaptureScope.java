package org.jacamo.bridge.contract;

import java.security.MessageDigest;
import java.util.*;

/** Immutable source-neutral capture bounds, compiled by the consumer, frozen
 * before agent execution. Wildcard populations are explicit, never inferred
 * from an empty set. Scope describes acquisition, not observed completeness. */
public record ObservationCaptureScope(boolean allArtifactClasses, Set<String> artifactClasses,
        boolean allProperties, Set<String> propertyNames, Set<RuntimeFactKind> moiseKinds, boolean nplObservation, String readSetHash) {
    public static final String VERSION = "OBSERVATION_CAPTURE_SCOPE/1.1";
    public ObservationCaptureScope(boolean allArtifactClasses,Set<String> artifactClasses,boolean allProperties,Set<String> propertyNames,String readSetHash){
        this(allArtifactClasses,artifactClasses,allProperties,propertyNames,Set.of(),false,readSetHash);
    }
    public ObservationCaptureScope {
        artifactClasses = bounded(artifactClasses); propertyNames = bounded(propertyNames);
        moiseKinds=Set.copyOf(moiseKinds);
        if(allArtifactClasses && !artifactClasses.isEmpty() || allProperties && !propertyNames.isEmpty()
                || !Set.of(RuntimeFactKind.GROUP_BOARD,RuntimeFactKind.SCHEME_BOARD).containsAll(moiseKinds)
                || readSetHash == null || !readSetHash.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("OBSERVATION_CAPTURE_SCOPE_INVALID");
    }
    private static Set<String> bounded(Set<String> values) {
        if(values == null || values.size() > 512 || values.stream().anyMatch(v -> v == null || v.isBlank() || v.length() > 512))
            throw new IllegalArgumentException("OBSERVATION_CAPTURE_SCOPE_CAPACITY");
        return Collections.unmodifiableSet(new TreeSet<>(values));
    }
    public boolean includesArtifactClass(String className) { return allArtifactClasses || artifactClasses.contains(className); }
    public boolean includesProperty(String name) { return allProperties || propertyNames.contains(name); }
    public Map<String,Object> evidence() {
        return Map.of("version", VERSION, "allArtifactClasses", allArtifactClasses,
                "artifactClasses", artifactClasses.stream().sorted().toList(), "allProperties", allProperties,
                "propertyNames", propertyNames.stream().sorted().toList(), "moiseKinds",moiseKinds.stream().map(Enum::name).sorted().toList(),
                "nplObservation",nplObservation,"readSetHash", readSetHash);
    }
    public String hash() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CanonicalJson.encode(evidence()))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static ObservationCaptureScope fromMap(Map<String,Object> map) {
        if(!map.keySet().equals(Set.of("version", "allArtifactClasses", "artifactClasses", "allProperties", "propertyNames", "moiseKinds", "nplObservation", "readSetHash"))
                || !VERSION.equals(map.get("version")) || !(map.get("allArtifactClasses") instanceof Boolean allClasses)
                || !(map.get("allProperties") instanceof Boolean allProperties) || !(map.get("nplObservation") instanceof Boolean npl) || !(map.get("readSetHash") instanceof String hash))
            throw new IllegalArgumentException("OBSERVATION_CAPTURE_SCOPE_SHAPE");
        return new ObservationCaptureScope(allClasses, names(map.get("artifactClasses")), allProperties, names(map.get("propertyNames")),
                names(map.get("moiseKinds")).stream().map(RuntimeFactKind::valueOf).collect(java.util.stream.Collectors.toSet()),npl,hash);
    }
    private static Set<String> names(Object value) {
        if(!(value instanceof List<?> list) || list.size() > 512 || list.stream().anyMatch(v -> !(v instanceof String))
                || new HashSet<>(list).size() != list.size()) throw new IllegalArgumentException("OBSERVATION_CAPTURE_SCOPE_NAMES");
        return new TreeSet<>(list.stream().map(String.class::cast).toList());
    }
}
