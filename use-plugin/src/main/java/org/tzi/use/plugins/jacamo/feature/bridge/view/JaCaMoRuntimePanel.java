package org.tzi.use.plugins.jacamo.feature.bridge.view;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.*;
import org.tzi.use.plugins.jacamo.bridge.protocol.CanonicalJson;

/** Embedded source browser, live agent inspector, Artifact inspector and runtime console. */
public final class JaCaMoRuntimePanel extends JPanel {
    private final JTextArea source=text(),agentDetails=text(),console=text();
    private final JLabel sourcePath=new JLabel("Select a source file");
    private final JComboBox<String> agents=new JComboBox<>();
    private final DefaultTableModel artifacts=table("Artifact","Type","Observable properties","Observers");
    private Map<String,Map<String,Object>> currentAgents=Map.of();
    private boolean updating;
    public JaCaMoRuntimePanel(Path project) {
        super(new BorderLayout());setName("jacamo-runtime-panel");
        var tabs=new JTabbedPane();var sourcePanel=new JPanel(new BorderLayout());sourcePanel.add(sourcePath,BorderLayout.NORTH);sourcePanel.add(new JScrollPane(source));
        var root=new DefaultMutableTreeNode(new SourceFile(project.getParent()));var tree=new JTree(root);tree.setName("jacamo-project-tree");
        var split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(tree),sourcePanel);split.setResizeWeight(.3);split.setDividerLocation(210);tabs.addTab("Project",split);
        var agentPanel=new JPanel(new BorderLayout());agentPanel.add(agents,BorderLayout.NORTH);agentPanel.add(new JScrollPane(agentDetails));tabs.addTab("Agents",agentPanel);
        var artifactTable=new JTable(artifacts);artifactTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);tabs.addTab("Artifacts",new JScrollPane(artifactTable));tabs.addTab("Console",new JScrollPane(console));add(tabs);
        agents.addActionListener(e->{if(!updating)showAgent();});
        tree.addTreeSelectionListener(e->{var selected=(DefaultMutableTreeNode)tree.getLastSelectedPathComponent();if(selected!=null&&selected.getUserObject() instanceof SourceFile item&&Files.isRegularFile(item.path()))readSource(item.path());});
        new SwingWorker<List<Path>,Void>() {
            protected List<Path> doInBackground()throws Exception {try(var files=Files.walk(project.getParent(),8)){return files.filter(Files::isRegularFile).filter(p->p.toString().matches(".*\\.(jcm|asl|java|xml|properties)$")).filter(p->!p.toString().contains(FileSystems.getDefault().getSeparator()+"target"+FileSystems.getDefault().getSeparator())).sorted().limit(1000).toList();}}
            protected void done(){try{for(Path file:get())addFile(root,project.getParent(),file);((DefaultTreeModel)tree.getModel()).reload();tree.expandRow(0);readSource(project);}catch(Exception error){source.setText(error.toString());}}
        }.execute();
    }
    private record SourceFile(Path path){public String toString(){return path.getFileName().toString();}}
    private static void addFile(DefaultMutableTreeNode root,Path folder,Path file) {
        var current=root;Path accumulated=folder;
        for(Path segment:folder.relativize(file)) {
            accumulated=accumulated.resolve(segment);DefaultMutableTreeNode found=null;
            for(int i=0;i<current.getChildCount();i++){var child=(DefaultMutableTreeNode)current.getChildAt(i);if(((SourceFile)child.getUserObject()).path().equals(accumulated)){found=child;break;}}
            if(found==null){found=new DefaultMutableTreeNode(new SourceFile(accumulated));current.add(found);}current=found;
        }
    }
    private void readSource(Path file) {
        sourcePath.setText(file.getFileName().toString());sourcePath.setToolTipText(file.toString());
        new SwingWorker<String,Void>() {
            protected String doInBackground()throws Exception{if(Files.size(file)>1024*1024)return "Source file exceeds preview limit (1 MiB).";return Files.readString(file);}
            protected void done(){try{if(sourcePath.getToolTipText().equals(file.toString())){source.setText(get());source.setCaretPosition(0);}}catch(Exception error){source.setText(error.toString());}}
        }.execute();
    }
    public void checkpoint(Map<String,Object> request) {
        var sourceState=CanonicalJson.object(request.getOrDefault("sourceState",Map.of()));var byName=new TreeMap<String,Map<String,Object>>();
        if(sourceState.get("agents") instanceof List<?> list)for(Object raw:list){var agent=CanonicalJson.object(raw);byName.put(agent.get("name").toString(),agent);}
        String selected=Objects.toString(agents.getSelectedItem(),"");currentAgents=Map.copyOf(byName);updating=true;
        try{agents.removeAllItems();byName.keySet().forEach(agents::addItem);if(byName.containsKey(selected))agents.setSelectedItem(selected);}finally{updating=false;}showAgent();
        artifacts.setRowCount(0);var snapshot=CanonicalJson.object(request.get("snapshot"));
        if(snapshot.get("artifacts") instanceof List<?> list)for(Object raw:list){var artifact=CanonicalJson.object(raw);artifacts.addRow(new Object[]{artifact.get("name"),artifact.get("javaClass"),artifact.get("properties"),artifact.get("observers")});}
    }
    private void showAgent() {
        var agent=currentAgents.get(Objects.toString(agents.getSelectedItem(),""));if(agent==null){agentDetails.setText("Waiting for the first checkpoint");return;}
        var text=new StringBuilder("Source: "+agent.get("source")+"\n");
        for(String key:List.of("beliefs","intentions","plans")){text.append("\n").append(key.toUpperCase(Locale.ROOT)).append("\n");if(agent.get(key) instanceof List<?> values)values.forEach(value->text.append(value).append("\n"));}
        agentDetails.setText(text.toString());agentDetails.setCaretPosition(0);
    }
    public void log(String line){console.append(line+"\n");if(console.getDocument().getLength()>256000)console.replaceRange("",0,console.getDocument().getLength()-200000);console.setCaretPosition(console.getDocument().getLength());}
    public void resetInspectors(){currentAgents=Map.of();agents.removeAllItems();agentDetails.setText("Waiting for the first checkpoint");artifacts.setRowCount(0);}
    private static JTextArea text(){var result=new JTextArea();result.setEditable(false);result.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));return result;}
    static DefaultTableModel table(String... columns){return new DefaultTableModel(columns,0){public boolean isCellEditable(int row,int column){return false;}};}
}
