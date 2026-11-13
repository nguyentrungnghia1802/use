package org.tzi.use.state;

import cartago.*;
import jacamo.infra.JaCaMoLauncher;
import jason.asSemantics.*;
import jason.asSyntax.*;
import jason.architecture.AgArch;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;

/** Local owned JaCaMo producer. iStar definitions and Task names never enter this process. */
public final class StateProducerMain {
    private static Gate gate;
    private static final java.io.PrintStream OUTPUT=System.out;
    private record Terminal(String token,String agent,String plan,String outcome) { }
    private static final Map<TransitionSystem,Monitor> monitors=new ConcurrentHashMap<>();
    public static void main(String[] args)throws Exception {
        if(args.length!=5)throw new IllegalArgumentException("project mapping port secret-file runtime-directory");
        Path project=Path.of(args[0]).toAbsolutePath(),work=Path.of(args[4]).toAbsolutePath();Files.createDirectories(work);
        var mapping=AttributeMapping.read(Path.of(args[1]));byte[] secret=HexFormat.of().parseHex(Files.readString(Path.of(args[3])).trim());
        for(String type:mapping.sourceClasses())if(!Artifact.class.isAssignableFrom(Class.forName(type,false,StateProducerMain.class.getClassLoader())))throw new IllegalArgumentException("MAPPED_SOURCE_NOT_ARTIFACT:"+type);
        gate=new Gate(mapping,Integer.parseInt(args[2]),secret,work);
        ExecutionBarrier.enable(gate.run);
        var launcher=new JaCaMoLauncher(){
            {runner=this;jason.runtime.RuntimeServicesFactory.set(new jacamo.infra.JaCaMoRuntimeServices(this));}
            @Override public synchronized void setupLogger(String ignored) {
                try {java.util.logging.LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(("handlers=java.util.logging.ConsoleHandler\n.level=WARNING\njava.util.logging.ConsoleHandler.level=WARNING\n").getBytes(StandardCharsets.UTF_8)));}
                catch(IOException e){throw new IllegalStateException(e);}
            }
            @Override protected void startAgs() {
                try {
                    long epoch=ExecutionBarrier.closeAdmission();
                    if(!ExecutionBarrier.awaitFrozen(epoch,Duration.ofSeconds(30)).clockFrozen())throw new IllegalStateException("INITIAL_SOURCE_BARRIER_NOT_FROZEN");
                    if(!gate.send(new Terminal(UUID.randomUUID().toString(),"","INITIAL","INITIAL")))throw new IllegalStateException("INITIAL_STATE_NOT_APPROVED");
                    ExecutionBarrier.release(epoch);
                }catch(Exception failure){throw new IllegalStateException("INITIAL_STATE_ADMISSION_FAILED",failure);}
                super.startAgs();
            }
        };
        gate.launcher=launcher;
        Runtime.getRuntime().addShutdownHook(new Thread(()->{ExecutionBarrier.stop();gate.worker.shutdownNow();}));
        if(launcher.init(new String[]{project.toString()})!=0)throw new IllegalArgumentException("JACAMO_PROJECT_INIT_FAILED");
        System.setOut(OUTPUT);System.setErr(OUTPUT);
        for(var agent:launcher.getJaCaMoProject().getAgents())agent.addArchClass(StateArchitecture.class.getName());
        launcher.create();
        for(var local:launcher.getAgs().values()) {
            if(local.getClass()!=jason.infra.local.LocalAgArch.class)throw new IllegalArgumentException("STATE_GATE_LOCAL_SCHEDULER_REQUIRED");
            local.setCycles(1);local.setCyclesAct(1);local.getTS().getSettings().setTRO(false);
            var monitor=new Monitor(local.getTS(),local.getAgName());monitors.put(local.getTS(),monitor);monitor.install();
        }
        OUTPUT.println("STATE_PRODUCER_READY run="+gate.run+" agents="+launcher.getAgs().size());
        launcher.start();
        new CountDownLatch(1).await();
    }
    public static final class StateArchitecture extends AgArch {
        @Override public void reasoningCycleStarting(){var monitor=monitors.get(getTS());if(monitor!=null)monitor.validateLibrary();super.reasoningCycleStarting();}
        @Override public void reasoningCycleFinished(){var monitor=monitors.get(getTS());if(monitor!=null)monitor.checkDropped();super.reasoningCycleFinished();}
    }
    private static final class Monitor implements GoalListener {
        private record Frame(String token,String plan,IntendedMeans means,Intention intention) { }
        private final TransitionSystem ts;private final String agent;
        private final Map<IntendedMeans,Frame> active=new IdentityHashMap<>();
        private final Map<Plan,PlanBody> admittedPlans=new IdentityHashMap<>();
        Monitor(TransitionSystem ts,String agent){this.ts=ts;this.agent=agent;}
        void install() {
            ts.addGoalListener(this);
            int ordinal=0;
            for(var plan:ts.getAg().getPL()) {
                if(plan.getBody()==null)continue;
                String definition=plan.getSrcInfo()+":"+(++ordinal)+":"+plan.getTrigger();
                var first=marker(definition,true);
                if(plan.getBody().getBodyTerm()!=null)first.setBodyNext((PlanBody)plan.getBody().clone());
                PlanBody tail=first;while(tail.getBodyNext()!=null)tail=tail.getBodyNext();tail.setBodyNext(marker(definition,false));
                plan.setTerm(3,first);
                admittedPlans.put(plan,first);
            }
        }
        void validateLibrary() {
            int count=0;
            for(var plan:ts.getAg().getPL()) {
                if(plan.getBody()==null)continue;count++;
                if(admittedPlans.get(plan)!=plan.getBody()){gate.unavailable("PLAN_LIBRARY_CHANGED");throw new IllegalStateException("STATE_GATE_PLAN_LIBRARY_CHANGED");}
            }
            if(count!=admittedPlans.size()){gate.unavailable("PLAN_LIBRARY_CHANGED");throw new IllegalStateException("STATE_GATE_PLAN_LIBRARY_CHANGED");}
        }
        private PlanBody marker(String plan,boolean entry) {
            var literal=new InternalActionLiteral(PlanMarker.class.getName());literal.setIA(new PlanMarker(this,plan,entry));
            return new PlanBodyImpl(PlanBody.BodyType.internalAction,literal);
        }
        synchronized void onMarker(String plan,boolean entry) {
            var intention=ts.getC().getSelectedIntention();if(intention==null||intention.isFinished())throw new IllegalStateException("PLAN_FRAME_MISSING");
            var means=intention.peek();
            if(entry) {
                if(active.containsKey(means))throw new IllegalStateException("PLAN_FRAME_DUPLICATE_ENTRY");
                active.put(means,new Frame(UUID.randomUUID().toString(),plan,means,intention));
            }else finish(means,"SUCCEEDED");
        }
        private void finish(IntendedMeans means,String outcome) {
            var frame=active.remove(means);if(frame!=null)gate.terminal(new Terminal(frame.token(),agent,frame.plan(),outcome));
        }
        @Override public synchronized void goalFailed(Trigger trigger,Term reason){for(var frame:new ArrayList<>(active.values()))if(frame.means().getTrigger()==trigger)finish(frame.means(),"FAILED");}
        synchronized void checkDropped() {
            var live=Collections.newSetFromMap(new IdentityHashMap<IntendedMeans,Boolean>());
            var intentions=ts.getC().getAllIntentions();while(intentions.hasNext())for(var means:intentions.next())live.add(means);
            var selected=ts.getC().getSelectedIntention();if(selected!=null)for(var means:selected)live.add(means);
            for(var frame:new ArrayList<>(active.values()))if(!live.contains(frame.means()))finish(frame.means(),"CANCELLED");
        }
    }
    private static final class PlanMarker extends DefaultInternalAction {
        private final Monitor monitor;private final String plan;private final boolean entry;
        PlanMarker(Monitor monitor,String plan,boolean entry){this.monitor=monitor;this.plan=plan;this.entry=entry;}
        @Override public Object execute(TransitionSystem ts,Unifier unifier,Term[] args){monitor.onMarker(plan,entry);return true;}
    }
    private static final class Gate {
        private final String run=UUID.randomUUID().toString();
        private final AttributeMapping mapping;private final int port;private final byte[] secret;private final Path work;
        private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"state-plan-checkpoints");t.setDaemon(true);return t;});
        private final ArrayDeque<Terminal> pending=new ArrayDeque<>();private boolean draining;private long sequence;
        private volatile boolean coverageLost;
        private JaCaMoLauncher launcher;
        private final Map<jason.infra.local.LocalAgArch,String> agentIdentities=new IdentityHashMap<>();
        Gate(AttributeMapping mapping,int port,byte[] secret,Path work){this.mapping=mapping;this.port=port;this.secret=secret;this.work=work;}
        void unavailable(String reason){coverageLost=true;terminal(new Terminal(UUID.randomUUID().toString(),"",reason,"UNAVAILABLE"));}
        synchronized void terminal(Terminal terminal) {
            pending.addLast(terminal);if(draining)return;draining=true;
            long epoch=ExecutionBarrier.closeAdmission();
            worker.execute(()->drain(epoch));
        }
        private void drain(long epoch) {
            try {
                var proof=ExecutionBarrier.awaitFrozen(epoch,Duration.ofSeconds(30));
                if(!proof.clockFrozen())throw new IllegalStateException("SOURCE_BARRIER_NOT_FROZEN");
                while(true) {
                    Terminal terminal;synchronized(this){terminal=pending.pollFirst();if(terminal==null){ExecutionBarrier.release(epoch);draining=false;return;}}
                    if(!send(terminal))return; // A held producer stays alive; there is no console/timeout resume fallback.
                }
            }catch(Exception failure){OUTPUT.println("STATE_PRODUCER_HELD "+failure);failure.printStackTrace(OUTPUT);}
        }
        boolean send(Terminal terminal)throws Exception {
            var snapshot=capture(++sequence);
            var request=Map.<String,Object>of("version",StateWire.VERSION,"token",terminal.token(),"run",run,"agent",terminal.agent(),"plan",terminal.plan(),"outcome",terminal.outcome(),"snapshot",StateWire.snapshot(snapshot));
            String requestHash=StateWire.digest(request);
            try(var socket=new Socket(InetAddress.getLoopbackAddress(),port)) {
                socket.setSoTimeout(30000);StateWire.write(socket.getOutputStream(),request,secret);
                var response=StateWire.read(socket.getInputStream(),secret);
                if(!StateWire.VERSION.equals(response.get("version"))||!terminal.token().equals(response.get("token"))||!run.equals(response.get("run"))||!requestHash.equals(response.get("requestHash")))throw new IOException("STATE_APPROVAL_IDENTITY_MISMATCH");
                Files.writeString(work.resolve("checkpoint-"+sequence+".json"),new String(org.jacamo.bridge.contract.CanonicalJson.encode(Map.of("request",request,"response",response)),StandardCharsets.UTF_8));
                boolean approved="APPROVE".equals(response.get("decision"));
                OUTPUT.println("STATE_CHECKPOINT sequence="+sequence+" agent="+terminal.agent()+" plan="+terminal.plan()+" outcome="+terminal.outcome()+" decision="+response.get("decision"));
                return approved;
            }
        }
        private CslStateVerifier.Snapshot capture(long sequence)throws Exception {
            var artifacts=new ArrayList<CslStateVerifier.Artifact>();var environment=CartagoEnvironment.getInstance();
            var nodes=new ArrayList<CslStateVerifier.Node>();var enactments=new ArrayList<CslStateVerifier.Enactment>();
            var memberships=new ArrayList<CslStateVerifier.Membership>();
            var groups=new LinkedHashMap<String,ora4mas.nopl.GroupBoard>();var organizations=new LinkedHashMap<String,ora4mas.nopl.OrgBoard>();var locations=new HashMap<String,String>();
            var agents=new HashMap<String,String>();
            for(var local:launcher.getAgs().values()) {
                String identity=agentIdentities.computeIfAbsent(local,k->UUID.randomUUID().toString());
                String source=local.getTS().getAg().getASLSrc().replace('\\','/');source=source.substring(source.lastIndexOf('/')+1).replaceFirst("\\.asl$","");
                agents.put(local.getAgName(),identity);
                nodes.add(new CslStateVerifier.Node(identity,"agent",source,local.getAgName(),"","",Map.of()));
            }
            var root=environment.getRootWSP();if(root!=null)capture(root,artifacts,groups,organizations,locations,agents);
            var orgIds=new HashMap<String,String>();
            for(var entry:organizations.entrySet()) {
                var board=entry.getValue();String type=organizationType(board);
                if(orgIds.putIfAbsent(board.getOEId(),entry.getKey())!=null)throw new IllegalStateException("ORGANIZATION_NAME_AMBIGUOUS");
                nodes.add(new CslStateVerifier.Node(entry.getKey(),"organization",type,board.getOEId(),locations.get(entry.getKey()),"",Map.of()));
            }
            for(var entry:groups.entrySet()) {
                var board=entry.getValue();var state=board.getGrpState().clone();String parent;
                if(state.getParentGroup().equals("root"))parent=orgIds.get(board.getOEId());
                else {
                    var candidates=groups.entrySet().stream().filter(e->e.getValue().getOEId().equals(board.getOEId())&&e.getValue().getArtId().equals(state.getParentGroup())).toList();
                    if(candidates.size()!=1)throw new IllegalStateException("MOISE_PARENT_GROUP_UNRESOLVED");parent=candidates.getFirst().getKey();
                }
                if(parent==null)throw new IllegalStateException("MOISE_ORGANIZATION_UNOBSERVED");
                nodes.add(new CslStateVerifier.Node(entry.getKey(),"group",board.getSpec().getId(),board.getArtId(),locations.get(entry.getKey()),parent,Map.of("wellFormed",board.isWellFormed())));
                memberships.add(new CslStateVerifier.Membership(entry.getKey(),parent));
                for(var player:state.getPlayers()) {
                    String id=agents.get(player.getAg());if(id==null)throw new IllegalStateException("MOISE_PLAYER_UNOBSERVED:"+player.getAg());
                    enactments.add(new CslStateVerifier.Enactment(entry.getKey(),id,player.getTarget()));
                }
            }
            return new CslStateVerifier.Snapshot(run,sequence,!coverageLost,artifacts,nodes,enactments,memberships);
        }
        private String organizationType(ora4mas.nopl.OrgBoard board)throws Exception {
            // Read only the local specification identity; role populations come from the live GroupBoard.
            String file=board.getOSFile();Path path;
            if(file.startsWith("file:")){var uri=java.net.URI.create(file);path=uri.isOpaque()?Path.of(uri.getSchemeSpecificPart()):Path.of(uri);}
            else path=Path.of(file);
            var factory=javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            String type=factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement().getAttribute("id");
            if(type.isBlank())throw new IllegalStateException("ORGANIZATION_SPEC_ID_REQUIRED");return type;
        }
        private void capture(WorkspaceDescriptor descriptor,List<CslStateVerifier.Artifact> result,Map<String,ora4mas.nopl.GroupBoard> groups,Map<String,ora4mas.nopl.OrgBoard> organizations,Map<String,String> locations,Map<String,String> agents)throws Exception {
            if(!descriptor.isLocal())throw new IllegalStateException("REMOTE_WORKSPACE_NOT_COVERED");
            var workspace=descriptor.getWorkspace();var controller=workspace.getController();
            for(var id:controller.getCurrentArtifacts()) {
                var actual=workspace.getArtifactDescriptor(id.getName()).getArtifact();String uuid=id.getId().toString();
                if(actual instanceof ora4mas.nopl.GroupBoard group){groups.put(uuid,group);locations.put(uuid,descriptor.getId().getFullName());}
                if(actual instanceof ora4mas.nopl.OrgBoard organization){organizations.put(uuid,organization);locations.put(uuid,descriptor.getId().getFullName());}
                if(!mapping.sourceClasses().contains(id.getArtifactType()))continue;
                var info=controller.getArtifactInfo(id.getName());var properties=new TreeMap<String,Object>();
                for(var property:info.getObsProperties())if(property.getValues().length==1) {
                    Object value=property.getValue();if(value instanceof Atom atom) {
                        if(atom.getArity()!=0||atom.negated()||atom.hasAnnot())throw new IllegalArgumentException("STRUCTURED_PROPERTY_REQUIRES_TYPED_CONVERTER:"+property.getName());
                        value=atom.getFunctor();
                    }
                    if(value instanceof String||value instanceof Boolean||value instanceof Number)properties.put(property.getName(),value);
                }
                if(info.getObservers()==null)throw new IllegalStateException("ARTIFACT_FOCUS_OBSERVATION_UNAVAILABLE");
                var observers=new HashSet<String>();
                for(var observer:info.getObservers()){String agent=agents.get(observer.getAgentId().getAgentName());if(agent!=null)observers.add(agent);}
                result.add(new CslStateVerifier.Artifact(id.getId().toString(),id.getArtifactType(),id.getName(),descriptor.getId().getFullName(),properties,observers));
            }
            for(var child:workspace.getChildWSPs())capture(child,result,groups,organizations,locations,agents);
        }
    }
}
