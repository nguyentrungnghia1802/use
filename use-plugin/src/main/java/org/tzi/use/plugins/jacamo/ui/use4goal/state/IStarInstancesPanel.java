package org.tzi.use.plugins.jacamo.ui.use4goal.state;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.*;
import org.tzi.use.plugins.jacamo.ui.use4goal.conformance.ObservedConformancePanel.Cut;
import org.tzi.use.plugins.jacamo.ui.use4goal.istar.*;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;

/** Current admitted execution instances on the authored iStar graph. No ISCN
 * expansion, historical cursor, model generation or native marking mutation. */
public final class IStarInstancesPanel extends JPanel {
    private final GoalModel model;
    private final LiveGoalSummary summary;
    private final JComboBox<String> executions=new JComboBox<>();
    private final JLabel status=new JLabel("Waiting for admitted executions");
    private final JPanel content=new JPanel(new GridLayout(0,1,8,8));
    private final Map<String,Integer> ordinals=new LinkedHashMap<>();
    private final Map<String,IStarView> views=new LinkedHashMap<>();
    private List<Map<String,Object>> contexts=List.of();
    private Cut latest;
    private boolean loading,active;

    public IStarInstancesPanel(GoalModel model) {
        super(new BorderLayout(6,6));this.model=Objects.requireNonNull(model);summary=new LiveGoalSummary(model);
        setName("istar-instances-view");executions.setName("istar-instance-selector");status.setName("istar-instance-status");
        executions.setRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
            var label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
            String id=Objects.toString(value,"");label.setText(ExecutionScopeViews.ALL.equals(id)?"All":contexts.stream().filter(c->id.equals(c.get("context"))).findFirst().map(c->ExecutionScopeViews.label(c,ordinal(c))).orElse(id));return label;
        }});
        var toolbar=new JPanel(new BorderLayout(6,3));toolbar.add(executions,BorderLayout.NORTH);toolbar.add(status,BorderLayout.SOUTH);
        add(toolbar,BorderLayout.NORTH);add(new JScrollPane(content),BorderLayout.CENTER);
        var legend=new JLabel("Current instances · C = Complete, I = Incomplete, ? = Unknown. Verification history is available in Verification.");
        add(legend,BorderLayout.SOUTH);
        executions.addActionListener(e->{if(!loading&&active)render();});
    }

    /** Called only with the accepted, unprojected ledger, never a history selection. */
    public void update(List<Cut> accepted) {
        if(accepted.isEmpty()){clear();return;}
        var cut=accepted.getLast();if(latest==cut)return;
        var core=ExecutionScopeViews.object(cut.visualEvidence().get("originalEngine"));
        if(!"PINNED_NATIVE_CUT".equals(core.get("evaluated"))) {status.setText("Waiting for accepted runtime evidence; retaining the last accepted instance view");return;}
        latest=cut;putClientProperty("accepted-cut",cut.id());contexts=ExecutionScopeViews.contexts(cut).stream().filter(ExecutionScopeViews::boundContext).toList();
        var alive=new HashSet<String>();contexts.forEach(c->alive.add(Objects.toString(c.get("context"))));
        if(Collections.disjoint(alive,ordinals.keySet()))ordinals.clear();
        contexts.forEach(c->ordinals.computeIfAbsent(Objects.toString(c.get("context")),key->ordinals.size()+1));
        contexts=contexts.stream().sorted(Comparator.comparingInt(this::ordinal)).toList();
        for(String id:new ArrayList<>(views.keySet()))if(!alive.contains(id))views.remove(id).detachModel();
        String previous=Objects.toString(executions.getSelectedItem(),ExecutionScopeViews.ALL);loading=true;
        try{executions.removeAllItems();executions.addItem(ExecutionScopeViews.ALL);contexts.forEach(c->executions.addItem(Objects.toString(c.get("context"))));executions.setSelectedItem(alive.contains(previous)?previous:ExecutionScopeViews.ALL);}
        finally{loading=false;}
        status.setText(contexts.isEmpty()?"Waiting for admitted executions":"Current admitted instances: "+contexts.size());
        if(active)render();
    }

    public void setActive(boolean value){if(active==value)return;active=value;if(active)render();}

    private void render() {
        content.removeAll();String selected=Objects.toString(executions.getSelectedItem(),ExecutionScopeViews.ALL);
        for(int i=0;i<contexts.size();i++){
            var context=contexts.get(i);String id=Objects.toString(context.get("context"));
            if(!ExecutionScopeViews.ALL.equals(selected)&&!id.equals(selected))continue;
            var view=views.computeIfAbsent(id,key->{var result=new IStarView();result.setName("istar-instance-"+key);result.setModelBrowserVisible(false);result.setModel(model);return result;});
            var evaluations=latest.evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")&&r.context().equals(id)).toList();
            var badges=new TreeMap<String,NodeBadge>();
            for(var row:summary.rows(evaluations)){
                String result=PresentationResults.goal(row.status(),false);
                Color color=result.equals("Complete")?new Color(30,145,65):result.equals("Incomplete")?new Color(175,110,25):Color.GRAY;
                badges.put(row.id(),new NodeBadge(color,result.equals("Complete")?"C":result.equals("Incomplete")?"I":"?",result+" · "+row.counts()));
            }
            view.setNodeBadges(badges);
            var players=new TreeMap<String,Set<String>>();var labels=new TreeMap<String,String>();
            for(Object raw:ExecutionScopeViews.object(context.get("occurrences")).values()){
                var occurrence=ExecutionScopeViews.object(raw);String actor=Objects.toString(occurrence.get("sourceActor"),""),player=Objects.toString(occurrence.get("player"),"");
                if(actor.isBlank()||player.isBlank())continue;
                players.computeIfAbsent(actor,k->new TreeSet<>()).add(player);
                if(player.equals(context.get("anchor")))labels.put(actor,actor+" · "+ExecutionScopeViews.actorLabel(context,ordinal(context)));
            }
            players.forEach((actor,members)->labels.putIfAbsent(actor,actor+" · "+(members.size()==1?player(members.iterator().next()):members.size()+" players")));
            view.setActorLabelOverrides(labels);
            var card=new JPanel(new BorderLayout());card.setBorder(BorderFactory.createTitledBorder(ExecutionScopeViews.label(context,ordinal(context))));card.add(view,BorderLayout.CENTER);card.setPreferredSize(new Dimension(1000,600));content.add(card);
        }
        content.revalidate();content.repaint();
    }

    private int ordinal(Map<String,Object> context){return ordinals.getOrDefault(Objects.toString(context.get("context")),1);}
    private static String player(String identity){try{return org.jacamo.bridge.contract.BridgeEntityId.parse(identity).localId();}catch(RuntimeException unavailable){return identity;}}
    private void clear(){latest=null;putClientProperty("accepted-cut",null);contexts=List.of();loading=true;try{executions.removeAllItems();}finally{loading=false;}views.values().forEach(IStarView::detachModel);views.clear();ordinals.clear();content.removeAll();content.revalidate();content.repaint();status.setText("Waiting for admitted executions");}
    public void dispose(){clear();}
}
