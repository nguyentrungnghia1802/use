package org.tzi.use.plugins.jacamo.ui;

import java.awt.BorderLayout;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import org.jacamo.bridge.contract.CanonicalJson;
import org.tzi.use.plugins.jacamo.codegrounded.runtime.RuntimeVerificationResult;

/** Read-only view of the native result. No compiler, evaluator or state writer lives in Swing. */
public final class SemanticEvidencePanel extends JPanel {
    private static final int DISPLAY_LIMIT=500;
    private final JLabel summary=new JLabel();
    private final DefaultTableModel constraints=model("Constraint","Kind","Verdict","Class","Object","Backend value","Diagnostic","Source identity","Expression","Witness");
    private final DefaultTableModel requirements=model("Occurrence","Raw","Evaluated","Lifecycle","Quality","Episode","History gap","First satisfied","First violated","Diagnostic");
    private final DefaultTableModel coverage=model("Scope","Evidence");
    private final DefaultTableModel original=model("Context","Occurrence","Element","Full binding stack","Leaf raw","Propagated raw","Observation quality","Historical fields","Identity / episode","Boundary","Scheme","Artifact","Player","Role","Execution element","Original API calls","Source rule / cell");
    private final DefaultTableModel caseEvidence=model("Kind","Verdict","Scope / scheme","Artifact","Player / name","Role","Goal invocation","Step invocation","Operation invocation","Obligation / activation","Source rule / cell","Observation / diagnostic","Native cuts","Clock / administrative","Full witness");
    private String displayedHash="";private RuntimeVerificationResult displayedResult;
    public SemanticEvidencePanel() {
        super(new BorderLayout(4,4));summary.setName("semantic-summary");add(summary,BorderLayout.NORTH);var tabs=new JTabbedPane();tabs.addTab("Constraints / invocation conformance",table(constraints,"semantic-constraints"));tabs.addTab("Original iStar history",table(original,"semantic-original-history"));tabs.addTab("Case evidence",table(caseEvidence,"semantic-case-evidence"));tabs.addTab("Legacy requirements",table(requirements,"semantic-requirements"));tabs.addTab("Coverage and decisions",table(coverage,"semantic-coverage"));add(tabs,BorderLayout.CENTER);
    }
    public void refresh(RuntimeVerificationResult result) {
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("SEMANTIC_VIEW_REQUIRES_EDT");
        if(displayedResult==result)return;displayedResult=result;String hash=result==null?"":result.resultHash();if(hash.equals(displayedHash))return;displayedHash=hash;constraints.setRowCount(0);requirements.setRowCount(0);coverage.setRowCount(0);original.setRowCount(0);caseEvidence.setRowCount(0);
        if(result==null || result.semantic()==null){summary.setText("No semantic observer result");return;}var evidence=result.semantic();
        summary.setText(evidence.profile()+" | "+result.freshness()+" | state "+result.stateVersion()+" | "+evidence.constraints().size()+" checks (up to "+DISPLAY_LIMIT+" displayed)");
        if(evidence.observation().get("originalEngine") instanceof java.util.Map<?,?> raw) {
            var core=CanonicalJson.object(raw);int total=0;
            for(var value:(java.util.List<?>)core.getOrDefault("contexts",java.util.List.of())) {
                var context=CanonicalJson.object(value);
                for(var entry:CanonicalJson.object(context.getOrDefault("occurrences",java.util.Map.of())).entrySet()) {
                    total++;if(original.getRowCount()>=DISPLAY_LIMIT)continue;var occurrence=CanonicalJson.object(entry.getValue());
                    original.addRow(new Object[]{context.get("context"),entry.getKey(),occurrence.get("sourceElement"),occurrence.get("stack"),occurrence.getOrDefault("leafStatus","UNAVAILABLE"),occurrence.getOrDefault("aggregateStatus","UNAVAILABLE"),core.getOrDefault("qualityOverlay",occurrence.getOrDefault("quality","UNAVAILABLE")),occurrence.getOrDefault("goal",occurrence.getOrDefault("task","UNAVAILABLE")),occurrence.getOrDefault("key","UNAVAILABLE"),context.get("boundary"),context.getOrDefault("scope",context.getOrDefault("scheme",context.getOrDefault("context","UNAVAILABLE"))),context.getOrDefault("artifact","UNAVAILABLE"),occurrence.getOrDefault("player","UNAVAILABLE"),occurrence.getOrDefault("role","UNAVAILABLE"),occurrence.getOrDefault("executionElement",occurrence.get("sourceElement")),context.getOrDefault("originalApiCalls",java.util.List.of()),occurrence.getOrDefault("sourceRule","UNAVAILABLE")+" / "+occurrence.getOrDefault("sourceCell",occurrence.get("sourceElement"))});
                }
            }
            summary.setText(summary.getText()+" | "+core.getOrDefault("evaluated","UNAVAILABLE")+" | history "+core.getOrDefault("historyQuality","UNAVAILABLE")+" | "+total+" original occurrences"+(total==0?" (no occurrence verdict available)":""));
            coverage.addRow(new Object[]{"Engine provenance",core.getOrDefault("classHashes",java.util.Map.of())});
            coverage.addRow(new Object[]{"Current consumer class origins (deployment, not recorded source)",org.tzi.use.plugins.jacamo.semantics.evidence.SemanticProgramManifest.currentClassOrigins()});
            coverage.addRow(new Object[]{"Program / state",core.getOrDefault("programHash","")+" / "+result.stateHash()});
        }
        evidence.constraints().stream().limit(DISPLAY_LIMIT).forEach(r->constraints.addRow(new Object[]{r.id(),r.kind(),r.verdict().name(),r.contextClass(),r.contextObject(),r.backendValue(),r.diagnostic(),r.sourceIdentity(),r.expression(),r.witness().toString()}));
        evidence.constraints().stream().filter(r->r.witness().containsKey("mappingPlanHash")||r.witness().containsKey("sourceRule")||r.witness().containsKey("targetTaskId")).limit(DISPLAY_LIMIT).forEach(r->{var w=r.witness();caseEvidence.addRow(new Object[]{r.kind(),r.verdict().name(),w.getOrDefault("scope",w.getOrDefault("scheme","UNAVAILABLE")),w.getOrDefault("artifact",r.sourceIdentity()),w.getOrDefault("player",w.getOrDefault("performerSemanticId",w.getOrDefault("playerName","UNAVAILABLE"))),w.getOrDefault("role","UNAVAILABLE"),w.getOrDefault("goalInvocation","UNAVAILABLE"),w.getOrDefault("stepInvocation","UNAVAILABLE"),operationIdentity(r),w.getOrDefault("obligation","")+" / "+w.getOrDefault("activation",""),w.getOrDefault("sourceRule",w.containsKey("mappingPlanHash")?"plan:"+w.get("mappingPlanHash"):"UNAVAILABLE")+" / "+w.getOrDefault("sourceCell",w.getOrDefault("targetTaskId","UNAVAILABLE")),w.getOrDefault("rawStepFrameQuality",w.getOrDefault("rawObservationQuality",w.getOrDefault("observationQuality","UNAVAILABLE")))+" / "+r.diagnostic(),w.getOrDefault("preStateHash",w.getOrDefault("matchingPreStateHash","UNAVAILABLE"))+" / "+w.getOrDefault("postStateHash","UNAVAILABLE"),w.getOrDefault("clockProof",w.getOrDefault("rawTimes","UNAVAILABLE")),w});});
        evidence.requirements().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).limit(DISPLAY_LIMIT).forEach(e->{var m=e.getValue();requirements.addRow(new Object[]{e.getKey(),m.raw().name(),m.evaluated().name(),m.lifecycle().name(),m.quality().name(),m.episodeIdentity(),m.historyGap(),m.firstSatisfied(),m.firstViolated(),m.diagnostic()});});
        evidence.coverage().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).limit(DISPLAY_LIMIT).forEach(e->coverage.addRow(new Object[]{e.getKey(),e.getValue()}));evidence.decisions().stream().limit(DISPLAY_LIMIT).forEach(d->coverage.addRow(new Object[]{"Decision",d}));
    }
    private static String operationIdentity(org.tzi.use.plugins.jacamo.semantics.evidence.SemanticConstraintResult row){var witness=row.witness();return witness.containsKey("stepInvocation")?witness.getOrDefault("sourceActionCorrelation","UNAVAILABLE")+" / "+witness.getOrDefault("sourceActionNamespace","UNAVAILABLE"):row.invocation();}
    private static DefaultTableModel model(String... columns){return new DefaultTableModel(columns,0){@Override public boolean isCellEditable(int row,int column){return false;}};}
    private static JScrollPane table(DefaultTableModel model,String name){var table=new JTable(model);table.setName(name);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i<5?150:300);return new JScrollPane(table);}
}
