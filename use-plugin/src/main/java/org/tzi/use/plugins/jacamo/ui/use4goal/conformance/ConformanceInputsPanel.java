package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.awt.*;
import javax.swing.*;

/** Adapted buildInputs workflow from use4Goal CslStateEvaluatorForm (GPL-3.0).
 * Project input is separate from the manually authored system specification folder.
 * Components/actions belong to the existing facade-backed controller.
 * See META-INF/use4goal-workflow-provenance.json for source attribution. */
public final class ConformanceInputsPanel extends JPanel {
    public ConformanceInputsPanel(JTextField jcm, JTextField folder, JTextField csl, JTextField istar,
            JTextField mapping, JTextField ocl, Runnable chooseJcm, Runnable chooseFolder,
            JButton prepareButton, Runnable prepare) {
        super(new BorderLayout());setName("use4goal-conformance-inputs");
        var inputs=new JPanel();inputs.setLayout(new BoxLayout(inputs,BoxLayout.Y_AXIS));
        inputs.add(row("Project input (.jcm):",jcm,browse("choose-jcm",chooseJcm)));
        folder.setEditable(false);
        folder.setToolTipText("One .csl, one .istar, one .xml; zero or one .ocl, directly inside this folder");
        inputs.add(row("System model folder:",folder,browse("choose-case-folder",chooseFolder)));
        for(var field:new JTextField[]{csl,istar,mapping,ocl})field.setEditable(false);
        inputs.add(row("CSL model:",csl,null));
        inputs.add(row("iStar model:",istar,null));
        inputs.add(row("Semantic mapping XML:",mapping,null));
        inputs.add(row("Extra OCL (optional):",ocl,null));
        ocl.setToolTipText("Empty means no additional external constraints; embedded model constraints still apply");
        prepareButton.setText("Prepare + Import");
        prepareButton.setFont(prepareButton.getFont().deriveFont(Font.BOLD));
        prepareButton.setEnabled(true);
        prepareButton.setToolTipText("Validate the specification folder and prepare the selected external JaCaMo project");
        prepareButton.addActionListener(e->prepare.run());
        var actions=new JPanel(new FlowLayout(FlowLayout.LEFT,6,2));actions.add(prepareButton);
        actions.add(new JLabel("Required: .csl / .istar / .xml; optional: .ocl. Missing evidence remains unknown."));
        inputs.add(actions);add(inputs,BorderLayout.CENTER);
    }
    private static JButton browse(String name,Runnable action) {
        var button=new JButton("Browse...");button.setName(name);button.addActionListener(e->action.run());return button;
    }
    private static JPanel row(String title,JTextField field,JButton button) {
        var row=new JPanel(new FlowLayout(FlowLayout.LEFT,6,2));var label=new JLabel(title);
        label.setPreferredSize(new Dimension(155,label.getPreferredSize().height));row.add(label);row.add(field);
        if(button!=null)row.add(button);return row;
    }
}
