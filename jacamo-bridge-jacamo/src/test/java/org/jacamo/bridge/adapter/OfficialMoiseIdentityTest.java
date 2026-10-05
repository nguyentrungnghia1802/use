package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import moise.os.OS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficialMoiseIdentityTest {
    private static Path fixture() {
        return Path.of("../use-plugin/src/test/resources/jacamo/moise/domain-projection.xml").toAbsolutePath().normalize();
    }

    @Test void liveGraphWithoutAnXmlUriRetainsExactSemanticIdentityAndHonestProvenance() throws Exception {
        var adapter=new OfficialMoiseAdapter();
        var expected=adapter.load(fixture().getParent(),fixture(),"generic");
        var official=OS.loadOSFromURI(fixture().toUri().toString()); official.setURI(null);
        var captured=adapter.capture(official,fixture().getParent(),"generic");
        assertEquals(expected.organization().metadata().semanticId(),captured.organization().metadata().semanticId());
        assertEquals(expected.groupRoleCardinalities().size(),captured.groupRoleCardinalities().size());
        assertEquals(expected.organization().structuralSpecification().roles().stream().map(r->r.metadata().semanticId()).toList(),
                captured.organization().structuralSpecification().roles().stream().map(r->r.metadata().semanticId()).toList());
        assertEquals("moise-live:/generic/factory",captured.organization().sourceUri());
        var evidence=captured.organization().metadata().evidence().getFirst();
        assertEquals(captured.organization().sourceUri(),evidence.sourceUri());
        assertEquals(64,evidence.sourceDigest().length());
        assertEquals(captured.organization(),adapter.capture(official,fixture().getParent(),"generic").organization());
    }

    @Test void localIdsAreNotCanonicalIdentitiesAndAllNeutralReferencesResolve() throws Exception {
        var result = new OfficialMoiseAdapter().load(fixture().getParent(), fixture(), "generic");
        var org = result.organization(); var official = OS.loadOSFromURI(fixture().toUri().toString());
        assertTrue(org.metadata().evidence().stream().allMatch(e->e.startLine()==0 && e.endLine()==0));
        assertTrue(org.functionalSpecification().schemes().stream().flatMap(s->s.goals().stream()).flatMap(g->g.metadata().evidence().stream())
                .allMatch(e->e.startLine()==0 && e.endLine()==0),"Official object graph has no XML source positions");
        assertEquals(official.getSS().getRolesDef().stream().map(r -> r.getId()).collect(Collectors.toSet()),
                org.structuralSpecification().roles().stream().map(r -> r.roleId()).collect(Collectors.toSet()));
        assertTrue(org.structuralSpecification().roles().stream().allMatch(r -> !r.roleId().equals(r.metadata().semanticId())));
        assertEquals(Set.of("s1", "s2"), org.functionalSpecification().schemes().stream().map(s -> s.schemeId()).collect(Collectors.toSet()));
        assertEquals(4, org.functionalSpecification().schemes().stream().flatMap(s -> s.goals().stream())
                .map(g -> g.metadata().semanticId()).distinct().count());
        assertEquals(2, org.functionalSpecification().schemes().stream().flatMap(s -> s.goals().stream())
                .map(g -> g.goalId()).distinct().count(), "same local goal ids are legal in different schemes");
        var ids = result.facts().stream().map(f -> f.id()).collect(Collectors.toSet());
        assertEquals(result.facts().size(), ids.size(), "neutral facts must also be owner-scoped");
        for (var fact : result.facts()) for (var references : fact.references().values())
            for (var reference : references) assertTrue(ids.contains(reference), fact.factKind() + " -> " + reference);
        assertTrue(result.facts().stream().filter(f -> f.factKind().equals("organisational-goal"))
                .anyMatch(f -> f.references().containsKey("plan")), "regression: Goal->Plan uses the emitted Plan identity");
        assertEquals(1, org.normativeSpecification().norms().size());
        var norm = org.normativeSpecification().norms().get(0);
        assertEquals("n1", norm.normId()); assertEquals("permission", norm.operationType());
        assertEquals("ready(S)", norm.condition()); assertEquals("10 minutes", norm.timeConstraint());
    }

    @Test void twoDeploymentsReuseOneExactSpecificationWithoutDuplicateFacts(@TempDir Path temporary) throws Exception {
        Files.createDirectories(temporary.resolve("src/org"));
        Files.copy(fixture(),temporary.resolve("src/org/factory.xml"));
        Path entry=temporary.resolve("multiple.jcm");
        Files.writeString(entry,"mas multiple { organisation first : factory.xml {} organisation second : factory.xml {} }");
        var snapshot=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(entry),entry);
        assertEquals(2,snapshot.semanticContract().organizationDeployments().size());
        assertEquals(1,snapshot.semanticContract().moiseOrganizations().size());
        assertEquals(snapshot.organisationFacts().size(),snapshot.organisationFacts().stream().map(f->f.id()).distinct().count());
        assertEquals(3,snapshot.groupRoleCardinalities().size());
    }

    @Test void conflictingSpecificationsWithTheSameExactIdentityFailClosed(@TempDir Path temporary) throws Exception {
        Files.createDirectories(temporary.resolve("src/org"));
        Files.copy(fixture(),temporary.resolve("src/org/first.xml"));
        Files.writeString(temporary.resolve("src/org/second.xml"),Files.readString(fixture()).replace("max=\"2\"","max=\"4\""));
        Path entry=temporary.resolve("conflict.jcm");
        Files.writeString(entry,"mas conflict { organisation first : first.xml {} organisation second : second.xml {} }");
        var error=assertThrows(IllegalArgumentException.class,()->new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(entry),entry));
        assertTrue(error.getMessage().startsWith("MOISE_OS_SPECIFICATION_ID_CONFLICT:"));
    }

    @Test void sameRoleNamesInDifferentOsRemainDifferentEntities(@TempDir Path temporary) throws Exception {
        Path other = temporary.resolve("other.xml");
        Files.writeString(other, Files.readString(fixture()).replace("id=\"factory\"", "id=\"warehouse\""));
        var adapter = new OfficialMoiseAdapter();
        var first = adapter.load(fixture().getParent(), fixture(), "same-project");
        var second = adapter.load(temporary, other, "same-project");
        var firstIds = first.facts().stream().map(f -> f.id()).collect(Collectors.toSet());
        assertTrue(second.facts().stream().noneMatch(f -> firstIds.contains(f.id())));
        assertNotEquals(first.organization().structuralSpecification().roles().get(0).metadata().semanticId(),
                second.organization().structuralSpecification().roles().get(0).metadata().semanticId());
    }
}
