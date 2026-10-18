package org.tzi.use.plugins.jacamo.semantics;

import java.awt.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;
import javax.swing.*;
import org.tzi.use.main.Session;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.MainPluginRuntime;
import org.tzi.use.runtime.impl.PluginRuntime;

/** Cold installed consumer. Linked only to JDK + installed USE host, never test/framework/plugin classes. */
public final class InstalledEngineMain {
    private static org.tzi.use.runtime.IPluginClassLoader loader;
    private InstalledEngineMain() { }
    public static void main(String[] args) throws Exception {
        try {run(args);} catch(Throwable failure) {failure.printStackTrace();System.exit(1);}
    }
    private static void run(String[] args) throws Exception {
        Path install=Path.of(args[0]).toRealPath();MainPluginRuntime.run(install.resolve("lib/plugins"));
        var plugin=((PluginRuntime)PluginRuntime.getInstance()).getPlugin("JaCaMo");check(plugin!=null,"Plugin discovery");
        loader=plugin.getPluginClassLoader();
        Object guard=call(loader.loadClass("org.tzi.use.semantic.engine.api.EngineInstallationGuard"),"verify");
        if(args[1].equals("guard")){System.out.println("INSTALLED_ENGINE_GUARD_PASS "+guard);return;}
        if(args[1].equals("profile")){call(loader.loadClass("org.tzi.use.plugins.jacamo.semantics.mapping.MappingRuleSet"),"read",install.resolve("examples/csl-counter/mapping.json"));System.out.println("INSTALLED_PROFILE_PASS");return;}
        var config=new Properties();try(var input=Files.newInputStream(Path.of(args[1]))){config.load(input);}
        for(String key:config.stringPropertyNames())if(key.startsWith("use."))System.setProperty(key,config.getProperty(key));
        check(System.getProperty("use.jacamo.runtime.journal.maxBytes")==null,"Default production journal must be tested");
        Path evidence=Path.of(config.getProperty("evidence")),jcm=Path.of(config.getProperty("jcm"));
        Path installedJar=install.resolve("lib/plugins/use-jacamo-plugin-1.0.1.jar").toRealPath();
        var classEvidence=new TreeMap<String,String>();
        for(String name:List.of("org.vnu.sme.goal.dsl.csl.mm.CslModel","org.vnu.sme.goal.dsl.istar.mm.GoalModel",
                "org.vnu.sme.goal.verify.conformance.semantics.GoalMarking","org.vnu.sme.goal.verify.conformance.semantics.TaskMarking",
                "org.vnu.sme.goal.verify.conformance.semantics.IStarMarking","org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation")) {
            var type=loader.loadClass(name);String origin=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().toString();
            check(origin.equals(installedJar.toString()),"Installed original engine origin: "+origin);classEvidence.put(name,origin);
        }
        for(Class<?> hostType:List.of(org.tzi.use.uml.sys.MSystem.class,org.tzi.use.uml.mm.MModel.class,
                org.tzi.use.parser.ocl.OCLCompiler.class,org.tzi.use.uml.ocl.expr.Evaluator.class))
            check(loader.loadClass(hostType.getName())==hostType,"Host class ownership "+hostType);
        for(String forbidden:List.of("cartago.Artifact","jacamo.infra.JaCaMoLauncher","jason.asSemantics.Agent"))
            try{loader.loadClass(forbidden);throw new AssertionError("Framework leaked: "+forbidden);}catch(ClassNotFoundException expected) { }
        var errors=new CopyOnWriteArrayList<String>();var delays=new CopyOnWriteArrayList<Long>();var session=new Session();
        Object facade=loader.loadClass("org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade").getConstructor(Path.class,Session.class).newInstance(install,session);
        MainWindow.setJavaFxCall(true);System.setProperty("use.gui.view.classdiagram.class.minheight","40");System.setProperty("use.gui.view.classdiagram.class.minwidth","140");
        var window=edt(()->MainWindow.create(session,PluginRuntime.getInstance()));
        var frame=edt(()->new JFrame("Installed CSL/iStar + native USE 7.5 acceptance"));
        var constructor=loader.loadClass("org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel").getDeclaredConstructor(loader.loadClass("org.tzi.use.plugins.jacamo.JaCaMoFacade"),Consumer.class);
        constructor.setAccessible(true);var panel=edt(()->(JPanel)constructor.newInstance(facade,(Consumer<String>)errors::add));
        long[] previous={System.nanoTime()};var timer=edt(()->new javax.swing.Timer(50,event->{long now=System.nanoTime();delays.add(Math.max(0,now-previous[0]-TimeUnit.MILLISECONDS.toNanos(50)));previous[0]=now;}));
        try {
            edt(()->{frame.setContentPane(panel);frame.setSize(1500,1000);frame.setVisible(true);timer.start();
                component(panel,"engine-profile",JComboBox.class).setSelectedItem("USE4GOAL_OBSERVED_V1");
                component(panel,"mapping-specification",JTextField.class).setText(config.getProperty("mapping"));call(panel,"importProject",jcm);return null;});
            enabled(panel,"start-runtime",errors);var system=session.system();check(system!=null,"MModel/MSystem import");
            Object program=call(field(field(facade,"nativeWorkspace"),"canonical"),"program");
            Object csl=call(program,"cslModel"),goal=call(program,"goalModel");
            check(csl.getClass()==loader.loadClass("org.vnu.sme.goal.dsl.csl.mm.CslModel"),"Real CslModel M1");
            check(loader.loadClass("org.vnu.sme.goal.dsl.istar.mm.GoalModel").isInstance(goal),"Real GoalModel M1");
            Object frontend=loader.loadClass("org.tzi.use.semantic.engine.frontend.CslFrontend").getConstructor().newInstance();
            check(Boolean.TRUE.equals(call(call(frontend,"validate",csl,"installed"),"ok")),"Original CSL validator");
            check(((List<?>)call(loader.loadClass("org.vnu.sme.goal.dsl.istar.mm.GoalModelValidator"),"validate",goal)).isEmpty(),"Original iStar validator");
            click(panel,"start-runtime");await(()->Files.exists(Path.of(config.getProperty("ready"))),errors);
            enabled(panel,"pause-runtime",errors);click(panel,"pause-runtime");enabled(panel,"resume-runtime",errors);
            Object projector=field(field(facade,"nativeWorkspace"),"runtimeProjector"),coordinator=call(projector,"coordinator");settle(coordinator);
            check(call(projector,"system")==system&&session.system()==system,"Single authoritative MSystem");
            var initial=map(call(call(facade,"verificationSnapshot"),"toMap"));write(evidence.resolve("initialized.json"),initial);
            check(initial.toString().contains("alice")&&initial.toString().contains("bob"),"Two live agents");
            var kinds=new TreeSet<String>();
            try(var lines=Files.lines((Path)call(call(coordinator,"journal"),"path"))) {
                for(String line:lines.toList()) {
                    var entry=map(call(loader.loadClass("org.jacamo.bridge.contract.CanonicalJson"),"decode",(Object)line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    if("SNAPSHOT".equals(entry.get("kind")))for(Object fact:(List<?>)map(entry.get("payload")).getOrDefault("facts",List.of()))kinds.add((String)map(fact).get("kind"));
                }
            }
            check(kinds.containsAll(List.of("AGENT","ARTIFACT","GROUP_BOARD","SCHEME_BOARD")),"Official accepted observation kinds "+kinds);
            check(!contexts(call(facade,"runtimeVerificationResult")).isEmpty(),"Actual role/player/context binding");
            edt(()->{call(facade,"showObjectDiagram");check(window.getObjectDiagrams().getLast().system()==system,"Native object diagram state");return null;});
            Files.writeString(Path.of(config.getProperty("release")),"run real Counter");click(panel,"resume-runtime");
            await(()->{Object image=call(call(facade,"verificationSnapshot"),"image");if(image==null)return false;
                for(Object value:map(call(image,"objects")).values())if("Counter".equals(call(value,"className"))&&"-1".equals(map(call(value,"attributes")).get("p")))return true;return false;},errors);
            enabled(panel,"pause-runtime",errors);click(panel,"pause-runtime");enabled(panel,"resume-runtime",errors);settle(coordinator);call(facade,"runFullVerification");settle(coordinator);
            Object accepted=call(facade,"runtimeVerificationResult");String state=(String)call(accepted,"stateHash"),result=(String)call(accepted,"resultHash");
            check("CURRENT_OBSERVED".equals(call(accepted,"freshness")),"Current accepted cut");
            check("OBSERVED_PREFIX".equals(map(map(call(call(accepted,"semantic"),"observation")).get("originalEngine")).get("historyQuality")),"Live engine history must have no capacity gap");
            var history=(List<?>)call(facade,"runtimeVerificationHistory");var calls=new TreeSet<String>();var invocations=new ArrayList<Object>();
            for(Object cut:history) {
                Object semantic=call(cut,"semantic");if(semantic==null)continue;
                for(Object row:(List<?>)call(semantic,"constraints"))if("INVOCATION_CONFORMANCE".equals(call(row,"kind")))invocations.add(row);
                for(var context:contexts(cut))for(Object name:(List<?>)context.getOrDefault("originalApiCalls",List.of()))calls.add(name.toString());
            }
            String expected=config.getProperty("step").equals("1")?"PASS":"FAIL";
            boolean exactInvocation=false;
            for(Object invocation:invocations) {
                var witness=map(call(invocation,"witness"));
                if(expected.equals(call(invocation,"verdict").toString())&&"0".equals(witness.get("before.p"))&&config.getProperty("step").equals(witness.get("after.p"))) {
                    check("PASS".equals(witness.get("preVerdict"))&&expected.equals(witness.get("postVerdict")),"Real native @pre pre/post tuple");exactInvocation=true;
                }
            }
            check(exactInvocation,"Missing exact native @pre "+expected+": "+invocations);
            boolean invariantFailed=false;for(Object outcome:(List<?>)call(accepted,"outcomes"))if(call(outcome,"constraintId").toString().startsWith("NATIVE:")&&"FAIL".equals(call(outcome,"outcome").toString()))invariantFailed=true;
            check(invariantFailed,"Negative p must fail native invariant");
            check(calls.containsAll(List.of("GoalMarking.update","TaskMarking.update","IStarPropagation.closePending","GoalModelInstanceTranslator.propagateDependencies")),"Original calls "+calls);
            status(accepted,"all","FULFILLED");status(accepted,"any","FULFILLED");status(accepted,"left","FULFILLED");status(accepted,"increment",expected.equals("PASS")?"FULFILLED":"PENDING");
            check(map(call(call(accepted,"semantic"),"requirements")).isEmpty(),"No local recurrence authority");
            long limit=((Number)call(loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationCoordinator"),"defaultJournalMaxBytes")).longValue();
            Object journal=call(coordinator,"journal");long bytes=((Number)call(journal,"persistedBytes")).longValue();check(limit==67108864L&&bytes<limit&&!Boolean.TRUE.equals(call(journal,"hasGap")),"Production journal budget "+bytes);
            edt(()->{call(panel,"refreshRuntime");JTable historyTable=component(panel,"semantic-original-history",JTable.class);check(historyTable.getRowCount()>0,"Original marking UI");
                // Show the verified original-engine table in the durable installed GUI evidence.
                for(Component selected=historyTable,parent=selected.getParent();parent!=null;selected=parent,parent=parent.getParent())
                    if(parent instanceof JTabbedPane tabs&&tabs.indexOfComponent(selected)>=0)tabs.setSelectedComponent(selected);
                check("true".equals(org.tzi.use.api.UseSystemApi.create(system,false).evaluate("Counter.allInstances()->forAll(c | c.p = -1)").toString()),"Native manual OCL");
                var image=new java.awt.image.BufferedImage(frame.getWidth(),frame.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();frame.printAll(graphics);graphics.dispose();javax.imageio.ImageIO.write(image,"png",evidence.resolve("installed-workbench.png").toFile());return null;});
            Path replay=evidence.resolve("replay");call(facade,"exportRuntimeReplay",replay);write(evidence.resolve("accepted.json"),call(accepted,"toMap"));
            var cycles=new ArrayList<Object>();
            for(int i=0;i<2;i++) {
                Object oldOwner=call(coordinator,"originalEngine");var oldSystem=session.system();
                click(panel,"stop-monitor");enabled(panel,"restart-monitor",errors);
                await(()->Boolean.TRUE.equals(map(call(oldOwner,"metrics")).get("terminated")),errors);
                var metrics=map(call(oldOwner,"metrics"));check(((Number)metrics.get("expressions")).intValue()==0&&((Number)metrics.get("pendingPins")).intValue()==0,"Old cache/pins cleanup");
                await(()->threads("use-original-engine-")==0&&threads("jacamo-bridge-tcp-subscription")==0,errors);
                check(session.system()==oldSystem&&ProcessHandle.of(Long.parseLong(config.getProperty("producerPid"))).orElseThrow().isAlive(),"Stop monitor must preserve USE/producer");
                check("true".equals(org.tzi.use.api.UseSystemApi.create(session.system(),false).evaluate("1 + 1 = 2").toString()),"Native OCL after Stop");
                click(panel,"restart-monitor");enabled(panel,"stop-monitor",errors);projector=field(field(facade,"nativeWorkspace"),"runtimeProjector");coordinator=call(projector,"coordinator");settle(coordinator);
                check(session.system()!=oldSystem&&call(coordinator,"originalEngine")!=oldOwner,"Fresh monitor ownership");status(call(facade,"runtimeVerificationResult"),"left","PENDING");
                check(threads("use-original-engine-")==1&&threads("jacamo-bridge-tcp-subscription")==1,"One active engine/subscription");cycles.add(metrics);
            }
            // .stopMAS executes in the real producer. USE and its native OCL remain alive.
            call(facade,"resumeRuntime");Files.writeString(Path.of(config.getProperty("stopMas")),"allow .stopMAS");
            await(()->!ProcessHandle.of(Long.parseLong(config.getProperty("producerPid"))).map(ProcessHandle::isAlive).orElse(false),errors);
            await(()->"STALE".equals(call(call(facade,"runtimeVerificationResult"),"freshness")),errors);
            check(session.hasSystem()&&ProcessHandle.current().isAlive(),"Producer shutdown closed USE");
            check("true".equals(org.tzi.use.api.UseSystemApi.create(session.system(),false).evaluate("1 + 1 = 2").toString()),"USE OCL after .stopMAS");
            call(facade,"stopMonitorSession");call(facade,"openStepReplay",replay);
            while(!call(call(facade,"stepReplayStatus"),"step").equals(call(call(facade,"stepReplayStatus"),"total")))call(facade,"nextStepReplay");
            check(result.equals(call(call(facade,"runtimeVerificationResult"),"resultHash")),"Packaged live/replay result parity");check(session.system().isReadOnly(),"Replay state is read-only");
            call(facade,"previousStepReplay");call(facade,"nextStepReplay");check(result.equals(call(call(facade,"runtimeVerificationResult"),"resultHash")),"Previous/Next replay parity");
            call(facade,"resetStepReplay");check(((Number)call(call(facade,"stepReplayStatus"),"step")).intValue()==0,"Replay reset");
            check(errors.isEmpty(),errors.toString());var sorted=delays.stream().sorted().toList();check(sorted.size()>=20,"Heartbeat sample count");double p95=sorted.get((int)Math.ceil(sorted.size()*.95)-1)/1e6;check(p95<=250,"EDT p95 delay "+p95);
            var children=new ArrayList<Map<String,Object>>();
            for(var child:ProcessHandle.current().children().toList()) {
                String executable=child.info().command().orElse("UNAVAILABLE");
                String console=Path.of(System.getenv().getOrDefault("SystemRoot","C:/Windows"),"System32","conhost.exe").toString();
                check(System.getProperty("os.name").startsWith("Windows")&&console.equalsIgnoreCase(executable),"Unexpected USE child process: "+child.pid()+" "+executable);
                children.add(Map.of("pid",child.pid(),"executable",executable,"kind","WINDOWS_CONSOLE_HOST"));
            }
            var receipt=new TreeMap<String,Object>();receipt.put("status","PASS");receipt.put("consumerPid",ProcessHandle.current().pid());receipt.put("producerPid",Long.parseLong(config.getProperty("producerPid")));receipt.put("consumerChildren",children);receipt.put("runtimeChildProcesses",0);
            receipt.put("classpath",System.getProperty("java.class.path"));receipt.put("engineClasses",classEvidence);receipt.put("guard",guard);receipt.put("originalCalls",calls);receipt.put("nativeInvocation",expected);receipt.put("stateHash",state);receipt.put("resultHash",result);receipt.put("programHash",call(program,"hash"));receipt.put("journalBytes",bytes);receipt.put("journalLimit",limit);receipt.put("monitorCycles",cycles);receipt.put("heartbeatP95Millis",p95);receipt.put("replayParity",true);receipt.put("stopMasUseSurvives",true);
            write(evidence.resolve("consumer-receipt.json"),receipt);System.out.println("INSTALLED_ORIGINAL_ENGINE_LIVE_PASS "+expected);
        }catch(Exception|Error failure) {
            try {
                write(evidence.resolve("failure-snapshot.json"),call(call(facade,"verificationSnapshot"),"toMap"));
                Object active=field(field(facade,"nativeWorkspace"),"runtimeProjector");Path journal=(Path)call(call(call(active,"coordinator"),"journal"),"path");
                if(Files.isRegularFile(journal))Files.copy(journal,evidence.resolve("failure-runtime.jsonl"));
                Files.writeString(evidence.resolve("failure-status.txt"),call(facade,"status")+"\n"+call(facade,"runtimeControlState"));
            }catch(Exception diagnostic){failure.addSuppressed(diagnostic);}
            throw failure;
        }finally {
            try{call(facade,"close");}finally{edt(()->{timer.stop();frame.dispose();window.getObjectDiagrams().forEach(v->v.detachModel());window.dispose();MainWindow.setJavaFxCall(false);return null;});javafx.application.Platform.exit();}
        }
    }
    private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Object call(Object target,String name,Object... args)throws Exception {
        Class<?> type=target instanceof Class<?> c?c:target.getClass();
        for(Method method:type.getMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length) {
            boolean matches=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!method.getParameterTypes()[i].isInstance(args[i]))matches=false;
            if(!matches)continue;
            try{return method.invoke(target instanceof Class<?>?null:target,args);}catch(InvocationTargetException error){var cause=error.getCause();if(cause instanceof Exception e)throw e;if(cause instanceof Error e)throw e;throw error;}
        }
        throw new NoSuchMethodException(type+"."+name+"/"+args.length);
    }
    private static void settle(Object coordinator)throws Exception{((CompletableFuture<?>)call(coordinator,"originalEngineDrained")).get(30,TimeUnit.SECONDS);call(coordinator,"read",(Callable<Object>)()->null);}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    private static List<Map<String,Object>> contexts(Object result)throws Exception{Object semantic=call(result,"semantic");if(semantic==null)return List.of();Object core=map(call(semantic,"observation")).get("originalEngine");if(core==null)return List.of();return ((List<?>)map(core).getOrDefault("contexts",List.of())).stream().map(InstalledEngineMain::map).toList();}
    private static void status(Object result,String source,String expected)throws Exception{for(var context:contexts(result))for(Object raw:map(context.getOrDefault("occurrences",Map.of())).values()){var row=map(raw);if(source.equals(row.get("sourceElement"))&&expected.equals(row.getOrDefault("leafStatus",row.get("aggregateStatus"))))return;}throw new AssertionError(source+" "+expected+" "+contexts(result));}
    private static void write(Path file,Object value)throws Exception{Files.write(file,(byte[])call(loader.loadClass("org.jacamo.bridge.contract.CanonicalJson"),"encode",value));}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static long threads(String prefix){return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive).filter(t->t.getName().startsWith(prefix)).count();}
    @FunctionalInterface private interface Probe {boolean ready()throws Exception;}
    private static void await(Probe ready,List<String> errors)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);while(!ready.ready()&&errors.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(25);check(ready.ready(),"Timeout/errors "+errors);}
    private static void enabled(Container root,String name,List<String> errors)throws Exception{await(()->edt(()->component(root,name,JButton.class).isEnabled()),errors);}
    private static void click(Container root,String name)throws Exception{edt(()->{component(root,name,JButton.class).doClick();return null;});}
    private static <T>T edt(Callable<T> action)throws Exception{var task=new FutureTask<T>(action);SwingUtilities.invokeLater(task);return task.get(30,TimeUnit.SECONDS);}
    private static <T extends Component>T component(Container root,String name,Class<T> type){for(var child:root.getComponents()){if(type.isInstance(child)&&name.equals(child.getName()))return type.cast(child);if(child instanceof Container nested){var found=component(nested,name,type);if(found!=null)return found;}}return null;}
}
