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
    private ExecutionGroups groups=ExecutionGroups.of(List.of());
    private Cut latest;
    private boolean loading,active;

    public IStarInstancesPanel(GoalModel model) {
        this(model,Map.of());
    }
    public IStarInstancesPanel(GoalModel model,Map<String,Object> mapping) {
        super(new BorderLayout(6,6));this.model=Objects.requireNonNull(model);summary=new LiveGoalSummary(model,mapping);
        setName("istar-instances-view");executions.setName("istar-instance-selector");status.setName("istar-instance-status");
        executions.setRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
            var label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
            String id=Objects.toString(value,"");label.setText(ExecutionScopeViews.ALL.equals(id)?"All":groups.label(id,ordinals.getOrDefault(id,1)));return label;
        }});
        var toolbar=new JPanel(new BorderLayout(6,3));toolbar.add(executions,BorderLayout.NORTH);toolbar.add(status,BorderLayout.SOUTH);
        add(toolbar,BorderLayout.NORTH);add(new JScrollPane(content),BorderLayout.CENTER);
        var legend=new JLabel("C = Complete · I = Incomplete · ? = Unknown · Overview follows declared population policies. Individual results are available in Verification.");
        add(legend,BorderLayout.SOUTH);
        executions.addActionListener(e->{if(!loading&&active)render();});
    }

    /** Called only with the accepted, unprojected ledger, never a history selection. */
    public void update(List<Cut> accepted) {
        if(accepted.isEmpty()){clear();return;}
        var cut=accepted.getLast();if(latest==cut)return;
        var core=ExecutionScopeViews.object(cut.visualEvidence().get("originalEngine"));
        if(!"PINNED_NATIVE_CUT".equals(core.get("evaluated"))) {status.setText("Waiting for accepted runtime evidence; retaining the last accepted instance view");return;}
        latest=cut;putClientProperty("accepted-cut",cut.id());groups=ExecutionGroups.of(accepted);contexts=ExecutionScopeViews.contexts(cut).stream().filter(ExecutionScopeViews::boundContext).toList();
        var alive=new HashSet<String>();contexts.forEach(c->alive.add(groups.canonical(Objects.toString(c.get("context")))));
        if(Collections.disjoint(alive,ordinals.keySet()))ordinals.clear();
        groups.ids().stream().filter(alive::contains).forEach(id->ordinals.computeIfAbsent(id,key->ordinals.size()+1));
        for(String id:new ArrayList<>(views.keySet()))if(!alive.contains(id))views.remove(id).detachModel();
        String previous=groups.canonical(Objects.toString(executions.getSelectedItem(),ExecutionScopeViews.ALL));loading=true;
        try{executions.removeAllItems();executions.addItem(ExecutionScopeViews.ALL);ordinals.keySet().stream().filter(alive::contains).forEach(executions::addItem);executions.setSelectedItem(alive.contains(previous)?previous:ExecutionScopeViews.ALL);}
        finally{loading=false;}
        status.setText(alive.isEmpty()?"Waiting for admitted executions":"Current admitted instances: "+alive.size());
        if(active)render();
    }

    public void setActive(boolean value){if(active==value)return;active=value;if(active)render();}

    private void render() {
        content.removeAll();String selected=Objects.toString(executions.getSelectedItem(),ExecutionScopeViews.ALL);
        for(String id:ordinals.keySet()){
            var phases=groups.contexts(latest,id).stream().filter(ExecutionScopeViews::boundContext).toList();if(phases.isEmpty())continue;
            if(!ExecutionScopeViews.ALL.equals(selected)&&!id.equals(selected))continue;
            var view=views.computeIfAbsent(id,key->{var result=new IStarView();result.setName("istar-instance-"+key);result.setModelBrowserVisible(false);result.setModel(model);return result;});
            var scopeMembers=new HashSet<>(groups.members(id));
            var evaluations=latest.evaluations().stream().filter(r->r.kind().equals("GOAL_TASK")&&scopeMembers.contains(r.context())).toList();
            var visible=new HashSet<String>();
            for(var phase:phases)for(var raw:ExecutionScopeViews.object(phase.get("occurrences")).values()) {
                var occurrence=ExecutionScopeViews.object(raw);visible.add(Objects.toString(occurrence.get("sourceElement"),""));
            }
            evaluations.forEach(r->visible.add(r.element()));
            for(var actor:model.getActors())for(var needed:actor.neededBys())if(visible.contains(needed.task()))visible.add(needed.resource());
            view.setVisibleElements(visible);
            var badges=new TreeMap<String,NodeBadge>();
            for(var row:summary.rows(evaluations)){
                if(row.displayInstances().isEmpty())continue;
                String result=PresentationResults.goal(row.status(),false);
                Color color=result.equals("Complete")?new Color(30,145,65):result.equals("Incomplete")?new Color(175,110,25):Color.GRAY;
                String glyph=result.equals("Complete")?"C":result.equals("Incomplete")?"I":"?";
                badges.put(row.id(),new NodeBadge(color,glyph,result));
            }
            view.setNodeBadges(badges);
            var players=new TreeMap<String,Set<String>>();var labels=new TreeMap<String,String>();
            for(var context:phases)for(Object raw:ExecutionScopeViews.object(context.get("occurrences")).values()){
                var occurrence=ExecutionScopeViews.object(raw);String actor=Objects.toString(occurrence.get("sourceActor"),""),player=Objects.toString(occurrence.get("player"),"");
                if(actor.isBlank()||player.isBlank())continue;
                players.computeIfAbsent(actor,k->new TreeSet<>()).add(player);
                if(player.equals(context.get("anchor")))labels.put(actor,actor+" · "+ExecutionScopeViews.actorLabel(context,ordinals.get(id)));
            }
            players.forEach((actor,members)->labels.putIfAbsent(actor,actor+" · "+(members.size()==1?player(members.iterator().next()):members.size()+" players")));
            view.setActorLabelOverrides(labels);
            var card=new JPanel(new BorderLayout());card.setBorder(BorderFactory.createTitledBorder(groups.label(id,ordinals.get(id))));
            card.add(new JLabel("Phases: "+phases.stream().map(c->Objects.toString(c.getOrDefault("scopeDefinition","Execution"))).distinct().collect(java.util.stream.Collectors.joining(" → "))),BorderLayout.NORTH);
            card.add(view,BorderLayout.CENTER);card.setPreferredSize(new Dimension(1000,600));content.add(card);
        }
        content.revalidate();content.repaint();
    }

    private int ordinal(Map<String,Object> context){return ordinals.getOrDefault(Objects.toString(context.get("context")),1);}
    private static String player(String identity){try{return org.jacamo.bridge.contract.BridgeEntityId.parse(identity).localId();}catch(RuntimeException unavailable){return identity;}}
    private void clear(){latest=null;putClientProperty("accepted-cut",null);contexts=List.of();loading=true;try{executions.removeAllItems();}finally{loading=false;}views.values().forEach(IStarView::detachModel);views.clear();ordinals.clear();content.removeAll();content.revalidate();content.repaint();status.setText("Waiting for admitted executions");}
    public void dispose(){clear();}
}
