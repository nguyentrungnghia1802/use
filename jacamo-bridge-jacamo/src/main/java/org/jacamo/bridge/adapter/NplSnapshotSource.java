package org.jacamo.bridge.adapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import npl.NPLInterpreter;
import npl.NormInstance;
import npl.NormativeListener;
import org.jacamo.bridge.contract.BridgeEntityId;
import org.jacamo.bridge.contract.Completeness;
import org.jacamo.bridge.contract.ProjectionStatus;
import org.jacamo.bridge.contract.RuntimeEvent;
import org.jacamo.bridge.contract.RuntimeEventKind;
import org.jacamo.bridge.contract.RuntimeFact;
import org.jacamo.bridge.contract.RuntimeFactKind;
import org.jacamo.bridge.contract.SourceWatermark;

/** Attaches after NPL interpreter creation and preserves lifecycle as evidence, never OCL. */
public final class NplSnapshotSource implements SnapshotSource, NormativeListener {
    private final NPLInterpreter interpreter;private final String scope;private final String sessionId;private final long generation;private final AtomicLong sequence=new AtomicLong();private volatile Consumer<RuntimeEvent> observer;
    public NplSnapshotSource(NPLInterpreter interpreter,String scope,String sessionId){this.interpreter=interpreter;this.scope=scope;this.sessionId=sessionId;this.generation=Long.parseLong(System.getProperty("jacamo.bridge.generation","0"));}
    @Override public String sourceId(){return "npl:"+scope;}
    @Override public void attach(Consumer<RuntimeEvent> observer){if(this.observer!=null)throw new IllegalStateException("NPL_ALREADY_ATTACHED");this.observer=observer;interpreter.addListener(this);}
    @Override public SourceWatermark watermark(){return new SourceWatermark(sourceId(),sequence.get());}
    @Override public String topologyFingerprint(){return AdapterEvidence.digest((scope+interpreter.getActivatedNorms()).getBytes());}
    @Override public List<RuntimeFact> capture(){var facts=new ArrayList<RuntimeFact>();add(facts,"ACTIVE",interpreter.getActive());add(facts,"FULFILLED",interpreter.getFulfilled());add(facts,"UNFULFILLED",interpreter.getUnFulfilled());add(facts,"INACTIVE",interpreter.getInactive());facts.sort(Comparator.comparing(f->f.id().canonical()));return facts;}
    private void add(List<RuntimeFact> facts,String state,List<NormInstance> values){for(NormInstance value:values)facts.add(new RuntimeFact(id(value),RuntimeFactKind.NORM_INSTANCE,Map.of("state",state,"norm",value.toString(),"modality",value.isObligation()?"OBLIGATION":value.isPermission()?"PERMISSION":value.isProhibition()?"PROHIBITION":"UNKNOWN","deadline",value.getTimeDeadline()),List.of(),ProjectionStatus.EVIDENCE_ONLY,Completeness.COMPLETE,List.of()));}
    @Override public Completeness completeness(){return Completeness.COMPLETE;}
    @Override public void close(){interpreter.removeListener(this);observer=null;}
    @Override public void created(NormInstance value){emit(value,RuntimeEventKind.NORM_ACTIVATED);}
    @Override public void fulfilled(NormInstance value){emit(value,RuntimeEventKind.NORM_FULFILLED);}
    @Override public void unfulfilled(NormInstance value){emit(value,RuntimeEventKind.NORM_UNFULFILLED);}
    @Override public void inactive(NormInstance value){emit(value,RuntimeEventKind.NORM_INACTIVATED);}
    private BridgeEntityId id(NormInstance value){String stable=AdapterEvidence.digest((value.getNorm()+"|"+value.getAg()+"|"+value.getUnifier()).getBytes()).substring(0,24);return new BridgeEntityId("npl","organisation","norm-instance",scope,stable,sessionId);}
    private void emit(NormInstance value,RuntimeEventKind kind){Consumer<RuntimeEvent> sink=observer;if(sink==null)return;long seq=sequence.incrementAndGet();sink.accept(new RuntimeEvent(sessionId+":"+sourceId()+":"+seq,sessionId,generation,System.getProperty("jacamo.bridge.modelRevision","unnegotiated"),"npl",sourceId(),seq,Instant.now(),kind,org.jacamo.bridge.contract.RuntimeFactKind.NORM_INSTANCE,org.jacamo.bridge.contract.ProjectionStatus.EVIDENCE_ONLY,id(value),null,"","",Map.of(),Map.of("state",String.valueOf(value.getState()),"norm",value.toString()),new SourceWatermark(sourceId(),seq),Completeness.COMPLETE,List.of()));}
}
