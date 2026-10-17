package org.tzi.use.plugins.jacamo.ui;

import java.awt.BorderLayout;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult;

/** Read-only view of the native result. No compiler, evaluator or state writer lives in Swing. */
public final class SemanticEvidencePanel extends JPanel {
    private static final int DISPLAY_LIMIT=500;
    private final JLabel summary=new JLabel();
    private final DefaultTableModel constraints=model("Constraint","Kind","Verdict","Class","Object","Backend value","Diagnostic","Source identity","Expression","Witness");
    private final DefaultTableModel requirements=model("Occurrence","Raw","Evaluated","Lifecycle","Quality","Episode","History gap","First satisfied","First violated","Diagnostic");
    private final DefaultTableModel coverage=model("Scope","Evidence");
    private String displayedHash="";private RuntimeVerificationResult displayedResult;
    public SemanticEvidencePanel() {
        super(new BorderLayout(4,4));summary.setName("semantic-summary");add(summary,BorderLayout.NORTH);var tabs=new JTabbedPane();tabs.addTab("Constraints",table(constraints,"semantic-constraints"));tabs.addTab("Requirements",table(requirements,"semantic-requirements"));tabs.addTab("Coverage and decisions",table(coverage,"semantic-coverage"));add(tabs,BorderLayout.CENTER);
    }
    public void refresh(RuntimeVerificationResult result) {
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("SEMANTIC_VIEW_REQUIRES_EDT");
        if(displayedResult==result)return;displayedResult=result;String hash=result==null?"":result.resultHash();if(hash.equals(displayedHash))return;displayedHash=hash;constraints.setRowCount(0);requirements.setRowCount(0);coverage.setRowCount(0);
        if(result==null || result.semantic()==null){summary.setText("No semantic observer result");return;}var evidence=result.semantic();
        summary.setText(evidence.profile()+" | "+result.freshness()+" | state "+result.stateVersion()+" | "+evidence.constraints().size()+" checks (up to "+DISPLAY_LIMIT+" displayed)");
        evidence.constraints().stream().limit(DISPLAY_LIMIT).forEach(r->constraints.addRow(new Object[]{r.id(),r.kind(),r.verdict().name(),r.contextClass(),r.contextObject(),r.backendValue(),r.diagnostic(),r.sourceIdentity(),r.expression(),r.witness().toString()}));
        evidence.requirements().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).limit(DISPLAY_LIMIT).forEach(e->{var m=e.getValue();requirements.addRow(new Object[]{e.getKey(),m.raw().name(),m.evaluated().name(),m.lifecycle().name(),m.quality().name(),m.episodeIdentity(),m.historyGap(),m.firstSatisfied(),m.firstViolated(),m.diagnostic()});});
        evidence.coverage().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).limit(DISPLAY_LIMIT).forEach(e->coverage.addRow(new Object[]{e.getKey(),e.getValue()}));evidence.decisions().stream().limit(DISPLAY_LIMIT).forEach(d->coverage.addRow(new Object[]{"Decision",d}));
    }
    private static DefaultTableModel model(String... columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int row,int column){return false;}};}
    private static JScrollPane table(DefaultTableModel model,String name){var table=new JTable(model);table.setName(name);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i<5?150:300);return new JScrollPane(table);}
}
