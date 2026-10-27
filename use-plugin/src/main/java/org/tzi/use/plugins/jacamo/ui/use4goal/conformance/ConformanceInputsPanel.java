package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.awt.*;
import javax.swing.*;

/** Selected buildInputs workflow from use4Goal CslStateEvaluatorForm (GPL-3.0).
 * BPMN/boundary actions are replaced by external-source/mapping selection.
 * Components and actions belong to the existing facade-backed controller.
 * See META-INF/use4goal-workflow-provenance.json for the exact source/patches. */
public final class ConformanceInputsPanel extends JPanel {
    private final JTextField cslField, jcmField, istarField, mappingField, oclField;
    private final Runnable chooseCsl, chooseJcm, chooseIStar, chooseMapping, chooseOcl, prepare;
    private final JButton validateWholeButton;
    public ConformanceInputsPanel(JTextField jcm, JTextField csl, JTextField istar, JTextField mapping, JTextField ocl,
            Runnable chooseJcm, Runnable chooseCsl, Runnable chooseIStar, Runnable chooseMapping, Runnable chooseOcl,
            JButton prepareButton, Runnable prepare) {
        super(new BorderLayout());setName("use4goal-conformance-inputs");
        this.jcmField=jcm;this.cslField=csl;this.istarField=istar;this.mappingField=mapping;this.oclField=ocl;
        this.chooseJcm=chooseJcm;this.chooseCsl=chooseCsl;this.chooseIStar=chooseIStar;
        this.chooseMapping=chooseMapping;this.chooseOcl=chooseOcl;this.prepare=prepare;
        this.validateWholeButton=prepareButton;
        add(buildInputs(),BorderLayout.CENTER);
    }

    private JPanel buildInputs() {
        JPanel inputs = new JPanel();
        inputs.setLayout(new BoxLayout(inputs, BoxLayout.Y_AXIS));
        JPanel cslRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel label = new JLabel("CSL state model:");
        label.setPreferredSize(new Dimension(125, label.getPreferredSize().height));
        JButton browse = new JButton("Browse...");
        browse.addActionListener(e -> chooseCsl.run());
        cslRow.add(label);
        cslRow.add(cslField);
        cslRow.add(browse);

        JPanel jcmRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel jcmLabel = new JLabel("JaCaMo project:");
        jcmLabel.setPreferredSize(new Dimension(125, jcmLabel.getPreferredSize().height));
        JButton browseJcm = new JButton("Browse...");
        browseJcm.addActionListener(e -> chooseJcm.run());
        jcmRow.add(jcmLabel);
        jcmRow.add(jcmField);
        jcmRow.add(browseJcm);

        JPanel istarRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel istarLabel = new JLabel("iStar requirements:");
        istarLabel.setPreferredSize(new Dimension(125, istarLabel.getPreferredSize().height));
        JButton browseIStar = new JButton("Browse...");
        browseIStar.addActionListener(e -> chooseIStar.run());
        istarRow.add(istarLabel);
        istarRow.add(istarField);
        istarRow.add(browseIStar);

        JPanel mappingRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel mappingLabel = new JLabel("Mapping XML:");
        mappingLabel.setPreferredSize(new Dimension(125, mappingLabel.getPreferredSize().height));
        JButton browseMapping = new JButton("Browse...");
        browseMapping.addActionListener(e -> chooseMapping.run());
        mappingRow.add(mappingLabel);
        mappingRow.add(mappingField);
        mappingRow.add(browseMapping);

        JPanel stateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        validateWholeButton.setText("Prepare + Import");
        validateWholeButton.setFont(validateWholeButton.getFont().deriveFont(Font.BOLD));
        validateWholeButton.setEnabled(true);
        validateWholeButton.setToolTipText(
                "Validate five authored inputs and prepare the owned external JaCaMo source");
        validateWholeButton.addActionListener(e -> prepare.run());
        stateRow.add(validateWholeButton);
        stateRow.add(new JLabel(
                "Observed execution prefix; unknown evidence remains unknown."));

        JPanel oclRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel oclLabel = new JLabel("OCL requirements:");
        oclLabel.setPreferredSize(new Dimension(125, oclLabel.getPreferredSize().height));
        JButton browseOcl = new JButton("Browse...");
        browseOcl.setName("choose-authored-ocl");
        browseOcl.addActionListener(e -> chooseOcl.run());
        oclRow.add(oclLabel); oclRow.add(oclField); oclRow.add(browseOcl);
        browse.setName("choose-authored-csl");
        browseIStar.setName("choose-authored-istar");
        browseMapping.setName("choose-mapping");
        browseJcm.setName("choose-jcm");
        inputs.add(cslRow);
        inputs.add(jcmRow);
        inputs.add(istarRow);
        inputs.add(mappingRow);
        inputs.add(oclRow);
        inputs.add(stateRow);
        return inputs;
    }
}
