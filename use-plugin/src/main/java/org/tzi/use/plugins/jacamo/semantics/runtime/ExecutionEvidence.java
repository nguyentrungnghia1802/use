package org.tzi.use.plugins.jacamo.semantics.runtime;

import java.util.*;
import org.tzi.use.semantic.engine.runtime.EngineOccurrenceKey;
import org.vnu.sme.goal.dsl.istar.mm.GoalType;
import org.vnu.sme.goal.verify.conformance.semantics.*;

/** Run/program-owned proofs of leaf completion and exact terminal failure, not a second marking
 * engine. The evaluation lane owns this ledger and snapshots it with recurrence.
 * Missing observations do not retract a past fact. New episodes cannot borrow it. */
public final class ExecutionEvidence {
    public record Key(String context,EngineOccurrenceKey occurrence) { }
    public record Certificate(String policy,Key key,String kind,long stateVersion,String stateHash,String invocation) {
        public Certificate(String policy,Key key,String kind,long stateVersion,String stateHash){this(policy,key,kind,stateVersion,stateHash,"");}
    }
    private final Map<Key,Certificate> proofs=new HashMap<>();

    public Optional<Certificate> leaf(String context,EngineOccurrenceKey occurrence,GoalMarking goal,
            TaskMarking task,boolean admitted,long version,String hash) {
        if(occurrence==null)return Optional.empty();
        var key=new Key(context,occurrence);
        boolean fulfilled=task!=null&&task.status()==GoalTaskStatus.FULFILLED
                ||goal!=null&&goal.type()==GoalType.ACHIEVE&&goal.active()&&goal.status()==GoalTaskStatus.FULFILLED;
        if(!fulfilled){if(terminal(context,occurrence).isEmpty())proofs.remove(key);return Optional.empty();}
        if(terminal(context,occurrence).isPresent())throw new IllegalStateException("EXECUTION_TERMINAL_OUTCOME_CONFLICT");
        if(admitted)proofs.putIfAbsent(key,new Certificate("CERTIFIED_EXECUTION/1.0",key,
                task!=null?"TASK":"ACHIEVE",version,hash));
        return Optional.ofNullable(proofs.get(key));
    }
    /** Caller must already have admitted the exact source FAILED marker and the
     * correlated native PRE/POST tuple. Unknown/ERROR/post-false alone is insufficient. */
    public void terminalFailure(String context,EngineOccurrenceKey occurrence,String invocation,boolean admitted,long version,String hash) {
        if(!admitted)return;
        if(context==null||context.isBlank()||occurrence==null||invocation==null||invocation.isBlank()||hash==null||hash.isBlank()||version<0)throw new IllegalArgumentException("EXECUTION_TERMINAL_IDENTITY_REQUIRED");
        var key=new Key(context,occurrence);var previous=proofs.get(key);
        if(previous!=null&&(!previous.kind().equals("TASK_FAILED")||!previous.invocation().equals(invocation)))throw new IllegalStateException("EXECUTION_TERMINAL_OUTCOME_CONFLICT");
        proofs.putIfAbsent(key,new Certificate("CERTIFIED_TERMINAL_EXECUTION/1.0",key,"TASK_FAILED",version,hash,invocation));
    }
    public Optional<Certificate> terminal(String context,EngineOccurrenceKey occurrence) {
        if(occurrence==null)return Optional.empty();var proof=proofs.get(new Key(context,occurrence));
        return proof!=null&&proof.kind().equals("TASK_FAILED")&&proof.policy().equals("CERTIFIED_TERMINAL_EXECUTION/1.0")?Optional.of(proof):Optional.empty();
    }
    public Map<Key,Certificate> snapshot(){return Map.copyOf(proofs);}
    public void restore(Map<Key,Certificate> image){proofs.clear();proofs.putAll(image);}
    public void retain(Set<Key> live){proofs.keySet().retainAll(live);}
    public void clear(){proofs.clear();}
}
