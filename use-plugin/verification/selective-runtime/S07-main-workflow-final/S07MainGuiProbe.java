import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.lang.ref.WeakReference;
import javax.swing.*;
import org.tzi.use.config.Options;
import org.tzi.use.main.Session;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.MainPluginRuntime;
import org.tzi.use.runtime.impl.PluginRuntime;

/** Installed-host launcher and read-only sampler. No selection, click, import,
 * start, stop, restart or dispose actions: those are exercised via real GUI. */
public class S07MainGuiProbe {
 static java.io.PipedOutputStream shellKeepAlive;
 static Object field(Object o,String name)throws Exception {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static Object call(Object o,String name)throws Exception {return o.getClass().getMethod(name).invoke(o);}
 public static void main(String[] args)throws Exception {
  Path install=Path.of(args[0]).toRealPath(), evidence=Path.of(args[1]).toAbsolutePath();Files.createDirectories(evidence);
  Options.processArgs(new String[]{"-H="+install,"-nr"});MainPluginRuntime.run(install.resolve("lib/plugins"));
  var loader=((PluginRuntime)PluginRuntime.getInstance()).getPlugin("JaCaMo").getPluginClassLoader();
  loader.loadClass("org.tzi.use.semantic.engine.api.EngineInstallationGuard").getMethod("verify").invoke(null);
  Class<?> facadeClass=loader.loadClass("org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade"),json=loader.loadClass("org.jacamo.bridge.contract.CanonicalJson");
  var registry=facadeClass.getDeclaredField("SESSION_FACADES");registry.setAccessible(true);
  var session=new Session();
  // Match the host bootstrap: MainWindow's ordinary close delegates to Shell.
  org.tzi.use.main.shell.Shell.createInstance(session,PluginRuntime.getInstance());
  // Run the ordinary host Shell initialization with a test-owned idle input.
  // No commands are sent; this initializes readline for MainWindow.close().
  var shellInput=new java.io.PipedInputStream();shellKeepAlive=new java.io.PipedOutputStream(shellInput);System.setIn(shellInput);
  new Thread(org.tzi.use.main.shell.Shell.getInstance(),"USE-shell").start();
  SwingUtilities.invokeAndWait(()->{
   var left=Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()).map(d->d.getDefaultConfiguration().getBounds()).min(Comparator.comparingInt(r->r.x)).orElseThrow();
   if(left.x>=0)throw new IllegalStateException("Authorized left monitor missing");
   MainWindow.setJavaFxCall(true);var window=MainWindow.create(session,PluginRuntime.getInstance());MainWindow.setJavaFxCall(false);
   window.setBounds(left.x+15,left.y+15,Math.min(1280,left.width-30),Math.min(900,left.height-30));window.setVisible(true);
   System.out.println("LEFT_MAIN_READY pid="+ProcessHandle.current().pid()+" bounds="+window.getBounds());
  });
  var panels=new ArrayList<WeakReference<Component>>();
  var sampler=new Thread(()->{
   try {while(!Files.exists(evidence.resolve("sampler.stop"))){
    Thread.sleep(1500);var row=new TreeMap<String,Object>();row.put("at",java.time.Instant.now().toString());row.put("usePid",ProcessHandle.current().pid());
    SwingUtilities.invokeAndWait(()->{try {
     row.put("sessionListeners",((java.util.List<?>)field(session,"fListenerStateChange")).size());
     var ref=(WeakReference<?>)((Map<?,?>)registry.get(null)).get(session);Object facade=ref==null?null:ref.get();
     row.put("facade",facade==null?"ABSENT":System.identityHashCode(facade));
     if(facade!=null){row.put("facadeClosed",field(facade,"closed"));Object owner=call(facade,"ownedProducerStatus");
      for(String key:java.util.List.of("state","pid","runId"))row.put("producer."+key,call(owner,key));
      row.put("nativeSystem",session.hasSystem()?System.identityHashCode(session.system()):0);
      Object ws=field(facade,"nativeWorkspace");row.put("workspacePresent",ws!=null);
      if(ws!=null){Object projector=field(ws,"runtimeProjector");if(projector!=null){row.put("sameMSystem",call(projector,"system")==session.system());Object c=call(projector,"coordinator"),j=call(c,"journal");row.put("journal",call(j,"path").toString());}}
      if(!Boolean.TRUE.equals(row.get("facadeClosed"))){Object result=call(facade,"runtimeVerificationResult");if(result!=null){Object map=call(result,"toMap");Files.write(evidence.resolve("gui-result.json"),(byte[])json.getMethod("encode",Object.class).invoke(null,map));row.put("resultHash",((Map<?,?>)map).get("resultHash"));row.put("generation",((Map<?,?>)map).get("generation"));}}
     }
     var windows=new ArrayList<Object>();for(Window w:Window.getWindows())if(w.isDisplayable()) {windows.add(Map.of("title",w instanceof Dialog d?d.getTitle():w instanceof Frame f?f.getTitle():"", "x",w.getX(),"y",w.getY(),"showing",w.isShowing()));scan(w,panels);}
     row.put("windows",windows);var ps=new ArrayList<Object>();
     for(var weak:panels){var p=weak.get();if(p==null){ps.add(Map.of("collected",true));continue;}var timer=(javax.swing.Timer)field(p,"runtimeStatusTimer");ps.add(Map.of("id",System.identityHashCode(p),"displayable",p.isDisplayable(),"timerRunning",timer.isRunning(),"cslDetached",field(p,"cslView")==null,"istarDetached",field(p,"istarView")==null));}row.put("panels",ps);
    }catch(Exception e){row.put("probeError",e.toString());}});
    byte[] bytes=(byte[])json.getMethod("encode",Object.class).invoke(null,row);Files.write(evidence.resolve("probe.json"),bytes);Files.write(evidence.resolve("probe-history.jsonl"),bytes,StandardOpenOption.CREATE,StandardOpenOption.APPEND);Files.writeString(evidence.resolve("probe-history.jsonl"),"\n",StandardOpenOption.APPEND);
   }}catch(Throwable e){e.printStackTrace();}
  },"read-only-main-workflow-probe");sampler.setDaemon(true);sampler.start();
 }
 static void scan(Container root,java.util.List<WeakReference<Component>> panels){for(Component c:root.getComponents()){if(c.getClass().getName().equals("org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel")&&panels.stream().noneMatch(w->w.get()==c))panels.add(new WeakReference<>(c));if(c instanceof Container child)scan(child,panels);}}
}
