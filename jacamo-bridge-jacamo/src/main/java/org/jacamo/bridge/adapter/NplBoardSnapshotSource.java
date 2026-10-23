package org.jacamo.bridge.adapter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import npl.*;
import ora4mas.nopl.OrgArt;
import cartago.*;
import org.jacamo.bridge.contract.*;

/** Authoritative reported lifecycle observations. EVIDENCE_ONLY remains distinct from deontic equivalence. */
public final class NplBoardSnapshotSource implements SnapshotSource {
    private final Supplier<? extends Collection<? extends OrgArt>> boards;
    private final String sessionId;private final long generation;private final Supplier<String> revision;
    private final AtomicLong sequence=new AtomicLong();
    private final Map<String,Attached> attached=new LinkedHashMap<>();
    private volatile Consumer<RuntimeEvent> observer;
    private volatile Completeness completeness=Completeness.PARTIAL;
    private List<String> diagnostics=List.of();
    private boolean closed;private long subscriptions;
    public synchronized Map<String,Object> captureMetadata(){return Map.of("selectedBoardUuids",attached.keySet().stream().sorted().toList(),"selectedSubscriptions",attached.values().stream().map(a->a.proof).toList(),"globalPopulationProof","UNAVAILABLE_SELECTED_BOARDS_ONLY","diagnostics",diagnostics,"nplRuntime",NplRuntimeProfile.evidence());}
    public NplBoardSnapshotSource(String sessionId,Supplier<? extends Collection<? extends OrgArt>> boards){this(sessionId,Long.parseLong(System.getProperty("jacamo.bridge.generation","0")),()->System.getProperty("jacamo.bridge.modelRevision","unnegotiated"),boards);}
    public NplBoardSnapshotSource(String sessionId,long generation,String revision,Supplier<? extends Collection<? extends OrgArt>> boards){this(sessionId,generation,()->revision,boards);if(revision==null||revision.isBlank())throw new IllegalArgumentException("NPL_SOURCE_REVISION_REQUIRED");}
    private NplBoardSnapshotSource(String sessionId,long generation,Supplier<String> revision,Supplier<? extends Collection<? extends OrgArt>> boards){NplRuntimeProfile.requireSupported();this.sessionId=Objects.requireNonNull(sessionId);this.boards=Objects.requireNonNull(boards);if(sessionId.isBlank()||generation<0)throw new IllegalArgumentException("NPL_SOURCE_EPOCH_REQUIRED");this.generation=generation;this.revision=revision;}
    public String sourceId(){return "npl";}
    public synchronized void attach(Consumer<RuntimeEvent> observer){if(closed||this.observer!=null)throw new IllegalStateException("NPL_ALREADY_ATTACHED_OR_CLOSED");this.observer=Objects.requireNonNull(observer);refresh();}
    public SourceWatermark watermark(){return new SourceWatermark(sourceId(),sequence.get());}
    public synchronized String topologyFingerprint(){refresh();var topology=new TreeSet<String>();for(var value:attached.values())topology.add(value.scope+":"+value.uuid+":"+value.interpreter.getActivatedNorms());return AdapterEvidence.digest(String.join("\n",topology).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public synchronized List<RuntimeFact> capture(){refresh();var result=new ArrayList<RuntimeFact>();for(var board:attached.values()) {add(result,board,"ACTIVE",board.interpreter.getActive());add(result,board,"FULFILLED",board.interpreter.getFulfilled());add(result,board,"UNFULFILLED",board.interpreter.getUnFulfilled());add(result,board,"INACTIVE",board.interpreter.getInactive());}var unique=new TreeMap<String,List<RuntimeFact>>();for(var fact:result)unique.computeIfAbsent(fact.id().canonical(),k->new ArrayList<>()).add(fact);var folded=new ArrayList<RuntimeFact>();for(var rows:unique.values())if(rows.size()==1)folded.add(rows.getFirst());else {var first=rows.getFirst();folded.add(new RuntimeFact(first.id(),RuntimeFactKind.NORM_INSTANCE,Map.of("observationVersion","NPL_OBSERVATION/1.0","activationIdentityStatus","AMBIGUOUS_NORM_INSTANCE","candidateObservations",rows.stream().map(RuntimeFact::values).toList()),List.of(),ProjectionStatus.EVIDENCE_ONLY,Completeness.PARTIAL,List.of()));}return List.copyOf(folded);}
    private void add(List<RuntimeFact> facts,Attached board,String state,List<NormInstance> values){for(var value:values){var payload=payload(board,value.copy(),"SNAPSHOT",state,sequence.get());facts.add(new RuntimeFact(identity(board,payload),RuntimeFactKind.NORM_INSTANCE,payload,List.of(),ProjectionStatus.EVIDENCE_ONLY,quality(payload),List.of()));}}
    private Completeness quality(Map<String,Object> payload){return "BOUND_PUBLIC_CREATED_ANNOTATION".equals(payload.get("activationIdentityStatus"))?Completeness.COMPLETE:Completeness.PARTIAL;}
    private BridgeEntityId identity(Attached board,Map<String,Object> payload){String key=Objects.toString(payload.get("activationKey"),"");if(key.isBlank())key="unresolved:"+AdapterEvidence.digest(CanonicalJson.encode(payload));return new BridgeEntityId("npl","organisation","norm-instance",board.scope,key,sessionId+":"+board.uuid);}
    private void refresh(){
        if(closed)return;
        var alive=new HashSet<String>();boolean complete=true;var errors=new ArrayList<String>();
        // Framework registries are mutable ArrayLists. Iterate a detached array,
        // never their fail-fast iterator. Discovery remains globally PARTIAL.
        for(var board:boards.get().toArray(OrgArt[]::new)) {if(board==null || board.getNormativeEngine()==null){complete=false;errors.add("NPL_BOARD_OR_INTERPRETER_UNAVAILABLE");continue;}
            try {var info=MoiseBoardArtifactObservation.capture(board);String uuid=info.getId().getId().toString();
                var interpreter=board.getNormativeEngine();
                // OrgArt publishes nengine before NPLInterpreter.init creates
                // its agent/BB. Test the actual public read before admission.
                // A published reference alone is not engine readiness.
                interpreter.getActive();alive.add(uuid);var old=attached.get(uuid);
                if(old!=null&&old.interpreter==interpreter)continue;
                bind(board,info.getId(),interpreter,false);}
            catch(Exception unavailable){complete=false;errors.add("NPL_BOARD_CAPTURE_UNAVAILABLE:"+unavailable.getClass().getSimpleName()+":"+unavailable.getMessage());}
        }
        for(String uuid:new ArrayList<>(attached.keySet()))if(!alive.contains(uuid)){var old=attached.remove(uuid);old.interpreter.removeListener(old);}
        // Only selected discovered boards are observed; no global normative population proof.
        if(complete && alive.isEmpty())errors.add("NPL_NO_SELECTED_BOARDS_GLOBAL_POPULATION_UNPROVEN");
        completeness=complete?Completeness.PARTIAL:Completeness.UNAVAILABLE;diagnostics=List.copyOf(errors);
    }
    /** Invoked by the actual CArtAgO creation logger before makeArtifact returns.
     * The pinned immediate lock guards the listener registration/empty baseline;
     * inability to obtain it is not silently promoted to continuous history. */
    public void artifactCreated(ArtifactId id) {
        try {
            var env=CartagoEnvironment.getInstance();var workspace=env.resolveWSP(id.getWorkspaceId().getFullName()).getWorkspace();var descriptor=workspace.getArtifactDescriptor(id.getName());
            if(descriptor==null||!(descriptor.getArtifact() instanceof OrgArt))return;
            var type=descriptor.getArtifact().getClass().asSubclass(OrgArt.class);
            CartagoArtifactReadScope.read(env,id,type,(board,actual,held)->{
                synchronized(this) {
                    if(closed||observer==null)return false;var engine=board.getNormativeEngine();if(engine==null)throw new IllegalStateException("NPL_ENGINE_UNAVAILABLE_AT_CREATION");engine.getActive();
                    bind(board,id,engine,true);return true;
                }
            });
        }catch(Exception|LinkageError unavailable){synchronized(this){if(!closed){completeness=Completeness.UNAVAILABLE;diagnostics=List.of("NPL_EARLY_SUBSCRIPTION_UNAVAILABLE:"+unavailable.getClass().getSimpleName());}}}
    }
    private void bind(OrgArt board,ArtifactId id,NPLInterpreter interpreter,boolean creation) {
        String uuid=id.getId().toString();var old=attached.get(uuid);if(old!=null&&old.interpreter==interpreter)return;
        if(old!=null)old.interpreter.removeListener(old);if(old==null&&attached.size()>=1024)throw new IllegalStateException("NPL_SELECTED_BOARD_CAPACITY");
        var value=new Attached(id.getWorkspaceId().getFullName()+":"+board.getArtId(),uuid,interpreter);
        attached.put(uuid,value);interpreter.addListener(value);
        boolean empty=creation&&interpreter.getActive().isEmpty()&&interpreter.getFulfilled().isEmpty()&&interpreter.getUnFulfilled().isEmpty()&&interpreter.getInactive().isEmpty()&&interpreter.getActivatedNorms().isEmpty();
        value.proof=Map.of("version","NPL_BOARD_SUBSCRIPTION/1.0","boardArtifactIdentity",uuid,"artifactName",id.getName(),"artifactClass",id.getArtifactType(),"workspaceUuid",id.getWorkspaceId().getUUID().toString(),"workspaceFullName",id.getWorkspaceId().getFullName(),"sourceSubscriptionNumber",++subscriptions,
                "origin",creation?"CARTAGO_CREATION_BEFORE_RETURN":"PUBLIC_ENGINE_DISCOVERY","emptyBaselineAfterListener",empty,"scopeQuality",empty?"CONTINUOUS_FROM_EMPTY_CREATION":"PARTIAL_LATE_OR_NONEMPTY_BASELINE");
    }
    private Map<String,Object> payload(Attached board,NormInstance value,String origin,String state,long sourceSequence) {
        var result=new LinkedHashMap<String,Object>(board.crosswalk.observe(board.uuid,value,origin,state));result.put("boardObservationProof",board.proof);result.put("sourceSequence",sourceSequence);return JasonStructuredTerms.immutable(result);
    }
    public Completeness completeness(){return completeness;}
    /** Scheduling hint only, never a completeness/absence proof. Reuse the
     * already selected engines; no calls that activate/update norms. */
    public synchronized boolean hasPendingTimedObligations() {
        refresh();int inspected=0;boolean unknown=completeness==Completeness.UNAVAILABLE;
        for(var board:attached.values())try {for(var value:board.interpreter.getActive()) {
                if(++inspected>8192)return true; // Unknown remainder still needs clock observations.
                if(value.isObligation()&&value.getTerm(3) instanceof TimeTerm)return true;
            }
        }catch(RuntimeException unavailable) {
            completeness=Completeness.UNAVAILABLE;diagnostics=List.of("NPL_ENGINE_READ_UNAVAILABLE:"+board.uuid+":"+unavailable.getClass().getSimpleName());unknown=true;
        }
        return unknown;
    }
    public synchronized void close(){closed=true;attached.values().forEach(a->a.interpreter.removeListener(a));attached.clear();observer=null;completeness=Completeness.UNAVAILABLE;}
    private final class Attached implements NormativeListener {
        final String scope,uuid;final NPLInterpreter interpreter;final NplActivationCrosswalk crosswalk=new NplActivationCrosswalk(sessionId,generation);
        Map<String,Object> proof=Map.of();
        Attached(String scope,String uuid,NPLInterpreter interpreter){this.scope=scope;this.uuid=uuid;this.interpreter=interpreter;}
        public void created(NormInstance value){emit(this,value,RuntimeEventKind.NORM_ACTIVATED,"ACTIVE","LISTENER_CREATED");}
        public void fulfilled(NormInstance value){emit(this,value,RuntimeEventKind.NORM_FULFILLED,"FULFILLED","LISTENER_TERMINAL");}
        public void unfulfilled(NormInstance value){emit(this,value,RuntimeEventKind.NORM_UNFULFILLED,"UNFULFILLED","LISTENER_TERMINAL");}
        public void inactive(NormInstance value){emit(this,value,RuntimeEventKind.NORM_INACTIVATED,"INACTIVE","LISTENER_TERMINAL");}
    }
    private synchronized void emit(Attached board,NormInstance value,RuntimeEventKind kind,String state,String origin){
        if(closed||observer==null||attached.get(board.uuid)!=board)return;var copy=value.copy();long next=sequence.incrementAndGet();var payload=payload(board,copy,origin,state,next);
        var event=new RuntimeEvent(sessionId+":npl:"+next,sessionId,generation,revision.get(),"npl",sourceId(),next,Instant.now(),kind,RuntimeFactKind.NORM_INSTANCE,ProjectionStatus.EVIDENCE_ONLY,identity(board,payload),null,"","",Map.of(),payload,new SourceWatermark(sourceId(),next),quality(payload),List.of());
        observer.accept(event);
        VerificationObservationSource.publishIfAttached(event,()->{
            var data=Map.<String,Object>of("boardIdentity",board.uuid,"activationKey",payload.get("activationKey"),"state",state,"origin",origin,
                    "norm",JasonStructuredTerms.term(copy),"identityStatus",payload.get("activationIdentityStatus"),"sourceObservation",payload);
            var proof=CanonicalJson.object(payload.get("sourceLifecycle"));String epoch=state.equals("ACTIVE")?(String)proof.get("createdEpochMillis"):Objects.toString(CanonicalJson.object(proof.get("terminalTimes")).get(state.toLowerCase(Locale.ROOT)),"");
            boolean qualified="COMPLETE".equals(proof.get("status"))&&!epoch.isBlank()&&!((String)proof.get("clockIdentity")).isBlank();
            var time=qualified?new VerificationObservation.SourceTime("OBSERVED",(String)proof.get("clockIdentity"),"EPOCH_MILLISECONDS",epoch,NplLifecycleEvidence.AUTHORITY,"")
                    :VerificationObservation.SourceTime.unavailable("NPL_SOURCE_TIME_ANNOTATION_UNAVAILABLE");
            boolean complete=qualified&&"CREATED_EVENT_OBSERVED".equals(payload.get("history"))&&quality(payload)==Completeness.COMPLETE;
            return new VerificationObservation(VerificationObservation.Kind.NPL_LIFECYCLE,event.entityId().canonical(),complete?Completeness.COMPLETE:Completeness.PARTIAL,
                    complete?"":"NPL_ACTIVATION_HISTORY_OR_SOURCE_TIME_UNPROVEN",time,data);
        });
    }
}
