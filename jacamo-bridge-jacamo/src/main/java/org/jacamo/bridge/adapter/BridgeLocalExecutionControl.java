package org.jacamo.bridge.adapter;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jason.JasonException;
import jason.infra.local.BaseLocalMAS;
import jason.infra.local.LocalAgArch;
import jason.infra.local.LocalExecutionControl;
import jason.mas2j.ClassParameters;
import org.jacamo.bridge.contract.RuntimeControlContract;
import org.jacamo.bridge.contract.RuntimeControlContract.*;

/** All-agent reasoning-boundary ACKs with exact LocalAgArch/incarnation ownership. No domain writes. */
public final class BridgeLocalExecutionControl extends LocalExecutionControl {
    private record Agent(String identity,String name,LocalAgArch architecture) { }
    private final BaseLocalMAS runner;
    private final ReasoningCycleGate gate=new ReasoningCycleGate();
    private final Map<String,Agent> agents=new LinkedHashMap<>();
    private final Set<String> pending=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor signals=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(4096),
            r->{var t=new Thread(r,"jacamo-jason-cycle-signal");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final AtomicBoolean stopped=new AtomicBoolean();
    private volatile String unavailable="";
    private long requestedAt;
    public BridgeLocalExecutionControl(BaseLocalMAS runner) throws JasonException {
        super(new ClassParameters(BridgeCycleExecutionControl.class.getName()),runner);this.runner=runner;
    }
    public synchronized void admit(LocalAgArch arch) {
        var identity=BridgeRuntimeRegistry.agentIdentity(arch.getAgName());
        if(arch.getClass()!=LocalAgArch.class || identity.isEmpty() || BridgeRuntimeRegistry.agentArchitecture(arch.getAgName()).orElse(null)!=arch) {
            rejectCapability("CONTROL_AGENT_SCHEDULER_OR_IDENTITY_UNSUPPORTED:"+arch.getAgName());return;
        }
        String id=identity.get().canonical();var old=agents.get(id);if(old!=null) return;
        // Admission must precede the new thread's first cycle. Never retroactively claim that boundary.
        if(arch.getThread()!=null && arch.getThread().isAlive()) {rejectCapability("CONTROL_AGENT_ADMITTED_AFTER_START:"+id);return;}
        arch.getTS().getSettings().setSync(true);arch.setControlInfraTier(this);
        agents.put(id,new Agent(id,arch.getAgName(),arch));gate.admit(id,true);
    }
    public synchronized void departed(String identity) {agents.remove(identity);gate.depart(identity);}
    public synchronized boolean capable() {reconcile();return unavailable.isEmpty() && !stopped.get() && !agents.isEmpty();}
    private synchronized void reconcile() {
        if(stopped.get()) {rejectCapability("CONTROL_RUNTIME_STOPPED");return;}
        var current=new ArrayList<>(runner.getAgs().values());
        for(var entry:new ArrayList<>(agents.values())) if(runner.getAg(entry.name())!=entry.architecture()) departed(entry.identity());
        for(var arch:current) {
            var identity=BridgeRuntimeRegistry.agentIdentity(arch.getAgName());
            var known=identity.isEmpty()?null:agents.get(identity.get().canonical());
            if(known==null) admit(arch);
            if(arch.getClass()!=LocalAgArch.class || known!=null && known.architecture()!=arch || arch.getControlInfraTier()!=this
                    || !arch.getTS().getSettings().isSync()) {rejectCapability("CONTROL_CURRENT_AGENT_NOT_OWNED:"+arch.getAgName());return;}
        }
        if(current.size()!=agents.size() || current.isEmpty()) {rejectCapability("CONTROL_AGENT_SET_UNAVAILABLE");return;}
        unavailable="";gate.available();
    }
    private void rejectCapability(String diagnostic) {unavailable=diagnostic;gate.unavailable(diagnostic);}
    @Override public void informAllAgsToPerformCycle(int cycle) {
        synchronized(this) {reconcile();if(!capable())return;}
        releaseReady();
    }
    public void started(LocalAgArch arch) {
        synchronized(this) {if(!agents.values().stream().anyMatch(a->a.architecture()==arch))return;}
        releaseReady();
    }
    void finished(String name,int cycle) {
        Agent agent;
        synchronized(this) {
            agent=agents.values().stream().filter(a->a.architecture()==runner.getAg(name) && a.name().equals(name)
                    && Thread.currentThread()==a.architecture().getThread()).findFirst().orElse(null);
            // A stopped/replaced incarnation may report its last cycle late. It cannot ACK its replacement.
            if(agent==null)return;
            gate.boundary(agent.identity(),cycle);
        }
        schedule(agent);
    }
    private void releaseReady() {
        List<Agent> copy;synchronized(this) {copy=List.copyOf(agents.values());}
        copy.forEach(this::schedule);
    }
    private void schedule(Agent agent) {
        long epoch;
        synchronized(this) {
            State state=gate.state();
            if(stopped.get() || !unavailable.isEmpty() || agent.architecture().getThread()==null || !agent.architecture().getThread().isAlive()
                    || state!=State.RUNNING && state!=State.RESUME_REQUESTED || !gate.ready(agent.identity())
                    || state==State.RESUME_REQUESTED && gate.acknowledged().contains(agent.identity()))return;
            epoch=gate.epoch();if(!pending.add(agent.identity()))return;
        }
        try {signals.execute(()->{
            try {
                int cycle;
                synchronized(this) {
                    if(runner.getAg(agent.name())!=agent.architecture() || agent.architecture().getThread()==null || !agent.architecture().getThread().isAlive())return;
                    cycle=gate.nextCycle(agent.identity());if(!gate.grant(agent.identity(),cycle,epoch))return;
                }
                // Official synchronous release may wait for the architecture's monitor. Never hold the ledger/EDT while waiting.
                // Same official infra-tier mechanism, addressed to the revalidated object.
                // The base implementation looks up by name again and could release a replacement incarnation.
                agent.architecture().getFirstAgArch().setCycleNumber(cycle);
                agent.architecture().receiveSyncSignal();
                synchronized(this) {
                    if(runner.getAg(agent.name())==agent.architecture() && agent.architecture().isRunning())gate.resumeAcknowledged(agent.identity(),epoch);
                }
            } catch(RuntimeException failed) {synchronized(this){rejectCapability("CONTROL_SIGNAL_FAILED:"+failed.getMessage());}}
            finally {
                pending.remove(agent.identity());
                if(!stopped.get())releaseReady();
            }
        });} catch(RejectedExecutionException overflow) {pending.remove(agent.identity());synchronized(this){rejectCapability("CONTROL_SIGNAL_QUEUE_UNAVAILABLE");}}
    }
    public synchronized Status request(Request request,String session,long generation,String revision) {
        if(!request.sessionId().equals(session) || request.generation()!=generation || !request.modelRevision().equals(revision))
            throw new IllegalArgumentException("CONTROL_RUNTIME_IDENTITY_STALE");
        reconcile();
        if(request.action()!=Action.STATUS && !capable())throw new IllegalStateException(unavailable);
        if(request.action()==Action.PAUSE) {State before=gate.state();gate.pause(request.requestId());if(before!=gate.state())requestedAt=System.nanoTime();}
        else if(request.action()==Action.RESUME) {State before=gate.state();gate.resume(request.requestId());if(before!=gate.state())requestedAt=System.nanoTime();releaseReady();}
        if(requestedAt>0 && System.nanoTime()-requestedAt>TimeUnit.SECONDS.toNanos(Long.getLong("jacamo.bridge.control.ackTimeoutSeconds",15L)))gate.timeout();
        return status(session,generation,revision);
    }
    private Status status(String session,long generation,String revision) {
        return new Status(RuntimeControlContract.VERSION,session,generation,revision,capable(),gate.state(),gate.requestId(),
                gate.required(),gate.acknowledged(),gate.departed(),gate.diagnostic(),Instant.now());
    }
    @Override public void stop() {
        if(!stopped.compareAndSet(false,true))return;
        synchronized(this) {rejectCapability("CONTROL_RUNTIME_STOPPED");}
        signals.shutdownNow();executor.shutdownNow();super.stop();
    }
}
