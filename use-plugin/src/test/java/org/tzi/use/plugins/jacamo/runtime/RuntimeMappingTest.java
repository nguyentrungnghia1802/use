package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class RuntimeMappingTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RuntimeMappingLoader loader = new RuntimeMappingLoader();
    private ObjectNode document() throws Exception {
        return (ObjectNode) json.readTree(RuntimeMappingLoader.resource("jacamo-use-runtime-mapping-draft.json"));
    }
    private void rejects(ObjectNode doc, String code) throws Exception {
        var error = assertThrows(RuntimeMappingException.class, () -> loader.loadBytes(json.writeValueAsBytes(doc)));
        assertEquals(code, error.code(), error.getMessage());
    }
    @Test void canonicalMappingCoversTaxonomyDeterministically() {
        var mapping = loader.loadDefault();
        assertEquals(mapping, loader.loadDefault());
        assertEquals(RuntimeEventKind.values().length, mapping.rules().size());
        assertEquals("DRAFT_WAITING_FOR_METAMODEL_V2", mapping.status());
        assertTrue(mapping.rules().stream().noneMatch(r -> r.anchor().contains("Auction")));
    }
    @Test void compatibilityReportIsDerivedAndMachineReadable() throws Exception {
        var report = new RuntimeMappingCompatibility().report(loader.loadDefault());
        assertEquals(RuntimeEventKind.values().length, report.size());
        var target = java.nio.file.Path.of("target/runtime-mapping-compatibility.json");
        json.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), report);
        assertTrue(report.stream().filter(r -> !r.mutation().equals("NONE"))
            .allMatch(r -> r.status().equals("READY_V1_TEMPORARY")));
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
    @Test void conflictsAndUnsafeMutationsFailClosed() throws Exception {
        String[][] cases = {{"id","RM-DESTROY_OBJECT","DUPLICATE_ID"}, {"eventKind","DESTROY_OBJECT","CONFLICTING_SELECTOR"},
            {"anchor","missing-anchor","ANCHOR_MISSING"}, {"support","DEFERRED_FOR_V2","UNSAFE_MUTATION"},
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
}
