package org.jacamo.bridge.contract;

/** PID alone is insufficient: a reused PID must not keep an orphan runtime alive. */
public record ProcessOwnerLease(long pid,long startedAtEpochMillis) {
    public ProcessOwnerLease {if(pid<1||startedAtEpochMillis<1)throw new IllegalArgumentException("PROCESS_OWNER_LEASE_REQUIRED");}
    public static ProcessOwnerLease current() {
        var process=ProcessHandle.current();return new ProcessOwnerLease(process.pid(),process.info().startInstant().orElseThrow(()->new IllegalStateException("PROCESS_START_IDENTITY_UNAVAILABLE")).toEpochMilli());
    }
    public boolean alive() {return ProcessHandle.of(pid).filter(ProcessHandle::isAlive).flatMap(p->p.info().startInstant()).map(t->t.toEpochMilli()==startedAtEpochMillis).orElse(false);}
}
