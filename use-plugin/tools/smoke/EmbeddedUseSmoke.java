import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.tzi.use.config.Options;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.*;
import org.tzi.use.runtime.gui.*;
import org.tzi.use.runtime.impl.PluginRuntime;

/** Cold packaged USE check. Requires a display; all application classes come from the installation. */
public final class EmbeddedUseSmoke {
    private static MainWindow owner;
    private static ClassLoader pluginLoader;
    private static final AtomicReference<Throwable> asynchronousFailure=new AtomicReference<>();
    public static void main(String[] args)throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{error.printStackTrace();asynchronousFailure.compareAndSet(null,error);});
        Path installation=Path.of(args[0]).toAbsolutePath(),evidence=Path.of(args[1]).toAbsolutePath();Files.createDirectories(evidence);
        boolean counter=args.length>2&&args[2].equals("counter");
        Options.processArgs(new String[]{"-H="+installation,"-nr","-it"});
        var runtime=MainPluginRuntime.run(installation.resolve("lib/plugins"));
        var plugins=PluginRuntime.getInstance().getPlugins();
        for(String name:List.of("CSL Model","OAL State","iStar Model","iStarSCN State","JaCaMo"))
            require(plugins.values().stream().anyMatch(p->p.getPluginModel().getName().equals(name)),"Missing plugin "+name);
        var bridge=plugins.values().stream().filter(p->p.getPluginModel().getName().equals("JaCaMo")).findFirst().orElseThrow();
        var loader=bridge.getPluginClassLoader();pluginLoader=loader.loadClass("org.tzi.use.plugins.jacamo.feature.bridge.JaCaMoExperimentAction").getClassLoader();
        var guard=pluginLoader.loadClass("org.tzi.use.plugins.jacamo.verify.api.EngineInstallationGuard");guard.getMethod("verify").invoke(null);
        System.out.println("PACKAGED_PLUGINS_OK engine="+guard.getProtectionDomain().getCodeSource().getLocation());
        var session=new Session();SwingUtilities.invokeAndWait(()->{owner=MainWindow.create(session,runtime);owner.setSize(1600,980);owner.setLocation(20,20);});
        var action=(IPluginActionDelegate)loader.loadClass("org.tzi.use.plugins.jacamo.feature.bridge.JaCaMoExperimentAction").getConstructor().newInstance();
        IPluginAction context=new IPluginAction(){public void actionPerformed(java.awt.event.ActionEvent e){}public MainWindow getParent(){return owner;}public Session getSession(){return session;}};
        try {
            action.performAction(context);
            waitFor(()->named("bridge-project")!=null,10,"run form");
            Path example=installation.resolve(counter?"examples/counter":"examples/auction");
            Path models=example;String base=counter?"counter":"auction";
            SwingUtilities.invokeAndWait(()->{
                ((JTextField)named("bridge-project")).setText(example.resolve(base+".jcm").toString());
                ((JTextField)named("bridge-csl")).setText(models.resolve(base+".csl").toString());
                ((JTextField)named("bridge-istar")).setText(models.resolve(base+".istar").toString());
                ((JTextField)named("bridge-mapping")).setText(models.resolve("attribute-mapping.xml").toString());
                for(Window window:Window.getWindows())if(window instanceof JDialog&&window.isVisible()){var run=button(window,"Run");if(run!=null){run.doClick();break;}}
            });
            waitFor(()->named("jacamo-evaluation-view")!=null,10,"embedded experiment view");
            var view=(JPanel)onEdt(()->named("jacamo-evaluation-view"));
            waitFor(()->runDirectory(view)!=null,20,"producer startup");Path work=onEdt(()->runDirectory(view));
            waitFor(()->completed(work,counter),35,"source verification");
            var latest=readReport(work);var response=object(latest.get("response"));var request=object(latest.get("request"));
            require(request.containsKey("sourceState"),"No source inspector state");
            var result=object(response.get("result"));
            require(!counter||response.get("decision").equals("HOLD_FAILURE"),"Counter must be held");
            if(!counter)require(completedGoals(result)==2,"Both Auction graphs must complete");
            var split=(JSplitPane)onEdt(()->find(view,c->c instanceof JSplitPane&&"jacamo-use-split".equals(c.getName())));
            require(split.getOrientation()==JSplitPane.HORIZONTAL_SPLIT&&split.getLeftComponent()!=null&&split.getRightComponent()!=null,"Missing requested side-by-side layout");
            SwingUtilities.invokeAndWait(()->selectTab(view,"OAL State"));capture(evidence.resolve("embedded-oal.png"));
            SwingUtilities.invokeAndWait(()->{selectTab(view,"Agents");selectTab(view,"iStarSCN");});capture(evidence.resolve("embedded-istar.png"));
            exportState(evidence.resolve("checkpoint.oal"),models.resolve(base+".csl"),"OAL",result);
            exportState(evidence.resolve("checkpoint.iscn"),models.resolve(base+".istar"),"ISTAR_SCN",result);
            Files.copy(work.resolve("latest.json"),evidence.resolve("latest.json"),StandardCopyOption.REPLACE_EXISTING);
            Files.copy(work.resolve("producer.log"),evidence.resolve("producer.log"),StandardCopyOption.REPLACE_EXISTING);
            for(var entry:List.of(new Tool("CslModelAction",models.resolve(base+".csl"),"CSL —"),new Tool("IStarModelAction",models.resolve(base+".istar"),"ISTAR —"),new Tool("OalModelAction",evidence.resolve("checkpoint.oal"),"OAL —"),new Tool("IStarScnAction",evidence.resolve("checkpoint.iscn"),"ISTAR_SCN —"))) {
                var tool=(IPluginActionDelegate)pluginLoader.loadClass("org.tzi.use.plugins.jacamo.feature."+(entry.action().startsWith("Csl")?"csl":entry.action().startsWith("Oal")?"oal":entry.action().equals("IStarScnAction")?"istarscn":"istar")+"."+entry.action()).getConstructor().newInstance();tool.performAction(context);
                waitFor(()->chooser()!=null,8,"file chooser for "+entry.action());SwingUtilities.invokeAndWait(()->{var choose=chooser();choose.setSelectedFile(entry.file().toFile());choose.approveSelection();});
                waitFor(()->internalFrame(entry.title())!=null,10,"independent plugin "+entry.action());
                SwingUtilities.invokeAndWait(()->internalFrame(entry.title()).doDefaultCloseAction());
            }
            long pid=producerPid(view);
            SwingUtilities.invokeAndWait(()->{var frame=internalFrame("JaCaMo —");require(frame!=null,"Embedded USE frame disappeared");frame.doDefaultCloseAction();});
            waitFor(()->ProcessHandle.of(pid).isEmpty()||!ProcessHandle.of(pid).orElseThrow().isAlive(),8,"owned producer shutdown");
            require(!Files.exists(work.resolve("bridge-secret.hex")),"Run credential survived shutdown");
            require(asynchronousFailure.get()==null,"Swing error: "+asynchronousFailure.get());
            Files.writeString(evidence.resolve("result.txt"),"PASS\ncase="+(counter?"counter_violation":"auction_valid")+"\nplugins=5\nindependent_model_opens=4\nowned_producer_stopped=true\ndecision="+response.get("decision")+"\ncompleted_auctions="+completedGoals(result)+"\n");
            System.out.println("EMBEDDED_USE_SMOKE_PASS case="+(counter?"counter":"auction")+" evidence="+evidence);
        }finally{
            SwingUtilities.invokeAndWait(()->{var frame=internalFrame("JaCaMo —");if(frame!=null)frame.doDefaultCloseAction();for(Window window:Window.getWindows())window.dispose();});
        }
        System.exit(0);
    }
    private record Tool(String action,Path file,String title){}
    private static Map<String,Object> object(Object value){return (Map<String,Object>)value;}
    private static Map<String,Object> readReport(Path work)throws Exception{return object(pluginLoader.loadClass("org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson").getMethod("decode",byte[].class).invoke(null,(Object)Files.readAllBytes(work.resolve("latest.json"))));}
    private static long completedGoals(Map<String,Object> result){if(!(result.get("elements") instanceof List<?> list))return 0;return list.stream().map(EmbeddedUseSmoke::object).filter(e->e.get("element").equals("AuctionCompleted")&&e.get("status").equals("FULFILLED")).count();}
    private static boolean completed(Path work,boolean counter){try{var response=object(readReport(work).get("response"));return counter?response.get("decision").equals("HOLD_FAILURE"):completedGoals(object(response.get("result")))==2;}catch(Exception ignored){return false;}}
    private static Path runDirectory(Container view){var label=(JLabel)find(view,c->c instanceof JLabel&&((JLabel)c).getToolTipText()!=null&&((JLabel)c).getToolTipText().startsWith("Run evidence: "));return label==null?null:Path.of(label.getToolTipText().substring(14));}
    private static long producerPid(JPanel view)throws Exception{var field=view.getClass().getDeclaredField("session");field.setAccessible(true);Object session=field.get(view);return (Long)session.getClass().getMethod("producerPid").invoke(session);}
    private static void exportState(Path file,Path model,String kind,Map<String,Object> result)throws Exception {
        Class<?> document=pluginLoader.loadClass("org.tzi.use.plugins.jacamo.dsl.state.ModelStateDocument"),kindType=pluginLoader.loadClass("org.tzi.use.plugins.jacamo.dsl.state.ModelStateDocument$Kind"),marking=pluginLoader.loadClass("org.tzi.use.plugins.jacamo.dsl.state.ModelStateDocument$Marking"),state=pluginLoader.loadClass("org.tzi.use.plugins.jacamo.dsl.oal.mm.AcceptedAolState");
        Object objects=document.getMethod("objects",List.class).invoke(null,result.get("state"));var markings=new ArrayList<Object>();
        for(Object raw:(List<?>)result.get("elements")){var e=object(raw);markings.add(marking.getConstructor(String.class,String.class,String.class,String.class).newInstance(e.get("context"),e.get("actor"),e.get("element"),e.get("status")));}
        Object instance=document.getConstructor(kindType,Path.class,String.class,state,List.class).newInstance(Enum.valueOf((Class)kindType,kind),model,result.get("sequence").toString(),objects,markings);document.getMethod("write",Path.class).invoke(instance,file);
    }
    private static void capture(Path file)throws Exception{SwingUtilities.invokeAndWait(()->{try{var image=new BufferedImage(owner.getWidth(),owner.getHeight(),BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();owner.paint(graphics);graphics.dispose();ImageIO.write(image,"png",file.toFile());}catch(Exception e){throw new RuntimeException(e);}});}
    private static void selectTab(Container root,String title){find(root,c->{if(c instanceof JTabbedPane tabs)for(int i=0;i<tabs.getTabCount();i++)if(tabs.getTitleAt(i).equals(title)){tabs.setSelectedIndex(i);return true;}return false;});}
    private static JInternalFrame internalFrame(String title){return owner==null?null:(JInternalFrame)find(owner,c->c instanceof JInternalFrame frame&&!frame.isClosed()&&frame.getTitle().startsWith(title));}
    private static JFileChooser chooser(){for(Window window:Window.getWindows())if(window.isVisible()){var found=find(window,c->c instanceof JFileChooser);if(found!=null)return (JFileChooser)found;}return null;}
    private static Component named(String name){for(Window window:Window.getWindows())if(window.isVisible()){var found=find(window,c->name.equals(c.getName()));if(found!=null)return found;}return null;}
    private static JButton button(Container root,String text){return (JButton)find(root,c->c instanceof JButton b&&b.getText().equals(text));}
    private static Component find(Component root,java.util.function.Predicate<Component> matches){if(matches.test(root))return root;if(root instanceof Container container)for(Component child:container.getComponents()){var result=find(child,matches);if(result!=null)return result;}return null;}
    private static <T>T onEdt(java.util.concurrent.Callable<T> action)throws Exception{var value=new AtomicReference<T>();var failure=new AtomicReference<Exception>();SwingUtilities.invokeAndWait(()->{try{value.set(action.call());}catch(Exception e){failure.set(e);}});if(failure.get()!=null)throw failure.get();return value.get();}
    private static void waitFor(BooleanSupplier condition,int seconds,String label)throws Exception {long until=System.nanoTime()+seconds*1_000_000_000L;while(System.nanoTime()<until){if(onEdt(condition::getAsBoolean))return;if(asynchronousFailure.get()!=null)throw new AssertionError("Asynchronous UI failure",asynchronousFailure.get());Thread.sleep(100);}throw new AssertionError("Timeout: "+label);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
