package org.jacamo.bridge.adapter;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jacamo.bridge.contract.RuntimeControlContract.State;

/** Exact-incarnation admission/ACK ledger. Caller serializes actual official signal dispatch. */
final class ReasoningCycleGate {
    private static final class Member { int granted=-1, completed=-1; boolean initialBoundary; }
    private final Map<String,Member> members=new LinkedHashMap<>();
    private final Set<String> required=new LinkedHashSet<>(), acknowledged=new LinkedHashSet<>(), departed=new LinkedHashSet<>();
    private State state=State.RUNNING;
    private boolean capabilityValid=true;
    private long epoch;
    private String requestId="", diagnostic="";

    synchronized void admit(String id,boolean initialBoundary) {
        if (id==null || id.isBlank()) throw new IllegalArgumentException("CONTROL_AGENT_ID_REQUIRED");
        if (members.containsKey(id)) return;
        var member=new Member(); member.initialBoundary=initialBoundary; members.put(id,member);
        if(state!=State.RUNNING) {
            required.add(id);
            if(state==State.PAUSED) state=State.PAUSE_REQUESTED;
            if(state==State.PAUSE_REQUESTED && initialBoundary) acknowledged.add(id);
            completePause();
        }
    }
    synchronized void depart(String id) {
        if(members.remove(id)==null) return;
        if(required.contains(id)) {
            departed.add(id);
            if(!acknowledged.contains(id)) {
                diagnostic="CONTROL_AGENT_DEPARTED_BEFORE_ACK:"+id;
                if(state==State.PAUSED) state=State.PAUSE_REQUESTED;
            }
        }
    }
    synchronized void pause(String id) {
        if(state==State.PAUSED || state==State.PAUSE_REQUESTED && departed.stream().allMatch(acknowledged::contains)) return;
        if(state==State.RESUME_REQUESTED) throw new IllegalStateException("CONTROL_RESUME_IN_PROGRESS");
        if(members.isEmpty()) throw new IllegalStateException("CONTROL_NO_ADMITTED_AGENTS");
        state=State.PAUSE_REQUESTED; requestId=id; epoch++; diagnostic="";
        required.clear(); required.addAll(members.keySet()); acknowledged.clear(); departed.clear();
        members.forEach((key,m)-> {if(m.initialBoundary || m.granted>=0 && m.completed>=m.granted) acknowledged.add(key);});
        completePause();
    }
    synchronized void resume(String id) {
        if(state==State.RUNNING || state==State.RESUME_REQUESTED) return;
        if(state!=State.PAUSED) throw new IllegalStateException("CONTROL_PAUSE_NOT_CONFIRMED");
        state=State.RESUME_REQUESTED; requestId=id; epoch++; diagnostic="";
        required.clear(); required.addAll(members.keySet()); acknowledged.clear(); departed.clear();
    }
    synchronized boolean grant(String id,int cycle,long expectedEpoch) {
        Member m=members.get(id);
        if(m==null || !capabilityValid || epoch!=expectedEpoch || state!=State.RUNNING && state!=State.RESUME_REQUESTED
                || cycle<=m.granted || m.granted>=0 && m.completed<m.granted) return false;
        m.granted=cycle; m.initialBoundary=false; return true;
    }
    synchronized void boundary(String id,int cycle) {
        Member m=members.get(id); if(m==null || cycle!=m.granted) return;
        m.completed=cycle;
        if(state==State.PAUSE_REQUESTED && required.contains(id)) { acknowledged.add(id); completePause(); }
    }
    synchronized void resumeAcknowledged(String id,long expectedEpoch) {
        if(epoch!=expectedEpoch || state!=State.RESUME_REQUESTED || !members.containsKey(id) || !required.contains(id)) return;
        acknowledged.add(id);
        if(acknowledged.containsAll(required) && departed.isEmpty()) state=State.RUNNING;
    }
    synchronized void timeout() {
        if(state==State.PAUSE_REQUESTED || state==State.RESUME_REQUESTED) diagnostic="CONTROL_ACK_TIMEOUT:"+missing();
    }
    synchronized void unavailable(String reason) {
        capabilityValid=false;
        diagnostic=reason;
        if(state==State.PAUSED) state=State.PAUSE_REQUESTED;
    }
    private void completePause() {
        if(capabilityValid && state==State.PAUSE_REQUESTED && !required.isEmpty() && acknowledged.containsAll(required)) {state=State.PAUSED; diagnostic="";}
    }
    synchronized void available() {capabilityValid=true;completePause();}
    synchronized State state() {return state;}
    synchronized long epoch() {return epoch;}
    synchronized String requestId() {return requestId;}
    synchronized String diagnostic() {return diagnostic;}
    synchronized Set<String> required() {return Set.copyOf(required);}
    synchronized Set<String> acknowledged() {return Set.copyOf(acknowledged);}
    synchronized Set<String> departed() {return Set.copyOf(departed);}
    synchronized Set<String> members() {return Set.copyOf(members.keySet());}
    synchronized int nextCycle(String id) {return members.get(id).granted+1;}
    synchronized boolean ready(String id) {var m=members.get(id);return m!=null && (m.initialBoundary || m.completed>=m.granted);}
    private Set<String> missing() {var set=new LinkedHashSet<>(required);set.removeAll(acknowledged);return set;}
}
