package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jason.asSyntax.PlanBody;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficialJasonAdapterTest {
    @TempDir Path temporary;

    @Test void officialJasonSnapshotRetainsEveryPlanAndEveryBodyChainNodeInOrder() throws Exception {
        Path jcm = Path.of("..", "use-plugin", "src", "test", "resources", "canonical-cases", "hello-world",
                "helloworld.jcm").toAbsolutePath().normalize();
        var snapshot = new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm), jcm);
        long typedBodies = snapshot.semanticContract().jasonPrograms().stream()
                .flatMap(program -> program.planLibrary().plans().stream()).mapToLong(plan -> plan.body().size()).sum();
        long compatibilityBodies = snapshot.agentDeclarations().stream()
                .filter(fact -> fact.factKind().equals("plan-body-element")).count();
        assertEquals(compatibilityBodies, typedBodies, "the typed contract must not drop any official body node");
        snapshot.semanticContract().jasonPrograms().forEach(program -> {
            var plans = program.planLibrary().plans();
            for (int planIndex = 0; planIndex < plans.size(); planIndex++) {
                var plan = plans.get(planIndex);
                assertEquals(planIndex, plan.ordinal());
                for (int bodyIndex = 0; bodyIndex < plan.body().size(); bodyIndex++) {
                    var body = plan.body().get(bodyIndex);
                    assertEquals(bodyIndex, body.ordinal());
                    assertEquals(bodyIndex + 1 < plan.body().size()
                                    ? plan.body().get(bodyIndex + 1).metadata().semanticId() : "",
                            body.nextSemanticId());
                }
            }
        });
    }

    @Test void auditedJasonBodyTypeSurfaceIsExact() {
        assertEquals(Set.of("none", "action", "internalAction", "achieve", "test", "addBel", "addBelNewFocus",
                        "addBelBegin", "addBelEnd", "delBel", "delBelNewFocus", "delAddBel", "achieveNF", "constraint"),
                Arrays.stream(PlanBody.BodyType.values()).map(Enum::name).collect(Collectors.toSet()));
    }

    @Test void j11UsesOfficialLexerOnlyForRecursiveProvenance() throws Exception {
        Path child = temporary.resolve("child.jcm");
        Path entry = temporary.resolve("entry.jcm");
        Files.writeString(child, "mas child { }");
        Files.writeString(entry, "mas entry uses child { agent fake : should_not_be_semantically_parsed.asl }");
        var provenance = new OfficialImportGraphCollector().collect(entry, temporary);
        assertEquals(1, provenance.size());
        var imported = provenance.getFirst();
        assertEquals("child", imported.requestedPath());
        assertEquals(child.toAbsolutePath().normalize().toString(), imported.canonicalPath());
        assertEquals(CapabilityStatus.COMPLETE, imported.status());
        assertEquals(EvidenceAuthority.OFFICIAL_GENERATED_LEXER, imported.metadata().evidenceAuthority());
        assertEquals(Fidelity.PROVENANCE_ONLY, imported.metadata().fidelity());
        assertTrue(imported.metadata().diagnostics().isEmpty());
    }

    @Test void unresolvedJ11PathRemainsProvenanceOnlyAndExplicitlyUnavailable() throws Exception {
        Path entry = temporary.resolve("entry.jcm");
        Files.writeString(entry, "mas entry uses absent { }");
        var imported = new OfficialImportGraphCollector().collect(entry, temporary).getFirst();
        assertEquals(CapabilityStatus.UNAVAILABLE, imported.status());
        assertEquals(List.of("UNRESOLVED_PROVENANCE"), imported.metadata().diagnostics());
        assertTrue(imported.sourceDigest().isEmpty());
    }
}
