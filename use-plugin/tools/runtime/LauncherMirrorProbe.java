import jacamo.infra.JaCaMoLauncher;
import cartago.*;

import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.tzi.use.plugins.jacamo.extraction.StaticProjectImporter;
import org.tzi.use.plugins.jacamo.mapping.*;
import org.tzi.use.plugins.jacamo.materialization.*;
import org.tzi.use.plugins.jacamo.runtime.*;
import org.tzi.use.plugins.jacamo.semantic.*;
import org.tzi.use.plugins.jacamo.trace.*;

/** Trusted derived control only. Launches real .jcm; never claims equivalence to Auction OS. */
public class LauncherMirrorProbe {
    static RuntimeMirrorService mirror;
    static MoiseRuntimeConnector moise;
    static CompositeRuntimeConnector composite;
    static RuntimeMutationEngine mutations;
    static final List<RuntimeEvent> events = new CopyOnWriteArrayList<>();
    static final List<Map<String,Object>> outcomes = new CopyOnWriteArrayList<>();
    static final List<RuntimeSnapshot> snapshots = new CopyOnWriteArrayList<>();
    static final List<String> checkpoints = new CopyOnWriteArrayList<>();
    static final List<Map<String,Object>> callbacks = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        Path module = Path.of(args[1]).toAbsolutePath(), output = Path.of(args[2]);
        var imported = new StaticProjectImporter().importProject(module.resolve("src/test/resources/auction/auction.jcm"));
        var semantic = imported.model();
        var mapping = new MappingLoader().loadCanonical(module);
        var structure = new TransformationPlanner().plan(semantic, mapping);
        var instances = new InstancePlanner().plan(semantic, mapping, structure);
        var generated = new TextBackend().generate("launcher_control", structure, instances);
        var direct = new DirectUseBackend().materialize(generated, instances);
        var trace = new TraceBuilder().build(semantic, mapping, structure, instances);
        var runtimeMapping = new RuntimeMappingLoader().loadDefault();
        String agent = id(semantic, MetamodelKind.Agent,"auctioneer");
        String artifact = id(semantic, MetamodelKind.Artifact,"auction1");
        var binding = new MoiseRuntimeBinding("auction_org", id(semantic,MetamodelKind.Organisation,"auction_org"),
                Map.of("auctioneer",agent), Map.of("auction_group",id(semantic,MetamodelKind.Group,"auction_group")),
                Map.of("auction_scheme",id(semantic,MetamodelKind.Scheme,"auction_scheme")));
        var artifactBinding = new CartagoArtifactBinding("/main/market","auction1",artifact,
                Map.of("open","open"),Map.of("placeBid","placeBid","closeAuction","closeAuction"));
        register(trace,agent,"jason:agent:auctioneer"); register(trace,agent,binding.agentRuntimeId("auctioneer"));
        register(trace,artifact,artifactBinding.runtimeSourceId());
        register(trace,binding.organisationSemanticId(),binding.organisationRuntimeId());
        binding.groups().forEach((k,v)->register(trace,v,binding.groupRuntimeId(k)));
        binding.schemes().forEach((k,v)->register(trace,v,binding.schemeRuntimeId(k)));
        JaCaMoLauncher launcher = new JaCaMoLauncher() {
            { // Same bootstrap as pinned JaCaMoLauncher.main, without interactive registries.
                runner = this;
                jason.runtime.RuntimeServicesFactory.set(new jacamo.infra.JaCaMoRuntimeServices(this));
            }
            protected java.io.InputStream getDefaultLogProperties() {
                return new java.io.ByteArrayInputStream("handlers=java.util.logging.ConsoleHandler\n.level=INFO\n".getBytes());
            }
            protected void startAgs() {
                var env = CartagoEnvironment.getInstance();
                moise = MoiseRuntimeConnector.forBoards("launcher-boards",binding,new MoiseBoardSnapshotSource("auction_org",
                    ()->ora4mas.nopl.GroupBoard.getGroupBoards().stream().filter(b->"auction_org".equals(b.getOEId())).toList(),
                    ()->ora4mas.nopl.SchemeBoard.getSchemeBoards().stream().filter(b->"auction_org".equals(b.getOEId())).toList()));
                var cartago = new CartagoRuntimeConnector("launcher-cartago",new RecordingAccess(env),List.of(artifactBinding));
                var jason = new JasonRuntimeConnector("launcher-jason",Map.of("auctioneer",getAg("auctioneer").getTS()),Map.of("auctioneer",agent));
                composite = new CompositeRuntimeConnector("launcher",List.of(jason,cartago,moise));
                mutations = new RuntimeMutationEngine(direct.system(),trace);
                mirror = new RuntimeMirrorService(composite,mutations,1024,new RuntimeEventObserver() {
                    public void eventReceived(RuntimeEvent e) { events.add(e); }
                    public void snapshotApplied(RuntimeSnapshot s) { snapshots.add(s); }
                    public void afterMutation(RuntimeEvent e, MutationResult r) {
                        var rule = runtimeMapping.select(e);
                        outcomes.add(Map.of("eventId",e.eventId(),"status",r.status().name(),
                            "diagnostic",String.valueOf(r.diagnostic()), "mappingRule",rule.id(),
                            "action",rule.action().name(), "runtimeSource",e.runtimeSourceId(),
                            "semanticSource",String.valueOf(e.semanticSourceId()),
                            "targets",trace.bySemanticId(e.semanticSourceId()).stream().map(t->t.targetUseId()).toList()));
                    }
                });
                mirror.connect(URI.create("jacamo://local/launcher-control"));
                require(mirror.state()==MirrorState.LIVE,"INITIAL_LIVE");
                super.startAgs();
            }
        };
        int exit=3;
        try {
            require(launcher.init(new String[]{args[0],"--no-net"})==0,"PARSE");
            launcher.create(); launcher.start();
            waitFor(()->ora4mas.nopl.GroupBoard.getGroupBoards().stream()
                    .anyMatch(b->"auction_org".equals(b.getOEId()) && b.getGrpState().hasPlayer("auctioneer","auctioneer")),"ROLE_READY");
            require(moise.discoveredGroups().equals(List.of("auction_group")),"EXACT_GROUP");
            require(moise.discoveredSchemes().equals(List.of("auction_scheme")),"EXACT_SCHEME");
            moise.pollChanges(); mirror.awaitIdle(Duration.ofSeconds(5));
            // Input triggers a checked-in probe-only AgentSpeak plan. Artifact calls are performed by Jason.
            launcher.getAg("auctioneer").getTS().getC().addAchvGoal(jason.asSyntax.ASSyntax.createLiteral("probe"),jason.asSemantics.Intention.EmptyInt);
            launcher.getAg("auctioneer").wake();
            waitFor(()->launcher.getAg("auctioneer").getTS().getAg().getBB()
                    .contains(jason.asSyntax.ASSyntax.createLiteral("probe_done"))!=null,"AGENT_SCENARIO_DONE");
            require(ora4mas.nopl.SchemeBoard.getSchemeBoards().stream().anyMatch(b->
                "auction_org".equals(b.getOEId()) && "auction_scheme".equals(b.getArtId())
                && b.getSchState().hasPlayer("auctioneer","run_auction")
                && b.getSchState().isSatisfied(b.getSpec().getGoal("sell_item"))),"MISSION_AND_GOAL_READY");
            moise.pollChanges();
            mirror.awaitIdle(Duration.ofSeconds(5));
            launcher.getAg("auctioneer").getTS().getC().addAchvGoal(jason.asSyntax.ASSyntax.createLiteral("probe_failure"),jason.asSemantics.Intention.EmptyInt);
            launcher.getAg("auctioneer").wake();
            waitFor(()->launcher.getAg("auctioneer").getTS().getAg().getBB()
                    .contains(jason.asSyntax.ASSyntax.createLiteral("failure_seen"))!=null,"AGENT_FAILURE_HANDLED");
            mirror.awaitIdle(Duration.ofSeconds(5));
            require(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.OP_FAIL),"OP_FAIL");
            require(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.OP_ENTER),"OP_ENTER");
            require(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.OP_EXIT),"OP_EXIT");
            require(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.OBS_PROPERTY_CHANGED),"PROPERTY_CHANGE");
            require(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.ROLE_ADOPTED),"ROLE_DELTA");
            require(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.MISSION_COMMITTED),"MISSION_DELTA");
            require(events.stream().anyMatch(e->e.kind()==RuntimeEventKind.SCHEME_STATE_CHANGED
                && "satisfied".equals(e.payload().get("state"))),"GOAL_DELTA");
            require(!mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY).drifted(),"INITIAL_DRIFT");
            mirror.disconnect();
            int before=events.size();
            // Exact trusted fixture stimulus while disconnected; no plugin-initiated business action.
            launcher.getAg("auctioneer").getTS().getC().addAchvGoal(jason.asSyntax.ASSyntax.createLiteral("probe_disconnected"),jason.asSemantics.Intention.EmptyInt);
            launcher.getAg("auctioneer").wake();
            waitFor(()->launcher.getAg("auctioneer").getTS().getAg().getBB()
                    .contains(jason.asSyntax.ASSyntax.createLiteral("disconnected_done"))!=null,"DISCONNECTED_MUTATION");
            require(events.size()==before,"DISCONNECTED_LISTENER");
            mirror.connect(URI.create("jacamo://local/launcher-control"));
            require(mirror.state()==MirrorState.LIVE,"RECONNECT_LIVE");
            require(!mirror.checkDrift(DriftResyncPolicy.REPORT_ONLY).drifted(),"RECONNECT_DRIFT");
            var metrics=mirror.metrics();
            require(metrics.failed()==0 && metrics.rejected()==0 && metrics.dropped()==0,"QUEUE");
            require(outcomes.stream().allMatch(o->"APPLIED".equals(o.get("status"))),"ALL_MUTATIONS_ACCOUNTED");
            Files.createDirectories(output);
            Files.writeString(output.resolve("model.use"),generated.useModel());
            Files.writeString(output.resolve("initial-state.cmd"),generated.initialCommands());
            new RuntimeEventCodec().writeEvents(output.resolve("runtime-events.json"),events);
            var json=new ObjectMapper();
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("outcomes.json").toFile(),outcomes);
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("checkpoints.json").toFile(),checkpoints);
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("raw-callbacks.json").toFile(),callbacks);
            List<RuntimeEvent> initial = snapshots.stream().flatMap(s->s.mutations().stream()).toList();
            new RuntimeEventCodec().writeEvents(output.resolve("snapshot-events.json"),initial);
            new org.tzi.use.plugins.jacamo.trace.TraceStore().write(output.resolve("trace.json"),trace);
            json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(),Map.of(
                "status","SUPPORTED_SUBSET_COMPLETE","originalAuctionEquivalent",false,
                "launcher","JaCaMo 1.3.0 .jcm","operationDriver","actual Jason agent",
                "organisationSource","actual launcher GroupBoard/SchemeBoard; no independent OE",
                "snapshots",snapshots.size(),"events",events.size(),"unexplainedDrift",0,
                "scope","Derived control only; original plan/deadline semantics remain unproven"));
            System.out.println("PHASE20_LAUNCHER_MIRROR_CONTROL_PASS");
            exit=0;
        } catch(Throwable e) {
            e.printStackTrace();
            var diagnostics = new LinkedHashMap<String,Object>();
            diagnostics.put("failure",e.toString()); diagnostics.put("checkpoints",checkpoints);
            diagnostics.put("mirrorState",mirror==null ? "NOT_ATTACHED" : mirror.state().name());
            diagnostics.put("groups",ora4mas.nopl.GroupBoard.getGroupBoards().stream()
                .map(b->b.getOEId()+"/"+b.getArtId()+":"+b.getGrpState()).toList());
            diagnostics.put("schemes",ora4mas.nopl.SchemeBoard.getSchemeBoards().stream()
                .map(b->b.getOEId()+"/"+b.getArtId()+":"+b.getSchState()).toList());
            diagnostics.put("agentBeliefs",launcher.getAg("auctioneer")==null ? "ABSENT"
                : launcher.getAg("auctioneer").getTS().getAg().getBB().toString());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("failure.json").toFile(),diagnostics);
        }
        finally {
            try { if(mirror!=null) { mirror.close(); require(composite.state()==ConnectorState.DISCONNECTED,"CONNECTOR_CLEANUP"); } }
            catch(Throwable e) { e.printStackTrace(); exit=3; }
            try { launcher.finish(0,false,0); } catch(Throwable e) { e.printStackTrace(); exit=3; }
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("checkpoints.json").toFile(),checkpoints);
        System.exit(exit); // Isolated process exit is containment, not proof of upstream thread quiescence.
    }
    static String id(JaCaMoSemanticModel m,MetamodelKind k,String name) {
        return m.elements().stream().filter(e->e.kind()==k && e.name().equals(name)).findFirst().orElseThrow().id().value();
    }
    static void register(TraceIndex trace,String semantic,String runtime) {
        var record=trace.bySemanticId(semantic).stream().filter(r->"OBJECT".equals(r.targetKind())).findFirst().orElseThrow();
        trace.registerRuntimeKey(record.traceId(),runtime);
    }
    static void require(boolean pass,String code) {
        if(!pass)throw new IllegalStateException("PHASE20_CONTROL_"+code);
        checkpoints.add(code);
    }
    /** Decorates the one connector-owned logger; does not register a second listener. */
    static final class RecordingAccess implements CartagoRuntimeAccess {
        final OfficialCartagoRuntimeAccess delegate;
        final Map<ICartagoLogger,ICartagoLogger> wrappers = new IdentityHashMap<>();
        RecordingAccess(CartagoEnvironment env) { delegate = new OfficialCartagoRuntimeAccess(env); }
        public ICartagoController controller(String workspace) throws CartagoException { return delegate.controller(workspace); }
        public synchronized void registerLogger(String workspace, ICartagoLogger logger) throws CartagoException {
            var wrapper = (ICartagoLogger)java.lang.reflect.Proxy.newProxyInstance(
                ICartagoLogger.class.getClassLoader(),new Class<?>[]{ICartagoLogger.class},(proxy,method,args)->{
                    if(method.getDeclaringClass()==Object.class) return switch(method.getName()) {
                        case "equals" -> proxy==args[0]; case "hashCode" -> System.identityHashCode(proxy);
                        default -> "Phase20RecordingLogger";
                    };
                    callbacks.add(Map.of("category",method.getName(),"workspace",workspace,
                        "receivedAt",java.time.Instant.now().toString(),"arguments",
                        args==null ? List.of() : Arrays.stream(args).map(LauncherMirrorProbe::rawValue).toList()));
                    try { return method.invoke(logger,args); }
                    catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                });
            require(!wrappers.containsKey(logger),"LOGGER_UNIQUE");
            delegate.registerLogger(workspace,wrapper); wrappers.put(logger,wrapper);
        }
        public synchronized void unregisterLogger(String workspace, ICartagoLogger logger) throws CartagoException {
            var wrapper = wrappers.get(logger);
            require(wrapper!=null,"LOGGER_REGISTERED");
            delegate.unregisterLogger(workspace,wrapper); wrappers.remove(logger);
        }
    }
    static Object rawValue(Object value) {
        if(value==null) return "null";
        if(value instanceof Number || value instanceof Boolean || value instanceof String) return value;
        if(value instanceof Object[] array) return Arrays.stream(array).map(LauncherMirrorProbe::rawValue).toList();
        if(value instanceof Op o) return Map.of("name",o.getName(),"parameters",rawValue(o.getParamValues()));
        if(value instanceof ArtifactObsProperty p) return Map.of("name",p.getName(),"values",rawValue(p.getValues()));
        if(value instanceof ArtifactId a) return Map.of("name",a.getName(),"id",a.getId().toString(),"workspace",a.getWorkspaceId().getFullName());
        if(value instanceof AgentId a) return Map.of("name",a.getAgentName(),"id",a.getGlobalId());
        if(value instanceof OpId o) return Map.of("id",o.getId(),"operation",o.getOpName(),"artifact",rawValue(o.getArtifactId()),"agent",rawValue(o.getAgentBodyId()));
        return value.toString();
    }
    static void waitFor(java.util.function.BooleanSupplier condition,String code) throws Exception {
        long end=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        while(!condition.getAsBoolean() && System.nanoTime()<end)Thread.sleep(10);
        require(condition.getAsBoolean(),code);
    }
}
