package org.tzi.use.plugins.jacamo.runtime;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import jason.architecture.AgArch;
import jason.asSemantics.*;
import jason.asSyntax.*;
import org.jacamo.checkpoint.runtime.ExecutionBarrier;
import org.tzi.use.plugins.jacamo.runtime.JaCaMoRuntimeAdapter.*;

/** Selects source execution boundaries without access to mapping, CSL, iStar or their values. */
final class JaCaMoCheckpoints implements AutoCloseable {
    @FunctionalInterface interface Delivery { void send() throws Exception; }
    private record Pending(Checkpoint checkpoint, CompletableFuture<Boolean> result) { }
    private final CheckpointHandler handler;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "jacamo-checkpoints"); thread.setDaemon(true); return thread;
    });
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final Set<TransitionSystem> owned = ConcurrentHashMap.newKeySet();
    private boolean draining;
    private volatile boolean complete = true;

    JaCaMoCheckpoints(String run, CheckpointHandler handler) {
        this.handler = Objects.requireNonNull(handler);
        ExecutionBarrier.enable(run);
    }
    void initial() {
        try {
            long epoch = ExecutionBarrier.closeAdmission();
            freeze(epoch);
            if (!handler.verify(checkpoint("", "INITIAL", "INITIAL", "INITIAL", "", -1, 0, List.of())))
                throw new IllegalStateException("INITIAL_STATE_NOT_APPROVED");
            ExecutionBarrier.release(epoch);
        } catch (Exception failure) { throw new IllegalStateException("INITIAL_SOURCE_ADMISSION_FAILED", failure); }
    }
    void install(TransitionSystem ts, String agent) {
        var monitor = new Monitor(ts, agent);
        owned.add(ts); Architecture.monitors.put(ts, monitor); monitor.install();
    }
    private Checkpoint checkpoint(String agent, String plan, String outcome, String boundary,
                                  String invocation, int intention, int merged, List<String> messages) {
        return new Checkpoint(UUID.randomUUID().toString(), agent, plan, outcome, boundary,
                invocation, intention, merged, messages, complete);
    }
    private synchronized CompletableFuture<Boolean> submit(Checkpoint checkpoint) {
        var result = new CompletableFuture<Boolean>(); pending.addLast(new Pending(checkpoint, result));
        if (!draining) {
            draining = true;
            long epoch = ExecutionBarrier.closeAdmission();
            worker.execute(() -> drain(epoch));
        }
        return result;
    }
    private void drain(long epoch) {
        try {
            freeze(epoch);
            while (true) {
                Pending next;
                synchronized (this) {
                    next = pending.pollFirst();
                    if (next == null) { ExecutionBarrier.release(epoch); draining = false; return; }
                }
                boolean approved = handler.verify(next.checkpoint());
                next.result().complete(approved);
                if (!approved) return; // No timeout, console action or failed transport may resume the source.
            }
        } catch (Exception failure) { System.out.println("JACAMO_CHECKPOINT_HELD " + failure); failure.printStackTrace(System.out); }
    }
    private static void freeze(long epoch) throws InterruptedException {
        if (!ExecutionBarrier.awaitFrozen(epoch, Duration.ofSeconds(30)).clockFrozen())
            throw new IllegalStateException("SOURCE_BARRIER_NOT_FROZEN");
    }
    private void await(Checkpoint checkpoint) throws Exception {
        var result = submit(checkpoint);
        // A source hook must yield its admitted scheduler step before waiting for the global cut.
        var suspension = ExecutionBarrier.suspend();
        try { result.get(); }
        finally { suspension.resume(); } // HOLD keeps this parked before dispatch.
    }
    private void unavailable(String reason) {
        complete = false;
        submit(checkpoint("", reason, "UNAVAILABLE", "SOURCE_UNAVAILABLE", "", -1, 0, List.of()));
    }
    @Override public void close() {
        ExecutionBarrier.stop(); worker.shutdownNow();
        owned.forEach(Architecture.monitors::remove);
    }

    /** Jason extension point; all reasoning, message delivery and scheduling remain in Jason. */
    public static final class Architecture extends AgArch {
        private static final Map<TransitionSystem, Monitor> monitors = new ConcurrentHashMap<>();
        @Override public void reasoningCycleStarting() {
            var monitor = monitors.get(getTS()); if (monitor != null) monitor.validateLibrary();
            super.reasoningCycleStarting();
        }
        @Override public void reasoningCycleFinished() {
            var monitor = monitors.get(getTS()); if (monitor != null) monitor.checkDropped();
            super.reasoningCycleFinished();
        }
        @Override public void sendMsg(Message message) throws Exception {
            var monitor = monitors.get(getTS());
            if (monitor == null) super.sendMsg(message);
            else try (var step = ExecutionBarrier.enter("jacamo-message:" + getAgName())) {
                monitor.send(message, () -> super.sendMsg(message));
            }
        }
        @Override public void broadcast(Message message) throws Exception {
            for (String receiver : getRuntimeServices().getAgentsName()) if (!receiver.equals(getAgName())) {
                var copy = message.clone(); copy.setReceiver(receiver); sendMsg(copy);
            }
        }
    }

    private final class Monitor implements GoalListener, CircumstanceListener {
        private final class Group {
            final String invocation = UUID.randomUUID().toString();
            final String plan;
            final IntendedMeans root;
            final Intention intention;
            final List<Delivery> deliveries = new ArrayList<>();
            final List<String> messages = new ArrayList<>();
            int merged;
            boolean forked, childFailed;
            Group(String plan, IntendedMeans root, Intention intention) {
                this.plan = plan; this.root = root; this.intention = intention;
            }
            Checkpoint event(String outcome, String boundary, List<String> ids) {
                return checkpoint(agent, plan, outcome, boundary, invocation, intention.getId(), merged, ids);
            }
        }
        private final TransitionSystem ts;
        private final String agent;
        private final Map<IntendedMeans, Group> active = new IdentityHashMap<>();
        private final Map<String, Group> roots = new HashMap<>();
        private final Set<Intention> droppedCandidates = Collections.newSetFromMap(new IdentityHashMap<>());
        // Jason clones IntendedMeans at fork/join. A private unifier binding survives that
        // public clone operation without reflection into Jason or modifying its scheduler.
        private final VarTerm continuationKey = new VarTerm("UseCheckpoint" + UUID.randomUUID().toString().replace("-", ""));
        private final Map<Plan, PlanBody> admittedPlans = new IdentityHashMap<>();
        private final Map<Trigger, Event> goals = new IdentityHashMap<>();
        Monitor(TransitionSystem ts, String agent) { this.ts = ts; this.agent = agent; }
        void install() {
            ts.addGoalListener(this);
            ts.getC().addEventListener(this);
            for (var event : ts.getC().getEvents()) goals.put(event.getTrigger(), event);
            int ordinal = 0;
            for (var plan : ts.getAg().getPL()) {
                if (plan.getBody() == null) continue;
                String definition = plan.getSrcInfo() + ":" + (++ordinal) + ":" + plan.getTrigger();
                var first = marker(definition, true);
                if (plan.getBody().getBodyTerm() != null) first.setBodyNext((PlanBody) plan.getBody().clone());
                PlanBody tail = first;
                while (tail.getBodyNext() != null) tail = tail.getBodyNext();
                tail.setBodyNext(marker(definition, false));
                plan.setTerm(3, first); admittedPlans.put(plan, first);
            }
        }
        void validateLibrary() {
            int count = 0;
            for (var plan : ts.getAg().getPL()) {
                if (plan.getBody() == null) continue;
                count++;
                if (admittedPlans.get(plan) != plan.getBody()) {
                    unavailable("PLAN_LIBRARY_CHANGED"); throw new IllegalStateException("SOURCE_PLAN_LIBRARY_CHANGED");
                }
            }
            if (count != admittedPlans.size()) {
                unavailable("PLAN_LIBRARY_CHANGED"); throw new IllegalStateException("SOURCE_PLAN_LIBRARY_CHANGED");
            }
        }
        private PlanBody marker(String plan, boolean entry) {
            var literal = new InternalActionLiteral(Marker.class.getName()); literal.setIA(new Marker(this, plan, entry));
            return new PlanBodyImpl(PlanBody.BodyType.internalAction, literal);
        }
        private Group group(Intention intention) {
            if (intention != null) for (var means : intention) {
                var group = active.get(means); if (group != null) return group;
                group = continuation(means);
                if (group != null && !awaitingJoin(means)) return group;
            }
            return null;
        }
        private Group continuation(IntendedMeans means) {
            var value = means.getUnif().get(continuationKey);
            return value instanceof StringTerm token ? roots.get(token.getString()) : null;
        }
        private boolean awaitingJoin(IntendedMeans means) {
            for (var body = means.getCurrentStep(); body != null; body = body.getBodyNext())
                if (body.getBodyTerm() instanceof InternalActionLiteral literal && literal.getFunctor().equals(".join")) return true;
            return false;
        }
        synchronized void onMarker(String plan, boolean entry) throws Exception {
            var intention = ts.getC().getSelectedIntention();
            if (intention == null || intention.isFinished()) throw new IllegalStateException("PLAN_FRAME_MISSING");
            var means = intention.peek();
            if (entry) {
                if (active.containsKey(means)) throw new IllegalStateException("PLAN_FRAME_DUPLICATE_ENTRY");
                var parent = group(intention);
                if (parent == null) {
                    parent = new Group(plan, means, intention); roots.put(parent.invocation, parent);
                    means.getUnif().bind(continuationKey, ASSyntax.createString(parent.invocation));
                }
                else parent.merged++;
                active.put(means, parent);
            } else finish(means, "SUCCEEDED");
        }
        private void finish(IntendedMeans means, String outcome) throws Exception {
            var group = active.remove(means);
            boolean isRoot = group != null && group.root == means;
            if (group == null) { group = continuation(means); isRoot = group != null; }
            if (!isRoot) return;
            var completed = group;
            if (outcome.equals("FAILED")) for (var frame : group.intention) {
                var parent = continuation(frame);
                if (parent != null && parent != group) parent.childFailed = true;
            }
            roots.remove(group.invocation); active.values().removeIf(value -> value == completed);
            var event = group.event(outcome, "PLAN_COMPLETED", group.messages);
            if (group.deliveries.isEmpty() || !outcome.equals("SUCCEEDED")) submit(event);
            else {
                // A final outgoing message is published only after the enclosing plan is approved.
                // This combines completion and return-to-caller into one checkpoint.
                await(event);
                try { for (var delivery : group.deliveries) delivery.send(); }
                catch (Exception failure) { unavailable("MESSAGE_DELIVERY_FAILED"); throw failure; }
            }
        }
        synchronized void send(Message message, Delivery delivery) throws Exception {
            if (agent.equals(message.getReceiver())) { delivery.send(); return; }
            var intention = ts.getC().getSelectedIntention();
            var group = group(intention);
            String id = message.getMsgId() + ":" + message.getReceiver();
            if (group != null && !message.isAsk() && onlyReturnsRemain(intention)) {
                group.messages.add(id); group.deliveries.add(delivery); return;
            }
            var event = group == null
                    ? checkpoint(agent, "SOURCE_MESSAGE", "RUNNING", "AGENT_HANDOFF", "", -1, 0, List.of(id))
                    : group.event("RUNNING", "AGENT_HANDOFF", List.of(id));
            await(event);
            delivery.send();
        }
        private boolean onlyReturnsRemain(Intention intention) throws Exception {
            for (var means : intention) {
                var current = means.getCurrentStep();
                if (current == null) return false;
                for (var next = current.getBodyNext(); next != null; next = next.getBodyNext()) {
                    if (!(next.getBodyTerm() instanceof InternalActionLiteral literal)
                            || !(literal.getIA(ts.getAg()) instanceof Marker marker) || marker.entry) return false;
                }
            }
            return true;
        }
        @Override public synchronized void goalStarted(Event event) { goals.put(event.getTrigger(), event); }
        @Override public synchronized void goalFinished(Trigger trigger, GoalStates state) {
            // Jason derives GoalStates.dropped from the same queue-removal callback;
            // an atomic subgoal selection is not a semantic goal cancellation.
            if (state == GoalStates.dropped) {
                for (var means : active.keySet()) if (means.getTrigger() == trigger)
                    droppedCandidates.add(active.get(means).intention);
                return;
            }
            goals.remove(trigger);
            if (state == GoalStates.failed || state == null) try {
                for (var means : new ArrayList<>(active.keySet())) if (means.getTrigger() == trigger) {
                    finish(means, "FAILED"); return;
                }
            } catch (Exception failure) { unavailable("SOURCE_GOAL_COMPLETION_OBSERVATION_FAILED"); }
        }
        @Override public synchronized void goalFailed(Trigger trigger, Term reason) {
            try {
                for (var means : new ArrayList<>(active.keySet())) if (means.getTrigger() == trigger) {
                    finish(means, "FAILED"); return;
                }
                var event = goals.remove(trigger);
                if (event == null) {
                    var selected = ts.getC().getSelectedEvent();
                    if (selected != null && selected.getTrigger().equals(trigger)) event = selected;
                }
                if (event != null && group(event.getIntention()) == null)
                    submit(checkpoint(agent, trigger.toString(), "FAILED", "GOAL_FAILED", "", -1, 0, List.of()));
            } catch (Exception failure) { unavailable("SOURCE_FAILURE_OBSERVATION_FAILED"); }
        }
        @Override public synchronized void intentionDropped(Intention intention) {
            // Jason also fires this callback when selecting an atomic event. Resolve it
            // after the cycle, once the intention has been moved to its next place.
            if (intention != null) droppedCandidates.add(intention);
        }
        synchronized void checkDropped() {
            var live = Collections.newSetFromMap(new IdentityHashMap<IntendedMeans, Boolean>());
            java.util.function.Consumer<Intention> include = intention -> {
                if (intention != null) for (var means : intention) live.add(means);
            };
            var intentions = ts.getC().getAllIntentions();
            while (intentions.hasNext()) include.accept(intentions.next());
            var running = ts.getC().getRunningIntentionsPlusAtomic();
            while (running.hasNext()) include.accept(running.next());
            var events = ts.getC().getEventsPlusAtomic();
            while (events.hasNext()) include.accept(events.next().getIntention());
            for (var event : ts.getC().getPendingEvents().values()) include.accept(event.getIntention());
            var selectedEvent = ts.getC().getSelectedEvent();
            if (selectedEvent != null) include.accept(selectedEvent.getIntention());
            include.accept(ts.getC().getSelectedIntention());
            for (var means : live) {
                var parent = continuation(means);
                if (parent != null && means != parent.root) parent.forked = true;
            }
            for (var group : new HashSet<>(active.values())) if (group.forked && !live.contains(group.root)
                    && live.stream().noneMatch(m -> continuation(m) == group)) {
                try { finish(group.root, group.childFailed ? "FAILED" : "CANCELLED"); }
                catch (Exception failure) { unavailable("SOURCE_FORK_COMPLETION_OBSERVATION_FAILED"); }
            }
            for (var group : new HashSet<>(active.values())) if (droppedCandidates.contains(group.intention)
                    && !live.contains(group.root) && live.stream().noneMatch(m -> continuation(m) == group)) {
                try { finish(group.root, "CANCELLED"); }
                catch (Exception failure) { unavailable("SOURCE_CANCELLATION_OBSERVATION_FAILED"); }
            }
            droppedCandidates.clear();
            // Retain a source-owned stack across transient queue gaps, not across an
            // actual drop resolved above. Fork continuations retain their invocation.
            for (var group : active.values()) include.accept(group.intention);
            for (var means : new ArrayList<>(active.keySet())) if (!live.contains(means)) {
                var group = active.get(means);
                if (group != null && means == group.root && live.stream().anyMatch(m -> continuation(m) == group)) continue;
                try { finish(means, "CANCELLED"); }
                catch (Exception failure) { unavailable("SOURCE_CANCELLATION_OBSERVATION_FAILED"); }
            }
        }
    }
    private static final class Marker extends DefaultInternalAction {
        private final Monitor monitor;
        private final String plan;
        private final boolean entry;
        Marker(Monitor monitor, String plan, boolean entry) { this.monitor = monitor; this.plan = plan; this.entry = entry; }
        @Override public Object execute(TransitionSystem ts, Unifier unifier, Term[] args) throws Exception {
            monitor.onMarker(plan, entry); return true;
        }
    }
}
