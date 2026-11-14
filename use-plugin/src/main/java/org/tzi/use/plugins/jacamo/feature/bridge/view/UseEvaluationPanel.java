package org.tzi.use.plugins.jacamo.feature.bridge.view;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.tzi.use.plugins.jacamo.dsl.state.ModelStateDocument;
import org.tzi.use.plugins.jacamo.dsl.istarscn.view.IStarScenarioView;
import org.tzi.use.plugins.jacamo.dsl.csl.view.CslView;
import org.tzi.use.plugins.jacamo.dsl.istar.view.IStarView;
import org.tzi.use.plugins.jacamo.dsl.oal.view.AolDiagram;
import org.tzi.use.plugins.jacamo.bridge.*;

/** Four model/state views share the exact verified cut and native schema. */
public final class UseEvaluationPanel extends JPanel implements AutoCloseable {
    private record Checkpoint(Map<String,Object> request,Map<String,Object> response,CslStateVerifier.Result result) { }
    private final StateRunConfiguration configuration;
    private final Consumer<Map<String,Object>> onSelected;
    private final JTabbedPane tabs=new JTabbedPane();
    private final JCheckBox follow=new JCheckBox("Follow live",true);
    private final JLabel selected=new JLabel("Waiting for the first checkpoint");
    private final DefaultTableModel checks=JaCaMoRuntimePanel.table("Object","Condition","Kind","OCL","Value","Diagnostic");
    private final DefaultTableModel history=JaCaMoRuntimePanel.table("Checkpoint","Agent","Plan","Boundary","Merged subplans","Source outcome","Decision");
    private final List<Checkpoint> checkpoints=new ArrayList<>();
    private final JPanel oalPanel=new JPanel(new BorderLayout());
    private CslView csl;
    private IStarView istar;
    private IStarScenarioView scenario;
    private AolDiagram oal;
    private Checkpoint displayed;
    private boolean selecting;
    public UseEvaluationPanel(StateRunConfiguration configuration,Consumer<Map<String,Object>> onSelected) {
        super(new BorderLayout());this.configuration=configuration;this.onSelected=onSelected;setName("use-evaluation-panel");
        var header=new JPanel(new BorderLayout());header.add(selected,BorderLayout.NORTH);
        var actions=new JPanel(new FlowLayout(FlowLayout.LEADING));actions.add(follow);header.add(actions);
        var saveOal=new JButton("Save OAL...");var saveScn=new JButton("Save iStarSCN...");actions.add(saveOal);actions.add(saveScn);
        saveOal.addActionListener(e->save(ModelStateDocument.Kind.OAL));saveScn.addActionListener(e->save(ModelStateDocument.Kind.ISTAR_SCN));
        add(header,BorderLayout.NORTH);add(tabs);tabs.addTab("Evaluation",new JLabel("Loading CSL and iStar models...",SwingConstants.CENTER));
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        follow.addActionListener(e->{if(follow.isSelected()&&!checkpoints.isEmpty())display(checkpoints.getLast());});
        tabs.addChangeListener(e->refreshDiagrams());
    }
    public void bind(StateVerificationSession session) {
        detachDiagrams();tabs.removeAll();
        csl=CslView.embedded(session.cslModel(),session.nativeModel());csl.setSourceFile(configuration.csl());
        istar=new IStarView();istar.setModel(session.goalModel());scenario=new IStarScenarioView(session.goalModel());
        oal=new AolDiagram(new java.io.PrintWriter(java.io.Writer.nullWriter()));oalPanel.removeAll();oalPanel.add(new JScrollPane(oal));
        tabs.addTab("CSL Model",csl);tabs.addTab("OAL State",oalPanel);tabs.addTab("iStar Model",istar);tabs.addTab("iStarSCN",scenario);
        var checkTable=new JTable(checks);checkTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);tabs.addTab("OCL",new JScrollPane(checkTable));
        var timeline=new JTable(history);timeline.setName("bridge-checkpoint-history");timeline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        timeline.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!selecting&&timeline.getSelectedRow()>=0){follow.setSelected(false);display(checkpoints.get(timeline.convertRowIndexToModel(timeline.getSelectedRow())));}});
        tabs.addTab("Checkpoints",new JScrollPane(timeline));
        var mapping=new JTextArea();mapping.setEditable(false);try{mapping.setText(Files.readString(configuration.mapping()));}catch(Exception error){mapping.setText(error.toString());}
        tabs.addTab("Mapping",new JScrollPane(mapping));
        if(!checkpoints.isEmpty())display(checkpoints.getLast());
    }
    public void checkpoint(Map<String,Object> request,Map<String,Object> response,CslStateVerifier.Result result) {
        var checkpoint=new Checkpoint(request,response,result);checkpoints.add(checkpoint);
        selecting=true;
        try {
            if(checkpoints.size()>1000){checkpoints.removeFirst();history.removeRow(0);}
            var source=request.get("checkpoint") instanceof Map<?,?> metadata?metadata:Map.of();
            history.addRow(new Object[]{result==null?"?":result.sequence(),request.get("agent"),request.get("plan"),source.get("boundary"),source.get("mergedPlans"),request.get("outcome"),response.get("decision")});
        }finally{selecting=false;}
        if(follow.isSelected())display(checkpoint);
    }
    private void display(Checkpoint checkpoint) {
        displayed=checkpoint;checks.setRowCount(0);
        if(checkpoint.result()!=null)checkpoint.result().checks().forEach(c->checks.addRow(new Object[]{c.context(),c.id(),c.kind(),c.expression(),c.verdict(),c.diagnostic()}));
        selected.setText("Checkpoint " + (checkpoint.result()==null?"?":checkpoint.result().sequence()) + " · " + checkpoint.response().get("decision"));
        onSelected.accept(checkpoint.request());refreshDiagrams();
    }
    private void refreshDiagrams() {
        if(displayed==null||displayed.result()==null)return;
        if(tabs.getSelectedComponent()==oalPanel&&oal!=null)oal.setState(ModelStateDocument.objects(displayed.result().state()));
        if(tabs.getSelectedComponent()==scenario&&scenario!=null){var labels=new HashMap<String,String>();ModelStateDocument.objects(displayed.result().state()).objects().forEach((id,object)->labels.put(id,object.displayName()));scenario.update(markings(displayed.result()),labels);}
    }
    private static List<ModelStateDocument.Marking> markings(CslStateVerifier.Result result) {
        return result.elements().stream().map(e->new ModelStateDocument.Marking(e.context(),e.actor(),e.element(),e.status())).toList();
    }
    private void save(ModelStateDocument.Kind kind) {
        if(displayed==null||displayed.result()==null){JOptionPane.showMessageDialog(this,"No evaluated checkpoint is available yet.");return;}
        var chooser=new JFileChooser();String extension=kind==ModelStateDocument.Kind.OAL?"oal":"iscn";
        chooser.setFileFilter(new FileNameExtensionFilter(kind+" snapshot",extension));chooser.setSelectedFile(new java.io.File("checkpoint-"+displayed.result().sequence()+"."+extension));
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path file=chooser.getSelectedFile().toPath();if(!file.getFileName().toString().endsWith("."+extension))file=file.resolveSibling(file.getFileName()+"."+extension);
        var result=displayed.result();var document=new ModelStateDocument(kind,kind==ModelStateDocument.Kind.OAL?configuration.csl():configuration.istar(),Long.toString(result.sequence()),ModelStateDocument.objects(result.state()),markings(result));
        try{document.write(file);}catch(Exception error){JOptionPane.showMessageDialog(this,error.toString(),"Cannot save state",JOptionPane.ERROR_MESSAGE);}
    }
    private void detachDiagrams(){if(csl!=null)csl.detachModel();if(istar!=null)istar.detachModel();if(scenario!=null)scenario.close();if(oal!=null)oal.stopLayoutThread();}
    public void reset(){displayed=null;checkpoints.clear();history.setRowCount(0);checks.setRowCount(0);follow.setSelected(true);selected.setText("Waiting for the first checkpoint");detachDiagrams();csl=null;istar=null;scenario=null;oal=null;tabs.removeAll();tabs.addTab("Evaluation",new JLabel("Loading CSL and iStar models...",SwingConstants.CENTER));}
    @Override public void close(){detachDiagrams();}
}
