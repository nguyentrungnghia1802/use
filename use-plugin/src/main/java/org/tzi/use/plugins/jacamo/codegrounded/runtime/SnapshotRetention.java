package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.ArrayDeque;
import java.util.List;

/** Bounded diagnostic copies; pin the original failure/previous cut independently of the live tail. */
public final class SnapshotRetention {
    private final int capacity;
    private final long maxBytes;
    private final ArrayDeque<VerificationSnapshot> tail=new ArrayDeque<>();
    // Snapshots are immutable. Remember the exact encoded cost already checked
    // for each retained/pinned copy; status polling and eviction must not encode
    // those large images again on the native Swing writer.
    private final java.util.IdentityHashMap<VerificationSnapshot,Long> costs=new java.util.IdentityHashMap<>();
    private VerificationSnapshot measured;
    private long measuredCost;
    private long sizingOperations;
    private long bytes;
    private VerificationSnapshot previousFailure,failure,confirmation,lastPassing,passingBeforeFailure;
    public SnapshotRetention(int capacity,long maxBytes) {
        if(capacity<2 || capacity>256 || maxBytes<1024 || maxBytes>256L*1024*1024) throw new IllegalArgumentException("SNAPSHOT_RETENTION_LIMIT_INVALID");
        this.capacity=capacity;this.maxBytes=maxBytes;
    }
    public synchronized void retain(VerificationSnapshot snapshot) {
        long cost=cost(snapshot);
        if(snapshot.metadata()==null || snapshot.image()==null || cost>maxBytes/2) throw new IllegalStateException("VERIFICATION_SNAPSHOT_TOO_LARGE");
        if(!tail.isEmpty() && snapshot.metadata().boundarySequence()<=tail.getLast().metadata().boundarySequence())
            throw new IllegalArgumentException("VERIFICATION_SNAPSHOT_SEQUENCE_STALE");
        tail.addLast(snapshot);costs.put(snapshot,cost);bytes+=cost;
        while(tail.size()>capacity || bytes>maxBytes) bytes-=cost(tail.removeFirst());
        var result=snapshot.result();
        if(result!=null && result.freshness().equals("CURRENT_OBSERVED") && !result.coverage().equals("STATIC_ONLY")
                && result.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.PASS)>0
                && result.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL)==0
                && result.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.ERROR)==0)lastPassing=snapshot;
        pruneCosts();
    }
    public synchronized void pinFailure(String snapshotId) {
        if(failure!=null) return; // Repeated FAIL must not overwrite the original pre-request state.
        VerificationSnapshot previous=null;
        for(var snapshot:tail) {
            if(snapshot.snapshotId().equals(snapshotId)) {previousFailure=previous;failure=snapshot;
                passingBeforeFailure=lastPassing!=null && lastPassing.metadata().boundarySequence()<snapshot.metadata().boundarySequence()?lastPassing:previous;
                if(passingBeforeFailure!=null && (passingBeforeFailure.result().count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL)>0
                        || passingBeforeFailure.result().count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.ERROR)>0))passingBeforeFailure=null;
                return;}
            previous=snapshot;
        }
        throw new IllegalArgumentException("FAILING_SNAPSHOT_NOT_RETAINED");
    }
    public synchronized void pinConfirmation(VerificationSnapshot snapshot) {
        if(failure==null) throw new IllegalStateException("FAILING_SNAPSHOT_REQUIRED");
        long cost=cost(snapshot);if(cost>maxBytes/2) throw new IllegalStateException("VERIFICATION_SNAPSHOT_TOO_LARGE");
        if(snapshot.snapshotId().equals(failure.snapshotId())) throw new IllegalArgumentException("CONFIRMATION_MUST_BE_SEPARATE");
        confirmation=snapshot;costs.put(snapshot,cost);pruneCosts();
    }
    public synchronized VerificationSnapshot failure() {return failure;}
    public synchronized VerificationSnapshot previousFailure() {return previousFailure;}
    public synchronized VerificationSnapshot passingBeforeFailure() {return passingBeforeFailure;}
    public synchronized VerificationSnapshot confirmation() {return confirmation;}
    public synchronized List<VerificationSnapshot> snapshots() {return List.copyOf(tail);}
    /** Validate before persistence; this must not publish a provisional success. */
    public synchronized void validateCompletion(VerificationSnapshot replacement) {
        if(replacement.metadata()==null||replacement.image()==null||cost(replacement)>maxBytes/2)throw new IllegalStateException("VERIFICATION_SNAPSHOT_TOO_LARGE");
        for(var old:tail)if(old.snapshotId().equals(replacement.snapshotId())&&(old.currentVersion()!=replacement.currentVersion()||old.image()!=replacement.image()||!old.result().stateHash().equals(replacement.result().stateHash())))throw new IllegalArgumentException("ASYNC_SNAPSHOT_CUT_MISMATCH");
    }
    /** Replace only the result of the exact frozen cut after ordered worker completion. */
    public synchronized void complete(VerificationSnapshot replacement) {
        validateCompletion(replacement);
        var values=new java.util.ArrayList<VerificationSnapshot>();boolean found=false;
        for(var old:tail)if(old.snapshotId().equals(replacement.snapshotId())) {
            bytes+=cost(replacement)-cost(old);costs.put(replacement,cost(replacement));values.add(replacement);found=true;
        }else values.add(old);
        if(!found)return;tail.clear();tail.addAll(values);
        while(tail.size()>capacity||bytes>maxBytes)bytes-=cost(tail.removeFirst());
        if(failure!=null&&failure.snapshotId().equals(replacement.snapshotId()))failure=replacement;
        if(previousFailure!=null&&previousFailure.snapshotId().equals(replacement.snapshotId()))previousFailure=replacement;
        if(confirmation!=null&&confirmation.snapshotId().equals(replacement.snapshotId()))confirmation=replacement;
        if(passingBeforeFailure!=null&&passingBeforeFailure.snapshotId().equals(replacement.snapshotId()))passingBeforeFailure=replacement;
        if(lastPassing!=null&&lastPassing.snapshotId().equals(replacement.snapshotId()))lastPassing=replacement;
        if(replacement.result().freshness().equals("CURRENT_OBSERVED")&&!replacement.result().coverage().equals("STATIC_ONLY")&&replacement.result().count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.PASS)>0&&replacement.result().count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL)==0&&replacement.result().count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.ERROR)==0
                &&(lastPassing==null||lastPassing.metadata().boundarySequence()<=replacement.metadata().boundarySequence()))lastPassing=replacement;
        pruneCosts();
    }
    public synchronized long retainedBytes() {return bytes+cost(previousFailure)+cost(failure)+cost(confirmation)+cost(lastPassing)+cost(passingBeforeFailure);}
    public synchronized void releasePins() {previousFailure=null;failure=null;confirmation=null;passingBeforeFailure=null;measured=null;pruneCosts();}
    public synchronized void clear() {tail.clear();bytes=0;lastPassing=null;releasePins();costs.clear();}
    private long cost(VerificationSnapshot snapshot){
        if(snapshot==null)return 0;Long retained=costs.get(snapshot);if(retained!=null)return retained;
        if(measured!=null&&snapshot.currentVersion()==measured.currentVersion()&&snapshot.result()==measured.result()&&snapshot.metadata()==measured.metadata()&&snapshot.image()==measured.image()&&snapshot.profile()==measured.profile())return measuredCost;
        long value=snapshot.estimatedBytes();sizingOperations++;
        // Rejected oversized copies must not become an extra retained image.
        if(value<=maxBytes/2){measured=snapshot;measuredCost=value;}return value;
    }
    private void pruneCosts(){
        var keep=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<VerificationSnapshot,Boolean>());keep.addAll(tail);
        for(var pinned:new VerificationSnapshot[]{previousFailure,failure,confirmation,lastPassing,passingBeforeFailure})if(pinned!=null)keep.add(pinned);
        costs.keySet().retainAll(keep);
    }
    record Savepoint(List<VerificationSnapshot> tail,long bytes,VerificationSnapshot previous,
                     VerificationSnapshot failure,VerificationSnapshot confirmation,VerificationSnapshot lastPassing,VerificationSnapshot passingBeforeFailure,java.util.Map<VerificationSnapshot,Long> costs) { }
    synchronized Savepoint savepoint() {return new Savepoint(List.copyOf(tail),bytes,previousFailure,failure,confirmation,lastPassing,passingBeforeFailure,new java.util.IdentityHashMap<>(costs));}
    synchronized void restore(Savepoint saved) {
        tail.clear();tail.addAll(saved.tail());bytes=saved.bytes();previousFailure=saved.previous();failure=saved.failure();confirmation=saved.confirmation();
        lastPassing=saved.lastPassing();passingBeforeFailure=saved.passingBeforeFailure();
        costs.clear();costs.putAll(saved.costs());measured=null;
    }
}
