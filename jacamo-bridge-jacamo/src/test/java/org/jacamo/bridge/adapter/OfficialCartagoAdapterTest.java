package org.jacamo.bridge.adapter;

import static org.junit.jupiter.api.Assertions.*;

import cartago.CartagoEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jacamo.bridge.contract.CapabilityStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.semantic.EvidenceAuthority;
import org.jacamo.bridge.contract.semantic.Fidelity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficialCartagoAdapterTest {
    @Test void officialObserversAndLiveRelationCallbacksRetainBothEndpoints() throws Exception {
        var environment=CartagoEnvironment.getInstance(); environment.init();
        var workspace=environment.getRootWSP().getWorkspace();
        var events=new java.util.concurrent.CopyOnWriteArrayList<RuntimeEvent>();
        try(var source=new CartagoSnapshotSource(environment,List.of(),"relations-test")) {
            source.attach(events::add);
            cartago.ICartagoCallback listener=event->{};
            var context=workspace.joinWorkspace(new cartago.AgentIdCredential("relationObserver"),listener);
            var agent=context.getAgentId(); var artifact=workspace.getArtifact("console");
            workspace.focus(agent,null,listener,artifact);
            var snapshot=new OfficialCartagoAdapter().capture(environment);
            var agentId=OfficialCartagoAdapter.agentId(snapshot.environmentId(),agent);
            var artifactId=OfficialCartagoAdapter.artifactId(snapshot.environmentId(),artifact);
            assertTrue(snapshot.focuses().stream().anyMatch(f->f.agentSemanticId().equals(agentId)
                    && f.artifactSemanticId().equals(artifactId) && f.focused()),"Official observer relation is lost");
            assertTrue(source.capture().stream().anyMatch(f->"SET_CARTAGO_FOCUS".equals(f.values().get("normalizedEventKind"))
                    && agentId.equals(f.values().get("agentSemanticId")) && artifactId.equals(f.values().get("artifactSemanticId"))));
            workspace.stopFocus(agent,listener,artifact); context.quit();
            for(String kind:List.of("UPSERT_CARTAGO_AGENT_IDENTITY","SET_CARTAGO_FOCUS","DELETE_CARTAGO_AGENT_IDENTITY"))
                assertTrue(events.stream().anyMatch(e->kind.equals(e.after().get("normalizedEventKind"))),kind);
            assertTrue(events.stream().anyMatch(e->Boolean.FALSE.equals(e.after().get("focused"))));
            assertEquals(List.of(),new OfficialCartagoAdapter().capture(environment).focuses());
        } finally { environment.shutdown(); }
    }

    @Test void implicitRootDeclarationBindsTheOfficialRootRatherThanAChild(@TempDir Path directory) throws Exception {
        Path entry=directory.resolve("root.jcm"); Files.writeString(entry,"mas root_identity {}\n");
        var project=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(entry),entry).semanticContract();
        var environment=CartagoEnvironment.getInstance(); environment.init();
        BridgeRuntimeRegistry.configureDeclarations(project);
        try(var source=new CartagoSnapshotSource(environment,List.of(),"root-test")) {
            source.attach(event->{});
            var root=source.capture().stream().filter(f->environment.getRootWSP().getId().getUUID().toString().equals(f.values().get("uuid")))
                    .findFirst().orElseThrow();
            assertEquals(project.workspaceDeclarations().getFirst().metadata().semanticId(),root.values().get("workspaceDeclarationSemanticId"));
        } finally { BridgeRuntimeRegistry.clearAgents(); environment.shutdown(); }
    }

    public static class UnlocatedArtifact extends cartago.Artifact { }
    @Test void loadableTypeWithoutProviderLocationDoesNotClaimCompleteApplicationProvenance() throws Exception {
        String name=UnlocatedArtifact.class.getName();
        byte[] bytes;
        try(var stream=getClass().getResourceAsStream("/"+name.replace('.','/')+".class")) { bytes=stream.readAllBytes(); }
        var previous=Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ClassLoader(previous) {
                @Override protected Class<?> loadClass(String requested,boolean resolve) throws ClassNotFoundException {
                    if(!requested.equals(name)) return super.loadClass(requested,resolve);
                    Class<?> type=findLoadedClass(requested);
                    if(type==null) type=defineClass(requested,bytes,0,bytes.length);
                    if(resolve) resolveClass(type); return type;
                }
            });
            assertEquals("UNAVAILABLE",OfficialCartagoAdapter.typeOrigin(name));
            var method=OfficialCartagoAdapter.class.getDeclaredMethod("artifactType",String.class,String.class,org.jacamo.bridge.contract.Evidence.class);
            method.setAccessible(true);
            var evidence=new org.jacamo.bridge.contract.Evidence("provider-test","test","project:/provider.jcm","digest","provider probe");
            var type=(org.jacamo.bridge.contract.semantic.CartagoSemanticContract.ArtifactTypeSemantic)
                    method.invoke(new OfficialCartagoAdapter(),"provider-test",name,evidence);
            assertEquals(CapabilityStatus.PARTIAL,type.metadata().capabilityStatus());
            assertTrue(type.metadata().diagnostics().contains("ARTIFACT_TYPE_PROVIDER_UNAVAILABLE"));
        } finally { Thread.currentThread().setContextClassLoader(previous); }
    }
    @Test void declarationAliasesUseOfficialRootChildrenAndRevalidatedArtifactIdentity(@TempDir Path directory) throws Exception {
        Path source=directory.resolve("identity.jcm");
        Files.writeString(source,"""
            mas identity_demo {
                workspace declaredSpace {
                    artifact console: cartago.tools.Console
                }
                workspace wrongType {
                    artifact console: cartago.tools.TupleSpace
                }
                workspace unavailable {}
            }
            """);
        var project=new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(source),source).semanticContract();
        var environment=CartagoEnvironment.getInstance(); environment.init();
        var root=environment.getRootWSP().getWorkspace();
        var declared=root.createWorkspace("declaredSpace");
        root.createWorkspace("wrongType");
        var decoy=root.createWorkspace("outer").getWorkspace().createWorkspace("declaredSpace");
        BridgeRuntimeRegistry.configureDeclarations(project);
        try(var adapter=new CartagoSnapshotSource(environment,List.of(),"identity-test")) {
            adapter.attach(event->{});
            var facts=adapter.capture();
            var declaration=project.workspaceDeclarations().stream().filter(d->d.name().equals("declaredSpace")).findFirst().orElseThrow();
            var exact=facts.stream().filter(f->declared.getId().getUUID().toString().equals(f.values().get("uuid"))).findFirst().orElseThrow();
            assertEquals(declaration.metadata().semanticId(),exact.values().get("workspaceDeclarationSemanticId"));
            var other=facts.stream().filter(f->decoy.getId().getUUID().toString().equals(f.values().get("uuid"))).findFirst().orElseThrow();
            assertFalse(other.values().containsKey("workspaceDeclarationSemanticId"));
            var artifactDeclaration=project.artifactDeclarations().stream().filter(d->d.workspace().equals("declaredSpace")).findFirst().orElseThrow();
            var artifact=facts.stream().filter(f->artifactDeclaration.metadata().semanticId().equals(f.values().get("artifactDeclarationSemanticId"))).findFirst().orElseThrow();
            assertEquals(declared.getWorkspace().getArtifact("console").getId().toString(),artifact.values().get("uuid"));
            assertEquals("PLATFORM",artifact.values().get("artifactTypeOrigin"));
            assertEquals(1,facts.stream().filter(f->f.values().containsKey("artifactDeclarationSemanticId")).count());
            assertFalse(facts.stream().anyMatch(f->f.values().getOrDefault("name","").equals("unavailable")));
        } finally { BridgeRuntimeRegistry.clearAgents(); environment.shutdown(); }
    }

    @Test void anOfficialControllerReadDoesNotHoldTheLoggerCallbackMonitor() throws Exception {
        var environment=CartagoEnvironment.getInstance(); environment.init();
        var artifact=environment.getRootWSP().getWorkspace().getArtifact("console");
        var events=new java.util.concurrent.CopyOnWriteArrayList<RuntimeEvent>();
        var workers=Executors.newFixedThreadPool(2);
        try(var adapter=new CartagoSnapshotSource(environment,List.of(),"callback-test")) {
            adapter.attach(events::add);
            var reader=new java.util.concurrent.atomic.AtomicReference<Thread>();
            java.util.concurrent.Future<List<org.jacamo.bridge.contract.RuntimeFact>> capture;
            synchronized(environment) {
                capture=workers.submit(()->{reader.set(Thread.currentThread());return adapter.capture();});
                var threads=java.lang.management.ManagementFactory.getThreadMXBean();
                boolean blocked=false; long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                while(System.nanoTime()<deadline) {
                    var thread=reader.get();
                    var info=thread==null ? null : threads.getThreadInfo(thread.threadId());
                    if(info!=null && info.getLockInfo()!=null && info.getLockInfo().getIdentityHashCode()==System.identityHashCode(environment)) {
                        blocked=true; break;
                    }
                    Thread.sleep(10);
                }
                assertTrue(blocked,"The official controller read must be blocked on the actual environment monitor");
                // A real logger callback must finish while the official read is
                // blocked. Otherwise workspace/artifact locks can invert.
                workers.submit(()->adapter.artifactsLinked(0,null,artifact,artifact)).get(2,TimeUnit.SECONDS);
                assertEquals(1,events.size());
            }
            assertFalse(capture.get(5,TimeUnit.SECONDS).isEmpty());
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5,TimeUnit.SECONDS)); environment.shutdown();
        }
    }

    @Test
    void sharedSnapshotAndLoggerIdentityUsesExactWorkspaceAndIncarnation() {
        assertEquals(
                "beid:v1:cartago/environment/artifact/%2Fmain/session_francois/artifact-uuid",
                CartagoSnapshotSource.artifactId("/main", "session_francois", "artifact-uuid").canonical());
        assertEquals(
                "beid:v1:cartago/environment/agent/%2Fmain/francois/39",
                CartagoSnapshotSource.agentId("/main", "francois", 39).canonical());
    }

    @Test
    void initializedEnvironmentUsesOfficialWorkspaceDescriptorIdentityAndClosedLivePropertyBoundary() throws Exception {
        CartagoEnvironment environment = CartagoEnvironment.getInstance();
        environment.init();

        var adapter = new OfficialCartagoAdapter();
        var first = adapter.capture(environment);
        var second = adapter.capture(environment);

        assertEquals(first.metadata().semanticId(), second.metadata().semanticId());
        assertEquals(first.workspaces(), second.workspaces());
        assertFalse(first.workspaces().isEmpty());
        var root = first.workspaces().stream().filter(value -> value.parentSemanticId().isBlank()).findFirst().orElseThrow();
        assertEquals(environment.getRootWSP().getId().getFullName(), root.fullName());
        assertEquals(EvidenceAuthority.OFFICIAL_CARTAGO_API, first.metadata().evidenceAuthority());
        assertEquals(Fidelity.EXACT, root.metadata().fidelity());
        assertEquals(CapabilityStatus.COMPLETE, root.metadata().capabilityStatus());
        assertEquals(List.of(), first.liveProperties());
        assertEquals(List.of(), first.signals());
        assertEquals(List.of(), first.focuses());
    }
}
