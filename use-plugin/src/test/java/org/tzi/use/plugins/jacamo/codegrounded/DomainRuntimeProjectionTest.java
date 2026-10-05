package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.jacamo.bridge.contract.semantic.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;

class DomainRuntimeProjectionTest {
    static ModelSnapshot load(Path entry) throws Exception {
        entry=entry.toAbsolutePath().normalize();
        return new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(entry),entry);
    }
    @Test void auctionStructuralTypesDeclarationsAndCardinality() throws Exception {
        var result=new CodeGroundedNativePipeline().build(load(Path.of("../../jacamo/examples/auction/auction.jcm")));
        var model=result.model().model(); var state=result.state().system().state();
        var expected=new HashSet<>(NativeProjectionPolicy.BASE_CLASSES);
        expected.addAll(Set.of("auction_Organization","auctionGroup","auction_capabilities_Agent","doAuction_Scheme","auctioneer","participant"));
        assertEquals(expected,
                model.classes().stream().map(c->c.name()).collect(java.util.stream.Collectors.toSet()));
        assertEquals("auction_Organization",state.objectByName("aorg").cls().name());
        assertEquals("auctionGroup",state.objectByName("agrp").cls().name());
        for(String name:List.of("bob","alice","maria","francois","giacomo"))
            assertEquals("auction_capabilities_Agent",state.objectByName(name).cls().name());
        assertEquals(5,state.allObjects().stream().filter(o->DomainProjection.kind(o.cls()).equals("agent-program")).count());
        assertEquals(4,state.allObjects().stream().filter(o->o.cls().name().equals("OrganizationalGoal")).count());
        assertEquals(2,state.allObjects().stream().filter(o->o.cls().name().equals("Mission")).count());
        assertTrue(model.getClass("auction_capabilities_Agent").parents().contains(model.getClass("Agent")));
        assertTrue(state.allObjects().stream().noneMatch(o->DomainProjection.kind(o.cls()).equals("role")));
        var auctioneer=model.getAssociation("auctioneer"); var participant=model.getAssociation("participant");
        assertInstanceOf(org.tzi.use.uml.mm.MAssociationClass.class,auctioneer);
        assertNotNull(auctioneer); assertNotNull(participant);
        assertEquals("Agent",auctioneer.associationEnds().get(0).cls().name());
        assertEquals("1",auctioneer.associationEnds().get(0).multiplicity().toString());
        assertEquals("0..300",participant.associationEnds().get(0).multiplicity().toString());
        assertEquals("*",auctioneer.associationEnds().get(1).multiplicity().toString());
        assertEquals("*",participant.associationEnds().get(1).multiplicity().toString());
        assertTrue(state.hasLinkBetweenObjects(auctioneer,new org.tzi.use.uml.sys.MObject[]{state.objectByName("bob"),state.objectByName("agrp")}));
        for(String player:List.of("alice","maria","francois","giacomo"))
            assertTrue(state.hasLinkBetweenObjects(participant,new org.tzi.use.uml.sys.MObject[]{state.objectByName(player),state.objectByName("agrp")}));
        assertEquals(5,state.allLinks().stream().filter(l->l.association().getAnnotation(MoiseDomainProjection.ROLE_ASSOCIATION)!=null).count());
        assertTrue(result.state().structureValid(),result.state().validationOutput());
        assertTrue(result.state().invariantsValid(),result.state().validationOutput());
        assertEquals(Set.of("1:1","0:300"),model.associations().stream()
            .filter(a->a.getAnnotationValue(MoiseDomainProjection.ANNOTATION,"ruleId").equals("M15"))
            .map(a->a.getAnnotationValue(MoiseDomainProjection.ANNOTATION,"min")+":"+a.getAnnotationValue(MoiseDomainProjection.ANNOTATION,"max"))
            .collect(java.util.stream.Collectors.toSet()));
        assertTrue(result.trace().records().stream().anyMatch(t->t.diagnostics().contains("STATUS=UNSUPPORTED_NORM_TRANSLATION")));
        assertTrue(result.trace().records().stream().anyMatch(t->t.diagnostics().contains("ORDER=EXACT_CHILD_ORDINAL_ATTRIBUTE")));
        assertEquals(result.model().structuralHash(),result.export().recompiledStructuralHash());
        Path evidence=Path.of("target/domain-projection/auction"); Files.createDirectories(evidence);
        Files.writeString(evidence.resolve("native.use"),result.export().useText());
        new NativeUseSoilExporter().export(result.state().system(),evidence.resolve("state.cmd"));
        System.out.println("DOMAIN_AUCTION_CLASSES="+model.classes().stream().map(c->c.name()).sorted().toList());
        System.out.println("DOMAIN_AUCTION_OBJECTS="+state.allObjects().stream().map(o->o.name()+":"+o.cls().name()).sorted().toList());
    }
    @Test void bothModesRespectFunctionalNormAndJasonBoundary() throws Exception {
        var snapshot=load(Path.of("../../jacamo/examples/auction/auction.jcm"));
        for(var mode:NativeProjectionMode.values()) {
            var result=new CodeGroundedNativePipeline().build(snapshot,mode);
            for(String forbidden:List.of("Plan","PlanLibrary","PlanBodyElement","Trigger","Action","Norm","Goal",
                    "ArtifactType","ObservablePropertySnapshot","CartagoAgentIdentity","RoleEnactment","GroupInstance","Role"))
                assertNull(result.model().model().getClass(forbidden),forbidden);
            assertTrue(result.source().programs().stream().anyMatch(p->!p.planLibrary().plans().isEmpty()));
            assertFalse(result.source().snapshot().moiseOrganizations().get(0).functionalSpecification().schemes().isEmpty());
        }
    }
    @Test void helloAndDynamicHouseDoNotInventUnobservedClassifiers() throws Exception {
        for(Path path:List.of(CodeGroundedTestFixtures.hello(),Path.of("../../jacamo/examples/house-building/house-building.jcm"))) {
            var result=new CodeGroundedNativePipeline().build(load(path));
            assertSame(result.model().model(),result.state().system().model());
            assertTrue(result.state().structureValid(),result.state().validationOutput());
            assertTrue(result.model().model().classes().stream().allMatch(NativeProjectionPolicy::allowsClass));
            assertEquals(result.model().structuralHash(),result.export().recompiledStructuralHash());
        }
    }
    @Test void dynamicArtifactPropertiesRemovalAndResyncUseSameSystem() throws Exception {
        try(var projector=RuntimeVerificationFixtures.projector()) {
            var system=projector.system(); var state=system.state();
            assertEquals("LiveRuntimePropertyArtifact",state.objectByName("box").cls().name());
            assertEquals("'A'",state.objectByName("box").state(state).attributeValue("status").toString());
            assertTrue(projector.apply(RuntimeVerificationFixtures.delta("change",1,"B")));
            assertEquals("'B'",state.objectByName("box").state(state).attributeValue("status").toString());
            var removed=RuntimeVerificationFixtures.event("remove",2,RuntimeEventKind.CHANGED,RuntimeVerificationFixtures.ID,
                    Map.of("normalizedEventKind","APPLY_CARTAGO_PROPERTY_DELTA","semanticId",RuntimeVerificationFixtures.ARTIFACT,
                            "properties",List.of(),"removedPropertySemanticIds",List.of(RuntimeVerificationFixtures.PROPERTY)));
            assertTrue(projector.apply(removed));
            assertTrue(state.objectByName("box").state(state).attributeValue("status").isUndefined());
            projector.applySnapshot(RuntimeVerificationFixtures.snapshot("resync",3));
            assertSame(system,projector.system());
            assertEquals("'A'",system.state().objectByName("box").state(system.state()).attributeValue("status").toString());
            assertTrue(system.model().getClass("LiveRuntimePropertyArtifact").operations().isEmpty());
            assertNull(system.model().getClass("ObservablePropertySnapshot"));
            assertNull(system.model().getClass("ArtifactType"));
        }
    }
    @Test void invalidTypedIdentityCannotRedirectArtifact() throws Exception {
        try(var projector=RuntimeVerificationFixtures.projector()) {
            var before=projector.mutations().savepoint();
            var result=projector.mutations().apply(new BridgeEntityId("cartago","environment","artifact","/elsewhere","box","uuid-1"),null,
                    RuntimeFactKind.ARTIFACT,ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,
                    RuntimeVerificationFixtures.artifact(RuntimeVerificationFixtures.ARTIFACT,"uuid-1"));
            assertEquals(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine.Status.REJECTED,result.status());
            assertTrue(result.diagnostic().contains("TYPED_IDENTITY_MISMATCH"));
            assertEquals(before,projector.mutations().savepoint());
        }
    }
    @Test void originalHouseArtifactCreatedDynamicallySupportsIntegerToDoubleUpdates() throws Exception {
        try(var observed=new AuctionDomainFixture(Path.of("../../jacamo/examples/house-building/house-building.jcm"),
                "src/env/tools/AuctionArt.java","tools.AuctionArt","house-task",1000)) {
            var system=observed.projector.system(); var cls=system.model().getClass("AuctionArt");
            assertEquals("artifact",DomainProjection.kind(cls)); assertFalse(cls.operations().isEmpty());
            var object=system.state().objectsOfClass(cls).iterator().next();
            assertEquals("Real",cls.attribute("currentBid",false).type().toString());
            assertEquals("1000.0",object.state(system.state()).attributeValue("currentBid").toString());
            observed.projector.coordinator().loadProfileSource("house-observed.ocl","context AuctionArt inv Budget: self.currentBid <= self.maxValue");
            observed.context.doAction(observed.artifact,new cartago.Op("bid",50.5)); observed.refresh();
            assertEquals("50.5",object.state(system.state()).attributeValue("currentBid").toString());
            assertSame(system,observed.projector.system());
            var outcomes=observed.projector.coordinator().latest().outcomes();
            assertEquals(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.PASS,outcomes.stream()
                    .filter(o->o.constraintId().equals("EXTERNAL:AuctionArt::Budget")).findFirst().orElseThrow().outcome());
            assertTrue(outcomes.stream().allMatch(o->o.outcome()==org.tzi.use.plugins.jacamo.verification.VerificationOutcome.PASS
                    || o.outcome()==org.tzi.use.plugins.jacamo.verification.VerificationOutcome.SKIPPED
                    && o.diagnostic().equals("REQUIRED_CAPABILITY_UNAVAILABLE")));
            assertNull(system.model().getClass("ObservablePropertySnapshot"));
        }
    }
    @Test void runtimeHouseGroupAdoptionRemovalAndViolationUseExactContextInSameSystem() throws Exception {
        var entry=Path.of("../../jacamo/examples/house-building/house-building.jcm").toAbsolutePath().normalize();
        var snapshot=load(entry); var pipeline=new CodeGroundedNativePipeline().build(snapshot);
        var definition=new OfficialMoiseAdapter().load(entry.getParent(),entry.getParent().resolve("src/org/house-os.xml"),snapshot.semanticContract().project().name()).organization();
        var group=definition.structuralSpecification().groups().get(0); var cards=definition.structuralSpecification().groupRoleCardinalities();
        var agents=snapshot.semanticContract().agentDeclarations(); var players=new ArrayList<Map<String,Object>>();
        for(var card:cards) if(card.groupId().equals(group.metadata().semanticId())) for(int i=0;i<card.min();i++)
            players.add(Map.of("agentDeclarationSemanticId",agents.get(i).metadata().semanticId(),"roleSemanticId",card.roleId()));
        var id=new BridgeEntityId("moise","organisation","group-board","runtime_org","runtime_group","session:board1");
        Map<String,Object> payload=new LinkedHashMap<>(); payload.put("normalizedEventKind","UPSERT_MOISE_GROUP");
        payload.put("runtimeIdentity",id.canonical()); payload.put("semanticId",id.canonical());
        payload.put("organisationDefinition",SemanticContractCodec.organizationToTree(definition));
        payload.put("organisationSpecSemanticId",definition.metadata().semanticId()); payload.put("groupSpecSemanticId",group.metadata().semanticId());
        payload.put("organisationSemanticId",new BridgeEntityId("moise","organisation","organisation","runtime_org","runtime_org","session:org1").canonical());
        payload.put("organisationName","runtime_org"); payload.put("name","runtime_group"); payload.put("players",players);
        try(var projector=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector(pipeline,"session",1,snapshot.modelRevision())) {
            var system=projector.system(); var before=system.state().numObjects();
            assertTrue(projector.apply(moiseEvent("group",0,id,RuntimeFactKind.GROUP_BOARD,payload,snapshot.modelRevision())));
            assertSame(system,projector.system()); assertEquals(before+2+players.size(),system.state().numObjects());
            assertEquals(DomainProjection.classFor(system.model(),"group",group.metadata().semanticId()),system.state().objectByName("runtime_group").cls().name());
            assertTrue(projector.mutations().structureValid());
            var player=players.get(0); var roleId=player.get("roleSemanticId").toString();
            var relation=MoiseDomainProjection.roleAssociation(system.model(),definition.metadata().semanticId(),group.metadata().semanticId(),roleId);
            var runtimeRole=new BridgeEntityId("moise","organisation","role-player","runtime_org","player/role/group","session:board1");
            var change=new LinkedHashMap<String,Object>(); change.put("normalizedEventKind","DELETE_MOISE_ROLE_LINK");
            change.put("runtimeIdentity",runtimeRole.canonical()); change.put("organisationSpecSemanticId",definition.metadata().semanticId());
            change.put("groupSpecSemanticId",group.metadata().semanticId()); change.put("groupSemanticId",id.canonical());
            change.put("agentDeclarationSemanticId",player.get("agentDeclarationSemanticId")); change.put("roleSemanticId",roleId);
            long links=system.state().linksOfAssociation(relation).size();
            assertTrue(projector.apply(moiseEvent("remove",1,runtimeRole,RuntimeFactKind.ROLE_PLAYER,change,snapshot.modelRevision())));
            assertEquals(links-1,system.state().linksOfAssociation(relation).size()); assertFalse(projector.mutations().structureValid());
            assertTrue(projector.coordinator().latest().outcomes().stream().anyMatch(o -> o.diagnostic().equals("NATIVE_MULTIPLICITY_VIOLATION")));
            var observedAgent=new BridgeEntityId("jason","agent","runtime-agent",snapshot.semanticContract().project().name(),"observed-agent","session:agent");
            var exactAgent=BridgeEntityId.parse(player.get("agentDeclarationSemanticId").toString());
            var agentBinding=new BridgeRelationId("runtime-model-binding",List.of(observedAgent,exactAgent),"exact-declaration","session:agent");
            var update=new RuntimeEvent("unrelated-agent-state", "session",1,snapshot.modelRevision(),"jason","jason",0,java.time.Instant.now(),
                RuntimeEventKind.CHANGED,RuntimeFactKind.AGENT,ProjectionStatus.MATERIALIZED_FAITHFULLY,observedAgent,agentBinding,"","",Map.of(),
                Map.of("normalizedEventKind","SET_ATTRIBUTE","attribute","host","valueType","STRING","value","observed-host"),
                new SourceWatermark("jason",0),Completeness.COMPLETE,List.of());
            assertTrue(projector.apply(update),"An existing observed role violation must not block unrelated runtime state");
            assertEquals("CURRENT_OBSERVED",projector.coordinator().latest().freshness());
            assertFalse(projector.mutations().structureValid());
            assertEquals(links-1,system.state().linksOfAssociation(relation).size());
            var beforeInvalidRelation=projector.mutations().savepoint();
            String organisationClass=DomainProjection.classFor(system.model(),"organisation",definition.metadata().semanticId());
            var invalid=new RuntimeEvent("invalid-owner-removal","session",1,snapshot.modelRevision(),"jason","jason",1,java.time.Instant.now(),
                RuntimeEventKind.REMOVED,RuntimeFactKind.RELATION_STATE,ProjectionStatus.MATERIALIZED_FAITHFULLY,observedAgent,agentBinding,"","",Map.of(),
                Map.of("normalizedEventKind","DELETE_LINK","association",DomainProjection.relation("containsGroup",organisationClass,system.state().objectByName("runtime_group").cls().name()),
                    "participantSemanticIds",List.of(payload.get("organisationSemanticId"),id.canonical())),new SourceWatermark("jason",1),Completeness.COMPLETE,List.of());
            var rejected=projector.mutations().apply(invalid.entityId(),invalid.relationId(),invalid.factKind(),invalid.projectionStatus(),invalid.completeness(),invalid.after());
            assertEquals(org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeMutationEngine.Status.REJECTED,rejected.status(),
                "The role exception must not allow an unrelated structural violation");
            assertEquals(beforeInvalidRelation,projector.mutations().savepoint());
            change.put("normalizedEventKind","INSERT_MOISE_ROLE_LINK");
            assertTrue(projector.apply(moiseEvent("adopt",2,runtimeRole,RuntimeFactKind.ROLE_PLAYER,change,snapshot.modelRevision())));
            assertEquals(links,system.state().linksOfAssociation(relation).size()); assertTrue(projector.mutations().structureValid());
            assertTrue(system.model().classes().stream().noneMatch(c -> DomainProjection.kind(c).equals("role")));
            assertEquals(before+2+players.size(),system.state().numObjects());
            assertTrue(system.state().linksOfAssociation(relation).links().stream().allMatch(l->l instanceof org.tzi.use.uml.sys.MLinkObject));
            var save=projector.mutations().savepoint(); change.put("roleSemanticId","foreign-role");
            assertThrows(RuntimeException.class,() -> projector.apply(moiseEvent("invalid",3,runtimeRole,RuntimeFactKind.ROLE_PLAYER,change,snapshot.modelRevision())));
            assertEquals(save,projector.mutations().savepoint());
        }
    }
    @Test void multipleAgentInstancesRequireExactRuntimeBindingsAndKeepDistinctIdentity() throws Exception {
        Path directory=Files.createDirectories(Path.of("target/domain-projection/instances",UUID.randomUUID().toString()));
        Files.createDirectories(directory.resolve("src/agt"));
        Files.writeString(directory.resolve("src/agt/shared.asl"),"+!boot <- .print(\"ready\").\n");
        Path entry=directory.resolve("instances.jcm");
        Files.writeString(entry,"mas runtime_agents {\n agent template : shared.asl {\n instances: 2\n }\n}\n");
        var snapshot=load(entry); var pipeline=new CodeGroundedNativePipeline().build(snapshot);
        var declaration=snapshot.semanticContract().agentDeclarations().getFirst();
        assertNull(pipeline.state().system().state().objectByName("template"),"A declaration with multiple instances is not a phantom runtime agent");
        try(var projector=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector(pipeline,RuntimeVerificationFixtures.SESSION,1,RuntimeVerificationFixtures.REVISION)) {
            projector.applySnapshot(RuntimeVerificationFixtures.snapshot("workspace",0));
            for(int i=0;i<2;i++) {
                String name="observed_"+(i==0?"alpha":"beta");
                var runtime=new BridgeEntityId("jason","agent","runtime-agent","runtime_agents",name,"runtime-"+i);
                var cartago=new BridgeEntityId("cartago","environment","agent","/main","global-"+i,Integer.toString(i));
                var payload=new LinkedHashMap<String,Object>(); payload.put("normalizedEventKind","UPSERT_CARTAGO_AGENT_IDENTITY");
                payload.put("semanticId",cartago.canonical()); payload.put("globalId","global-"+i); payload.put("localId",i); payload.put("name",name);
                payload.put("workspaceSemanticId",RuntimeVerificationFixtures.WORKSPACE); payload.put("agentDeclarationSemanticId",declaration.metadata().semanticId());
                payload.put("agentRuntimeSemanticId",runtime.canonical());
                assertTrue(projector.apply(RuntimeVerificationFixtures.event("instance-"+i,i+1,RuntimeEventKind.CREATED,RuntimeFactKind.AGENT,cartago,payload)));
                var object=projector.system().state().objectByName(name); assertNotNull(object); assertEquals("shared_Agent",object.cls().name());
                assertEquals(runtime.canonical(),((org.tzi.use.uml.ocl.value.StringValue)object.state(projector.system().state()).attributeValue("semanticId")).value());
                if(i==1) {
                    Path bundle=directory.resolve("replay"); var replay=new org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay();
                    replay.exportBundle(projector,pipeline.export().useText(),bundle);
                    var report=replay.replay(bundle); assertTrue(report.complete(),report.toString());
                    var saved=projector.mutations().savepoint(); payload.put("agentRuntimeSemanticId",new BridgeEntityId("jason","agent","runtime-agent","foreign",name,"runtime-"+i).canonical());
                    assertThrows(RuntimeException.class,()->projector.apply(RuntimeVerificationFixtures.event("wrong-context",3,RuntimeEventKind.CHANGED,RuntimeFactKind.AGENT,cartago,payload)));
                    assertEquals(saved,projector.mutations().savepoint());
                    assertEquals("STALE",projector.coordinator().latest().freshness());
                }
            }
            assertEquals("true",org.tzi.use.api.UseSystemApi.create(projector.system(),false).evaluate("Agent.allInstances()->size() = 2 and Agent.allInstances()->isUnique(semanticId)").toString());
        }
    }
    private static RuntimeEvent moiseEvent(String event,long sequence,BridgeEntityId id,RuntimeFactKind kind,Map<String,Object> payload,String revision) {
        return new RuntimeEvent(event,"session",1,revision,"moise","moise:dynamic",sequence,java.time.Instant.now(),RuntimeEventKind.CHANGED,
            kind,ProjectionStatus.MATERIALIZED_FAITHFULLY,id,null,"","",Map.of(),Map.copyOf(payload),new SourceWatermark("moise:dynamic",sequence),Completeness.COMPLETE,List.of());
    }
}
