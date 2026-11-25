package org.tzi.use.plugins.jacamo.gui.main;

import org.tzi.use.plugins.jacamo.gui.views.JaCaMoRuntimePanel;
import org.tzi.use.plugins.jacamo.gui.views.UseEvaluationPanel;
import org.tzi.use.plugins.jacamo.session.ExperimentBundle;
import org.tzi.use.plugins.jacamo.session.SnapshotVerifier;
import org.tzi.use.plugins.jacamo.session.VerificationSession;
import org.tzi.use.plugins.jacamo.session.VerificationSession.Configuration;
import java.awt.*;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.gui.main.ViewFrame;
import org.tzi.use.gui.views.View;

/** The USE-owned experiment screen: source/runtime left, CSL/iStar evaluation right. */
public final class ExperimentWindow extends JPanel implements View {
    private static final Map<MainWindow, ViewFrame> WINDOWS = new WeakHashMap<>();
    private static final Set<MainWindow> LOADING = Collections.newSetFromMap(new WeakHashMap<>());

    private final Configuration configuration;
    private final JaCaMoRuntimePanel runtime;
    private final UseEvaluationPanel evaluation;
    private final JLabel status=new JLabel("Preparing JaCaMo...");
    private final JButton stop=new JButton("Stop"),restart=new JButton("Restart");
    private VerificationSession session;
    private long epoch;
    private boolean disposed,launching;
    private ExperimentWindow(Configuration configuration) {
        super(new BorderLayout(0,8));this.configuration=configuration;setName("jacamo-evaluation-view");
        runtime=new JaCaMoRuntimePanel(configuration.project());evaluation=new UseEvaluationPanel(configuration,runtime::checkpoint);
        var split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,section("JaCaMo",runtime),section("USE Evaluation",evaluation));
        split.setName("jacamo-use-split");split.setResizeWeight(.35);split.setOneTouchExpandable(true);
        SwingUtilities.invokeLater(()->split.setDividerLocation(.35));
        var header=new JPanel(new BorderLayout());header.setBorder(BorderFactory.createEmptyBorder(8,12,0,12));header.add(status);
        var controls=new JPanel(new FlowLayout(FlowLayout.TRAILING,6,0));controls.add(new JLabel(configuration.project().getFileName().toString()));controls.add(stop);controls.add(restart);header.add(controls,BorderLayout.EAST);
        add(header,BorderLayout.NORTH);add(split);stop.addActionListener(e->stopRun());restart.addActionListener(e->startRun());
    }

    /** Opens a single experiment for the USE desktop, on the Swing event thread. */
    public static void open(MainWindow owner) {
        var existing = WINDOWS.get(owner);
        if (existing != null && !existing.isClosed()) {
            existing.toFront();
            try { existing.setSelected(true); }
            catch (java.beans.PropertyVetoException ignored) { }
            return;
        }
        if (!LOADING.add(owner)) return;
        var selected = RunDialog.show(owner);
        if (selected.isEmpty()) {
            LOADING.remove(owner);
            return;
        }
        owner.statusBar().showTmpMessage("Opening " + selected.get().getFileName());
        new SwingWorker<Configuration, Void>() {
            @Override protected Configuration doInBackground() throws Exception {
                return ExperimentBundle.unpack(selected.get(), Path.of(System.getProperty("java.io.tmpdir")));
            }

            @Override protected void done() {
                LOADING.remove(owner);
                if (!owner.isDisplayable()) return;
                try {
                    open(owner, get());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException failure) {
                    JOptionPane.showMessageDialog(owner, failure.getCause().getMessage(),
                            "Cannot open MRV experiment", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static void open(MainWindow owner, Configuration configuration) {
        var view = new ExperimentWindow(configuration);
        var frame = new ViewFrame("JaCaMo — " + configuration.project().getFileName(), view, "Diagram.gif");
        frame.setContentPane(view);
        frame.setSize(1400, 820);
        WINDOWS.put(owner, frame);
        frame.addInternalFrameListener(new InternalFrameAdapter() {
            @Override public void internalFrameClosed(InternalFrameEvent event) {
                WINDOWS.remove(owner);
                view.detachModel();
            }
        });
        owner.addWorkspaceViewFrame(frame);
        view.startRun();
    }
    private static JPanel section(String title,JComponent content){var panel=new JPanel(new BorderLayout());panel.setBorder(BorderFactory.createTitledBorder(title));panel.add(content);return panel;}
    private void startRun() {
        if(disposed||launching||session!=null&&session.isRunning())return;
        launching=true;long runEpoch=++epoch;restart.setEnabled(false);stop.setEnabled(true);evaluation.reset();runtime.resetInspectors();status.setText("Loading models and starting JaCaMo...");
        new SwingWorker<VerificationSession,Void>() {
            protected VerificationSession doInBackground()throws Exception {
                return VerificationSession.start(configuration,new VerificationSession.Listener(){
                    private void present(Runnable update){SwingUtilities.invokeLater(()->{if(!disposed&&epoch==runEpoch)update.run();});}
                    public void checkpoint(Map<String,Object> request,Map<String,Object> response,SnapshotVerifier.Result result){present(()->{
                        evaluation.checkpoint(request,response,result);String decision=response.get("decision").toString();
                        status.setText(decision+(result==null?"":" · checkpoint "+result.sequence())+" · "+response.get("diagnostic"));
                        status.setForeground(decision.equals("APPROVE")?new Color(20,110,55):new Color(175,35,35));
                    });}
                    public void status(String text){present(()->status.setText(text));}
                    public void log(String line){present(()->runtime.log(line));}
                });
            }
            protected void done(){
                launching=false;
                try {
                    var running=get();
                    if(disposed||epoch!=runEpoch){Thread.ofPlatform().daemon().start(running::close);restart.setEnabled(!disposed);return;}
                    session=running;evaluation.bind(running);status.setToolTipText("Run evidence: "+running.work());runtime.log("Run evidence: "+running.work());
                }catch(Exception error){if(!disposed&&epoch==runEpoch){status.setForeground(new Color(175,35,35));status.setText("Cannot start JaCaMo");runtime.log(error.getCause()==null?error.toString():error.getCause().toString());JOptionPane.showMessageDialog(ExperimentWindow.this,error.getCause()==null?error.toString():error.getCause().toString(),"Cannot start JaCaMo",JOptionPane.ERROR_MESSAGE);restart.setEnabled(true);stop.setEnabled(false);}}
            }
        }.execute();
    }
    private void stopRun() {
        ++epoch;stop.setEnabled(false);restart.setEnabled(false);status.setText("Stopping JaCaMo...");
        var running=session;session=null;
        new SwingWorker<Void,Void>() {
            protected Void doInBackground(){if(running!=null)running.close();return null;}
            protected void done(){if(!disposed){status.setText("Stopped · evaluated states retained for review");restart.setEnabled(!launching);}}
        }.execute();
    }
    @Override public void detachModel(){if(disposed)return;disposed=true;stopRun();evaluation.close();}
}
