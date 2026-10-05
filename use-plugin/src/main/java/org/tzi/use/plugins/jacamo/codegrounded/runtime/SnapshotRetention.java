package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.util.ArrayDeque;
import java.util.List;

/** Bounded diagnostic copies; pin the original failure/previous cut independently of the live tail. */
public final class SnapshotRetention {
    private final int capacity;
    private final long maxBytes;
    private final ArrayDeque<VerificationSnapshot> tail=new ArrayDeque<>();
    private long bytes;
    private VerificationSnapshot previousFailure,failure,confirmation,lastPassing,passingBeforeFailure;
    public SnapshotRetention(int capacity,long maxBytes) {
        if(capacity<2 || capacity>256 || maxBytes<1024 || maxBytes>256L*1024*1024) throw new IllegalArgumentException("SNAPSHOT_RETENTION_LIMIT_INVALID");
        this.capacity=capacity;this.maxBytes=maxBytes;
    }
    public synchronized void retain(VerificationSnapshot snapshot) {
        long cost=snapshot.estimatedBytes();
        if(snapshot.metadata()==null || snapshot.image()==null || cost>maxBytes/2) throw new IllegalStateException("VERIFICATION_SNAPSHOT_TOO_LARGE");
        if(!tail.isEmpty() && snapshot.metadata().boundarySequence()<=tail.getLast().metadata().boundarySequence())
            throw new IllegalArgumentException("VERIFICATION_SNAPSHOT_SEQUENCE_STALE");
        tail.addLast(snapshot);bytes+=cost;
        while(tail.size()>capacity || bytes>maxBytes) bytes-=tail.removeFirst().estimatedBytes();
        var result=snapshot.result();
        if(result!=null && result.freshness().equals("CURRENT_OBSERVED") && !result.coverage().equals("STATIC_ONLY")
                && result.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.PASS)>0
                && result.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.FAIL)==0
                && result.count(org.tzi.use.plugins.jacamo.verification.VerificationOutcome.ERROR)==0)lastPassing=snapshot;
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
        if(snapshot.estimatedBytes()>maxBytes/2) throw new IllegalStateException("VERIFICATION_SNAPSHOT_TOO_LARGE");
        if(snapshot.snapshotId().equals(failure.snapshotId())) throw new IllegalArgumentException("CONFIRMATION_MUST_BE_SEPARATE");
        confirmation=snapshot;
    }
    public synchronized VerificationSnapshot failure() {return failure;}
    public synchronized VerificationSnapshot previousFailure() {return previousFailure;}
    public synchronized VerificationSnapshot passingBeforeFailure() {return passingBeforeFailure;}
    public synchronized VerificationSnapshot confirmation() {return confirmation;}
    public synchronized List<VerificationSnapshot> snapshots() {return List.copyOf(tail);}
    public synchronized long retainedBytes() {return bytes+(previousFailure==null?0:previousFailure.estimatedBytes())
            +(failure==null?0:failure.estimatedBytes())+(confirmation==null?0:confirmation.estimatedBytes())
            +(lastPassing==null?0:lastPassing.estimatedBytes())+(passingBeforeFailure==null?0:passingBeforeFailure.estimatedBytes());}
    public synchronized void releasePins() {previousFailure=null;failure=null;confirmation=null;passingBeforeFailure=null;}
    public synchronized void clear() {tail.clear();bytes=0;lastPassing=null;releasePins();}
    record Savepoint(List<VerificationSnapshot> tail,long bytes,VerificationSnapshot previous,
                     VerificationSnapshot failure,VerificationSnapshot confirmation,VerificationSnapshot lastPassing,VerificationSnapshot passingBeforeFailure) { }
    synchronized Savepoint savepoint() {return new Savepoint(List.copyOf(tail),bytes,previousFailure,failure,confirmation,lastPassing,passingBeforeFailure);}
    synchronized void restore(Savepoint saved) {
        tail.clear();tail.addAll(saved.tail());bytes=saved.bytes();previousFailure=saved.previous();failure=saved.failure();confirmation=saved.confirmation();
        lastPassing=saved.lastPassing();passingBeforeFailure=saved.passingBeforeFailure();
    }
}
