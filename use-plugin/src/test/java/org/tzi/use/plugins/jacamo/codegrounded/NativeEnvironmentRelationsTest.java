package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;

class NativeEnvironmentRelationsTest {
    @TempDir Path directory;

    @Test void liveFocusQuitAndCompleteResyncPreserveOneAgentAndExactLinks() throws Exception {
        runChild("lifecycle");
    }
    @Test void wrongFocusEndpointIdentityRollsBackWithoutInferringMembership() throws Exception {
        runChild("identity");
    }
    private void runChild(String scenario) throws Exception {
        // Official JCM source resolution retains an ASL handle on Windows.
        // Exercise the real parser in a child JVM; its exit closes those handles.
        var output=directory.resolve("child.log");
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),getClass().getName(),scenario,directory.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(60,java.util.concurrent.TimeUnit.SECONDS),"relation test timeout");
            assertEquals(0,process.exitValue(),Files.readString(output));
            assertTrue(Files.readString(output).contains("ENVIRONMENT_RELATIONS_PASS"));
        } finally { if(process.isAlive()) process.destroyForcibly(); }
    }
    public static void main(String[] args) throws Exception {
        var test=new NativeEnvironmentRelationsTest(); test.directory=Path.of(args[1]);
        if(args[0].equals("lifecycle")) test.checkLifecycle(); else test.checkIdentity();
        System.out.println("ENVIRONMENT_RELATIONS_PASS");
    }
    private void checkLifecycle() throws Exception {
        var pipeline=fixture();
        try(var projector=new NativeRuntimeProjector(pipeline,RuntimeVerificationFixtures.SESSION,1,RuntimeVerificationFixtures.REVISION)) {
            var facts=facts(pipeline); projector.applySnapshot(snapshot("initial",0,facts));
            var system=projector.system(); var agent=system.state().objectByName("worker");
            assertEquals(1,system.state().objectsOfClassAndSubClasses(system.model().getClass("Agent")).size());
            assertEquals(2,count(projector,"memberOf","Workspace")); assertEquals(1,count(projector,"focuses","Artifact"));
            assertEquals(2,system.state().objectsOfClass(system.model().getClass("Workspace")).size(),"No duplicate implicit root");
            var focus=facts.stream().filter(f->f.kind()==RuntimeFactKind.RELATION_STATE).findFirst().orElseThrow();
            var unfocus=new LinkedHashMap<>(focus.values()); unfocus.put("focused",false);
            assertTrue(projector.apply(RuntimeVerificationFixtures.event("unfocus",1,RuntimeEventKind.UNFOCUSED,RuntimeFactKind.RELATION_STATE,focus.id(),unfocus)));
            assertEquals(0,count(projector,"focuses","Artifact")); assertEquals(2,count(projector,"memberOf","Workspace"));
            assertTrue(projector.apply(RuntimeVerificationFixtures.event("focus",2,RuntimeEventKind.FOCUSED,RuntimeFactKind.RELATION_STATE,focus.id(),focus.values())));
            assertTrue(projector.apply(RuntimeVerificationFixtures.event("focus-again",3,RuntimeEventKind.FOCUSED,RuntimeFactKind.RELATION_STATE,focus.id(),focus.values())));
            assertEquals(1,count(projector,"focuses","Artifact"));
            var lab=facts.stream().filter(f->f.kind()==RuntimeFactKind.AGENT && f.id().scope().equals("/main/lab")).findFirst().orElseThrow();
            var quit=new LinkedHashMap<>(lab.values()); quit.put("normalizedEventKind","DELETE_CARTAGO_AGENT_IDENTITY");
            assertTrue(projector.apply(RuntimeVerificationFixtures.event("quit",4,RuntimeEventKind.QUIT,RuntimeFactKind.AGENT,lab.id(),quit)));
            assertSame(agent,system.state().objectByName("worker")); assertEquals(1,count(projector,"memberOf","Workspace"));
            assertEquals(1,count(projector,"focuses","Artifact"),"Quitting a different workspace must retain focus");
            var partial=new RuntimeSnapshot("partial",RuntimeVerificationFixtures.REVISION,Instant.EPOCH,Instant.EPOCH,Map.of(),Map.of(),1,
                    List.of(),Map.of("cartago",Completeness.PARTIAL),"partial");
            var before=projector.mutations().savepoint(); projector.mutations().reconcileAuthoritativeCartago(partial);
            assertEquals(before,projector.mutations().savepoint());
            projector.applySnapshot(snapshot("removed",4,facts.stream().filter(f->f!=lab && f!=focus).toList()));
            assertSame(system,projector.system()); assertEquals(1,count(projector,"memberOf","Workspace"));
            assertEquals(0,count(projector,"focuses","Artifact"));
            GenericFunctionalRuntimeProjectionTest.assertBindingsComplete(projector.mutations());
            var replay=new NativeRuntimeReplay(); var bundle=directory.resolve("replay");
            replay.exportBundle(projector,new NativeUseExporter().export(system.model()).useText(),bundle);
            assertTrue(replay.replay(bundle).complete());
        }
    }

    private void checkIdentity() throws Exception {
        var pipeline=fixture();
        try(var projector=new NativeRuntimeProjector(pipeline,RuntimeVerificationFixtures.SESSION,1,RuntimeVerificationFixtures.REVISION)) {
            var facts=facts(pipeline); projector.applySnapshot(snapshot("initial",0,facts));
            var focus=facts.stream().filter(f->f.kind()==RuntimeFactKind.RELATION_STATE).findFirst().orElseThrow();
            var wrong=new LinkedHashMap<>(focus.values()); wrong.put("agentSemanticId","unproved-agent");
            var before=projector.mutations().savepoint();
            var result=projector.mutations().apply(focus.id(),null,focus.kind(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,wrong);
            assertEquals(NativeRuntimeMutationEngine.Status.REJECTED,result.status()); assertEquals(before,projector.mutations().savepoint());
            assertEquals(2,count(projector,"memberOf","Workspace"));
            var root=facts.stream().filter(f->f.kind()==RuntimeFactKind.AGENT && f.id().scope().equals("/main")).findFirst().orElseThrow();
            var quit=new LinkedHashMap<>(root.values()); quit.put("normalizedEventKind","DELETE_CARTAGO_AGENT_IDENTITY");
            assertEquals(NativeRuntimeMutationEngine.Status.MATERIALIZED,projector.mutations().apply(root.id(),null,root.kind(),
                    ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,quit).status());
            assertEquals(1,count(projector,"memberOf","Workspace")); assertEquals(0,count(projector,"focuses","Artifact"));
            assertEquals(NativeRuntimeMutationEngine.Status.EVIDENCE_ONLY,projector.mutations().apply(focus.id(),null,focus.kind(),
                    ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,focus.values()).status());
            assertEquals(1,count(projector,"memberOf","Workspace"),"Focus must not infer a join or bind an agent by its display name");
            String platform="cartago:artifact:env:/main:platform-uuid";
            var platformFocus=new LinkedHashMap<>(focus.values()); platformFocus.put("artifactSemanticId",platform);
            platformFocus.put("artifactTypeJavaClassName","cartago.tools.Console"); platformFocus.put("artifactTypeOrigin","PLATFORM");
            var platformId=new BridgeEntityId("cartago","environment","focus",platform,focus.values().get("agentSemanticId").toString(),"relation");
            var soil=new NativeUseSoilExporter().export(projector.system()).commands();
            assertEquals(NativeRuntimeMutationEngine.Status.EVIDENCE_ONLY,projector.mutations().apply(platformId,null,focus.kind(),
                    ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,platformFocus).status());
            assertEquals(soil,new NativeUseSoilExporter().export(projector.system()).commands());
            assertNull(projector.system().model().getClass("Console"));
        }
    }

    private CodeGroundedNativePipeline.Result fixture() throws Exception {
        Files.createDirectories(directory.resolve("src/agt"));
        Files.writeString(directory.resolve("src/agt/worker.asl"),"+!run <- .print(\"ready\").\n");
        var entry=directory.resolve("relations.jcm"); Files.writeString(entry,"mas relations { agent worker : worker.asl {} }\n");
        return new CodeGroundedNativePipeline().build(new OfficialProjectAdapter().adapt(new OfficialProjectLoader().load(entry),entry));
    }
    private static List<RuntimeFact> facts(CodeGroundedNativePipeline.Result pipeline) {
        var result=new ArrayList<>(RuntimeVerificationFixtures.snapshot("seed",0).facts());
        var root=result.getFirst(); var rootValues=new LinkedHashMap<>(root.values());
        rootValues.put("workspaceDeclarationSemanticId",pipeline.source().snapshot().workspaceDeclarations().getFirst().metadata().semanticId());
        result.set(0,fact(root.id(),root.kind(),rootValues));
        var lab=new LinkedHashMap<>(root.values()); lab.put("name","lab"); lab.put("fullName","/main/lab"); lab.put("uuid","ws-2");
        lab.put("semanticId","cartago:workspace:env:/main/lab:ws-2");
        result.add(fact(new BridgeEntityId("cartago","environment","workspace","env","/main/lab","ws-2"),RuntimeFactKind.WORKSPACE,lab));
        String declaration=pipeline.source().snapshot().agentDeclarations().getFirst().metadata().semanticId();
        for(var workspace:List.of(rootValues,lab)) {
            String path=workspace.get("fullName").toString(); String agent="cartago:agent:env:"+path+":worker:7";
            var payload=Map.<String,Object>of("normalizedEventKind","UPSERT_CARTAGO_AGENT_IDENTITY","semanticId",agent,
                    "globalId","worker","localId",7,"name","worker","workspaceSemanticId",workspace.get("semanticId"),"agentDeclarationSemanticId",declaration);
            result.add(fact(new BridgeEntityId("cartago","environment","agent",path,"worker","7"),RuntimeFactKind.AGENT,payload));
        }
        String agent="cartago:agent:env:/main:worker:7",artifact=RuntimeVerificationFixtures.ARTIFACT;
        result.add(fact(new BridgeEntityId("cartago","environment","focus",artifact,agent,"relation"),RuntimeFactKind.RELATION_STATE,
                Map.of("normalizedEventKind","SET_CARTAGO_FOCUS","agentSemanticId",agent,"artifactSemanticId",artifact,"focused",true)));
        return result;
    }
    private static RuntimeFact fact(BridgeEntityId id,RuntimeFactKind kind,Map<String,Object> payload) {
        return new RuntimeFact(id,kind,payload,List.of(),ProjectionStatus.MATERIALIZED_FAITHFULLY,Completeness.COMPLETE,List.of());
    }
    private static RuntimeSnapshot snapshot(String name,long sequence,List<RuntimeFact> facts) {
        var watermark=Map.of("cartago",new SourceWatermark("cartago",sequence));
        return new RuntimeSnapshot(name,RuntimeVerificationFixtures.REVISION,Instant.EPOCH,Instant.EPOCH,watermark,watermark,1,
                facts,Map.of("cartago",Completeness.COMPLETE),name);
    }
    private static long count(NativeRuntimeProjector projector,String relation,String second) {
        String name=DomainProjection.relation(relation,"Agent",second);
        return projector.system().state().allLinks().stream().filter(l->l.association().name().equals(name)).count();
    }
}
