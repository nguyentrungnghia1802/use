import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.main.Session;
import org.tzi.use.plugins.jacamo.*;
import org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.*;

/** Diagnostic only: runs authored input unchanged; never acknowledges a failed/unknown cut. */
class AdditionalVerifiedProbe {
 static void write(Path path,Object value)throws Exception {Files.write(path,CanonicalJson.encode(value));}
 static Map<String,Object> view(VerifiedCheckpointService.View v){return Map.of("state",v.state(),"token",v.token(),"reason",v.reason(),"resultHash",v.resultHash(),"useHash",v.useHash(),"reviewable",v.reviewable(),"request",v.request());}
 static Map<String,String> goals(VerificationSnapshot snapshot){var out=new TreeMap<String,String>();var result=snapshot.result();if(result==null||result.semantic()==null)return out;
  var engine=CanonicalJson.object(result.semantic().observation().getOrDefault("originalEngine",Map.of()));
  for(var raw:(List<?>)engine.getOrDefault("contexts",List.of()))for(var value:CanonicalJson.object(CanonicalJson.object(raw).getOrDefault("occurrences",Map.of())).values()){
   var occurrence=CanonicalJson.object(value);out.put(Objects.toString(occurrence.get("sourceElement")),Objects.toString(occurrence.get("aggregateStatus")));
  }return out;
 }
 public static void main(String[] args)throws Exception {
  Path checkout=Path.of(args[0]).toRealPath(),root=Path.of(args[1]).toRealPath(),evidence=Path.of(args[2]).toAbsolutePath();Files.createDirectories(evidence);
  var manifest=CanonicalJson.object(CanonicalJson.decode(Files.readAllBytes(root.resolve("case-manifest.json"))));
  System.out.println("MANIFEST="+manifest);
  String prefix=root.getFileName().toString().equals("hello-world")?"hello":"house";
  String jcm=prefix.equals("hello")?"helloworld.jcm":"house-building.jcm";
  org.tzi.use.config.Options.processArgs(new String[]{"-H="+checkout.resolve("use-assembly/target/new"),"-nogui","-nr"});
  var session=new Session();var facade=new DefaultJaCaMoFacade(checkout,session);facade.verifiedCheckpointMode(true);
  var frame=new java.util.concurrent.atomic.AtomicReference<JFrame>();String status="NOT_STARTED";Throwable failure=null;
  var tokens=new LinkedHashSet<String>();var presented=new ArrayList<Object>();
  System.out.println("PLUGIN="+DefaultJaCaMoFacade.class.getProtectionDomain().getCodeSource().getLocation());
  try {
   facade.prepareOwnedProducer(root.resolve(jcm),new JaCaMoFacade.ImportSelection("JACAMO_DECLARATIVE_XML",root.resolve(prefix+"-mapping.xml"),root.resolve(prefix+".ocl"),root.resolve(prefix+".csl"),root.resolve(prefix+".istar"),false),null,false);
   status="MODEL_READY";var system=session.system();System.out.println("MODEL_READY producer="+facade.ownedProducerStatus());
   SwingUtilities.invokeAndWait(()->{var panel=JaCaMoWorkbenchPanel.authoredWorkflow(facade);var window=new JFrame("Verified diagnostic — "+root.getFileName());frame.set(window);window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);window.add(panel);window.setSize(1250,440);window.setVisible(true);panel.startRuntime();});
   long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(150);String prior="";
   while(System.nanoTime()<end){
    if(system!=session.system())throw new IllegalStateException("MSYSTEM_REPLACED");
    var v=facade.verifiedCheckpointState();String key=v.state()+":"+v.token()+":"+v.reason();
    if(!prior.equals(key)){System.out.println("CHECKPOINT "+key);prior=key;presented.add(view(v));}
    if(!v.token().isBlank())tokens.add(v.token());
    if(v.reviewable()||v.state().equals("HELD_UNVERIFIED")&&!v.reason().contains("WAITING")&&!v.reason().contains("DRAIN")){
     Thread.sleep(700);var held=facade.verifiedCheckpointState();if(!held.token().equals(v.token()))continue;
     status=held.state();write(evidence.resolve("held.json"),view(held));write(evidence.resolve("held-snapshot.json"),facade.verificationSnapshot().toMap());break;
    }
    var marking=goals(facade.verificationSnapshot());String terminal=prefix.equals("hello")?"TwelveMessagesObserved":"HouseRealized";
    if(marking.getOrDefault(terminal,"").equals("FULFILLED")&&v.state().equals("RUNNING")){status="NATIVE_OUTCOME_REACHED";break;}
    Thread.sleep(150);
   }
   if(status.equals("MODEL_READY"))status="TIMED_OUT";
   write(evidence.resolve("final-snapshot.json"),facade.verificationSnapshot().toMap());
  }catch(Throwable error){failure=error;status="ERROR";error.printStackTrace();}
  finally {
   try{facade.exportRuntimeReplay(evidence.resolve("replay"));}catch(Throwable error){Files.writeString(evidence.resolve("replay-error.txt"),error.toString());}
   write(evidence.resolve("probe.json"),Map.of("status",status,"case",root.toString(),"verifiedMode",facade.verifiedCheckpointMode(),"tokens",tokens.stream().toList(),"goalStatuses",goals(facade.verificationSnapshot()),"checkpoint",view(facade.verifiedCheckpointState()),"observedViews",presented,"error",failure==null?"":failure.toString(),"manualAcknowledgements",0));
   try{facade.stopOwnedProducer();}finally{facade.close();SwingUtilities.invokeAndWait(()->{if(frame.get()!=null)frame.get().dispose();});}
  }
  System.out.println("DIAGNOSTIC_RESULT="+status);System.exit(0);
 }
}
