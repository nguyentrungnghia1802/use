package org.tzi.use.plugins.jacamo.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.*;
import org.tzi.use.plugins.jacamo.materialization.*;
import org.tzi.use.plugins.jacamo.constraint.*;
import org.tzi.use.plugins.jacamo.ocl.*;
import org.tzi.use.plugins.jacamo.semantic.*;
import org.tzi.use.plugins.jacamo.trace.*;
import org.tzi.use.plugins.jacamo.verification.*;
import org.tzi.use.plugins.jacamo.verification.profile.*;
import cartago.*;
import cartago.util.agent.*;

class CounterTeamIntegrationTest {
    @TempDir Path compiled;
    @Test void secondCaseUsesUnchangedPipelineWithLiveStateFailuresAndResync() throws Exception {
        long started=System.nanoTime();
        Path project=Path.of("src/test/resources/counter-team").toAbsolutePath();
        var semantic=new StaticProjectImporter().importProject(project.resolve("counter-team.jcm")).model();
        var mapping=new MappingLoader().loadCanonical(Path.of("."));
        var structure=new VerificationSemanticLayer().apply(new TransformationPlanner().plan(semantic,mapping),new VerificationProfileLoader().loadV1()).transformation();
        var instances=new InstancePlanner().plan(semantic,mapping,structure);
        var loader=new OclProfileLoader();var core=loader.loadCore();var policy=loader.loadCase(project,Path.of("verification/counter.ocl"));
        var constraints=new ConstraintExtractor().extract(semantic,structure,Map.of());
        assertTrue(constraints.stream().anyMatch(c->c.sourceKind()==ConstraintSpec.SourceKind.CARTAGO_GUARD && c.status()==TranslationStatus.EXACT));
        assertTrue(constraints.stream().anyMatch(c->c.sourceKind()==ConstraintSpec.SourceKind.JASON_CONTEXT && c.status()==TranslationStatus.UNSUPPORTED));
        var ocl=new OclGenerator().generate("counterTeam",structure,constraints,List.of(core,policy));
        assertEquals(ocl,new OclGenerator().generate("counterTeam",structure,constraints,List.of(core,policy)));
        var generated=new TextBackend().generate("counterTeam",structure,instances);
        var direct=new DirectUseBackend().materialize(new TextBackend.GeneratedArtifacts(ocl.useModel(),generated.initialCommands()),instances);
        assertTrue(direct.structureValid(),direct.validationOutput());
        assertTrue(direct.invariantsValid(),direct.validationOutput());
        var trace=new TraceBuilder().build(semantic,mapping,structure,instances);
        var registry=ConstraintRegistry.load(direct.system().model(),ocl,List.of(ConstraintRegistry.profile(ConstraintOrigin.CORE,core),ConstraintRegistry.profile(ConstraintOrigin.CASE,policy)));
        var cross=new CrossDimensionalVerifier().verify(semantic,direct.system(),trace);
        assertTrue(cross.stream().anyMatch(r->r.outcome()==VerificationOutcome.PASS));
        assertFalse(cross.stream().anyMatch(r->r.outcome()==VerificationOutcome.FAIL || r.outcome()==VerificationOutcome.ERROR),cross.toString());
        String artifactId=id(semantic,MetamodelKind.Artifact,"counter1");String agentId=id(semantic,MetamodelKind.Agent,"worker");
        var compiler=javax.tools.ToolProvider.getSystemJavaCompiler();
        assertEquals(0,compiler.run(null,null,null,"-classpath",System.getProperty("java.class.path"),"-d",compiled.toString(),project.resolve("src/env/demo/Counter.java").toString()));
        var environment=CartagoEnvironment.getInstance();environment.init();
        var factory=new URLArtifactFactory("phase24-counter",compiled.toUri().toURL());
        environment.addArtifactFactory("/main",factory);
        var agent=new jason.asSemantics.Agent();agent.setConsiderToAddMIForThisAgent(false);agent.initAg(project.resolve("src/agt/worker.asl").toString());
        var context=new CartagoBasicContext("counter-worker");
        ArtifactId artifact=null;RuntimeMirrorService mirror=null;
        try {
            artifact=context.makeArtifact(context.getJoinedWspId("main"),"counter1","demo.Counter");
            var binding=new CartagoArtifactBinding("/main","counter1",artifactId,Map.of("count","count"),Map.of("setValue","setValue","guardedSet","guardedSet"));
            var cartago=new CartagoRuntimeConnector("counter-cartago",new OfficialCartagoRuntimeAccess(environment),List.of(binding));
            var jason=new JasonRuntimeConnector("counter-jason",Map.of("worker",agent.getTS()),Map.of("worker",agentId));
            var os=new moise.os.OSBuilder();os.addRootGroup("team_group");os.addRole("team_group","worker");os.addScheme("counting","count_items");os.addMission("counting","count","count_items");
            var norm=new moise.os.ns.Norm(os.getOS().getSS().getRoleDef("worker"),os.getOS().getFS().findMission("count"),os.getOS().getNS(),moise.os.ns.NS.OpTypes.permission);norm.setId("can_count");os.getOS().getNS().addNorm(norm);
            var oe=new moise.oe.OE(null,os.getOS());var group=oe.addGroup("team_group","team_group");var scheme=oe.startScheme("counting","counting");scheme.addResponsibleGroup(group);
            var worker=oe.addAgent("worker");worker.adoptRole("worker",group);worker.commitToMission("count",scheme);
            var mb=new MoiseRuntimeBinding("team",id(semantic,MetamodelKind.Organisation,"team"),Map.of("worker",agentId),Map.of("team_group",id(semantic,MetamodelKind.Group,"team_group")),Map.of("counting",id(semantic,MetamodelKind.Scheme,"counting")));
            var moise=new MoiseRuntimeConnector("counter-moise",oe,mb);
            register(trace,artifactId,binding.runtimeSourceId());register(trace,agentId,"jason:agent:worker");register(trace,agentId,mb.agentRuntimeId("worker"));
            register(trace,mb.organisationSemanticId(),mb.organisationRuntimeId());register(trace,mb.groups().get("team_group"),mb.groupRuntimeId("team_group"));register(trace,mb.schemes().get("counting"),mb.schemeRuntimeId("counting"));
            var verifier=new RuntimeVerificationEngine(direct.system(),registry,trace);var mutations=new RuntimeMutationEngine(direct.system(),trace);
            mirror=new RuntimeMirrorService(new CompositeRuntimeConnector("counter",List.of(jason,cartago,moise)),mutations,64,verifier);
            mirror.connect(URI.create("jacamo://local/counter-team"));
            assertEquals(MirrorState.LIVE,mirror.state());
            assertFalse(mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY).drifted());
            assertFalse(verifier.latestReport().hasViolation());
            context.doAction(artifact,new Op("setValue",3));mirror.awaitIdle(Duration.ofSeconds(5));
            assertFalse(mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY).drifted());
            assertTrue(verifier.reports().stream().flatMap(r->r.verification().results().stream()).anyMatch(r->r.outcome()==VerificationOutcome.PASS && r.constraintId().contains("ObservedResult")));
            context.doAction(artifact,new Op("setValue",-1));mirror.awaitIdle(Duration.ofSeconds(5));
            assertTrue(verifier.reports().stream().anyMatch(r->r.event()!=null && r.verification().results().stream().anyMatch(v->v.outcome()==VerificationOutcome.FAIL && v.sourceTrace().contains(artifactId) && v.runtimeEventIds().contains(r.event().eventId()))));
            ArtifactId target=artifact;
            assertThrows(ActionFailedException.class,()->context.doAction(target,new Op("setValue",-2)));mirror.awaitIdle(Duration.ofSeconds(5));
            assertTrue(verifier.reports().stream().anyMatch(r->r.diagnostics().contains("RUNTIME_OPERATION_ABORTED")));
            scheme.getGoal("count_items").setAchieved(worker);moise.pollChanges();mirror.awaitIdle(Duration.ofSeconds(5));
            assertFalse(moise.normativeSnapshot().unsupported().isEmpty());
            assertEquals(0,mirror.metrics().failed());assertEquals(0,mirror.metrics().rejected());assertEquals(0,mirror.metrics().dropped());
            mirror.disconnect();context.doAction(artifact,new Op("setValue",2));mirror.reconnectAndResync();
            assertEquals(MirrorState.LIVE,mirror.state());assertFalse(mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY).drifted());
            assertFalse(verifier.latestReport().hasViolation());
            var output=Path.of("target/phase24-counter-evidence");Files.createDirectories(output);
            Files.writeString(output.resolve("model.use"),ocl.useModel());Files.writeString(output.resolve("initial-state.cmd"),generated.initialCommands());Files.writeString(output.resolve("translation-manifest.txt"),ocl.provenanceManifest());
            var events=mutations.runtimeTrace().entries().stream().filter(e->e.disposition().equals("ACCEPTED")).map(RuntimeTrace.Entry::event).toList();
            new RuntimeEventCodec().writeEvents(output.resolve("runtime-events.json"),events);
            var exporter=new RuntimeVerificationReportExporter();
            Files.writeString(output.resolve("reports.json"),"["+verifier.reports().stream().map(exporter::toJson).collect(java.util.stream.Collectors.joining(","))+"]");
            var json=new com.fasterxml.jackson.databind.ObjectMapper();
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("trace.json").toFile(),trace.records().stream().map(r->Map.of("traceId",r.traceId(),"semanticId",r.sourceSemanticId(),"useId",r.targetUseId(),"status",r.status().name())).toList());
            var hashes=new TreeMap<String,String>();
            try(var files=Files.list(output)) {for(var f:files.filter(Files::isRegularFile).filter(f->!f.getFileName().toString().equals("summary.json")).toList()) hashes.put(f.getFileName().toString(),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f))));}
            var sourceHashes=new TreeMap<String,String>();
            try(var files=Files.walk(project)) {for(var f:files.filter(Files::isRegularFile).toList()) sourceHashes.put(project.relativize(f).toString().replace('\\','/'),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f))));}
            long elapsed=System.nanoTime()-started;
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(),Map.of("status","SUPPORTED_SUBSET_COMPLETE","elapsedNanos",elapsed,"eventCount",events.size(),"hashes",hashes,"sourceHashes",sourceHashes,"javaVersion",System.getProperty("java.version"),"limitations",List.of("Test-driven in-process component execution","Static XML is not launcher OS","No full NPL lifecycle or autonomous cross-dimensional chain")));
            System.out.println("PHASE24_COUNTER elapsedNanos="+elapsed+" events="+events.size());
        } finally {
            if(mirror!=null) mirror.close();
            if(artifact!=null) environment.getController("/main").removeArtifact("counter1");
            environment.removeArtifactFactory("/main","phase24-counter");factory.cloader.close();
        }
    }
    private String id(JaCaMoSemanticModel model,MetamodelKind kind,String name) {
        return model.elements().stream().filter(e->e.kind()==kind && e.name().equals(name)).findFirst().orElseThrow().id().value();
    }
    private void register(TraceIndex trace,String semantic,String runtime) {
        trace.registerRuntimeKey(trace.bySemanticId(semantic).stream().filter(r->r.targetKind().equals("OBJECT")).findFirst().orElseThrow().traceId(),runtime);
    }
}
