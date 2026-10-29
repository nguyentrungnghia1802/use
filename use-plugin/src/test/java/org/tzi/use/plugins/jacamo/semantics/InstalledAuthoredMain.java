package org.tzi.use.plugins.jacamo.semantics;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.MainPluginRuntime;
import org.tzi.use.runtime.impl.PluginRuntime;

/** Cold acceptance driver: JDK/installed host only; all plugin calls cross its real loader. */
public final class InstalledAuthoredMain {
    private InstalledAuthoredMain(){ }
    public static void main(String[] args)throws Exception {
        Path install=Path.of(args[0]).toRealPath(),frameworkClasspath=Path.of(args[2]).toRealPath(),evidence=Path.of(args[3]).toAbsolutePath();
        Files.createDirectories(evidence);boolean confirmed=args[1].equals("auction-confirmed"),auction=args[1].startsWith("auction");
        org.tzi.use.config.Options.processArgs(new String[]{"-H="+install,"-nr"});
        MainPluginRuntime.run(install.resolve("lib/plugins"));
        var descriptor=((PluginRuntime)PluginRuntime.getInstance()).getPlugin("JaCaMo");check(descriptor!=null,"Plugin discovery");
        var loader=descriptor.getPluginClassLoader();Class<?> json=loader.loadClass("org.jacamo.bridge.contract.CanonicalJson");
        call(loader.loadClass("org.tzi.use.semantic.engine.api.EngineInstallationGuard"),"verify");
        Path jar=install.resolve("lib/plugins/use-jacamo-plugin-1.0.1.jar").toRealPath();
        for(String name:List.of("org.vnu.sme.goal.dsl.csl.mm.CslModel","org.vnu.sme.goal.dsl.istar.mm.GoalModel","org.vnu.sme.goal.verify.conformance.semantics.GoalMarking","org.vnu.sme.goal.verify.conformance.semantics.TaskMarking","org.vnu.sme.goal.verify.conformance.semantics.IStarPropagation"))
            check(jar.equals(Path.of(loader.loadClass(name).getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()),"Installed engine origin: "+name);
        check(loader.loadClass("org.tzi.use.uml.sys.MSystem")==org.tzi.use.uml.sys.MSystem.class,"One host MSystem class");
        for(String name:List.of("org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ConformanceInputsPanel","org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel","org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePresenter"))
            check(jar.equals(Path.of(loader.loadClass(name).getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()),"Installed reused UI origin: "+name);
        check(loader.loadClass("org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel").getResource("/META-INF/use4goal-workflow-provenance.json")!=null,"Installed original workflow attribution");
        for(String name:List.of("cartago.Artifact","jacamo.infra.JaCaMoLauncher","jason.asSemantics.Agent"))try{loader.loadClass(name);throw new AssertionError("Framework leaked: "+name);}catch(ClassNotFoundException expected){ }
        Path project=install.resolve(auction?"reference/auction":"examples/csl-counter"),input=project.resolve("authored-v1"),selected=Files.createDirectory(evidence.resolve("selected"));
        String xml=auction?"auction-mapping.xml":"stock-mapping.xml",ocl=confirmed?"auction_confirmed.ocl":auction?"auction_constraints_final_fixed.ocl":"stock.ocl",csl=auction?"auction_csl.csl":"stock.csl",istar=auction?"auction_istar_final.istar":"stock.istar";
        for(String name:List.of(xml,ocl,csl,istar))Files.copy((confirmed&&List.of(xml,ocl).contains(name)?project.resolve("source-confirmed-v1"):input).resolve(name),selected.resolve(name));
        var selection=loader.loadClass("org.tzi.use.plugins.jacamo.JaCaMoFacade$ImportSelection").getConstructor(String.class,Path.class,Path.class,Path.class,Path.class)
                .newInstance("JACAMO_DECLARATIVE_XML",selected.resolve(xml),selected.resolve(ocl),selected.resolve(csl),selected.resolve(istar));
        var host=new Session();Object facade=loader.loadClass("org.tzi.use.plugins.jacamo.DefaultJaCaMoFacade").getConstructor(Path.class,Session.class).newInstance(install,host);
        try {
            call(facade,"nextJournalBudgetBytes",268435456L);
            call(facade,"prepareOwnedProducer",project.resolve(auction?"auction.jcm":"counter.jcm"),selection,frameworkClasspath);
            long producer=((Number)call(call(facade,"ownedProducerStatus"),"pid")).longValue();check(producer!=ProcessHandle.current().pid(),"External producer PID");
            var system=host.system();check(system!=null,"Native system");Object models=call(facade,"semanticModels");check(call(models,"nativeModel")==system.model(),"Canonical view borrows host model");
            Object projector=field(field(facade,"nativeWorkspace"),"runtimeProjector"),coordinator=call(projector,"coordinator"),journal=call(coordinator,"journal");
            var preflight=map(map(call(facade,"semanticProgramEvidence")).get("bindingPreflight"));
            var captureScope=map(preflight.get("captureScope"));String scopeHash=Objects.toString(preflight.get("captureScopeHash"),"");
            check(scopeHash.matches("[0-9a-f]{64}"),"Frozen selective scope");
            check("OBSERVATION_CAPTURE_SCOPE/1.1".equals(captureScope.get("version")),"Installed selective scope version");
            check(Boolean.FALSE.equals(captureScope.get("allProperties")),"Cases explicitly select their property set");
            var propertyNames=new HashSet<>((List<?>)captureScope.get("propertyNames"));
            if(!auction)check(Boolean.FALSE.equals(captureScope.get("nplObservation")),"Stock does not request NPL lifecycle/clock");
            long scopedEvents=0,scopedEventPayloadBytes=0;
            Path journalPath=(Path)call(journal,"path");var tasks=new TreeSet<String>();var calls=new TreeSet<String>();
            call(facade,"startRuntime");check("RUNNING".equals(call(call(facade,"ownedProducerStatus"),"state")),"Owned runtime state");
            Set<String> expected=Set.of("A_start_t1","A_start_t2","A_decide_t2","P_bid_t1","P_bid_t2");boolean ready=false;
            try(var cursor=Files.newBufferedReader(journalPath,java.nio.charset.StandardCharsets.UTF_8)) {
                long position=0,end=System.nanoTime()+TimeUnit.SECONDS.toNanos(360);
                while(System.nanoTime()<end) {
                    long count=((Number)call(journal,"persistedEntries")).longValue();
                    while(position<count) {
                        String line=cursor.readLine();check(line!=null,"Persisted journal row");position++;
                        var record=map(call(json,"decode",line.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        var payload=map(record.get("payload"));var after=map(payload.get("after"));
                        if(after.containsKey("producerCaptureScopeHash")) {
                            check(scopeHash.equals(after.get("producerCaptureScopeHash")),"Frozen scope on actual external TCP callback");
                            if(!"ENGINE_RESULT".equals(record.get("kind"))){scopedEvents++;scopedEventPayloadBytes+=((byte[])call(json,"encode",payload)).length;}
                            for(Object raw:(List<?>)after.getOrDefault("properties",List.of()))check(propertyNames.contains(map(raw).get("name")),"Unselected callback property payload");
                        }
                        var result=map(record.get("result"));if(!(result.get("semantic") instanceof Map<?,?>))continue;
                        var semantic=map(result.get("semantic"));
                        for(Object raw:(List<?>)semantic.getOrDefault("constraints",List.of())){var row=map(raw);if("TASK_POST".equals(row.get("kind"))&&Set.of("PASS","FAIL").contains(row.get("verdict")))tasks.add(Objects.toString(map(row.get("witness")).get("targetTaskId"),""));}
                        var core=map(map(semantic.get("observation")).getOrDefault("originalEngine",Map.of()));
                        for(Object raw:(List<?>)core.getOrDefault("contexts",List.of()))for(Object name:(List<?>)map(raw).getOrDefault("originalApiCalls",List.of()))calls.add(name.toString());
                    }
                    check(host.system()==system&&call(projector,"system")==system,"Single authoritative live system");
                    if(auction)ready=tasks.equals(expected)&&calls.contains("TaskMarking.update");
                    else ready=(Boolean)call(coordinator,"read",(Callable<Boolean>)()->system.state().allObjects().stream().anyMatch(o->o.cls().name().equals("Pallet")&&o.state(system.state()).attributeValue("weight").toString().equals("-1")))&&calls.contains("GoalMarking.update");
                    if(ready)break;Thread.sleep(100);
                }
            }
            check(ready,"Live accepted semantics: tasks="+tasks+", calls="+calls+", status="+call(facade,"status"));
            check(scopedEvents>0,"Actual production events must carry the negotiated selected scope");
            ((CompletableFuture<?>)call(facade,"pauseRuntime","cold authored accepted cut")).get(30,TimeUnit.SECONDS);
            call(facade,"resyncRuntime");call(facade,"runFullVerification");var accepted=map(call(call(facade,"runtimeVerificationResult"),"toMap"));
            Object admittedRuntime=call(field(facade,"bridgeAccepted"),"runtime");
            var sourceEvidence=map(map(call(admittedRuntime,"captureMetadata")).get("sourceEvidence"));
            check(scopeHash.equals(map(sourceEvidence.get("cartago")).get("captureScopeHash")),"Actual admitted resync pins the same capture scope");
            if(!auction)check("NOT_REQUESTED".equals(map(sourceEvidence.get("npl")).get("observationPolicy")),"Stock actual producer leaves NPL listeners and reads disabled");
            for(Object fact:(List<?>)call(admittedRuntime,"facts"))if("PROPERTY".equals(call(fact,"kind").toString()))
                check(propertyNames.contains(map(call(fact,"values")).get("name")),"No unselected PROPERTY in the actual TCP snapshot");
            var constraints=(List<?>)map(accepted.get("semantic")).get("constraints");
            var populations=new TreeMap<String,String>();
            if(auction)for(String id:List.of("NATIVE:OrgContext::CslV4RoleCompatibility","NATIVE:auctionGroup::AuctioneerPopulation","NATIVE:auctionGroup::ParticipantPopulation")) {
                var row=constraints.stream().map(InstalledAuthoredMain::map).filter(c->id.equals(c.get("id"))&&"INVARIANT".equals(c.get("kind"))).findFirst().orElseThrow();
                check(Set.of("PASS","FAIL").contains(row.get("verdict")),"Source-qualified current population: "+row);
                populations.put(id,row.get("verdict").toString());
            }
            if(auction)for(String id:confirmed?List.of("NATIVE:AuctionMonitor::ConfirmedSchemeOrder","NATIVE:AuctionMonitor::ConfirmedBidEvidence"):List.of("NATIVE:AuctionMonitor::SchemeOrder","NATIVE:AuctionMonitor::BidEvidence")) {
                var row=constraints.stream().map(InstalledAuthoredMain::map).filter(c->id.equals(c.get("id"))&&"INVARIANT".equals(c.get("kind"))).findFirst().orElseThrow();
                check("COMPLETE".equals(map(row.get("witness")).get("population")),"Monitor inventory closure: "+row);
                if(confirmed)check(Set.of("PASS","FAIL").contains(row.get("verdict")),"Qualified public-confirmation temporal verdict: "+row);
                else check(!Set.of("PASS","FAIL").contains(row.get("verdict")),"Inventory closure must not invent missing internal transition times");
                populations.put(id,row.get("verdict").toString());
            }
            check(constraints.stream().map(InstalledAuthoredMain::map).anyMatch(c->auction?"NATIVE:AuctionObligation::WithinDeadline".equals(c.get("id"))&&"INVARIANT_BODY".equals(c.get("kind"))&&Set.of("PASS","FAIL").contains(c.get("verdict")):"FAIL".equals(c.get("verdict"))&&Objects.toString(c.get("expression")).contains("weight")),"Native authored OCL result");
            var confirmationTimes=new TreeMap<String,Object>();
            if(confirmed)for(var object:system.state().allObjects())if(object.cls().name().equals("AuctionMonitor")) {
                var times=new TreeMap<String,Integer>();
                for(String field:List.of("startSatisfiedConfirmedAt","bidEnabledConfirmedAt","bidSatisfiedConfirmedAt","decideEnabledConfirmedAt","decideSatisfiedConfirmedAt")) {
                    var value=object.state(system.state()).attributeValue(field);
                    check(value instanceof org.tzi.use.uml.ocl.value.IntegerValue integer&&integer.value()>=0,"Actual qualified confirmation time: "+object.name()+"."+field+"="+value);
                    times.put(field,((org.tzi.use.uml.ocl.value.IntegerValue)value).value());
                }
                confirmationTimes.put(object.name(),times);
            }
            if(confirmed)check(confirmationTimes.size()==2,"Two distinct confirmed physical scheme scopes");
            Files.write(evidence.resolve("accepted.json"),(byte[])call(json,"encode",accepted));
            Path recording=Files.createDirectory(evidence.resolve("replay"));call(facade,"exportRuntimeReplay",recording);
            call(facade,"stopOwnedProducer");check(!ProcessHandle.of(producer).map(ProcessHandle::isAlive).orElse(false),"Owned producer stopped");
            for(String name:List.of(xml,ocl,csl,istar))Files.delete(selected.resolve(name));
            Object replay=loader.loadClass("org.tzi.use.plugins.jacamo.codegrounded.runtime.NativeRuntimeReplay").getConstructor().newInstance();Object report=call(replay,"replay",recording);
            check(Boolean.TRUE.equals(call(report,"complete")),"Exact frozen replay: "+call(report,"diagnostics"));
            check(accepted.get("stateHash").equals(call(report,"finalStateHash")),"Replay state hash");check(accepted.get("resultHash").equals(call(report,"finalResultHash")),"Replay result hash");
            var receipt=new TreeMap<String,Object>();receipt.put("status","PASS");receipt.put("scope","COLD_INSTALLED_AUTHORED_LIVE_AND_EXACT_REPLAY_NOT_INTERNAL_MOISE_TIME");receipt.put("case",args[1]);receipt.put("usePid",ProcessHandle.current().pid());receipt.put("producerPid",producer);receipt.put("sameMSystem",host.system()==system);receipt.put("tasks",tasks);receipt.put("originalCalls",calls);receipt.put("stateHash",accepted.get("stateHash"));receipt.put("resultHash",accepted.get("resultHash"));receipt.put("checkedReplayEntries",call(report,"checkedEntries"));receipt.put("selectedInputsDeleted",true);receipt.put("producerStopped",true);receipt.put("pluginSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
            receipt.put("currentPopulationVerdicts",populations);
            receipt.put("sourceConfirmationTimes",confirmationTimes);
            receipt.put("captureScopeHash",scopeHash);receipt.put("readSetHash",preflight.get("readSetHash"));receipt.put("captureScope",captureScope);
            receipt.put("producerNplPolicy",map(sourceEvidence.get("npl")).getOrDefault("observationPolicy","SELECTED_NPL_SOURCE"));
            receipt.put("scopedEventCount",scopedEvents);receipt.put("scopedJournalEventPayloadBytes",scopedEventPayloadBytes);
            receipt.put("measurementScope","Accepted external TCP event payloads in journal, excludes envelope/framing and historical full-mode throughput");
            Files.write(evidence.resolve("receipt.json"),(byte[])call(json,"encode",receipt));System.out.println("COLD_AUTHORED_LIVE_REPLAY_PASS "+receipt);
        }finally{((AutoCloseable)facade).close();}
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value){return value==null?Map.of():(Map<String,Object>)value;}
    private static Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Object call(Object owner,String name,Object...args)throws Exception{
        Class<?> type=owner instanceof Class<?> c?c:owner.getClass();
        for(Method m:type.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){boolean match=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!m.getParameterTypes()[i].isInstance(args[i])&&!(m.getParameterTypes()[i]==long.class&&args[i] instanceof Long))match=false;if(!match)continue;try{return m.invoke(owner instanceof Class<?>?null:owner,args);}catch(InvocationTargetException error){var cause=error.getCause();if(cause instanceof Exception e)throw e;if(cause instanceof Error e)throw e;throw error;}}
        throw new NoSuchMethodException(type+"."+name);
    }
    private static void check(boolean condition,String diagnostic){if(!condition)throw new AssertionError(diagnostic);}
}
