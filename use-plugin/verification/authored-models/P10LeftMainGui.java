import java.awt.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import org.tzi.use.config.Options;
import org.tzi.use.main.Session;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.MainPluginRuntime;
import org.tzi.use.runtime.impl.PluginRuntime;

/** Qualification launcher: installed host APIs only; no input selection or runtime action.
 * Initial placement keeps the user's right monitor free. All actions use the real GUI. */
public class P10LeftMainGui {
 public static void main(String[] args)throws Exception {
  Path install=Path.of(args[0]).toRealPath(),evidence=Path.of(args[1]).toAbsolutePath();
  Files.createDirectories(evidence);
  Options.processArgs(new String[]{"-H="+install,"-nr"});
  MainPluginRuntime.run(install.resolve("lib/plugins"));
  var descriptor=((PluginRuntime)PluginRuntime.getInstance()).getPlugin("JaCaMo");
  if(descriptor==null)throw new IllegalStateException("Installed plugin missing");
  var loader=descriptor.getPluginClassLoader();
  loader.loadClass("org.tzi.use.semantic.engine.api.EngineInstallationGuard").getMethod("verify").invoke(null);
  if(loader.loadClass("org.tzi.use.uml.sys.MSystem")!=org.tzi.use.uml.sys.MSystem.class)throw new IllegalStateException("Duplicate host state class");
  var session=new Session();
  SwingUtilities.invokeAndWait(()->{
   Rectangle left=Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()).map(d->d.getDefaultConfiguration().getBounds()).min(Comparator.comparingInt(r->r.x)).orElseThrow();
   if(left.x>=0||left.width<1100)throw new IllegalStateException("The authorized left monitor is unavailable");
   MainWindow.setJavaFxCall(true);
   var window=MainWindow.create(session,PluginRuntime.getInstance());
   MainWindow.setJavaFxCall(false);
   int width=Math.min(1280,left.width-30),height=Math.min(900,left.height-30);
   window.setBounds(left.x+(left.width-width)/2,left.y+15,width,height);
   window.setVisible(true);
   System.out.println("LEFT_MAIN_USE_READY pid="+ProcessHandle.current().pid()+" bounds="+window.getBounds()+" install="+install);
  });
  var sampler=new Thread(()->{
   Object facade=null;String previous="";
   try {
    while(!Files.exists(evidence.resolve("sampler.stop"))){
     Thread.sleep(1500);
     if(!session.hasSystem())continue;
     if(facade==null)facade=loader.loadClass("org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade").getMethod("forSession",Session.class).invoke(null,session);
     Object result=facade.getClass().getMethod("runtimeVerificationResult").invoke(facade);
     if(result==null)continue;
     var map=(Map<?,?>)result.getClass().getMethod("toMap").invoke(result);
     String hash=Objects.toString(map.get("resultHash"),"");
     if(!hash.isBlank()&&!hash.equals(previous)){
      byte[] bytes=(byte[])loader.loadClass("org.jacamo.bridge.contract.CanonicalJson").getMethod("encode",Object.class).invoke(null,map);
      Files.write(evidence.resolve("gui-result.json"),bytes);previous=hash;
     }
     Object owner=facade.getClass().getMethod("ownedProducerStatus").invoke(facade);
     var metrics=new Properties();metrics.setProperty("usePid",Long.toString(ProcessHandle.current().pid()));
     for(String field:java.util.List.of("state","pid","runId","diagnostic"))metrics.setProperty("producer."+field,Objects.toString(owner.getClass().getMethod(field).invoke(owner)));
     metrics.setProperty("nativeSystemIdentity",Integer.toString(System.identityHashCode(session.system())));
     var workspaceField=facade.getClass().getDeclaredField("nativeWorkspace");workspaceField.setAccessible(true);Object workspace=workspaceField.get(facade);
     if(workspace!=null){var projectorField=workspace.getClass().getDeclaredField("runtimeProjector");projectorField.setAccessible(true);Object projector=projectorField.get(workspace);
      if(projector!=null){metrics.setProperty("sameMSystem",Boolean.toString(projector.getClass().getMethod("system").invoke(projector)==session.system()));
       Object coordinator=projector.getClass().getMethod("coordinator").invoke(projector),journal=coordinator.getClass().getMethod("journal").invoke(coordinator);
       metrics.setProperty("journal",Objects.toString(journal.getClass().getMethod("path").invoke(journal)));}
     }
     Object models=facade.getClass().getMethod("semanticModels").invoke(facade);
     if(models!=null)metrics.setProperty("sameMModel",Boolean.toString(models.getClass().getMethod("nativeModel").invoke(models)==session.system().model()));
     try(var out=Files.newOutputStream(evidence.resolve("gui-owner.properties"))){metrics.store(out,"Read-only qualification sampler; no runtime/UI action");}
    }
   }catch(Throwable error){error.printStackTrace();}
  },"read-only-gui-qualification");
  sampler.setDaemon(true);sampler.start();
 }
}
