package org.tzi.use.plugins.jacamo.ui;

import java.awt.BorderLayout;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.tree.*;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.GoalViewSnapshot;

/** Facade DTO rendering only; no parser, runtime command or domain mutation. */
final class GoalViewPanel extends JPanel {
    private final JTree tree=new JTree(new DefaultMutableTreeNode("Goals"));
    private final JTextArea details=new JTextArea();
    private final JLabel state=new JLabel("No Goal state");
    private final JButton source=new JButton("Open source");
    private final Consumer<GoalViewSnapshot.Source> navigate;
    private GoalViewSnapshot.Goal selected;
    private GoalViewSnapshot rendered;
    private record Row(GoalViewSnapshot.Goal goal) {
        @Override public String toString(){return goal.id()+" | "+goal.state()+" | "+goal.verification()+
                (goal.operator().isBlank()?"":" | "+goal.operator())+(goal.order()==null?"":" ["+goal.order()+"]");}
    }
    GoalViewPanel(Consumer<GoalViewSnapshot.Source> navigate) {
        super(new BorderLayout());this.navigate=navigate;tree.setName("goal-tree");details.setName("goal-detail");state.setName("goal-view-state");source.setName("goal-open-source");
        details.setEditable(false);details.setLineWrap(true);details.setWrapStyleWord(true);source.setEnabled(false);
        tree.addTreeSelectionListener(event->{var node=(DefaultMutableTreeNode)tree.getLastSelectedPathComponent();
            selected=node!=null && node.getUserObject() instanceof Row row?row.goal():null;showSelection();});
        source.addActionListener(event->{if(selected!=null && !selected.sources().isEmpty())navigate.accept(selected.sources().getFirst());});
        var split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(tree),new JScrollPane(details));split.setResizeWeight(.45);
        add(state,BorderLayout.NORTH);add(split,BorderLayout.CENTER);add(source,BorderLayout.SOUTH);
    }
    void refresh(GoalViewSnapshot view) {
        if(view.equals(rendered))return;rendered=view;
        String selection=selected==null?"":selected.object();
        var root=new DefaultMutableTreeNode("Schemes / Goals");
        for(var scheme:view.schemes()) {
            var node=new DefaultMutableTreeNode(scheme.name()+" ["+(scheme.runtime()?"runtime":"specification / deployment")+"] | "+scheme.object()+" | "+scheme.specId());
            root.add(node);var byName=new HashMap<String,GoalViewSnapshot.Goal>();scheme.goals().forEach(g->byName.put(g.object(),g));
            var shown=new HashSet<String>();
            for(var goal:scheme.goals())if(goal.parent().isBlank() || !byName.containsKey(goal.parent()))node.add(branch(goal,byName,shown));
            for(var goal:scheme.goals())if(!shown.contains(goal.object()))node.add(branch(goal,byName,shown));
        }
        tree.setModel(new DefaultTreeModel(root));for(int row=0;row<tree.getRowCount();row++)tree.expandRow(row);
        for(var iterator=root.depthFirstEnumeration();iterator.hasMoreElements();) {
            var node=(DefaultMutableTreeNode)iterator.nextElement();if(node.getUserObject() instanceof Row row && row.goal().object().equals(selection))tree.setSelectionPath(new TreePath(node.getPath()));
        }
        state.setText("Synchronization: "+view.synchronization()+" | Jason control: "+view.control()+" | "+view.diagnostic());
    }
    private DefaultMutableTreeNode branch(GoalViewSnapshot.Goal goal,Map<String,GoalViewSnapshot.Goal> goals,Set<String> shown) {
        var node=new DefaultMutableTreeNode(new Row(goal));if(!shown.add(goal.object())){node.add(new DefaultMutableTreeNode("Repeated/cyclic relation — inspect verification"));return node;}
        for(String child:goal.children())if(goals.containsKey(child))node.add(branch(goals.get(child),goals,shown));return node;
    }
    private void showSelection() {
        source.setEnabled(selected!=null && !selected.sources().isEmpty());if(selected==null){details.setText("");return;}
        details.setText("Goal: "+selected.id()+"\nUSE object: "+selected.object()+"\nSemantic id: "+selected.semanticId()+"\nSpecification: "+selected.specId()
                +"\nState: "+selected.state()+"\nState evidence: "+selected.evidence()+"\nDecomposition: "+selected.operator()+" / order="+selected.order()
                +"\nMissions: "+selected.missions().stream().map(m->m.id()+" ["+m.object()+"] agents="+m.committedAgents().stream().map(GoalViewSnapshot.Agent::name).toList()).toList()
                +"\nCommitted agents: "+selected.committedAgents().stream().map(a->a.name()+" ["+a.object()+"] "+a.roleContexts()).toList()
                +"\nAchieved agents (authoritative only): "+selected.achievedAgents().stream().map(a->a.name()+" ["+a.object()+"]").toList()
                +"\nVerification: "+selected.verification()+"\nViolations: "+selected.violations().stream().sorted(Comparator.comparing(org.tzi.use.plugins.jacamo.codegrounded.runtime.VerificationViolation::timestamp).reversed()).map(v->v.constraintId()+" | "+v.confirmation()+" | snapshot="+v.failingSnapshotId()+" | expected="+v.expected()+" | actual condition="+v.actual().getOrDefault("conditionTruth","unavailable")
                        +" | involved="+v.involvedObjects().values().stream().map(o->o.name()+" : "+o.className()).sorted().toList()).toList()
                +"\nFull immutable failing/comparison values: select this violation in Verification."
                +"\nSources: "+selected.sources().stream().map(s->s.file()+(s.line()>0?":"+s.line():" (line unavailable)")+" | "+s.rule()).toList());details.setCaretPosition(0);
    }
    void selectGoal(String object) {
        var root=(DefaultMutableTreeNode)tree.getModel().getRoot();
        for(var iterator=root.depthFirstEnumeration();iterator.hasMoreElements();) {
            var node=(DefaultMutableTreeNode)iterator.nextElement();
            if(node.getUserObject() instanceof Row row && row.goal().object().equals(object)) {
                var path=new TreePath(node.getPath());tree.setSelectionPath(path);tree.scrollPathToVisible(path);return;
            }
        }
    }
}
