package org.jacamo.bridge.adapter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import npl.*;
import ora4mas.nopl.NormativeBoard;
import org.jacamo.bridge.contract.*;

/** Authoritative reported lifecycle observations. EVIDENCE_ONLY remains distinct from deontic equivalence. */
public final class NplBoardSnapshotSource implements SnapshotSource {
    private final Supplier<? extends Collection<? extends NormativeBoard>> boards;
    private final String sessionId;private final long generation;
    private final AtomicLong sequence=new AtomicLong();
    private final Map<String,Attached> attached=new LinkedHashMap<>();
    private volatile Consumer<RuntimeEvent> observer;
    private volatile Completeness completeness=Completeness.PARTIAL;
    private List<String> diagnostics=List.of();
    public synchronized Map<String,Object> captureMetadata(){return Map.of("selectedBoardUuids",attached.keySet().stream().sorted().toList(),"globalPopulationProof","UNAVAILABLE_SELECTED_BOARDS_ONLY","diagnostics",diagnostics,"nplRuntime",NplRuntimeProfile.evidence());}
    public NplBoardSnapshotSource(String sessionId,Supplier<? extends Collection<? extends NormativeBoard>> boards){NplRuntimeProfile.requireSupported();this.sessionId=Objects.requireNonNull(sessionId);this.boards=Objects.requireNonNull(boards);generation=Long.parseLong(System.getProperty("jacamo.bridge.generation","0"));}
    public String sourceId(){return "npl";}
    public synchronized void attach(Consumer<RuntimeEvent> observer){if(this.observer!=null)throw new IllegalStateException("NPL_ALREADY_ATTACHED");this.observer=Objects.requireNonNull(observer);refresh();}
    public SourceWatermark watermark(){return new SourceWatermark(sourceId(),sequence.get());}
    public synchronized String topologyFingerprint(){refresh();var topology=new TreeSet<String>();for(var value:attached.values())topology.add(value.scope+":"+value.uuid+":"+value.interpreter.getActivatedNorms());return AdapterEvidence.digest(String.join("\n",topology).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public synchronized List<RuntimeFact> capture(){refresh();var result=new ArrayList<RuntimeFact>();for(var board:attached.values()) {add(result,board,"ACTIVE",board.interpreter.getActive());add(result,board,"FULFILLED",board.interpreter.getFulfilled());add(result,board,"UNFULFILLED",board.interpreter.getUnFulfilled());add(result,board,"INACTIVE",board.interpreter.getInactive());}var unique=new TreeMap<String,List<RuntimeFact>>();for(var fact:result)unique.computeIfAbsent(fact.id().canonical(),k->new ArrayList<>()).add(fact);var folded=new ArrayList<RuntimeFact>();for(var rows:unique.values())if(rows.size()==1)folded.add(rows.getFirst());else {var first=rows.getFirst();folded.add(new RuntimeFact(first.id(),RuntimeFactKind.NORM_INSTANCE,Map.of("observationVersion","NPL_OBSERVATION/1.0","activationIdentityStatus","AMBIGUOUS_NORM_INSTANCE","candidateObservations",rows.stream().map(RuntimeFact::values).toList()),List.of(),ProjectionStatus.EVIDENCE_ONLY,Completeness.PARTIAL,List.of()));}return List.copyOf(folded);}
    private void add(List<RuntimeFact> facts,Attached board,String state,List<NormInstance> values){for(var value:values){var payload=board.crosswalk.observe(board.uuid,value.copy(),"SNAPSHOT",state);facts.add(new RuntimeFact(identity(board,payload),RuntimeFactKind.NORM_INSTANCE,payload,List.of(),ProjectionStatus.EVIDENCE_ONLY,quality(payload),List.of()));}}
    private Completeness quality(Map<String,Object> payload){return "BOUND_PUBLIC_CREATED_ANNOTATION".equals(payload.get("activationIdentityStatus"))?Completeness.COMPLETE:Completeness.PARTIAL;}
    private BridgeEntityId identity(Attached board,Map<String,Object> payload){String key=Objects.toString(payload.get("activationKey"),"");if(key.isBlank())key="unresolved:"+AdapterEvidence.digest(CanonicalJson.encode(payload));return new BridgeEntityId("npl","organisation","norm-instance",board.scope,key,sessionId+":"+board.uuid);}
    private void refresh(){
        var alive=new HashSet<String>();boolean complete=true;var errors=new ArrayList<String>();
        for(var board:boards.get()) {if(board==null || board.getNormativeEngine()==null){complete=false;errors.add("NPL_BOARD_OR_INTERPRETER_UNAVAILABLE");continue;}
            try {var info=MoiseBoardArtifactObservation.capture(board);String uuid=info.getId().getId().toString();alive.add(uuid);if(attached.containsKey(uuid))continue;var value=new Attached(info.getId().getWorkspaceId().getFullName()+":"+board.getArtId(),uuid,board.getNormativeEngine());value.interpreter.addListener(value);attached.put(uuid,value);}
            catch(Exception unavailable){complete=false;errors.add("NPL_BOARD_CAPTURE_UNAVAILABLE:"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage());}
        }
        for(String uuid:new ArrayList<>(attached.keySet()))if(!alive.contains(uuid)){var old=attached.remove(uuid);old.interpreter.removeListener(old);}
        // Only selected discovered boards are observed; no global normative population proof.
        if(complete && alive.isEmpty())errors.add("NPL_NO_SELECTED_BOARDS_GLOBAL_POPULATION_UNPROVEN");
        completeness=complete?Completeness.PARTIAL:Completeness.UNAVAILABLE;diagnostics=List.copyOf(errors);
    }
    public Completeness completeness(){return completeness;}
    public synchronized void close(){attached.values().forEach(a->a.interpreter.removeListener(a));attached.clear();observer=null;}
    private final class Attached implements NormativeListener {
        final String scope,uuid;final NPLInterpreter interpreter;final NplActivationCrosswalk crosswalk=new NplActivationCrosswalk();
        Attached(String scope,String uuid,NPLInterpreter interpreter){this.scope=scope;this.uuid=uuid;this.interpreter=interpreter;}
        public void created(NormInstance value){emit(this,value,RuntimeEventKind.NORM_ACTIVATED,"ACTIVE","LISTENER_CREATED");}
        public void fulfilled(NormInstance value){emit(this,value,RuntimeEventKind.NORM_FULFILLED,"FULFILLED","LISTENER_TERMINAL");}
        public void unfulfilled(NormInstance value){emit(this,value,RuntimeEventKind.NORM_UNFULFILLED,"UNFULFILLED","LISTENER_TERMINAL");}
        public void inactive(NormInstance value){emit(this,value,RuntimeEventKind.NORM_INACTIVATED,"INACTIVE","LISTENER_TERMINAL");}
    }
    private synchronized void emit(Attached board,NormInstance value,RuntimeEventKind kind,String state,String origin){if(observer==null)return;var payload=board.crosswalk.observe(board.uuid,value.copy(),origin,state);long next=sequence.incrementAndGet();observer.accept(new RuntimeEvent(sessionId+":npl:"+next,sessionId,generation,System.getProperty("jacamo.bridge.modelRevision","unnegotiated"),"npl",sourceId(),next,Instant.now(),kind,RuntimeFactKind.NORM_INSTANCE,ProjectionStatus.EVIDENCE_ONLY,identity(board,payload),null,"","",Map.of(),payload,new SourceWatermark(sourceId(),next),quality(payload),List.of()));}
}
