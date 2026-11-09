package org.jacamo.bridge.adapter;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.jacamo.bridge.contract.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;
import jacamo.checkpoint.VerifiedCheckpointCoordinator;
import static org.junit.jupiter.api.Assertions.*;
class VerifiedCheckpointEarlyBoundaryTest {
 @Test void selectedMarkerClosesAdmissionBeforePopulationCaptureButReadyWaitsForPublishedEvidence()throws Exception{
  String javaBinary = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
  for(String mode:List.of("post","pre")){
   var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",javaBinary).toString(),"-cp",System.getProperty("java.class.path"),getClass().getName(),mode).redirectErrorStream(true).start();
   assertTrue(child.waitFor(25,TimeUnit.SECONDS));assertEquals(0,child.exitValue(),new String(child.getInputStream().readAllBytes()));
  }
 }
 public static void main(String[] args)throws Exception{
  boolean preMode=args.length>0&&"pre".equals(args[0]);
  String hash="a".repeat(64);Path file=Files.createTempFile("early-boundary-policy-",".json");
  var rules=preMode
   ?List.of(new VerifiedCheckpointProtocol.Rule("task-pre","observation:steps","fixture.Domain",Set.of("Work"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),"TASK_PRE"),
            new VerifiedCheckpointProtocol.Rule("task-post","observation:steps","fixture.Domain",Set.of("Work"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),"TASK_POST"))
   :List.of(new VerifiedCheckpointProtocol.Rule("task","observation:steps","fixture.Domain",Set.of("Work"),Set.of(VerifiedCheckpointProtocol.REQUIRED_CHECK),"TASK_POST"));
  var policy=new VerifiedCheckpointProtocol.Policy(hash,Map.of("source",hash),rules);
  Files.write(file,CanonicalJson.encode(policy.payload()));System.setProperty("jacamo.execution.mode","VERIFIED_CHECKPOINTS");System.setProperty("jacamo.checkpoint.policy",file.toString());ExecutionBarrier.enable("run");
  var request=new ObservationCapabilityRequest("steps",JasonStepCapability.PROVIDER,true,Map.of("plans",List.of(Map.of("steps",List.of(Map.of("task","Work","artifactClass","fixture.Domain"))))));
  var plan=new ObservationRequestPlan("session",1,"revision",hash,List.of(request),Map.of());var mark=Map.of("source",new SourceWatermark("source",1));
  var snapshot=new RuntimeSnapshot("cut","revision",Instant.EPOCH,Instant.EPOCH,mark,mark,1,List.of(),Map.of("source",Completeness.COMPLETE),"state");
  var published=new AtomicBoolean();var captures=new AtomicInteger();var worker=Executors.newSingleThreadExecutor();
  try(var bridge=new VerifiedCheckpointBridge("session","run",1,"revision",plan,()->{assertTrue(published.get());captures.incrementAndGet();return snapshot;},()->true)){
   var field=VerifiedCheckpointBridge.class.getDeclaredField("coordinator");field.setAccessible(true);var coordinator=(VerifiedCheckpointCoordinator)field.get(bridge);
   if(!preMode){
    bridge.beforeObservation(step("ENABLED"));assertFalse(ExecutionBarrier.proof().admissionClosed());
   }
   String targetPhase=preMode?"ENABLED":"SUCCEEDED";
   try(var source=ExecutionBarrier.enter("source-marker")){
    bridge.beforeObservation(step(targetPhase));assertTrue(ExecutionBarrier.proof().admissionClosed());assertFalse(ExecutionBarrier.proof().clockFrozen());assertEquals(0,captures.get());
    var late=worker.submit(()->{try(var ignored=ExecutionBarrier.enter("new-domain-work")){return true;}catch(ExecutionBarrier.Stopped stopped){return false;}});
    assertThrows(TimeoutException.class,()->late.get(50,TimeUnit.MILLISECONDS));
    published.set(true);bridge.observe(step(targetPhase).event("session",1,"revision",1,Instant.EPOCH,"",""));
    assertEquals(0,captures.get());assertTrue(coordinator.view().request().boundary().sourceOccurrence().endsWith(":"+targetPhase));
   }
   long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(captures.get()==0&&System.nanoTime()<end)Thread.sleep(10);
   assertEquals(1,captures.get());assertTrue(ExecutionBarrier.proof().clockFrozen());assertTrue(ExecutionBarrier.proof().active().isEmpty());
  }finally{worker.shutdown();assertTrue(worker.awaitTermination(3,TimeUnit.SECONDS));Files.deleteIfExists(file);}
  System.out.println("EARLY_ADMISSION_AND_PUBLISH_BEFORE_READY_PASS");
 }
 private static VerificationObservation step(String phase){
  var data=new LinkedHashMap<String,Object>();data.put("agentIdentity","physical-agent");data.put("goalInvocation","goal-1");data.put("stepInvocation","step-1");data.put("sourceStepId","Work");data.put("phase",phase);data.put("artifactIdentity","physical-target");data.put("schemeIdentity","");data.put("operationInvocation","");data.put("bindings",Map.of());
  return new VerificationObservation(VerificationObservation.Kind.JASON_STEP,"step-1",Completeness.COMPLETE,"",VerificationObservation.SourceTime.unavailable("No clock claimed"),data);
 }
}
