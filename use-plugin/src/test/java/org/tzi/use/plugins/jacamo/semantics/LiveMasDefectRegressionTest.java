package org.tzi.use.plugins.jacamo.semantics;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.adapter.*;
import org.jacamo.bridge.contract.*;
import org.tzi.use.plugins.jacamo.bridge.*;
import org.tzi.use.plugins.jacamo.codegrounded.*;
import org.tzi.use.plugins.jacamo.codegrounded.use.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;
import org.tzi.use.plugins.jacamo.semantics.csl.*;
import org.tzi.use.plugins.jacamo.semantics.istar.*;
import org.tzi.use.plugins.jacamo.semantics.profile.*;
import org.tzi.use.plugins.jacamo.semantics.mapping.*;
import org.tzi.use.plugins.jacamo.semantics.evidence.*;

/** Complete accepted production subscription, without a polling or raw-stream fallback. */
public class LiveMasDefectRegressionTest {
    public static void main(String[] args)throws Exception {new LiveMasDefectRegressionTest().actualTwoAgentMasKeepsNplStreamLiveAndAttributesTasksToTheirPerformer();System.exit(0);}
    @Test void actualTwoAgentMasKeepsNplStreamLiveAndAttributesTasksToTheirPerformer()throws Exception {
        String expected=System.getProperty("use.fix.expectedPlugin");
        if(expected!=null)assertEquals(Path.of(expected).toRealPath(),Path.of(NativeRuntimeProjector.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath());
        Path dir=Files.createTempDirectory(Path.of("target"),"live-mas-defects-").toAbsolutePath();
        Files.createDirectories(dir.resolve("src/agt"));Files.createDirectories(dir.resolve("src/org"));
        Files.writeString(dir.resolve("src/org/audit.xml"),"""
            <?xml version="1.0"?><organisational-specification id="audit" os-version="1.1" xmlns="http://moise.sourceforge.net/os"><structural-specification><role-definitions><role id="worker"/><role id="reviewer"/></role-definitions><group-specification id="team"><roles><role id="worker" min="1" max="1"/><role id="reviewer" min="1" max="1"/></roles></group-specification></structural-specification><functional-specification><scheme id="service"><goal id="g" type="achievement"/><mission id="work" min="1" max="1"><goal id="g"/></mission></scheme></functional-specification><normative-specification><norm id="may_work" type="permission" role="worker" mission="work"/></normative-specification></organisational-specification>
            """);
        String includes="\n{ include(\"$jacamo/templates/common-cartago.asl\") }\n{ include(\"$jacamo/templates/common-moise.asl\") }\n";
        Files.writeString(dir.resolve("src/agt/a.asl"),"!audit.\n+!audit <- .wait(2000); ?focusing(X,counter,_,_,_,_); gate[artifact_id(X)]; ?focusing(N,norms,_,_,_,_); load(\"scope monitor { norm monitor_new: monitor_start -> obligation(a,true,monitor_done,`now`+`10 seconds`). }\")[artifact_id(N)]; addFact(\"monitor_start\")[artifact_id(N)]; commitMission(work)[artifact_name(service_run),wsp(org)]; increment[artifact_id(X)]; addFact(\"monitor_done\")[artifact_id(N)]; set(-1)[artifact_id(X)]; .wait(30000).\n"+includes);
        Files.writeString(dir.resolve("src/agt/b.asl"),"!idle.\n+!idle <- .wait(30000).\n"+includes);
        int port;try(var socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){port=socket.getLocalPort();}
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);Path secretFile=dir.resolve("secret.hex");Files.writeString(secretFile,HexFormat.of().formatHex(secret));
        String distribution=RuntimeDistributionFingerprint.compute().distributionDigest();Path jcm=dir.resolve("audit.jcm"),stop=dir.resolve("stop.flag"),ready=dir.resolve("consumer-ready.flag");
        Files.writeString(jcm,"""
            mas audit_live {
              agent a : a.asl {
                focus: main.counter, main.norms
              }
              agent b : b.asl {
                focus: main.counter
              }
              workspace main {
                artifact counter: %s
                artifact norms: ora4mas.nopl.NormativeBoard
              }
              organisation org: audit.xml {
                group team: team {
                  responsible-for: service_run
                  players: a worker, b reviewer
                }
                scheme service_run: service
              }
              platform: org.jacamo.bridge.adapter.JaCaMoBridgePlatform("port=%s", "secretFile=%s", "jcmFile=%s", "distributionSha256=%s")
            }
            """.formatted(LiveRegressionCounterArtifact.class.getName(),port,secretFile.toString().replace('\\','/'),jcm.toString().replace('\\','/'),distribution));
        String cp=Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator)).map(p->Path.of(p).toAbsolutePath().normalize().toString()).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-Djacamo.launch.captureConsole=true","-Duse.fix.bootReady="+dir.resolve("boot-ready.flag"),"-Duse.fix.release="+dir.resolve("release.flag"),"-cp",cp,"jason.infra.local.LiveJaCaMoLauncherMain",jcm.toString(),stop.toString(),"150","true",ready.toString()).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(dir.resolve("producer.log").toFile()).start();
        var pending=new LinkedBlockingQueue<RuntimeEvent>(8192);var errors=new CopyOnWriteArrayList<String>();var observed=new ArrayList<Map<String,Object>>();var families=new HashSet<String>();
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);boolean open=false;
            while(System.nanoTime()<deadline && child.isAlive()){try(var s=new Socket()){s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),port),100);open=true;break;}catch(java.io.IOException wait){Thread.sleep(30);}}
            assertTrue(open,()->"bridge startup failed: "+readLog(dir));
            // Startup is outside the monitored interval. Pace official cycles before taking its authoritative cut.
            try(var bootstrap=new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(),port,secret,4*1024*1024,10000)) {
                var identity=ContractCodec.decode(bootstrap.handshake(),4*1024*1024,64,256*1024);new ContractValidator().validate(identity);
                startupControl(bootstrap,identity,RuntimeControlContract.Action.PAUSE);Files.writeString(ready,"start bootstrap");
                long bootstrapDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
                while(!Files.exists(dir.resolve("boot-ready.flag")) && System.nanoTime()<bootstrapDeadline) {
                    startupControl(bootstrap,identity,RuntimeControlContract.Action.RESUME);
                    while(startupControl(bootstrap,identity,RuntimeControlContract.Action.STATUS).state()!=RuntimeControlContract.State.RUNNING && System.nanoTime()<bootstrapDeadline)Thread.sleep(1);
                    startupControl(bootstrap,identity,RuntimeControlContract.Action.PAUSE);
                    while(startupControl(bootstrap,identity,RuntimeControlContract.Action.STATUS).state()!=RuntimeControlContract.State.PAUSED && System.nanoTime()<bootstrapDeadline)Thread.sleep(1);
                    Thread.sleep(50);
                }
                assertTrue(Files.exists(dir.resolve("boot-ready.flag")),()->readLog(dir));
                assertEquals(RuntimeControlContract.State.PAUSED,startupControl(bootstrap,identity,RuntimeControlContract.Action.STATUS).state());
            }
            var mirror=new BridgeMirrorStateMachine(8192);
            try(var client=new BridgeClient(new LocalTcpBridgeTransport(InetAddress.getLoopbackAddress(),port,secret,4*1024*1024,10000),mirror,distribution,Set.of("official.model","runtime.snapshot"),8192,e->{if(!pending.offer(e))errors.add("CONSUMER_QUEUE_OVERFLOW");},(e,d)->errors.add(d))) {
                var accepted=client.synchronize();control(client,accepted,RuntimeControlContract.Action.PAUSE);assertTrue(accepted.capabilities().stream().anyMatch(c->c.name().equals("cartago.invocation.suspend-resume.v1") && c.status()==CapabilityStatus.UNAVAILABLE));
                var model=accepted.model();var source=model.semanticContract();assertEquals(2,source.agentDeclarations().size());
                var group=accepted.runtime().facts().stream().filter(f->f.kind()==RuntimeFactKind.GROUP_BOARD).findFirst().orElseThrow();
                var artifact=accepted.runtime().facts().stream().filter(f->f.kind()==RuntimeFactKind.ARTIFACT && "counter".equals(f.values().get("name"))).findFirst().orElseThrow();
                var type=new CslSchema.Classifier("domain",DomainProjection.artifactId(LiveRegressionCounterArtifact.class.getName()),"LiveCounter",CslSchema.Kind.ENTITY,"",false,List.of(new CslSchema.Attribute("p","p","Integer","p",false)));
                var base=new OrganizationalMappingRules().derive(model,List.of(type),List.of(),List.of(new CslSchema.Invariant("nonnegative","domain","Nonnegative","self.p >= 0")),List.of(new CslSchema.OperationContract("increment","domain",List.of(LiveRegressionCounterArtifact.class.getName(),"increment",List.of(),"void"),"self.p >= 0","self.p = self.p@pre + 1")),new RequirementModel(List.of()));
                var nodes=new ArrayList<RequirementModel.Node>();
                for(String agent:List.of("a","b")) {
                    String roleName=agent.equals("a")?"worker":"reviewer";
                    String roleSource=source.moiseOrganizations().getFirst().structuralSpecification().roles().stream().filter(r->r.roleId().equals(roleName)).findFirst().orElseThrow().metadata().semanticId();
                    String role=base.schema().classifiers().stream().filter(c->c.sourceId().equals(roleSource)).findFirst().orElseThrow().id();
                    String player=source.agentDeclarations().stream().filter(a->a.name().equals(agent)).findFirst().orElseThrow().metadata().semanticId();
                    var binding=new RequirementModel.Binding(agent,player,role,group.id().canonical(),artifact.id().canonical(),"domain","episode");
                    nodes.add(new RequirementModel.Node("task"+agent,"task"+agent,RequirementModel.Kind.TASK,RequirementModel.Temporal.NONE,binding,"","true",RequirementModel.Refinement.NONE,List.of(),"","increment"));
                    if(agent.equals("a"))nodes.add(new RequirementModel.Node("ready","ready",RequirementModel.Kind.GOAL,RequirementModel.Temporal.ACHIEVE,binding,"self.p >= 1","true",RequirementModel.Refinement.NONE,List.of(),"",""));
                }
                var config=new CslMonitorConfiguration(base.version(),base.profile(),base.schema(),new RequirementModel(nodes),base.decisions());
                var built=new CodeGroundedNativePipeline().buildSemanticBundle(model,NativeProjectionMode.AUTO,new SemanticTransformationService().fromOfficial(model,config)).nativeResult();
                try(var p=new NativeRuntimeProjector(built,accepted.sessionId(),accepted.generation(),model.modelRevision())) {
                    p.applySnapshot(accepted.runtime());var x=p.mutations().objectForSemanticId(artifact.id().canonical());assertNotNull(x);assertEquals("0",x.state(p.system().state()).attributeValue("p").toString());
                    Files.writeString(ready,"start agents under cycle control");boolean released=false,committed=false,increment=false,negative=false,norm=false;long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
                    while(System.nanoTime()<end && !(increment && negative && norm && committed)) {
                        // Drive real cycles with the production PAUSE/RESUME protocol; consume every resulting callback.
                        // Quotas and source-event fidelity stay unchanged. This is not an open-loop throughput test.
                        if(pending.isEmpty()) {
                            control(client,accepted,RuntimeControlContract.Action.RESUME);
                            long controlDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                            while(control(client,accepted,RuntimeControlContract.Action.STATUS).state()!=RuntimeControlContract.State.RUNNING && System.nanoTime()<controlDeadline)Thread.sleep(1);
                            control(client,accepted,RuntimeControlContract.Action.PAUSE);
                            while(control(client,accepted,RuntimeControlContract.Action.STATUS).state()!=RuntimeControlContract.State.PAUSED && System.nanoTime()<controlDeadline)Thread.sleep(1);
                            assertEquals(RuntimeControlContract.State.PAUSED,control(client,accepted,RuntimeControlContract.Action.STATUS).state());
                        }
                        if(!released && Files.exists(dir.resolve("boot-ready.flag")) && p.system().state().allObjects().stream().filter(o->!p.mutations().metadata(o,"roleContext").isBlank()).count()==2) {Files.writeString(dir.resolve("release.flag"),"release workload");released=true;}
                        assertTrue(errors.isEmpty(),errors.toString());RuntimeEvent event=pending.poll(250,TimeUnit.MILLISECONDS);if(event==null)continue;
                        if(event.sourceSequence()<=p.sourceWatermarks().getOrDefault(event.sourceId(),-1L))continue;
                        p.apply(event);families.add(event.subsystem());var result=p.coordinator().lastObservation();
                        if(result.freshness().equals("STALE") || event.kind()==RuntimeEventKind.SUCCEEDED && "increment".equals(event.after().get("operation")))Files.write(dir.resolve("boundary-result.json"),CanonicalJson.encode(result.toMap()));
                        assertEquals("CURRENT_OBSERVED",result.freshness(),result.diagnostic());
                        assertNotEquals(IntentionalMarking.Status.SATISFIED,result.semantic().requirements().get("taskb").evaluated(),"idle b was credited with a's invocation");
                        norm|=event.kind()==RuntimeEventKind.NORM_ACTIVATED;
                        committed|=event.factKind()==RuntimeFactKind.SCHEME_BOARD && event.after().get("commitments") instanceof List<?> commitments && !commitments.isEmpty();
                        if(event.kind()==RuntimeEventKind.SUCCEEDED && "increment".equals(event.after().get("operation"))) {
                            var post=result.semantic().constraints().stream().filter(r->r.id().equals("increment:post")).findFirst().orElseThrow();assertEquals(SemanticVerdict.PASS,post.verdict(),post.toString()+" coverage="+result.semantic().coverage());assertEquals("0",post.witness().get("before.p"));assertEquals("1",post.witness().get("after.p"));
                            assertEquals(IntentionalMarking.Status.SATISFIED,result.semantic().requirements().get("taska").evaluated());increment=true;
                        }
                        if(x.state(p.system().state()).attributeValue("p").toString().equals("-1")) {
                            assertTrue(result.semantic().constraints().stream().anyMatch(r->r.kind().equals("INVARIANT") && r.id().contains("Nonnegative") && r.verdict()==SemanticVerdict.FAIL));assertEquals(IntentionalMarking.Status.SATISFIED,result.semantic().requirements().get("ready").evaluated());negative=true;
                        }
                        if(event.kind()==RuntimeEventKind.NORM_ACTIVATED || event.after().containsKey("phase") || negative)observed.add(Map.of("source",event.sourceId(),"kind",event.kind().name(),"sequence",event.sourceSequence(),"p",x.state(p.system().state()).attributeValue("p").toString(),"resultHash",result.resultHash()));
                    }
                    assertTrue(increment && negative && norm && committed,()->"missing live scenario: "+observed+" errors="+errors+" producer="+readLog(dir));assertTrue(errors.isEmpty(),errors.toString());assertEquals(BridgeClientState.LIVE,mirror.state());
                    assertTrue(families.containsAll(Set.of("jason","cartago","moise","npl")),families.toString());
                    var out=dir.resolve("replay");new NativeRuntimeReplay().exportBundle(p,built.export().useText(),out);var replay=new NativeRuntimeReplay().replay(out);assertTrue(replay.complete(),replay.diagnostics().toString());assertEquals(p.coordinator().latest().resultHash(),replay.finalResultHash());
                    Files.write(Path.of("target/live-mas-defect-regression.json"),CanonicalJson.encode(Map.of("productionSubscription","LIVE","families",families.stream().sorted().toList(),"observations",observed,"errors",errors,"replay",SemanticJson.tree(replay),"directory",dir.toString(),"npl",NplRuntimeProfile.evidence())));
                }
            }
        } finally {Files.writeString(stop,"stop");if(!child.waitFor(15,TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor(5,TimeUnit.SECONDS);}Files.writeString(dir.resolve("consumer-errors.txt"),errors.toString());Files.write(dir.resolve("observed.json"),CanonicalJson.encode(observed));}
    }
    private static RuntimeControlContract.Status startupControl(LocalTcpBridgeTransport transport,ContractEnvelope identity,RuntimeControlContract.Action action) {
        var request=new RuntimeControlContract.Request(RuntimeControlContract.VERSION,identity.sessionId(),identity.generation(),identity.modelRevision(),UUID.randomUUID().toString(),action,"bounded bootstrap");
        var response=ContractCodec.decode(transport.control(request),4*1024*1024,64,256*1024);new ContractValidator().validate(response);return RuntimeControlContract.Status.decode(response.payload());
    }
    private static RuntimeControlContract.Status control(BridgeClient client,BridgeClient.Accepted accepted,RuntimeControlContract.Action action) {
        return client.control(new RuntimeControlContract.Request(RuntimeControlContract.VERSION,accepted.sessionId(),accepted.generation(),accepted.modelRevision(),UUID.randomUUID().toString(),action,"bounded integration test"));
    }
    private static String readLog(Path dir){try{return Files.readString(dir.resolve("producer.log"));}catch(Exception e){return e.toString();}}
}
