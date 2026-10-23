package org.tzi.use.plugins.jacamo.semantics;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.awt.*;
import javax.swing.*;
import javax.swing.SwingUtilities;
import org.tzi.use.main.Session;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.MainPluginRuntime;
import org.tzi.use.runtime.impl.PluginRuntime;

/** Cold consumer: host/JDK links only. All semantic and bridge calls use the actual plugin loader. */
public final class InstalledAuctionMain {
    @FunctionalInterface private interface ClassResolver {Class<?> loadClass(String name)throws ClassNotFoundException;}
    private static ClassResolver loader;
    public static void main(String[] args)throws Exception {
        try {run(args);}catch(Throwable failure){failure.printStackTrace();System.exit(1);}
    }
    private static void run(String[] args)throws Exception {
        if(args[0].equals("--source")){runSource(Path.of(args[1]),Path.of(args[2]));return;}
        Path install=Path.of(args[0]).toRealPath();
        org.tzi.use.config.Options.processArgs(new String[]{"-H="+install,"-nr"});
        var config=new Properties();
        try(var input=Files.newInputStream(Path.of(args[1]))){config.load(input);}
        for(String key:config.stringPropertyNames())if(key.startsWith("use."))System.setProperty(key,config.getProperty(key));
        MainPluginRuntime.run(install.resolve("lib/plugins"));
        var plugin=((PluginRuntime)PluginRuntime.getInstance()).getPlugin("JaCaMo");check(plugin!=null,"Plugin discovery");
        loader=plugin.getPluginClassLoader()::loadClass;Object guard=call(loader.loadClass("org.tzi.use.semantic.engine.api.EngineInstallationGuard"),"verify");
        Path jar=install.resolve("lib/plugins/use-jacamo-plugin-1.0.1.jar").toRealPath();var origins=new TreeMap<String,String>();
        for(String name:List.of("org.vnu.sme.goal.dsl.csl.parser.CslCompiler","org.vnu.sme.goal.dsl.csl.mm.CslModel",
                "org.vnu.sme.goal.dsl.istar.parser.IStarCompiler","org.vnu.sme.goal.dsl.istar.mm.GoalModel",
                "org.vnu.sme.goal.verify.conformance.semantics.GoalMarking","org.vnu.sme.goal.verify.conformance.semantics.TaskMarking",
                "org.vnu.sme.goal.verify.conformance.semantics.IStarMarking","org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation")) {
            var type=loader.loadClass(name);Path origin=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            check(origin.equals(jar),"Installed core origin "+name+": "+origin);origins.put(name,origin.toString());
        }
        for(Class<?> host:List.of(org.tzi.use.uml.mm.MModel.class,org.tzi.use.uml.sys.MSystem.class,org.tzi.use.parser.ocl.OCLCompiler.class,org.tzi.use.uml.ocl.expr.Evaluator.class))
            check(loader.loadClass(host.getName())==host,"Host ownership "+host);
        for(String name:List.of("cartago.Artifact","jason.asSemantics.Agent","jacamo.infra.JaCaMoLauncher"))
            try{loader.loadClass(name);throw new AssertionError("Consumer framework leak "+name);}catch(ClassNotFoundException expected) { }
        var session=new Session();Object facade=loader.loadClass("org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade").getConstructor(Path.class,Session.class).newInstance(install,session);
        Gui gui=Boolean.parseBoolean(config.getProperty("live","false"))?new Gui(session,facade):null;
        try {
            Object selection=loader.loadClass("org.tzi.use.plugins.jacamo.JaCaMoFacade$ImportSelection").getConstructor(String.class,Path.class)
                    .newInstance("AUCTION_OBSERVED_V1",Path.of(config.getProperty("mapping")));
            if(gui==null)call(facade,"importProject",Path.of(config.getProperty("jcm")),selection);
            else gui.importProject(Path.of(config.getProperty("jcm")),config.getProperty("mapping"));
            Object workspace=field(facade,"nativeWorkspace"),canonical=field(workspace,"canonical"),program=call(canonical,"program");
            Object csl=call(program,"cslModel"),goal=call(program,"goalModel"),plan=call(program,"casePlan");
            check(csl.getClass()==loader.loadClass("org.vnu.sme.goal.dsl.csl.mm.CslModel"),"Real CSL M1");
            check(goal.getClass()==loader.loadClass("org.vnu.sme.goal.dsl.istar.mm.GoalModel"),"Real iStar M1");
            Object frontend=loader.loadClass("org.tzi.use.semantic.engine.frontend.CslFrontend").getConstructor().newInstance();
            check(Boolean.TRUE.equals(call(call(frontend,"validate",csl,"installed-auction"),"ok")),"Original CSL validator");
            var validator=loader.loadClass("org.vnu.sme.goal.dsl.istar.mm.GoalModelValidator");
            check(((List<?>)call(validator,"validate",goal)).size()==3,"Strict original limitations preserved");
            Object policy=loader.loadClass("org.vnu.sme.goal.dsl.istar.mm.GoalModelValidationPolicy").getField("DERIVED_AND_DEPENDEE_V1").get(null);
            check(((List<?>)call(validator,"validate",goal,policy)).isEmpty(),"Explicit policy validation");
            check(((Map<?,?>)call(call(plan,"selectors"),"selections")).size()==18,"All eighteen XML selectors");
            check(((List<?>)call(plan,"tasks")).size()==6,"Six bound source task sites");
            check(session.hasSystem(),"Native Session published");
            check(session.system().state().allObjects().stream().noneMatch(o->o.cls().name().equals("Auction")),"Configured requests must not invent Auction population before Start");
            check(session.system().model().getClass("Auction")!=null&&session.system().model().getClass("AuctionMonitor")!=null,"Domain/observer model");
            check(session.system().model().prePostConditions().size()==5,"Five native operation clauses");
            check("true".equals(org.tzi.use.api.UseSystemApi.create(session.system(),false).evaluate("Auction.allInstances()->isEmpty()").toString()),"Same native state/OCL");
            check(ProcessHandle.current().pid()!=Long.parseLong(config.getProperty("producerPid")),"External producer PID");
            var receipt=new TreeMap<String,Object>();receipt.put("status","PASS");receipt.put("scope","INSTALLED_STATIC_IMPORT_ONLY");
            receipt.put("consumerPid",ProcessHandle.current().pid());receipt.put("producerPid",Long.parseLong(config.getProperty("producerPid")));
            receipt.put("classpath",System.getProperty("java.class.path"));receipt.put("guard",guard);receipt.put("engineClasses",origins);
            receipt.put("programHash",call(program,"hash"));receipt.put("caseProgram",call(plan,"evidence"));receipt.put("semanticProgram",call(facade,"semanticProgramEvidence"));
            receipt.put("nativeObjects",session.system().state().numObjects());receipt.put("auctionObjects",session.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).count());receipt.put("journalLimit",call(loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationCoordinator"),"defaultJournalMaxBytes"));
            if(gui!=null)runLive(facade,session,gui,config,receipt);
            Files.write(Path.of(config.getProperty("evidence")).resolve("consumer-receipt.json"),(byte[])call(loader.loadClass("org.jacamo.bridge.contract.CanonicalJson"),"encode",receipt));
            System.out.println(gui==null?"INSTALLED_AUCTION_STATIC_PASS":"INSTALLED_AUCTION_LIVE_PASS");
        }catch(Exception|Error failure) {
            try {write(Path.of(config.getProperty("evidence")).resolve("failure-snapshot.json"),call(call(facade,"verificationSnapshot"),"toMap"));
                Files.writeString(Path.of(config.getProperty("evidence")).resolve("failure-status.txt"),call(facade,"status")+"\n"+call(facade,"runtimeVerificationResult"));
            }catch(Exception diagnostic){failure.addSuppressed(diagnostic);}throw failure;
        }finally{try{call(facade,"close");}finally{if(gui!=null)gui.close();SwingUtilities.invokeAndWait(()->{});}}
    }
    /** Same scenario with development production classes; never presented as cold installation evidence. */
    private static void runSource(Path install,Path configuration)throws Exception {
        org.tzi.use.config.Options.processArgs(new String[]{"-H="+install.toRealPath(),"-nr"});
        var config=new Properties();try(var input=Files.newInputStream(configuration)){config.load(input);}
        for(String key:config.stringPropertyNames())if(key.startsWith("use."))System.setProperty(key,config.getProperty(key));
        loader=InstalledAuctionMain.class.getClassLoader()::loadClass;
        var session=new Session();Object facade=loader.loadClass("org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade").getConstructor(Path.class,Session.class).newInstance(install,session);
        var receipt=new TreeMap<String,Object>();receipt.put("status","PASS");receipt.put("consumerPid",ProcessHandle.current().pid());receipt.put("producerPid",Long.parseLong(config.getProperty("producerPid")));
        receipt.put("guard",call(loader.loadClass("org.tzi.use.semantic.engine.api.EngineInstallationGuard"),"verify"));
        receipt.put("classpath",System.getProperty("java.class.path"));receipt.put("journalLimit",call(loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationCoordinator"),"defaultJournalMaxBytes"));
        check(ProcessHandle.current().pid()!=Long.parseLong(config.getProperty("producerPid")),"External source producer PID");
        var gui=new Gui(session,facade);
        edt(()->{gui.frame.setTitle("Development reactor-artifact Auction acceptance");return null;});
        try {
            gui.importProject(Path.of(config.getProperty("jcm")),config.getProperty("mapping"));
            check(session.system().state().allObjects().stream().noneMatch(o->o.cls().name().equals("Auction")),"Source import cannot invent Auction population");
            receipt.put("auctionObjects",session.system().state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).count());
            Object program=call(field(field(facade,"nativeWorkspace"),"canonical"),"program");
            check(call(program,"cslModel").getClass()==loader.loadClass("org.vnu.sme.goal.dsl.csl.mm.CslModel")&&call(program,"goalModel").getClass()==loader.loadClass("org.vnu.sme.goal.dsl.istar.mm.GoalModel"),"Real source CSL/iStar models");
            receipt.put("programHash",call(program,"hash"));receipt.put("caseProgram",call(call(program,"casePlan"),"evidence"));receipt.put("semanticProgram",call(facade,"semanticProgramEvidence"));
            runLive(facade,session,gui,config,receipt);receipt.put("scope","DEVELOPMENT_CONTROLLED_AUCTION_LIVE");
            write(Path.of(config.getProperty("evidence")).resolve("consumer-receipt.json"),receipt);System.out.println("SOURCE_AUCTION_CONTROLLED_PASS");
        }finally{try{call(facade,"close");}finally{gui.close();}}
    }
    private static void runLive(Object facade,Session session,Gui gui,Properties config,Map<String,Object> receipt)throws Exception {
        Path evidence=Path.of(config.getProperty("evidence"));var system=session.system();
        boolean controlled=Boolean.parseBoolean(config.getProperty("controlled","false"));
        Object projector=field(field(facade,"nativeWorkspace"),"runtimeProjector"),coordinator=call(projector,"coordinator");
        var watch=new Watch();var stream=new JournalCursor(call(coordinator,"journal"),watch);gui.click("start-runtime");gui.enabled("pause-runtime");
        // The extended roster/negative workload records more accepted cuts. This
        // is a bounded harness wait, independent of the unchanged 10s NPL deadline.
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(controlled?480:240);
        while(System.nanoTime()<until&&!watch.complete()) {
            stream.drain();
            for(Object cut:(List<?>)call(facade,"runtimeVerificationHistory"))watch.accept(cut);
            check(gui.errors.isEmpty(),"Workbench errors "+gui.errors);
            check(!Boolean.TRUE.equals(call(call(coordinator,"journal"),"hasGap")),"Journal GAP "+call(call(coordinator,"journal"),"diagnostic"));
            check(session.system()==system&&call(projector,"system")==system,"Single live native state");Thread.sleep(100);
        }
        stream.close();
        check(watch.complete(),"Actual six source terminals on each scheme "+watch.scopedTerminals);
        if(controlled)controlledOperations(facade,session,coordinator,config,watch,receipt);
        gui.click("pause-runtime");gui.enabled("resume-runtime");settle(coordinator);
        Object control=call(facade,"runtimeControlState");check(((Set<?>)call(control,"required")).size()==5&&call(control,"required").equals(call(control,"acknowledged")),"Five actual Jason pause ACKs");
        for(Object cut:(List<?>)call(facade,"runtimeVerificationHistory"))watch.accept(cut);
        check(watch.passTasks.containsAll(Set.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1")),"Native Task POST truth "+watch.passTasks);
        check(watch.schemes.size()==2,"Two observed scheme scopes "+watch.schemes);
        check(watch.calls.containsAll(Set.of("TaskMarking.update","IStarPropagation.closePending","GoalModelInstanceTranslator.propagateDependencies")),"Original runtime calls "+watch.calls);
        check(!watch.calls.contains("GoalMarking.update"),"No fabricated leaf Goal input for derived-only Auction Goals");
        validateTaskPlayers((Set<?>)call(control,"required"),watch.actorPlayers,controlled);
        var expectedClauses=new TreeSet<String>();system.model().prePostConditions().forEach(c->expectedClauses.add("COMPILED:"+c));
        check(watch.nativeClauses.containsAll(expectedClauses),"All actual native operation clauses "+watch.nativeClauses+" expected "+expectedClauses);
        Object accepted=completedResult(coordinator);String state=(String)call(accepted,"stateHash"),result=(String)call(accepted,"resultHash");
        Object journal=call(coordinator,"journal");long bytes=((Number)call(journal,"persistedBytes")).longValue();check(bytes<((Number)receipt.get("journalLimit")).longValue(),"Bounded source evidence "+bytes);
        long auctions=(Long)call(coordinator,"read",(Callable<Long>)()->system.state().allObjects().stream().filter(o->o.cls().name().equals("Auction")).count());check(auctions==2,"Two actual Artifacts in native state "+auctions);
        String query=(String)call(coordinator,"read",(Callable<String>)()->org.tzi.use.api.UseSystemApi.create(system,false).evaluate("Auction.allInstances()->size() = 2").toString());check(query.equals("true"),"Native manual OCL same cut");
        edt(()->{call(facade,"showObjectDiagram");check(gui.window.getObjectDiagrams().getLast().system()==system,"Native diagram shares Session");call(gui.panel,"refreshRuntime");
            for(String table:List.of("semantic-original-history","semantic-case-evidence","semantic-coverage"))check(component(gui.panel,table,JTable.class).getRowCount()>0,"Evidence table "+table);
            check(component(gui.panel,"program-trace",JTextArea.class).getText().contains("caseProgram"),"Program XML trace UI");
            var rootTabs=component(gui.panel,"workbench-tabs",JTabbedPane.class);int semanticTab=rootTabs.indexOfTab("Semantic verification");check(semanticTab>=0,"Visible semantic evidence tab");rootTabs.setSelectedIndex(semanticTab);
            capture(gui,evidence.resolve("installed-auction-workbench.png"));
            var history=component(gui.panel,"semantic-original-history",JTable.class);var detailTabs=(JTabbedPane)SwingUtilities.getAncestorOfClass(JTabbedPane.class,history);
            for(String title:List.of("Original iStar history","Case evidence")) {int selected=detailTabs.indexOfTab(title);check(selected>=0,"Visible "+title);detailTabs.setSelectedIndex(selected);capture(gui,evidence.resolve(title.startsWith("Original")?"original-istar-history.png":"case-evidence.png"));}
            return null;});
        write(evidence.resolve("accepted.json"),call(accepted,"toMap"));write(evidence.resolve("source-task-evidence.json"),watch.rows);
        Path recording=evidence.resolve("recording");call(facade,"exportRuntimeReplay",recording);
        var cycles=new ArrayList<Object>();
        for(int cycle=0;cycle<2;cycle++) {
            Object owner=call(coordinator,"originalEngine");var before=session.system();gui.click("stop-monitor");gui.enabled("restart-monitor");
            await(()->Boolean.TRUE.equals(map(call(owner,"metrics")).get("terminated")),15);
            var metrics=map(call(owner,"metrics"));check(((Number)metrics.get("expressions")).intValue()==0&&((Number)metrics.get("pendingPins")).intValue()==0,"Retired cache/pin cleanup");
            check(session.system()==before&&ProcessHandle.of(Long.parseLong(config.getProperty("producerPid"))).orElseThrow().isAlive(),"Stop monitor preserves USE and producer");
            check("true".equals(org.tzi.use.api.UseSystemApi.create(before,false).evaluate("1+1=2").toString()),"USE OCL survives Stop");
            gui.click("restart-monitor");gui.enabled("stop-monitor");projector=field(field(facade,"nativeWorkspace"),"runtimeProjector");coordinator=call(projector,"coordinator");settle(coordinator);
            check(session.system()!=before&&call(coordinator,"originalEngine")!=owner,"Fresh monitor state/history");
            check(threads("use-original-engine-")==1&&threads("jacamo-bridge-tcp-subscription")==1,"One active engine and subscription");cycles.add(metrics);
        }
        gui.enabled("resume-runtime");gui.click("resume-runtime");gui.enabled("pause-runtime");
        Files.writeString(Path.of(config.getProperty("producerStop")),"E01 official external shutdown");
        await(()->!ProcessHandle.of(Long.parseLong(config.getProperty("producerPid"))).map(ProcessHandle::isAlive).orElse(false),25);
        check(session.hasSystem()&&ProcessHandle.current().isAlive(),"Producer shutdown leaves USE alive");
        check("true".equals(org.tzi.use.api.UseSystemApi.create(session.system(),false).evaluate("1+1=2").toString()),"Native OCL after producer exit");
        gui.click("stop-monitor");gui.enabled("restart-monitor");
        Object replay=loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay").getConstructor().newInstance();
        Object bundle=call(replay,"openBundle",recording);try {
            Object report=call(bundle,"report");write(evidence.resolve("replay-report.json"),call(loader.loadClass("org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson"),"tree",report));
            check(Boolean.TRUE.equals(call(report,"complete")),"Installed replay complete "+report);check(state.equals(call(report,"finalStateHash"))&&result.equals(call(report,"finalResultHash")),"Installed replay exact hashes");
            receipt.put("replayCheckedEntries",call(report,"checkedEntries"));
        }finally{call(bundle,"close");}
        var samples=gui.delays.stream().sorted().toList();check(samples.size()>20,"EDT heartbeat samples");double p95=samples.get((int)Math.ceil(samples.size()*.95)-1)/1e6;check(p95<250,"EDT p95 "+p95);
        var children=new ArrayList<Object>();for(var child:ProcessHandle.current().children().toList()) {
            String exe=child.info().command().orElse("UNAVAILABLE");check(System.getProperty("os.name").startsWith("Windows")&&Path.of(System.getenv("SystemRoot"),"System32","conhost.exe").toString().equalsIgnoreCase(exe),"Unexpected USE child "+exe);children.add(Map.of("pid",child.pid(),"executable",exe,"kind","WINDOWS_CONSOLE_HOST"));
        }
        receipt.put("scope",controlled?"INSTALLED_CONTROLLED_AUCTION_LIVE":"INSTALLED_CANONICAL_AUCTION_LIVE");receipt.put("finalAuctionObjects",auctions);receipt.put("journalBytes",bytes);receipt.put("journalGap",false);
        receipt.put("originalCalls",watch.calls);receipt.put("sourceTerminalSites",watch.terminalSites);receipt.put("nativeTaskPostPass",watch.passTasks);receipt.put("schemes",watch.schemes);receipt.put("players",watch.actorPlayers);
        receipt.put("scopedTerminals",watch.scopedTerminals);receipt.put("nativeOperationClauses",watch.nativeClauses);receipt.put("requiredPauseAgents",call(control,"required"));receipt.put("acknowledgedPauseAgents",call(control,"acknowledged"));
        receipt.put("stateHash",state);receipt.put("resultHash",result);receipt.put("monitorCycles",cycles);receipt.put("heartbeatP95Millis",p95);receipt.put("consumerChildren",children);receipt.put("runtimeChildProcesses",0);receipt.put("officialProducerExitUseSurvives",true);
    }
    static void validateTaskPlayers(Set<?> runtimeAgents,Set<?> taskPlayers,boolean controlled) {
        // Canonical mParticipant has min=1: a role declaration does not force
        // every agent to commit before another player completes the scheme.
        // E02 explicitly commits its five-player roster and retains that gate.
        check(runtimeAgents.size()==5,"Five actual runtime agents "+runtimeAgents);
        check(taskPlayers.size()>=2&&runtimeAgents.containsAll(taskPlayers),"Exact observed Task players belong to the runtime roster "+taskPlayers);
        if(controlled)check(taskPlayers.equals(runtimeAgents),"Explicit controlled five-player commitments "+taskPlayers);
    }
    private static void capture(Gui gui,Path file)throws Exception {
        // Render Swing content, not the heavyweight native frame peer. A tab
        // selected in this EDT turn must be laid out before printing it.
        gui.frame.validate();gui.panel.validate();gui.panel.doLayout();
        var image=new java.awt.image.BufferedImage(gui.panel.getWidth(),gui.panel.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();
        try{graphics.setColor(gui.panel.getBackground());graphics.fillRect(0,0,image.getWidth(),image.getHeight());gui.panel.printAll(graphics);}finally{graphics.dispose();}
        var colors=new HashSet<Integer>();for(int y=0;y<image.getHeight();y+=7)for(int x=0;x<image.getWidth();x+=7)colors.add(image.getRGB(x,y));
        check(colors.size()>16,"Workbench capture must contain actual rendered UI: "+file);
        javax.imageio.ImageIO.write(image,"png",file.toFile());
    }
    /** Commands only request real Jason actions. Assertions use the admitted native journal/state. */
    private static void controlledOperations(Object facade,Session session,Object coordinator,Properties config,Watch watch,Map<String,Object> receipt)throws Exception {
        Path fixture=Path.of(config.getProperty("fixture"));
        check(Files.isRegularFile(fixture.resolve("late-role-request.json")),"Late role must follow six actual original failures");
        var system=session.system();Object journal=call(coordinator,"journal");
        var commands=List.of(new String[]{"bob","e02_start(a1,\"E02_service\")","start","SUCCEEDED"},
                new String[]{"alice","e02_bid(a1,42.5)","bid","SUCCEEDED"},
                new String[]{"maria","e02_bid(a1,80.0)","bid","SUCCEEDED"},
                new String[]{"bob","e02_stop(a1)","stop","SUCCEEDED"},
                new String[]{"giacomo","e02_bid(a1,5.0)","bid","FAILED"});
        var operations=new ArrayList<Object>();var nativeRows=new ArrayList<Object>();
        try(var cursor=new JournalCursor(journal)) {
            cursor.drain();
            check(cursor.failedSourceSteps.size()==8,"Eight original seeded failures including actual late player: "+cursor.failedSourceSteps);
            check(cursor.closedArtifacts.size()==2,"Both original no-bid auctions must stop before supplemental controls");
            // Late commitment creates later actual deadlines. A decide Task on
            // the original scheme does not certify admission of their expirations.
            await(()->expiredBidObligations(coordinator)==8,180);
            cursor.drain();
            check(nativeQuery(coordinator,system,"Auction.allInstances()->forAll(a | not a.running and a.winner = 'no_winner' and a.currentWinner = 'no_winner')").equals("true"),"Actual no-bid stop population");
            settle(coordinator);controlledFailureProof(coordinator,config,"controlled-before.json",true);
            String second=nativeQuery(coordinator,system,"Auction.allInstances()->any(a | a.id = 'a2').oclAsType(Auction).task");
            for(int i=0;i<commands.size();i++) {
                String[] command=commands.get(i);Path file=fixture.resolve(String.format(Locale.ROOT,"command-%03d.json",i+1));
                Path part=file.resolveSibling(file.getFileName()+".part");write(part,Map.of("profile","AUCTION_DETERMINISTIC_CONTROLS/1.0","agent",command[0],"goal",command[1]));Files.move(part,file,StandardCopyOption.ATOMIC_MOVE);
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(40);Map<String,Object> event=null;
                while(event==null&&System.nanoTime()<deadline) {
                    for(var candidate:cursor.drain()) {
                        var after=map(candidate.get("after"));
                        if(command[2].equals(after.get("operation"))&&command[3].equals(candidate.get("kind"))&&command[0].equals(after.get("performerName"))) {
                            check(event==null,"Ambiguous actual operation terminal");event=candidate;
                        }
                    }
                    check(!Boolean.TRUE.equals(call(journal,"hasGap")),"Controlled journal GAP");
                    if(event==null)Thread.sleep(50);
                }
                check(event!=null,"Actual operation terminal missing for "+Arrays.toString(command));operations.add(event);
                String correlation=event.get("correlationId").toString();
                String clause=i==0?"::Opened":i==3?"::Closed":"::RunningForBid";
                // The durable terminal is appended before asynchronous original
                // evaluation publishes its completion. A drained() marker alone
                // cannot fence a concurrent ingress submission. Read the exact
                // invocation's completed native clause from the bounded journal,
                // including completions no longer present in its memory tail.
                await(()->{cursor.drain();return cursor.nativeRows.stream().anyMatch(r->correlation.equals(r.get("invocation"))
                        &&r.get("id").toString().endsWith(clause)&&Set.of("PASS","FAIL").contains(r.get("verdict")));},40);
                nativeRows.addAll(cursor.nativeRows.stream().filter(r->correlation.equals(r.get("invocation"))).toList());
                settle(coordinator);
                for(Object cut:(List<?>)call(facade,"runtimeVerificationHistory")) {
                    watch.accept(cut);
                }
                String predicate=switch(i) {
                    case 0->"a.running and a.task = 'E02_service'";
                    case 1,2->"a.running and a.best_bid = 42.5 and a.currentWinner = 'alice' and a.winner = 'no_winner'";
                    default->"not a.running and a.best_bid = 42.5 and a.currentWinner = 'alice' and a.winner = 'alice'";
                };
                check(nativeQuery(coordinator,system,"Auction.allInstances()->one(a | a.id = 'a1' and "+predicate+")").equals("true"),"Native state after "+command[1]);
                check(second.equals(nativeQuery(coordinator,system,"Auction.allInstances()->any(a | a.id = 'a2').oclAsType(Auction).task")),"Other Auction must remain unchanged");
                check(session.system()==system,"Controls preserve sole live MSystem");
            }
        }
        var posts=nativeRows.stream().map(InstalledAuctionMain::map).filter(r->"POST".equals(r.get("kind"))&&r.get("id").toString().endsWith("::Opened")).toList();
        check(posts.stream().anyMatch(r->"PASS".equals(r.get("verdict"))&&r.get("expression").toString().contains("@pre")&&"false".equals(map(r.get("witness")).get("before.running"))&&"true".equals(map(r.get("witness")).get("after.running"))),"Actual native @pre/before/after "+posts);
        controlledFailureProof(coordinator,config,"controlled-after.json",false);
        check(!watch.passTasks.contains("P_bid_t2"),"Supplemental physical bids cannot fulfill canonical Jason Task history");
        write(Path.of(config.getProperty("evidence")).resolve("controlled-operations.json"),Map.of("operations",operations,"nativeRows",nativeRows,"expectedDifference","Explicit supplemental controls have no canonical Task markers; original failed Tasks retain historical status. NPL061 samples created and absolute deadline separately: retain raw deltas, including subcycle sampling differences; the original strict OCL duration equality is unchanged."));
        receipt.put("controlledOperationCount",operations.size());receipt.put("controlledNativeAtPre",true);receipt.put("controlledOriginalFailedInvocations",8);
    }
    private static Object completedResult(Object coordinator)throws Exception {
        return coordinator.getClass().getMethod("awaitEvaluation",loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult"),long.class)
                .invoke(coordinator,call(coordinator,"lastObservation"),30000L);
    }
    private static long expiredBidObligations(Object coordinator)throws Exception {
        var semantic=map(call(call(completedResult(coordinator),"semantic"),"toMap"));
        return ((List<?>)semantic.get("constraints")).stream().map(InstalledAuctionMain::map)
                .filter(row->"INVARIANT_BODY".equals(row.get("kind"))&&"NATIVE:AuctionObligation::WithinDeadline".equals(row.get("id")))
                .filter(row->{var witness=map(row.get("witness"));return "'bid'".equals(witness.get("goal"))&&"UNFULFILLED".equals(witness.get("sourceNormState"))&&"FAIL".equals(row.get("verdict"));}).count();
    }
    private static void controlledFailureProof(Object coordinator,Properties config,String file,boolean before)throws Exception {
        assertDeclaredBidDeadline(config);
        var accepted=map(call(completedResult(coordinator),"toMap"));var semantic=map(accepted.get("semantic"));
        var core=map(map(semantic.get("observation")).get("originalEngine"));var pending=new TreeSet<String>();
        for(Object raw:(List<?>)core.get("contexts")) {
            var context=map(raw);
            for(Object value:map(context.get("occurrences")).values()) {
                var row=map(value);
                if("P_bid_t2".equals(row.get("sourceElement"))&&row.containsKey("leafStatus")) {
                    check("PENDING".equals(row.get("leafStatus")),"Original Task history cannot gain credit from admin/control operations "+row);
                    pending.add(context.get("scheme")+":"+row.get("player"));
                }
                if("O_bid".equals(row.get("sourceElement"))&&"SOURCE_ELEMENT".equals(row.get("expansionKind")))check(!"FULFILLED".equals(row.get("aggregateStatus")),"Original dependency/AND cannot fabricate bid fulfillment");
            }
        }
        check(pending.size()==8,"Eight exact player/scheme historical Task occurrences "+pending);
        var norms=new TreeSet<String>();int monitors=0;
        for(Object raw:(List<?>)semantic.get("constraints")) {
            var row=map(raw);var witness=map(row.get("witness"));
            if("INVARIANT_BODY".equals(row.get("kind"))&&"NATIVE:AuctionObligation::WithinDeadline".equals(row.get("id"))&&"'bid'".equals(witness.get("goal"))) {
                check("FAIL".equals(row.get("verdict"))&&"UNFULFILLED".equals(witness.get("sourceNormState")),"Actual deadline failure remains visible after admin recovery "+row);
                var time=map(call(loader.loadClass("org.jacamo.bridge.contract.CanonicalJson"),"decode",witness.get("rawTimes").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                var deadline=new java.math.BigInteger(time.get("deadline").toString());var created=new java.math.BigInteger(time.get("created").toString());
                var rawDelta=deadline.subtract(created);
                var nativeDelta=new java.math.BigInteger(witness.get("deadlineAt").toString()).subtract(new java.math.BigInteger(witness.get("activatedAt").toString()));
                check(rawDelta.equals(nativeDelta),"Native projection must preserve actual NPL time samples, not normalize them to the nominal duration");
                check(new java.math.BigInteger(time.get("ended").toString()).compareTo(deadline)>=0,"Actual unfulfilled terminal must be at/after the source deadline");
                norms.add(witness.get("scheme")+":"+witness.get("playerName"));
            }
            if("INVARIANT_BODY".equals(row.get("kind"))&&"NATIVE:AuctionMonitor::BidEvidence".equals(row.get("id"))) {
                monitors++;check("true".equals(witness.get("adminBidOverride")),"Actual source admin recovery required");
                check("SKIPPED".equals(row.get("verdict")),"Unavailable internal MOISE timing remains SKIPPED");
                if(before)check("Set{}".equals(witness.get("actualBidBy")),"Admin recovery must not invent an actual bid");
            }
        }
        check(norms.size()==8&&monitors==2,"Eight source obligations and two observer scopes "+norms);
        write(Path.of(config.getProperty("evidence")).resolve(file),accepted);
    }
    private static void assertDeclaredBidDeadline(Properties config)throws Exception {
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        var document=factory.newDocumentBuilder().parse(Path.of(config.getProperty("jcm")).getParent().resolve("src/org/auction-os.xml").toFile());
        var goals=document.getElementsByTagName("goal");int count=0;
        for(int i=0;i<goals.getLength();i++) {
            var goal=(org.w3c.dom.Element)goals.item(i);
            if(goal.getAttribute("id").equals("bid")&&goal.hasAttribute("ttf")){count++;check(goal.getAttribute("ttf").equals("10 seconds"),"Pinned MOISE bid duration must remain ten seconds");}
        }
        check(count==1,"Exactly one declared bid deadline");
    }
    private static String nativeQuery(Object coordinator,org.tzi.use.uml.sys.MSystem system,String expression)throws Exception {
        return (String)call(coordinator,"read",(Callable<String>)()->org.tzi.use.api.UseSystemApi.create(system,false).evaluate(expression).toString());
    }
    /** Reads only complete persisted records, on this worker; no extra subscription or live state. */
    private static final class JournalCursor implements AutoCloseable {
        final Object journal;final java.io.BufferedReader file;final Watch watch;long position;
        final Set<String> failedSourceSteps=new TreeSet<>(),closedArtifacts=new TreeSet<>();
        final List<Map<String,Object>> nativeRows=new ArrayList<>();
        JournalCursor(Object journal)throws Exception {this(journal,null);}
        JournalCursor(Object journal,Watch watch)throws Exception {this.journal=journal;this.watch=watch;file=Files.newBufferedReader((Path)call(journal,"path"),java.nio.charset.StandardCharsets.UTF_8);}
        List<Map<String,Object>> drain()throws Exception {
            long end=((Number)call(journal,"persistedEntries")).longValue();var operations=new ArrayList<Map<String,Object>>();
            while(position<end) {
                String line=file.readLine();check(line!=null,"Complete journal prefix required");position++;
                byte[] bytes=line.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                var entry=map(call(loader.loadClass("org.jacamo.bridge.contract.CanonicalJson"),"decode",bytes));
                var result=map(entry.get("result"));if(watch!=null)watch.acceptJson(result);
                var semantic=map(result.get("semantic"));
                for(Object raw:(List<?>)semantic.getOrDefault("constraints",List.of())) {
                    var row=map(raw);if(Set.of("PRE","POST").contains(row.get("kind")))nativeRows.add(row);
                }
                var event=map(entry.get("payload"));if(!event.containsKey("eventId")||"REJECTED".equals(entry.get("kind")))continue;
                var after=map(event.get("after"));
                // Source step details are retained separately by the actual observation envelope.
                Object envelope=after.get("verificationObservation");
                if(envelope instanceof Map<?,?>) {
                    var frame=map(envelope);var data=map(frame.get("data"));
                    if("P_bid_t2".equals(data.get("sourceStepId"))&&"FAILED".equals(data.get("phase")))failedSourceSteps.add(data.get("stepInvocation").toString());
                }
                if("auction_env.AuctionArtifact".equals(after.containsKey("operationSignature")?((List<?>)after.get("operationSignature")).getFirst():null)
                        &&Set.of("SUCCEEDED","FAILED").contains(event.get("kind"))) {
                    operations.add(event);if("stop".equals(after.get("operation"))&&"SUCCEEDED".equals(event.get("kind")))closedArtifacts.add(after.get("artifactSemanticId").toString());
                }
            }
            return operations;
        }
        public void close()throws Exception {file.close();}
    }
    private static final class Watch {
        static final Set<String> TASKS=Set.of("A_start_t1","A_start_t2","A_decide_t1","A_decide_t2","P_bid_t1","P_bid_t2");
        final Set<String> terminalSites=new TreeSet<>(),passTasks=new TreeSet<>(),calls=new TreeSet<>(),schemes=new TreeSet<>(),actorPlayers=new TreeSet<>(),nativeClauses=new TreeSet<>();
        final Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<String,Set<String>> scopedTerminals=new TreeMap<>();
        final List<Object> rows=new ArrayList<>();
        boolean complete(){return scopedTerminals.size()==2&&scopedTerminals.values().stream().allMatch(s->s.containsAll(TASKS));}
        void acceptJson(Map<String,Object> cut) {
            if(!(cut.get("semantic") instanceof Map<?,?>))return;var semantic=map(cut.get("semantic"));
            for(Object raw:(List<?>)semantic.get("constraints")) {
                var row=map(raw);String kind=row.get("kind").toString();var witness=map(row.get("witness"));
                if(kind.equals("TASK_POST")) {
                    String task=Objects.toString(witness.get("targetTaskId"),"");
                    if(Set.of("PASS","FAIL").contains(row.get("verdict"))) {
                        terminalSites.add(task);scopedTerminals.computeIfAbsent(Objects.toString(witness.get("scheme"),""),ignored->new TreeSet<>()).add(task);
                    }
                    if("PASS".equals(row.get("verdict")))passTasks.add(task);
                    if(witness.containsKey("scheme"))schemes.add(witness.get("scheme").toString());
                    if(witness.containsKey("player"))actorPlayers.add(witness.get("player").toString());rows.add(row);
                }
                if(Set.of("PRE","POST").contains(kind)&&Set.of("PASS","FAIL").contains(row.get("verdict"))) {nativeClauses.add(row.get("id").toString());rows.add(row);}
            }
            var core=map(map(semantic.get("observation")).getOrDefault("originalEngine",Map.of()));
            for(Object context:(List<?>)core.getOrDefault("contexts",List.of()))for(Object name:(List<?>)map(context).getOrDefault("originalApiCalls",List.of()))calls.add(name.toString());
        }
        void accept(Object cut)throws Exception {
            Object semantic=call(cut,"semantic");if(semantic==null)return;
            if(!seen.add(cut))return;
            for(Object raw:(List<?>)call(semantic,"constraints")) {
                String kind=(String)call(raw,"kind");var witness=map(call(raw,"witness"));
                if(kind.equals("TASK_POST")) {
                    String task=Objects.toString(witness.get("targetTaskId"),"");
                    if(Set.of("PASS","FAIL").contains(call(raw,"verdict").toString())) {
                        terminalSites.add(task);scopedTerminals.computeIfAbsent(Objects.toString(witness.get("scheme"),""),ignored->new TreeSet<>()).add(task);
                    }
                    if(call(raw,"verdict").toString().equals("PASS"))passTasks.add(task);
                    if(witness.containsKey("scheme"))schemes.add(witness.get("scheme").toString());
                    if(witness.containsKey("player"))actorPlayers.add(witness.get("player").toString());
                    rows.add(call(loader.loadClass("org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson"),"tree",raw));
                }
                if(Set.of("PRE","POST").contains(kind)&&Set.of("PASS","FAIL").contains(call(raw,"verdict").toString())) {
                    nativeClauses.add(call(raw,"id").toString());rows.add(call(loader.loadClass("org.tzi.use.plugins.jacamo.semantics.evidence.SemanticJson"),"tree",raw));
                }
            }
            var core=map(map(call(semantic,"observation")).getOrDefault("originalEngine",Map.of()));
            for(Object context:(List<?>)core.getOrDefault("contexts",List.of()))for(Object name:(List<?>)map(context).getOrDefault("originalApiCalls",List.of()))calls.add(name.toString());
        }
    }
    private static final class Gui implements AutoCloseable {
        final MainWindow window;final JFrame frame;final JPanel panel;final javax.swing.Timer timer;
        final List<String> errors=new CopyOnWriteArrayList<>();final List<Long> delays=new CopyOnWriteArrayList<>();
        Gui(Session session,Object facade)throws Exception {
            MainWindow.setJavaFxCall(true);window=edt(()->MainWindow.create(session,PluginRuntime.getInstance()));
            var ctor=loader.loadClass("org.tzi.use.plugins.jacamo.ui.JaCaMoWorkbenchPanel").getDeclaredConstructor(loader.loadClass("org.tzi.use.plugins.jacamo.JaCaMoFacade"),Consumer.class);ctor.setAccessible(true);
            panel=edt(()->(JPanel)ctor.newInstance(facade,(Consumer<String>)errors::add));frame=edt(()->new JFrame("Installed original Auction CSL/iStar/OCL acceptance"));
            long[] previous={System.nanoTime()};timer=edt(()->new javax.swing.Timer(50,event->{long now=System.nanoTime();delays.add(Math.max(0,now-previous[0]-TimeUnit.MILLISECONDS.toNanos(50)));previous[0]=now;}));
            edt(()->{frame.setContentPane(panel);frame.setSize(1500,1000);frame.setVisible(true);timer.start();return null;});
        }
        void importProject(Path jcm,String mapping)throws Exception {edt(()->{component(panel,"engine-profile",JComboBox.class).setSelectedItem("AUCTION_OBSERVED_V1");component(panel,"mapping-specification",JTextField.class).setText(mapping);call(panel,"importProject",jcm);return null;});enabled("start-runtime");}
        void click(String name)throws Exception {edt(()->{component(panel,name,JButton.class).doClick();return null;});}
        void enabled(String name)throws Exception {await(()->{check(errors.isEmpty(),errors.toString());return edt(()->component(panel,name,JButton.class).isEnabled());},60);}
        public void close()throws Exception {edt(()->{timer.stop();frame.dispose();window.getObjectDiagrams().forEach(v->v.detachModel());window.dispose();MainWindow.setJavaFxCall(false);return null;});javafx.application.Platform.exit();}
    }
    private static void settle(Object coordinator)throws Exception{((CompletableFuture<?>)call(coordinator,"originalEngineDrained")).get(30,TimeUnit.SECONDS);call(coordinator,"read",(Callable<Object>)()->null);}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}
    private static void write(Path file,Object value)throws Exception{Files.write(file,(byte[])call(loader.loadClass("org.jacamo.bridge.contract.CanonicalJson"),"encode",value));}
    private static long threads(String prefix){return Thread.getAllStackTraces().keySet().stream().filter(Thread::isAlive).filter(t->t.getName().startsWith(prefix)).count();}
    @FunctionalInterface private interface Probe {boolean ready()throws Exception;}
    private static void await(Probe ready,int seconds)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);while(!ready.ready()&&System.nanoTime()<deadline)Thread.sleep(50);check(ready.ready(),"Timeout");}
    private static <T>T edt(Callable<T> action)throws Exception{var task=new FutureTask<T>(action);SwingUtilities.invokeLater(task);return task.get(30,TimeUnit.SECONDS);}
    private static <T extends Component>T component(Container root,String name,Class<T> type){for(var child:root.getComponents()){if(type.isInstance(child)&&name.equals(child.getName()))return type.cast(child);if(child instanceof Container nested){var found=component(nested,name,type);if(found!=null)return found;}}return null;}
    private static Object field(Object owner,String name)throws Exception {var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Object call(Object target,String name,Object... args)throws Exception {
        Class<?> type=target instanceof Class<?> c?c:target.getClass();
        for(Method method:type.getMethods())if(method.getName().equals(name)&&method.getParameterCount()==args.length) {
            boolean matches=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!method.getParameterTypes()[i].isInstance(args[i]))matches=false;
            if(!matches)continue;
            try{return method.invoke(target instanceof Class<?>?null:target,args);}catch(InvocationTargetException error){var cause=error.getCause();if(cause instanceof Exception e)throw e;if(cause instanceof Error e)throw e;throw error;}
        }
        throw new NoSuchMethodException(type+"."+name+"/"+args.length);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
