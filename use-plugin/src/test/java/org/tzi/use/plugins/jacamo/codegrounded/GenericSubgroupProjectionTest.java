package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.SemanticContractCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.uml.sys.MObject;

class GenericSubgroupProjectionTest {
    @TempDir Path directory;

    @Test void officialJcmContainmentBecomesExactSubgroupLinkAndSurvivesReplay() throws Exception {
        var pipeline=fixture(); var system=pipeline.state().system();
        var parent=system.state().objectByName("outer"); var child=system.state().objectByName("inner");
        var association=system.model().getAssociation(DomainProjection.relation("containsSubgroup",parent.cls().name(),child.cls().name()));
        assertEquals("0..1",association.associationEnds().getFirst().multiplicity().toString());
        assertTrue(system.state().hasLinkBetweenObjects(association,new MObject[]{parent,child}));
        assertTrue(pipeline.trace().records().stream().anyMatch(t->t.diagnostics().contains("EXACT_JCM_PARENT_DECLARATION")));
        var soil=new NativeUseSoilExporter(); var replay=soil.replay(pipeline.export().recompiledModel(),soil.export(system).commands());
        assertEquals(soil.export(system).commands(),soil.export(replay).commands());
    }

    @Test void runtimeParentRemovalReattachmentAndForeignOwnerFailClosed() throws Exception {
        var pipeline=fixture(); var system=pipeline.state().system();
        var source=pipeline.source().snapshot(); var parent=source.groupDeployments().stream().filter(g->g.name().equals("outer")).findFirst().orElseThrow();
        var child=source.groupDeployments().stream().filter(g->g.name().equals("inner")).findFirst().orElseThrow();
        var org=source.moiseOrganizations().getFirst();
        var engine=new NativeRuntimeMutationEngine(system,pipeline.state().semanticObjectIndex(),new CodeGroundedRuntimeRuleRegistry());
        var id=new BridgeEntityId("moise","organisation","group-parent","entity","inner","actual-board");
        var values=new LinkedHashMap<String,Object>(); values.put("normalizedEventKind","UPSERT_MOISE_GROUP_PARENT");
        values.put("runtimeIdentity",id.canonical()); values.put("semanticId",child.metadata().semanticId());
        values.put("groupSpecSemanticId",org.structuralSpecification().groups().stream().filter(g->g.groupId().equals(child.type())).findFirst().orElseThrow().metadata().semanticId());
        values.put("parentSpecSemanticId",org.structuralSpecification().groups().stream().filter(g->g.groupId().equals(parent.type())).findFirst().orElseThrow().metadata().semanticId());
        values.put("organisationSemanticId",source.organizationDeployments().getFirst().metadata().semanticId()); values.put("parentSemanticId","");
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,id,values).status());
        assertEquals(0,system.state().allLinks().stream().filter(l->l.association().getAnnotation("SubgroupContext")!=null).count());
        values.put("parentSemanticId",parent.metadata().semanticId());
        assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,apply(engine,id,values).status());
        var before=engine.savepoint(); values.put("organisationSemanticId","foreign-owner");
        assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,apply(engine,id,values).status()); assertEquals(before,engine.savepoint());
        values.put("parentSemanticId","");
        assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,apply(engine,id,values).status()); assertEquals(before,engine.savepoint());
        assertSame(system,engine.system());
    }
    @Test void shuffledSnapshotCreatesDynamicGroupBeforeItsParentRelationAndResyncIsIdempotent() throws Exception {
        var org=MoiseDomainProjectionTest.organization();
        var pipeline=new CodeGroundedNativePipeline().build(MoiseDomainProjectionTest.withOrganizations(CodeGroundedTestFixtures.helloSnapshot(),List.of(org)));
        var group=org.structuralSpecification().groups().stream().filter(g->g.groupId().equals("team")).findFirst().orElseThrow();
        var role=org.structuralSpecification().groupRoleCardinalities().stream().filter(c->c.groupId().equals(group.metadata().semanticId()) && c.min()>0).findFirst().orElseThrow();
        var agent=pipeline.source().snapshot().agentDeclarations().getFirst();
        var identity=new BridgeEntityId("moise","organisation","group-board","generic-oe","dynamic-group","actual-board");
        var parentIdentity=new BridgeEntityId("moise","organisation","group-parent","generic-oe","dynamic-group","actual-board");
        var groupValues=new LinkedHashMap<String,Object>();
        groupValues.put("normalizedEventKind","UPSERT_MOISE_GROUP"); groupValues.put("runtimeIdentity",identity.canonical()); groupValues.put("semanticId",identity.canonical());
        groupValues.put("organisationDefinition",SemanticContractCodec.organizationToTree(org));
        groupValues.put("organisationSpecSemanticId",org.metadata().semanticId()); groupValues.put("groupSpecSemanticId",group.metadata().semanticId());
        groupValues.put("organisationSemanticId","actual:generic-oe"); groupValues.put("organisationName","generic-oe"); groupValues.put("name","dynamic-group");
        groupValues.put("players",List.of(Map.of("agentDeclarationSemanticId",agent.metadata().semanticId(),"roleSemanticId",role.roleId())));
        var parentValues=new LinkedHashMap<>(groupValues); parentValues.put("normalizedEventKind","UPSERT_MOISE_GROUP_PARENT");
        parentValues.put("runtimeIdentity",parentIdentity.canonical()); parentValues.put("parentSemanticId",""); parentValues.put("parentSpecSemanticId","");
        var facts=List.of(new RuntimeFact(parentIdentity,RuntimeFactKind.RELATION_STATE,parentValues,List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()),
                new RuntimeFact(identity,RuntimeFactKind.GROUP_BOARD,groupValues,List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of()));
        var snapshot=new RuntimeSnapshot("shuffled","revision",java.time.Instant.EPOCH,java.time.Instant.EPOCH,Map.of(),Map.of(),1,facts,Map.of("moise",Completeness.PARTIAL),"cut");
        try(var projector=new NativeRuntimeProjector(pipeline,"session",1,"revision")) {
            assertEquals(2,projector.applySnapshot(snapshot).materialized());
            assertNotNull(projector.mutations().objectForSemanticId(identity.canonical()));
            var soil=new NativeUseSoilExporter(); String after=soil.export(projector.system()).commands();
            projector.applySnapshot(snapshot);
            assertEquals(after,soil.export(projector.system()).commands()); assertSame(pipeline.state().system(),projector.system());
        }
    }
    private static NativeRuntimeMutationEngine.ApplyResult apply(NativeRuntimeMutationEngine engine,BridgeEntityId id,Map<String,Object> values) {
        return engine.apply(id,null,RuntimeFactKind.RELATION_STATE,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,values);
    }
    private CodeGroundedNativePipeline.Result fixture() throws Exception {
        Files.writeString(directory.resolve("os.xml"),Files.readString(Path.of("src/test/resources/jacamo/moise/domain-projection.xml"))
                .replace("id=\"base\" min=\"1\" max=\"1\"","id=\"base\" min=\"0\" max=\"1\""));
        Path jcm=directory.resolve("generic.jcm");
        Files.writeString(jcm,"mas generic { organisation entity: os.xml { group outer: team { group inner: leaf } } }");
        return new CodeGroundedNativePipeline().build(new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(jcm),jcm));
    }
}
