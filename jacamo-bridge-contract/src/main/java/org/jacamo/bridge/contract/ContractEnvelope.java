package org.jacamo.bridge.contract;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Versioned transport-neutral envelope. The digest is over canonical payload JSON only. */
public record ContractEnvelope(String schemaVersion, MessageType messageType, String bridgeBuild,
                               DistributionFingerprint distribution, String projectKey, String modelRevision,
                               String sessionId, long generation, String messageId, Instant producedAt,
                               List<Capability> capabilities, Completeness completeness,
                               Map<String, SourceWatermark> watermarks, List<Evidence> evidence,
                               String payloadDigest, Map<String, Object> payload) {
    public ContractEnvelope {
        schemaVersion = ContractSupport.required(schemaVersion, "schemaVersion");
        if (messageType == null || distribution == null || producedAt == null || completeness == null)
            throw new ContractException("messageType/distribution/producedAt/completeness is required");
        bridgeBuild = ContractSupport.required(bridgeBuild, "bridgeBuild");
        projectKey = ContractSupport.required(projectKey, "projectKey");
        modelRevision = ContractSupport.required(modelRevision, "modelRevision");
        sessionId = ContractSupport.required(sessionId, "sessionId");
        if (generation < 0) throw new ContractException("generation must be non-negative");
        messageId = ContractSupport.required(messageId, "messageId");
        capabilities = ContractSupport.list(capabilities);
        watermarks = ContractSupport.map(watermarks);
        evidence = ContractSupport.list(evidence);
        payloadDigest = ContractSupport.required(payloadDigest, "payloadDigest");
        payload = ContractSupport.canonicalObject(payload);
    }

    public static ContractEnvelope create(String schemaVersion, MessageType type, String bridgeBuild,
            DistributionFingerprint distribution, String projectKey, String modelRevision, String sessionId,
            long generation, String messageId, Instant producedAt, List<Capability> capabilities,
            Completeness completeness, Map<String, SourceWatermark> watermarks, List<Evidence> evidence,
            Map<String, Object> payload) {
        String digest = ContractSupport.sha256(new String(CanonicalJson.encode(payload), java.nio.charset.StandardCharsets.UTF_8));
        return new ContractEnvelope(schemaVersion, type, bridgeBuild, distribution, projectKey, modelRevision,
                sessionId, generation, messageId, producedAt, capabilities, completeness, watermarks, evidence, digest, payload);
    }

    public Map<String, Object> toMap() {
        var result = new LinkedHashMap<String, Object>();
        result.put("schemaVersion", schemaVersion); result.put("messageType", messageType.name());
        result.put("bridgeBuild", bridgeBuild); result.put("distribution", distributionMap(distribution));
        result.put("projectKey", projectKey); result.put("modelRevision", modelRevision);
        result.put("sessionId", sessionId); result.put("generation", generation); result.put("messageId", messageId);
        result.put("producedAt", producedAt.toString());
        result.put("capabilities", capabilities.stream().map(ContractEnvelope::capabilityMap).toList());
        result.put("completeness", completeness.name());
        var wm = new TreeMap<String, Object>(); watermarks.forEach((key, value) -> wm.put(key, watermarkMap(value)));
        result.put("watermarks", wm); result.put("evidence", evidence.stream().map(ContractEnvelope::evidenceMap).toList());
        result.put("payloadDigest", payloadDigest); result.put("payload", payload);
        return result;
    }

    public static ContractEnvelope fromMap(Map<String, Object> map) {
        requireExactKeys(map, List.of("schemaVersion", "messageType", "bridgeBuild", "distribution", "projectKey",
                "modelRevision", "sessionId", "generation", "messageId", "producedAt", "capabilities",
                "completeness", "watermarks", "evidence", "payloadDigest", "payload"));
        var distributionMap = object(map, "distribution");
        var components = new TreeMap<String, String>(); object(distributionMap, "components").forEach((k, v) -> components.put(k, string(v, k)));
        var distribution = new DistributionFingerprint(string(distributionMap.get("jacamoVersion"), "jacamoVersion"),
                string(distributionMap.get("distributionDigest"), "distributionDigest"), components);
        var capabilities = new ArrayList<Capability>();
        for (Object item : list(map, "capabilities")) {
            var value = CanonicalJson.object(item); var ids = new ArrayList<String>();
            for (Object id : list(value, "evidenceIds")) ids.add(string(id, "evidenceId"));
            capabilities.add(new Capability(string(value.get("name"), "name"),
                    enumValue(CapabilityStatus.class, value.get("status")), ids, string(value.get("reason"), "reason")));
        }
        var watermarks = new TreeMap<String, SourceWatermark>();
        object(map, "watermarks").forEach((key, item) -> { var value = CanonicalJson.object(item);
            watermarks.put(key, new SourceWatermark(string(value.get("sourceId"), "sourceId"), number(value.get("sequence")))); });
        var evidence = new ArrayList<Evidence>();
        for (Object item : list(map, "evidence")) { var value = CanonicalJson.object(item);
            evidence.add(new Evidence(string(value.get("evidenceId"), "evidenceId"), string(value.get("authority"), "authority"),
                    string(value.get("sourceUri"), "sourceUri"), string(value.get("sourceDigest"), "sourceDigest"), string(value.get("detail"), "detail"))); }
        return new ContractEnvelope(string(map.get("schemaVersion"), "schemaVersion"), enumValue(MessageType.class, map.get("messageType")),
                string(map.get("bridgeBuild"), "bridgeBuild"), distribution, string(map.get("projectKey"), "projectKey"),
                string(map.get("modelRevision"), "modelRevision"), string(map.get("sessionId"), "sessionId"), number(map.get("generation")),
                string(map.get("messageId"), "messageId"), Instant.parse(string(map.get("producedAt"), "producedAt")), capabilities,
                enumValue(Completeness.class, map.get("completeness")), watermarks, evidence,
                string(map.get("payloadDigest"), "payloadDigest"), object(map, "payload"));
    }

    private static Map<String, Object> distributionMap(DistributionFingerprint value) { return Map.of("jacamoVersion", value.jacamoVersion(), "distributionDigest", value.distributionDigest(), "components", value.components()); }
    private static Map<String, Object> capabilityMap(Capability value) { return Map.of("name", value.name(), "status", value.status().name(), "evidenceIds", value.evidenceIds(), "reason", value.reason()); }
    private static Map<String, Object> watermarkMap(SourceWatermark value) { return Map.of("sourceId", value.sourceId(), "sequence", value.sequence()); }
    private static Map<String, Object> evidenceMap(Evidence value) { return Map.of("evidenceId", value.evidenceId(), "authority", value.authority(), "sourceUri", value.sourceUri(), "sourceDigest", value.sourceDigest(), "detail", value.detail()); }
    private static void requireExactKeys(Map<String, Object> map, List<String> keys) { if (!map.keySet().equals(new java.util.HashSet<>(keys))) throw new ContractException("unknown or missing envelope fields: " + map.keySet()); }
    private static Map<String, Object> object(Map<String, Object> map, String key) { return CanonicalJson.object(map.get(key)); }
    private static List<?> list(Map<String, Object> map, String key) { if (!(map.get(key) instanceof List<?> value)) throw new ContractException(key + " must be an array"); return value; }
    private static String string(Object value, String key) { if (!(value instanceof String text)) throw new ContractException(key + " must be a string"); return text; }
    private static long number(Object value) { if (!(value instanceof Number number)) throw new ContractException("number required"); return number.longValue(); }
    private static <E extends Enum<E>> E enumValue(Class<E> type, Object value) { try { return Enum.valueOf(type, string(value, type.getSimpleName())); } catch (IllegalArgumentException error) { throw new ContractException("unknown " + type.getSimpleName(), error); } }
}
