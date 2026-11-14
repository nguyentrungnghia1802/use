package org.tzi.use.plugins.jacamo.dsl.istarscn.view;
import org.tzi.use.plugins.jacamo.dsl.state.ModelStateDocument;
import org.tzi.use.plugins.jacamo.dsl.istarscn.view.IStarScenarioView;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import org.tzi.use.plugins.jacamo.dsl.istar.view.*;
import org.vnu.sme.goal.dsl.istar.mm.GoalModel;

/** State of one iStar execution context at a time; never merges separate auctions' verdicts. */
public final class IStarScenarioView extends JPanel implements AutoCloseable {
    private final JComboBox<String> contexts = new JComboBox<>();
    private final IStarView diagram = new IStarView();
    private final DefaultTableModel rows = new DefaultTableModel(new String[]{"Context","Actor","Goal / Task","Status"},0) { public boolean isCellEditable(int row,int column){return false;} };
    private List<ModelStateDocument.Marking> markings = List.of();
    private boolean updating;
    private Map<String,String> labels=Map.of();
    public IStarScenarioView(GoalModel model) {
        super(new BorderLayout()); setName("istar-scenario-view");
        diagram.setModelBrowserVisible(false); diagram.setModel(model);
        contexts.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focused){return super.getListCellRendererComponent(list,labels.getOrDefault(Objects.toString(value,""),Objects.toString(value,"")),index,selected,focused);}});
        var header = new JPanel(new FlowLayout(FlowLayout.LEADING)); header.add(new JLabel("Execution:")); header.add(contexts);
        var tabs = new JTabbedPane(); tabs.addTab("Diagram",diagram); tabs.addTab("Markings",new JScrollPane(new JTable(rows)));
        add(header,BorderLayout.NORTH); add(tabs); contexts.addActionListener(e -> { if(!updating)render(); });
    }
    public void update(List<ModelStateDocument.Marking> values) {
        markings=List.copyOf(values);String selected=Objects.toString(contexts.getSelectedItem(),"");
        var ids=new TreeSet<String>();values.forEach(v->ids.add(v.context()));updating=true;
        try { contexts.removeAllItems();ids.forEach(contexts::addItem);if(ids.contains(selected))contexts.setSelectedItem(selected); }
        finally { updating=false; }render();
    }
    public void update(List<ModelStateDocument.Marking> values,Map<String,String> contextLabels){labels=Map.copyOf(contextLabels);update(values);}
    private void render() {
        var badges=new HashMap<String,NodeBadge>();rows.setRowCount(0);String selected=Objects.toString(contexts.getSelectedItem(),"");
        for(var item:markings)if(item.context().equals(selected)) {
            Color color=switch(item.status()){case "FULFILLED"->new Color(25,125,65);case "VIOLATED"->new Color(185,45,45);default->new Color(130,105,35);};
            String glyph=switch(item.status()){case "FULFILLED"->"+";case "VIOLATED"->"!";default->"·";};
            badges.put(item.element(),new NodeBadge(color,glyph,item.status()));rows.addRow(new Object[]{item.context(),item.actor(),item.element(),item.status()});
        }
        diagram.setNodeBadges(badges);
    }
    @Override public void close(){diagram.detachModel();}
}
