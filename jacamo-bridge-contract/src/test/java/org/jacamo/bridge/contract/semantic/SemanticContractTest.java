package org.jacamo.bridge.contract.semantic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.ContractException;
import org.jacamo.bridge.contract.ContractPayloads;
import org.jacamo.bridge.contract.ModelSnapshot;
import org.junit.jupiter.api.Test;

class SemanticContractTest {
    @Test void typedContractRoundTripsWithAllEvidenceMetadata() {
        JacamoSemanticSnapshot snapshot = sample();
        byte[] encoded = SemanticContractCodec.encode(snapshot);
        assertEquals(snapshot, SemanticContractCodec.decode(encoded));
        assertArrayEquals(encoded, SemanticContractCodec.encode(SemanticContractCodec.decode(encoded)));

        var model = new ModelSnapshot("revision", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), Map.of(), snapshot);
        assertEquals(snapshot, ContractPayloads.model(ContractPayloads.model(model)).semanticContract());
    }

    @Test void encodingIsCanonicalAndRejectsVersionOrShapeDrift() {
        Object tree = SemanticContractCodec.toTree(sample());
        @SuppressWarnings("unchecked") Map<String,Object> source = (Map<String,Object>) tree;
        var reordered = new java.util.TreeMap<String,Object>(source);
        assertArrayEquals(SemanticContractCodec.encode(sample()),
                org.jacamo.bridge.contract.CanonicalJson.encode(reordered));

        var wrongVersion = new LinkedHashMap<String,Object>(source);
        wrongVersion.put("contractVersion", "2.0.0");
        assertThrows(ContractException.class, () -> SemanticContractCodec.fromTree(wrongVersion));

        var extraKey = new LinkedHashMap<String,Object>(source);
        extraKey.put("unexpected", true);
        assertThrows(ContractException.class, () -> SemanticContractCodec.fromTree(extraKey));
    }

    private static JacamoSemanticSnapshot sample() {
        var evidence = new SourceEvidence(EvidenceAuthority.OFFICIAL_JACAMO_API, "project:/hello.jcm",
                "a".repeat(64), "jacamo.project.JaCaMoProject", "jcm:project:hello", 1, 7, "", "session-1", 1,
                Fidelity.EXACT, CapabilityStatus.COMPLETE, List.of());
        var metadata = new SemanticMetadata("jcm:project:hello", "Project", "jacamo.project.JaCaMoProject",
                EvidenceAuthority.OFFICIAL_JACAMO_API, Fidelity.EXACT, CapabilityStatus.COMPLETE,
                List.of(evidence), List.of());
        var project = new JcmSemanticContract.ProjectSemantic(metadata, "hello", "a".repeat(64));
        return new JacamoSemanticSnapshot(JacamoSemanticSnapshot.CURRENT_VERSION, project, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
    }
}
