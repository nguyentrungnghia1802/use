package org.tzi.use.plugins.jacamo.feature.bridge.view;

import java.awt.*;
import java.nio.file.*;
import java.util.Map;
import javax.swing.*;
import org.tzi.use.gui.views.View;
import org.tzi.use.plugins.jacamo.bridge.*;

/** The USE-owned experiment screen: source/runtime left, CSL/iStar evaluation right. */
public final class JaCaMoEvaluationView extends JPanel implements View {
    private final StateRunConfiguration configuration;
    private final JaCaMoRuntimePanel runtime;
    private final UseEvaluationPanel evaluation;
    private final JLabel status=new JLabel("Preparing JaCaMo...");
    private final JButton stop=new JButton("Stop"),restart=new JButton("Restart");
    private StateVerificationSession session;
    private long epoch;
    private boolean disposed,launching;
    public JaCaMoEvaluationView(StateRunConfiguration configuration) {
        super(new BorderLayout(0,8));this.configuration=configuration;setName("jacamo-evaluation-view");
        runtime=new JaCaMoRuntimePanel(configuration.project());evaluation=new UseEvaluationPanel(configuration,runtime::checkpoint);
        var split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,section("JaCaMo",runtime),section("USE Evaluation",evaluation));
        split.setName("jacamo-use-split");split.setResizeWeight(.35);split.setOneTouchExpandable(true);
        SwingUtilities.invokeLater(()->split.setDividerLocation(.35));
        var header=new JPanel(new BorderLayout());header.setBorder(BorderFactory.createEmptyBorder(8,12,0,12));header.add(status);
        var controls=new JPanel(new FlowLayout(FlowLayout.TRAILING,6,0));controls.add(new JLabel(configuration.project().getFileName().toString()));controls.add(stop);controls.add(restart);header.add(controls,BorderLayout.EAST);
        add(header,BorderLayout.NORTH);add(split);stop.addActionListener(e->stopRun());restart.addActionListener(e->startRun());
    }
    private static JPanel section(String title,JComponent content){var panel=new JPanel(new BorderLayout());panel.setBorder(BorderFactory.createTitledBorder(title));panel.add(content);return panel;}
    public void startRun() {
        if(disposed||launching||session!=null&&session.isRunning())return;
        launching=true;long runEpoch=++epoch;restart.setEnabled(false);stop.setEnabled(true);evaluation.reset();runtime.resetInspectors();status.setText("Loading models and starting JaCaMo...");
        new SwingWorker<StateVerificationSession,Void>() {
            protected StateVerificationSession doInBackground()throws Exception {
                String classpath=BridgeRuntimeClasspath.resolve();Path work=Files.createTempDirectory("use-jacamo-");
                return StateVerificationSession.start(configuration,work,classpath,new StateVerificationSession.Listener(){
                    private void present(Runnable update){SwingUtilities.invokeLater(()->{if(!disposed&&epoch==runEpoch)update.run();});}
                    public void checkpoint(Map<String,Object> request,Map<String,Object> response,CslStateVerifier.Result result){present(()->{
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
                }catch(Exception error){if(!disposed&&epoch==runEpoch){status.setForeground(new Color(175,35,35));status.setText("Cannot start JaCaMo");runtime.log(error.getCause()==null?error.toString():error.getCause().toString());JOptionPane.showMessageDialog(JaCaMoEvaluationView.this,error.getCause()==null?error.toString():error.getCause().toString(),"Cannot start JaCaMo",JOptionPane.ERROR_MESSAGE);restart.setEnabled(true);stop.setEnabled(false);}}
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
