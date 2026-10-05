package org.tzi.use.plugins.jacamo;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeProjector;
import org.tzi.use.plugins.jacamo.codegrounded.use.DomainProjection;
import org.tzi.use.plugins.jacamo.codegrounded.use.MoiseDomainProjection;
import org.tzi.use.uml.ocl.value.StringValue;

/** Original agents create their artifacts/organisation after the managed execution gate. */
class HouseBuildingDomainProjectionIT {
    @Test void originalHouseBuildingDiscoversDynamicDomainStateOnTheSameSystem() throws Exception {
        Path evidence=Path.of("target/house-domain-live",Long.toString(System.currentTimeMillis())).toAbsolutePath();
        Path original=Path.of("../../jacamo/examples/house-building/house-building.jcm");
        try(var producer=new ManagedProducerTestSupport(original,evidence)) {
            producer.configureWorkflow(); Session session=new Session();
            var recording=new HelloWorldSemanticInventoryIT.RecordingTransportFactory(evidence);
            try(var facade=new DefaultJaCaMoFacade(Path.of("."),SemanticAuthority.BRIDGE,
                    ()->producer.configuration,recording,PipelineMode.CODE_GROUNDED_NATIVE,session)) {
                producer.clearWorkflowProperties(); facade.configureBridge(producer.configuration);
                facade.importProject(producer.jcm);
                assertEquals("MODEL_READY",facade.workflowStatus().state());
                var system=facade.materializedSystem(); var model=system.model();
                var projector=projector(facade);
                try(var diagramAudit=new org.tzi.use.plugins.jacamo.codegrounded.ObjectDiagramLifecycleEvidence(facade,session,evidence)) {
                diagramAudit.capture("01-model-ready");
                assertEquals(0,projector.coordinator().read(()->model.classes().stream()
                        .filter(c->List.of("organisation","group").contains(DomainProjection.kind(c))).count()));
                Path profile=evidence.resolve("identities.ocl");
                Files.writeString(profile,"context Agent inv StableIdentity: Agent.allInstances()->isUnique(semanticId)\n"
                        +"context Agent inv DomainBeliefs: self.beliefs->isUnique(semanticId)\n");
                facade.loadVerificationProfile(profile); facade.startRuntime();
                diagramAudit.capture("02-start-ack");
                assertEquals("LIVE",facade.workflowStatus().state());
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
                boolean discovered=false;
                boolean firstCut=true;
                while(System.nanoTime()<deadline) {
                    Thread.sleep(500); facade.resyncRuntime();
                    if(firstCut) {diagramAudit.capture("03-first-live-resync");firstCut=false;}
                    // Group creation precedes role adoption in the real producer. Wait for the
                    // state this acceptance asserts, not merely the first empty GroupBoard.
                    discovered=projector.coordinator().read(()->system.state().allObjects().stream()
                            .anyMatch(o->DomainProjection.kind(o.cls()).equals("group"))
                            && system.state().allLinks().stream().anyMatch(link ->
                                link.association().getAnnotation(MoiseDomainProjection.ROLE_ASSOCIATION)!=null
                                && DomainProjection.decode(link.association().getAnnotationValue(
                                    MoiseDomainProjection.ROLE_ASSOCIATION,"roleId64")).endsWith(":role:house_owner"))
                            && system.state().allObjects().stream().anyMatch(o->DomainProjection.kind(o.cls()).equals("scheme"))
                            && system.state().allLinks().stream().anyMatch(l->l.association().name().equals(DomainProjection.relation("responsibleFor","Group","Scheme"))));
                    if(discovered) break;
                }
                facade.disconnectRuntime(); facade.connectRuntime(); facade.resyncRuntime();
                diagramAudit.capture("04-dynamic-reconnected");
                diagramAudit.assertSourceRelations(recording.runtime,"04-dynamic-reconnected");
                assertSame(system,facade.materializedSystem()); assertSame(system,session.system());
                var goalView=facade.goalView();
                assertTrue(goalView.schemes().stream().anyMatch(s->s.runtime() && !s.goals().isEmpty()));
                var operators=goalView.schemes().stream().flatMap(s->s.goals().stream()).map(g->g.operator()).collect(java.util.stream.Collectors.toSet());
                assertTrue(operators.contains("sequence"),operators.toString());assertTrue(operators.contains("parallel"),operators.toString());
                assertTrue(goalView.schemes().stream().flatMap(s->s.goals().stream()).anyMatch(g->!g.children().isEmpty() && !g.parent().isBlank()),"Nested decomposition missing");
                org.tzi.use.plugins.jacamo.codegrounded.GoalWorkbenchEvidence.capture(facade,session,evidence,false);
                Files.write(evidence.resolve("runtime-performance.json"),CanonicalJson.encode(facade.runtimePerformanceMetrics()));
                Files.write(evidence.resolve("verification-snapshot.json"),CanonicalJson.encode(facade.verificationSnapshot().toMap()));
                var authoritativeCut=recording.runtime;
                facade.exportNativeUse(evidence.resolve("house.use"));
                facade.exportNativeSoil(evidence.resolve("house.cmd"));
                facade.disconnectRuntime();
                diagramAudit.capture("05-disconnected");
                // The identical real Bridge cut must reconstruct identically after reconnect;
                // a moving producer is not compared to a fictitious frozen wall-clock state.
                projector.applySnapshot(authoritativeCut);
                diagramAudit.capture("06-identical-cut");
                String reconstructed=new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().export(system).commands();
                projector.applySnapshot(authoritativeCut);
                diagramAudit.capture("07-repeated-identical-cut");
                diagramAudit.assertSourceRelations(authoritativeCut,"07-repeated-identical-cut");
                assertEquals(reconstructed,new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().export(system).commands());
                facade.exportNativeUse(evidence.resolve("house.use")); facade.exportNativeSoil(evidence.resolve("house.cmd"));
                assertTrue(discovered,"No projected live GroupBoard; see "+evidence+" "+recording.runtime.facts().stream()
                        .filter(f->f.id().authority().equals("moise")).map(f->Map.of("id",f.id().canonical(),
                                "diagnostic",String.valueOf(f.values().get("diagnostic")))).toList());
                assertSame(system,session.system()); assertSame(model,system.model());
                var state=system.state();
                var agents=state.allObjects().stream().filter(o->DomainProjection.kind(o.cls()).equals("agent-program")).toList();
                assertEquals(recording.model.semanticContract().agentDeclarations().stream().mapToInt(a->a.instances()).sum(),agents.size(),
                        "Each actual agent instance must have its own object; declarations are not runtime templates");
                assertNull(state.objectByName("companyC")); assertNull(state.objectByName("companyD"));
                assertEquals(agents.size(),agents.stream().map(o->((StringValue)o.state(state)
                        .attributeValue(model.getClass("Agent").attribute("semanticId",false))).value()).distinct().count());
                assertNull(model.getClass("Agent").attribute("role",true));
                var groups=state.allObjects().stream().filter(o->DomainProjection.kind(o.cls()).equals("group")).toList();
                assertFalse(groups.isEmpty());
                assertTrue(state.allObjects().stream().anyMatch(o->DomainProjection.kind(o.cls()).equals("organisation")));
                var artifacts=state.allObjects().stream().filter(o->DomainProjection.kind(o.cls()).equals("artifact")
                        && DomainProjection.decode(o.cls().getAnnotationValue(DomainProjection.ANNOTATION,"javaClass64")).equals("tools.AuctionArt")).toList();
                assertEquals(8,artifacts.size(),"All eight auctions must be actual public runtime objects");
                for(var artifact:artifacts) {
                    assertEquals("Real",artifact.cls().attribute("currentBid",false).type().toString());
                    assertFalse(artifact.state(state).attributeValue(artifact.cls().attribute("task",false)).isUndefined());
                    assertFalse(artifact.cls().operations().isEmpty());
                    assertTrue(artifact.cls().parents().contains(model.getClass("Artifact")));
                }
                var ownerLinks=state.allLinks().stream().filter(link->link.association().getAnnotation(MoiseDomainProjection.ROLE_ASSOCIATION)!=null
                        && DomainProjection.decode(link.association().getAnnotationValue(MoiseDomainProjection.ROLE_ASSOCIATION,"roleId64")).endsWith(":role:house_owner")).toList();
                assertEquals(1,ownerLinks.size());
                assertEquals("giacomo",((StringValue)ownerLinks.getFirst().linkedObjects().getFirst().state(state)
                        .attributeValue(model.getClass("Agent").attribute("name",false))).value());
                for(var association:model.associations()) if(association.getAnnotation(MoiseDomainProjection.ROLE_ASSOCIATION)!=null) {
                    assertEquals("Agent",association.associationEnds().getFirst().cls().name());
                    assertEquals("*",association.associationEnds().get(1).multiplicity().toString());
                }
                assertTrue(model.classes().stream().allMatch(org.tzi.use.plugins.jacamo.codegrounded.use.NativeProjectionPolicy::allowsClass));
                assertTrue(ownerLinks.stream().allMatch(l->l instanceof org.tzi.use.uml.sys.MLinkObject));
                assertTrue(state.allObjects().stream().anyMatch(o->o.cls().name().equals("OrganizationalGoal")));
                assertTrue(state.allObjects().stream().anyMatch(o->o.cls().name().equals("Mission")));
                assertTrue(state.allLinks().stream().anyMatch(l->l.association().name().equals(DomainProjection.relation("responsibleFor","Group","Scheme"))));
                var replay=new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseSoilExporter().replay(
                    new org.tzi.use.plugins.jacamo.codegrounded.use.NativeUseExporter().export(model).recompiledModel(),Files.readString(evidence.resolve("house.cmd")));
                assertEquals(state.numObjects(),replay.state().numObjects()); assertEquals(state.allLinks().size(),replay.state().allLinks().size());
                for(String forbidden:List.of("Role","RoleEnactment","GroupInstance","SchemeInstance","MissionCommitment","Plan","PlanLibrary","PlanBodyElement","Norm","ObservablePropertySnapshot","ArtifactType"))
                    assertNull(model.getClass(forbidden));
                assertTrue(state.allLinks().stream().allMatch(link->state.allObjects().containsAll(link.linkedObjects())));
                assertTrue(producer.sourceUnchanged());
                Files.write(evidence.resolve("summary.json"),CanonicalJson.encode(Map.ofEntries(
                        Map.entry("status","PASS"),Map.entry("sameActiveSystem",true),Map.entry("sourceUnchanged",true),Map.entry("reconnectResyncSameCut",true),
                        Map.entry("classes",model.classes().stream().map(c->Map.of("name",c.name(),"kind",DomainProjection.kind(c))).toList()),
                        Map.entry("objects",state.numObjects()),Map.entry("links",state.allLinks().size()),Map.entry("auctionArtifacts",artifacts.size()),
                        Map.entry("directOwnerLinks",ownerLinks.size()),Map.entry("groups",groups.stream().map(o->o.name()).toList()),
                        Map.entry("agents",agents.stream().map(o->o.name()+":"+o.cls().name()).sorted().toList()))));
                Files.write(evidence.resolve("selective-projection.json"),CanonicalJson.encode(
                        org.tzi.use.plugins.jacamo.codegrounded.ProjectionExposureEvidence.assertAndReport(projector.mutations())));
                System.out.println("HOUSE_BUILDING_DOMAIN_LIVE_PASS evidence="+evidence);
                }
            }
        }
    }
    private static NativeRuntimeProjector projector(DefaultJaCaMoFacade facade) throws Exception {
        var workspace=facade.getClass().getDeclaredField("nativeWorkspace"); workspace.setAccessible(true);
        Object current=workspace.get(facade);
        var field=current.getClass().getDeclaredField("runtimeProjector"); field.setAccessible(true);
        return (NativeRuntimeProjector)field.get(current);
    }
}
