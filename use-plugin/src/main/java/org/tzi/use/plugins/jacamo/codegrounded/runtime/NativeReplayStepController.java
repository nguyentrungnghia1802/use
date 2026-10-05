package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.tzi.use.main.Session;
import org.tzi.use.uml.sys.MSystem;

/** Only navigation/ownership: all recorded semantics remain in NativeRuntimeReplay and its native projector. */
public final class NativeReplayStepController implements AutoCloseable {
    public record Status(int step, int total, long stateVersion, String event, String source,
                         boolean busy, boolean reconstructionRequired) { }
    private record Selection(NativeRuntimeReplay.Bundle bundle, NativeRuntimeReplay.Cursor cursor, int step) { }
    private final Session session;
    private final AtomicReference<Selection> selected=new AtomicReference<>();
    private final AtomicBoolean busy=new AtomicBoolean(), closed=new AtomicBoolean();
    private final AtomicLong epoch=new AtomicLong();
    public NativeReplayStepController(Session session) { this.session=java.util.Objects.requireNonNull(session); }
    public boolean active() { return selected.get()!=null; }
    public boolean busy() { return busy.get(); }
    public MSystem system() { var current=selected.get(); return current==null ? null : current.cursor().projector().system(); }
    public VerificationSnapshot verificationSnapshot() {
        var current=selected.get();
        return current==null ? VerificationSnapshot.empty() : current.cursor().projector().coordinator().verificationSnapshot();
    }
    public GoalViewSnapshot goalView() {
        var current=selected.get();if(current==null)return GoalViewSnapshot.empty();
        var view=GoalViewSnapshot.read(current.cursor().projector().coordinator(),List.of(),List.of(),null);
        return new GoalViewSnapshot(view.schemes(),"RECORDED_REPLAY","READ_ONLY",view.diagnostic());
    }
    public RuntimeHistoryPage historyTail() {
        var current=selected.get(); return current==null ? RuntimeHistoryPage.empty()
                : selectedHistory(current,current.cursor().projector().coordinator().journal().tailPage(128));
    }
    public RuntimeHistoryPage historyPage(long offset,int limit) {
        var current=requireSelected(); return selectedHistory(current,current.cursor().projector().coordinator().journal().page(offset,limit));
    }
    private RuntimeHistoryPage selectedHistory(Selection current,RuntimeHistoryPage page) {
        if(!current.cursor().failed())return page;
        long boundary=current.bundle().steps().get(current.step()).endOrdinal()+1;
        // Failed local dispatch is not a selected historical state. Do not expose its uncommitted results.
        // Never truncate the append-only journal or suppress any actual persistence GAP.
        return new RuntimeHistoryPage(page.entries().stream().filter(entry->entry.ordinal()<boundary).toList(),
                Math.min(page.nextOrdinal(),boundary),Math.min(page.persistedEntries(),boundary),
                Math.min(page.retainedFromOrdinal(),boundary),page.gap(),page.diagnostic()+" FAILED_REPLAY_CONTEXT: Reset/Previous required; unselected range is not formal history",page.persistedPage());
    }
    public Status status() {
        var current=selected.get(); if(current==null)return null;
        var step=current.bundle().steps().get(current.step());
        return new Status(current.step(),current.bundle().steps().size()-1,step.recordedStateVersion(),
                step.event(),step.source(),busy.get(),current.cursor().failed());
    }
    public List<NativeRuntimeReplay.Step> steps() { return requireSelected().bundle().steps(); }
    public <T>T read(java.util.function.Function<MSystem,T> action) {
        var current=requireSelected();
        return current.cursor().projector().coordinator().read(()->action.apply(current.cursor().projector().system()));
    }
    public void open(Path recording) {
        long ticket=begin(); NativeRuntimeReplay.Bundle bundle=null; NativeRuntimeReplay.Cursor cursor=null;
        Selection old=selected.get(); MSystem previous=session.hasSystem()?session.system():null;
        try {
            bundle=new NativeRuntimeReplay().openBundle(recording);
            if(!bundle.report().complete())throw new IllegalArgumentException("STEP_REPLAY_RECORDED_VALIDATION_FAILED:"+bundle.report().diagnostics());
            cursor=bundle.atStep(0);
            activate(new Selection(bundle,cursor,0),old,previous,ticket);
            bundle=null; cursor=null;
            if(old!=null){old.cursor().close();old.bundle().close();}
        } catch(Exception error){throw failure(error);}
        finally{if(cursor!=null)cursor.close();if(bundle!=null)bundle.close();busy.set(false);}
    }
    public void reset() { reconstruct(0); }
    public void previous() { var current=requireSelected(); reconstruct(Math.max(0,current.step()-1)); }
    private void reconstruct(int target) {
        var old=requireSelected();long ticket=begin();NativeRuntimeReplay.Cursor cursor=null;
        try {
            cursor=old.bundle().atStep(target);
            activate(new Selection(old.bundle(),cursor,target),old,old.cursor().projector().system(),ticket);
            cursor=null; old.cursor().close();
        } catch(Exception error){throw failure(error);}
        finally{if(cursor!=null)cursor.close();busy.set(false);}
    }
    public void next() {
        long ticket=begin();
        Selection old=null;
        try {
            old=requireSelected();
            Selection admitted=old;
            if(old.cursor().failed())throw new IllegalStateException("REPLAY_CURSOR_RECONSTRUCTION_REQUIRED: Reset or Previous");
            if(old.step()==old.bundle().steps().size()-1)return;
            int target=old.step()+1;
            old.cursor().forward(old.bundle().steps().get(target).endOrdinal(),()->{
                check(ticket,admitted,admitted.cursor().projector().system());
            },()->selected.set(new Selection(admitted.bundle(),admitted.cursor(),target)));
        } catch(Exception error){
            Selection previous=old;
            if(previous!=null)try{onEdt(()->{var current=selected.get();
                if(current!=null&&current.cursor()==previous.cursor())selected.set(previous);return null;});}
            catch(Exception cleanup){error.addSuppressed(cleanup);}
            throw failure(error);
        }
        finally{busy.set(false);}
    }
    private void activate(Selection candidate,Selection old,MSystem previous,long ticket)throws Exception {
        onEdt(()->{
            check(ticket,old,previous);
            candidate.cursor().projector().system().setReadOnly(true);
            selected.set(candidate);
            try {
                session.setSystem(candidate.cursor().projector().system());
                candidate.cursor().projector().coordinator().refreshViews();
            }
            catch(RuntimeException failure){selected.set(old);session.setSystem(previous);throw failure;}
            return null;
        });
    }
    private void check(long ticket,Selection expected,MSystem system) {
        if(closed.get()||epoch.get()!=ticket||selected.get()!=expected
                || (session.hasSystem()?session.system():null)!=system)
            throw new IllegalStateException("REPLAY_OPERATION_CANCELLED_OR_SESSION_REPLACED");
    }
    private long begin() {
        if(SwingUtilities.isEventDispatchThread())throw new IllegalStateException("REPLAY_REQUIRES_WORKER");
        if(closed.get())throw new IllegalStateException("REPLAY_CONTROLLER_CLOSED");
        if(!busy.compareAndSet(false,true))throw new IllegalStateException("REPLAY_NAVIGATION_BUSY");
        return epoch.incrementAndGet();
    }
    private Selection requireSelected(){var current=selected.get();if(current==null)throw new IllegalStateException("REPLAY_NOT_OPEN");return current;}
    private static RuntimeException failure(Exception error){
        Throwable cause=error;
        while(cause instanceof java.util.concurrent.ExecutionException && cause.getCause()!=null)cause=cause.getCause();
        return cause instanceof RuntimeException runtime ? runtime
                : new IllegalStateException("REPLAY_NAVIGATION_FAILED: "+cause.getClass().getSimpleName()+": "+cause.getMessage(),cause);
    }
    private static <T>T onEdt(java.util.concurrent.Callable<T> action)throws Exception {
        if(SwingUtilities.isEventDispatchThread())return action.call();
        var future=new FutureTask<T>(action);SwingUtilities.invokeAndWait(future);return future.get();
    }
    @Override public void close() {
        if(!closed.compareAndSet(false,true))return;
        epoch.incrementAndGet();
        try {
            var old=onEdt(()->{var current=selected.getAndSet(null);
                if(current!=null&&session.hasSystem()&&session.system()==current.cursor().projector().system())session.setSystem(null);
                return current;});
            if(old!=null){old.cursor().close();old.bundle().close();}
        } catch(Exception error){throw failure(error);}
    }
}
