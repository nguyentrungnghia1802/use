package org.jacamo.bridge.adapter;

import cartago.AgentId;
import cartago.ArtifactId;
import cartago.ArtifactObsProperty;
import cartago.CartagoEnvironment;
import cartago.ICartagoController;
import cartago.ICartagoLogger;
import cartago.IEventFilter;
import cartago.Op;
import cartago.OpId;
import cartago.Tuple;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.SourceWatermark;

/** Read-only official CArtAgO controller/logger adapter with UUID identity revalidation. */
public final class CartagoSnapshotSource implements SnapshotSource, ICartagoLogger {
    private final CartagoEnvironment environment; private final List<String> workspaces; private final String sessionId;private final long generation;
    private final AtomicLong sequence=new AtomicLong(); private volatile Consumer<RuntimeEvent> observer;
    public CartagoSnapshotSource(CartagoEnvironment environment,List<String> workspaces,String sessionId){this.environment=environment;this.workspaces=List.copyOf(workspaces);this.sessionId=sessionId;this.generation=Long.parseLong(System.getProperty("jacamo.bridge.generation","0"));}
    @Override public String sourceId(){return "cartago";}
    @Override public void attach(Consumer<RuntimeEvent> observer)throws Exception{if(this.observer!=null)throw new IllegalStateException("CARTAGO_ALREADY_ATTACHED");this.observer=observer;for(String workspace:workspaces)environment.registerLogger(workspace,this);}
    @Override public SourceWatermark watermark(){return new SourceWatermark(sourceId(),sequence.get());}
    @Override public String topologyFingerprint()throws Exception{var ids=new ArrayList<String>();for(String workspace:workspaces){ICartagoController controller=environment.getController(workspace);for(AgentId id:controller.getCurrentAgents())ids.add("a:"+id.getGlobalId());for(ArtifactId id:controller.getCurrentArtifacts())ids.add("r:"+id.getId());}ids.sort(String::compareTo);return AdapterEvidence.digest(String.join("\n",ids).getBytes());}
    @Override public List<RuntimeFact> capture() throws Exception {
        var facts = new ArrayList<RuntimeFact>();
        for (String workspace : workspaces) {
            ICartagoController controller = environment.getController(workspace);
            for (AgentId agent : controller.getCurrentAgents()) {
                facts.add(new RuntimeFact(agentId(agent), RuntimeFactKind.AGENT,
                        Map.of("name", agent.getAgentName(), "role", agent.getAgentRole()), List.of(),
                        ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of()));
            }
            for (ArtifactId artifact : controller.getCurrentArtifacts()) {
                var info = controller.getArtifactInfo(artifact.getName());
                if (info == null || !artifact.equals(info.getId()))
                    throw new IllegalStateException("CARTAGO_ARTIFACT_NAME_RACE: " + artifact.getName());
                var aid = artifactId(artifact);
                facts.add(new RuntimeFact(aid, RuntimeFactKind.ARTIFACT,
                        Map.of("name", artifact.getName(), "type", artifact.getArtifactType(),
                                "workspace", artifact.getWorkspaceId().getFullName()),
                        List.of(), ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of()));
                info.getOperations().stream().sorted(Comparator.comparing(op -> op.getKeyId())).forEach(op ->
                        facts.add(new RuntimeFact(new BridgeEntityId("cartago", "environment", "operation-descriptor",
                                aid.canonical(), op.getKeyId(), artifact.getId().toString()), RuntimeFactKind.OPERATION,
                                Map.of("signature", op.getKeyId(), "dynamic", op.isDynamic(), "link", op.isLinkOperation()),
                                List.of(), ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of())));
                for (ArtifactObsProperty property : info.getObsProperties()) {
                    facts.add(new RuntimeFact(new BridgeEntityId("cartago", "environment", "observable-property",
                            aid.canonical(), property.getFullId(), artifact.getId().toString()), RuntimeFactKind.PROPERTY,
                            Map.of("name", property.getName(), "values", Arrays.stream(property.getValues()).map(String::valueOf).toList()),
                            List.of(), ProjectionStatus.EVIDENCE_ONLY, Completeness.COMPLETE, List.of()));
                }
            }
        }
        facts.sort(Comparator.comparing(fact -> fact.id().canonical()));
        return facts;
    }
    @Override public Completeness completeness(){return Completeness.COMPLETE;}
    @Override public void close()throws Exception{if(observer==null)return;for(String workspace:workspaces)environment.unregisterLogger(workspace,this);observer=null;}
    private BridgeEntityId artifactId(ArtifactId id){return new BridgeEntityId("cartago","environment","artifact",id.getWorkspaceId().getFullName(),id.getName(),id.getId().toString());}
    private BridgeEntityId agentId(AgentId id){return new BridgeEntityId("cartago","environment","agent",id.getWorkspaceId().getFullName(),id.getGlobalId(),Integer.toString(id.getLocalId()));}
    private void event(RuntimeEventKind kind,ArtifactId artifact,AgentId agent,Map<String,Object> values){Consumer<RuntimeEvent> sink=observer;if(sink==null)return;long seq=sequence.incrementAndGet();BridgeEntityId id=artifact!=null?artifactId(artifact):agentId(agent);String correlation=values.get("opId") instanceof String value?value:"";sink.accept(new RuntimeEvent(sessionId+":cartago:"+seq,sessionId,generation,System.getProperty("jacamo.bridge.modelRevision","unnegotiated"),"cartago",sourceId(),seq,Instant.now(),kind,artifact!=null?org.jacamo.bridge.contract.RuntimeFactKind.ARTIFACT:org.jacamo.bridge.contract.RuntimeFactKind.AGENT,org.jacamo.bridge.contract.ProjectionStatus.EVIDENCE_ONLY,id,null,correlation,"",Map.of(),values,new SourceWatermark(sourceId(),seq),Completeness.COMPLETE,List.of()));}
    @Override public void opRequested(long time,AgentId agent,ArtifactId artifact,Op op){event(RuntimeEventKind.STARTED,artifact,agent,Map.of("phase","requested","operation",op.getName(),"arity",op.getParamValues().length));}
    @Override public void opStarted(long time,OpId id,ArtifactId artifact,Op op){event(RuntimeEventKind.STARTED,artifact,null,Map.of("phase","started","operation",op.getName(),"opId",id.toString()));}
    @Override public void opSuspended(long time,OpId id,ArtifactId artifact,Op op){event(RuntimeEventKind.CHANGED,artifact,null,Map.of("phase","suspended","opId",id.toString()));}
    @Override public void opResumed(long time,OpId id,ArtifactId artifact,Op op){event(RuntimeEventKind.CHANGED,artifact,null,Map.of("phase","resumed","opId",id.toString()));}
    @Override public void opCompleted(long time,OpId id,ArtifactId artifact,Op op){event(RuntimeEventKind.SUCCEEDED,artifact,null,Map.of("operation",op.getName(),"opId",id.toString()));}
    @Override public void opFailed(long time,OpId id,ArtifactId artifact,Op op,String message,Tuple descriptor){event(RuntimeEventKind.FAILED,artifact,null,Map.of("operation",op.getName(),"opId",id.toString(),"message",String.valueOf(message),"descriptor",String.valueOf(descriptor)));}
    @Override public void newPercept(long time,ArtifactId artifact,Tuple signal,ArtifactObsProperty[] added,ArtifactObsProperty[] removed,ArtifactObsProperty[] changed){event(RuntimeEventKind.CHANGED,artifact,null,Map.of("signal",String.valueOf(signal),"added",Arrays.toString(added),"removed",Arrays.toString(removed),"changed",Arrays.toString(changed)));}
    @Override public void artifactCreated(long time,ArtifactId artifact,AgentId creator){event(RuntimeEventKind.CREATED,artifact,creator,Map.of());}
    @Override public void artifactDisposed(long time,ArtifactId artifact,AgentId disposer){event(RuntimeEventKind.DISPOSED,artifact,disposer,Map.of());}
    @Override public void artifactFocussed(long time,AgentId agent,ArtifactId artifact,IEventFilter filter){event(RuntimeEventKind.FOCUSED,artifact,agent,Map.of("agent",agent.getGlobalId()));}
    @Override public void artifactNoMoreFocussed(long time,AgentId agent,ArtifactId artifact){event(RuntimeEventKind.UNFOCUSED,artifact,agent,Map.of("agent",agent.getGlobalId()));}
    @Override public void artifactsLinked(long time,AgentId agent,ArtifactId source,ArtifactId target){event(RuntimeEventKind.CHANGED,source,agent,Map.of("linkedArtifact",target.getId().toString()));}
    @Override public void agentJoined(long time,AgentId agent){event(RuntimeEventKind.JOINED,null,agent,Map.of());}
    @Override public void agentQuit(long time,AgentId agent){event(RuntimeEventKind.QUIT,null,agent,Map.of());}
}
