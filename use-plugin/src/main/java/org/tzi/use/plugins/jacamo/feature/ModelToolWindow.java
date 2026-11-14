package org.tzi.use.plugins.jacamo.feature;
import org.tzi.use.plugins.jacamo.dsl.state.ModelStateDocument;
import org.tzi.use.plugins.jacamo.dsl.istarscn.view.IStarScenarioView;

import java.awt.*;
import java.nio.file.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import org.tzi.use.gui.main.*;
import org.tzi.use.gui.views.View;
import org.tzi.use.plugins.jacamo.dsl.csl.view.CslView;
import org.tzi.use.plugins.jacamo.dsl.istar.view.IStarView;
import org.tzi.use.plugins.jacamo.dsl.oal.view.AolDiagram;
import org.tzi.use.plugins.jacamo.verify.api.EngineSemanticsProfile;
import org.tzi.use.plugins.jacamo.dsl.csl.parser.*;
import org.tzi.use.plugins.jacamo.dsl.istar.parser.*;
import org.tzi.use.plugins.jacamo.verify.use75.*;

/** Four independent USE menu adapters share parsing, placement and disposal here. */
public final class ModelToolWindow {
    public enum Kind { CSL, OAL, ISTAR, ISTAR_SCN }
    private ModelToolWindow() { }
    public static void open(MainWindow owner,Kind kind) {
        var chooser=new JFileChooser();chooser.setDialogTitle("Open " + kind);
        chooser.setFileFilter(new FileNameExtensionFilter(switch(kind){case CSL->"CSL model (*.csl)";case OAL->"OAL snapshot (*.oal, *.aol)";case ISTAR->"iStar model (*.istar)";case ISTAR_SCN->"iStarSCN snapshot (*.iscn)";},switch(kind){case CSL->new String[]{"csl"};case OAL->new String[]{"oal","aol"};case ISTAR->new String[]{"istar"};case ISTAR_SCN->new String[]{"iscn"};}));
        if(chooser.showOpenDialog(owner)!=JFileChooser.APPROVE_OPTION)return;
        Path file=chooser.getSelectedFile().toPath();
        new SwingWorker<Loaded,Void>() {
            protected Loaded doInBackground()throws Exception{return load(file,kind);}
            protected void done(){try{show(owner,file,kind,get());}catch(Exception error){JOptionPane.showMessageDialog(owner,error.getCause()==null?error.toString():error.getCause().toString(),"Cannot open model",JOptionPane.ERROR_MESSAGE);}}
        }.execute();
    }
    private record Loaded(org.vnu.sme.goal.dsl.csl.mm.CslModel csl,org.tzi.use.uml.mm.MModel nativeModel,org.vnu.sme.goal.dsl.istar.mm.GoalModel goals,ModelStateDocument state) { }
    private static Loaded load(Path file,Kind kind)throws Exception {
        ModelStateDocument state=kind==Kind.OAL||kind==Kind.ISTAR_SCN?ModelStateDocument.read(file):null;
        if(state!=null&&state.kind()!=(kind==Kind.OAL?ModelStateDocument.Kind.OAL:ModelStateDocument.Kind.ISTAR_SCN))throw new IllegalArgumentException("Snapshot belongs to another model tool");
        Path model=state==null?file:state.model();String text=Files.readString(model);
        if(kind==Kind.CSL||kind==Kind.OAL) {
            var csl=new CslFrontend().compile(text,model.toString()).requireModel();
            var binding=new Use75CslModelBackend().compile(csl,M0ExtensionSpec.unifiedContexts(),CslObservationSchema::prepare);
            return new Loaded(csl,binding.model(),null,state);
        }
        return new Loaded(null,null,new IStarFrontend().compileScoped(text,model.toString(),EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel(),state);
    }
    private static void show(MainWindow owner,Path file,Kind kind,Loaded loaded)throws Exception {
        var content=new JPanel(new BorderLayout());Runnable dispose;
        if(kind==Kind.CSL){var view=CslView.embedded(loaded.csl(),loaded.nativeModel());view.setSourceFile(file);content.add(view);dispose=view::detachModel;}
        else if(kind==Kind.ISTAR){var view=new IStarView();view.setModel(loaded.goals());content.add(view);dispose=view::detachModel;}
        else if(kind==Kind.OAL){var view=new AolDiagram(owner.logWriter());view.setState(loaded.state().state());content.add(new JScrollPane(view));dispose=view::stopLayoutThread;}
        else {var view=new IStarScenarioView(loaded.goals());var labels=new java.util.HashMap<String,String>();loaded.state().state().objects().forEach((id,object)->labels.put(id,object.displayName()));view.update(loaded.state().markings(),labels);content.add(view);dispose=view::close;}
        if(loaded.state()!=null)content.add(new JLabel("Saved checkpoint " + loaded.state().checkpoint()),BorderLayout.NORTH);
        var view=new View(){public void detachModel(){dispose.run();}};
        var frame=new ViewFrame(kind + " — " + file.getFileName(),view,"Diagram.gif");frame.setContentPane(content);frame.setSize(1100,760);
        frame.addInternalFrameListener(new InternalFrameAdapter(){public void internalFrameClosed(InternalFrameEvent e){dispose.run();}});
        owner.addNewViewFrame(frame);frame.setSize(1100,760);try{frame.setMaximum(true);}catch(java.beans.PropertyVetoException ignored){}
    }
}
