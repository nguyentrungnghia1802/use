package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class RuntimeMappingTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RuntimeMappingLoader loader = new RuntimeMappingLoader();
    private ObjectNode document() throws Exception {
        return ((ObjectNode) json.readTree(RuntimeMappingLoader.resource("jacamo-use-runtime-mapping-v2.json"))).put("status", "CUSTOM_VALIDATED");
    }
    private void rejects(ObjectNode doc, String code) throws Exception {
        var error = assertThrows(RuntimeMappingException.class, () -> loader.loadBytes(json.writeValueAsBytes(doc)));
        assertEquals(code, error.code(), error.getMessage());
    }
    @Test void canonicalMappingCoversTaxonomyDeterministically() {
        var mapping = loader.loadDefault();
        assertEquals(mapping, loader.loadDefault());
        var covered = java.util.EnumSet.noneOf(RuntimeEventKind.class);
        mapping.rules().forEach(r -> assertTrue(covered.add(r.eventKind()), "duplicate historical selector"));
        assertTrue(covered.contains(RuntimeEventKind.REPLACE_ORDER));
        assertEquals(java.util.EnumSet.allOf(RuntimeEventKind.class), covered);
        assertEquals("FROZEN", mapping.status());
        assertTrue(mapping.rules().stream().noneMatch(r -> r.anchor().contains("Auction")));
    }
    @Test void compatibilityReportIsDerivedAndMachineReadable() throws Exception {
        var report = new RuntimeMappingCompatibility().report(loader.loadDefault());
        assertEquals(loader.loadDefault().rules().size(), report.size());
        var target = java.nio.file.Path.of("target/runtime-mapping-compatibility.json");
        json.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), report);
        assertTrue(report.stream().filter(r -> !r.mutation().equals("NONE"))
            .allMatch(r -> r.status().equals("SUPPORTED")));
        assertTrue(report.stream().filter(r -> r.event().equals("REPLACE_ORDER"))
            .allMatch(r -> r.useTarget().contains("authoritative ranks")));
    }
    @Test void frozenFingerprintAndLegacyDraftFailClosed() throws Exception {
        var doc = document();
        ((ObjectNode)doc.path("targetContract")).put("ecoreSha256", "0".repeat(64));
        rejects(doc, "RUNTIME_MAPPING_BASELINE_MISMATCH");
        doc = document(); doc.put("schemaVersion", "1.0.0").put("status", "DRAFT_WAITING_FOR_METAMODEL_V2");
        rejects(doc, "RUNTIME_MAPPING_VERSION_UNSUPPORTED");
        var historical = (ObjectNode) json.readTree(
                RuntimeMappingLoader.historicalResource("runtime/jacamo-use-runtime-mapping-v1.json"));
        rejects(historical, "RUNTIME_MAPPING_VERSION_UNSUPPORTED");
    }
    @Test void strictSchemaAndMalformedInput() throws Exception {
        for (String field : new String[]{"id","identity","eventKind","action"}) {
            var doc = document(); ((ObjectNode)doc.path("rules").get(0)).put(field, "invalid");
            if (!field.equals("id")) rejects(doc, "RUNTIME_MAPPING_SCHEMA_INVALID");
        }
        var doc = document(); doc.put("extra", true); rejects(doc,"RUNTIME_MAPPING_SCHEMA_INVALID");
        doc = document(); ((ObjectNode)doc.path("rules").get(0)).remove("evidence"); rejects(doc,"RUNTIME_MAPPING_SCHEMA_INVALID");
        assertThrows(RuntimeMappingException.class, () -> loader.loadBytes("{".getBytes()));
    }
    @Test void minimalDocumentAndSemanticNegativeControls() throws Exception {
        var doc = document();
        var rules = json.createArrayNode().add(doc.path("rules").get(0)); doc.set("rules",rules);
        assertEquals(1,loader.loadBytes(json.writeValueAsBytes(doc)).rules().size());
        doc = document(); ((ObjectNode)doc.path("rules").get(0)).put("anchor","VP003");
        rejects(doc,"RUNTIME_MAPPING_ANCHOR_INCOMPATIBLE");
        doc = document(); ((ObjectNode)doc.path("rules").get(2)).putArray("payload").add("impossible");
        rejects(doc,"RUNTIME_MAPPING_PAYLOAD_CONFLICT");
        doc = document(); ((ObjectNode)doc.path("rules").get(8)).put("dimension","ORGANISATION").put("runtime","MOISE");
        rejects(doc,"RUNTIME_MAPPING_AUTHORITY_CONFLICT");
    }
    @Test void conflictsAndUnsafeMutationsFailClosed() throws Exception {
        String[][] cases = {{"id","RM-DESTROY_OBJECT","DUPLICATE_ID"}, {"eventKind","DESTROY_OBJECT","CONFLICTING_SELECTOR"},
            {"anchor","missing-anchor","ANCHOR_MISSING"}, {"support","TRACE_ONLY","UNSAFE_MUTATION"},
            {"mutation","NONE","ACTION_CONFLICT"}, {"runtime","MOISE","AUTHORITY_CONFLICT"}};
        for (var entry : cases) {
            var doc=document(); ((ObjectNode)doc.path("rules").get(0)).put(entry[0],entry[1]);
            rejects(doc,"RUNTIME_MAPPING_"+entry[2]);
        }
        var doc=document(); ((ObjectNode)doc.path("rules").get(0)).put("traceRequired",false);
        rejects(doc,"RUNTIME_MAPPING_UNSAFE_MUTATION");
        doc=document(); ((ObjectNode)doc.path("rules").get(5)).put("correlationRequired",false);
        rejects(doc,"RUNTIME_MAPPING_CORRELATION_REQUIRED");
    }
    @Test void structurallyCompatibleActionsCannotInvertSourceEventMeaning() throws Exception {
        for (String kind : new String[]{"INSERT_LINK", "DELETE_LINK"}) {
            var doc = document();
            for (var node : doc.path("rules")) {
                if (node.path("eventKind").asText().equals(kind)) {
                    ((ObjectNode) node).put("action", kind.equals("INSERT_LINK") ? "RELATION_DELETE" : "RELATION_INSERT")
                        .put("mutation", kind.equals("INSERT_LINK") ? "DELETE_LINK" : "INSERT_LINK");
                }
            }
            rejects(doc, "RUNTIME_MAPPING_SOURCE_SEMANTICS_CONFLICT");
        }
        for (String kind : new String[]{"SET_ATTRIBUTE", "OBS_PROPERTY_ADDED", "OBS_PROPERTY_CHANGED", "OBS_PROPERTY_REMOVED"}) {
            var doc = document();
            for (var node : doc.path("rules")) {
                if (node.path("eventKind").asText().equals(kind)) {
                    boolean removal = kind.equals("OBS_PROPERTY_REMOVED");
                    ((ObjectNode) node).put("action", removal ? "ATTRIBUTE_STATE_SET" : "ATTRIBUTE_STATE_UNSET");
                    var payload = ((ObjectNode) node).putArray("payload").add("attribute");
                    if (removal) payload.add("valueType").add("value");
                }
            }
            rejects(doc, "RUNTIME_MAPPING_SOURCE_SEMANTICS_CONFLICT");
        }
    }
}
