package org.tzi.use.plugins.jacamo.ui.use4goal.conformance;

import java.awt.*;
import javax.swing.*;

/** Adapted inputs panel for use4Goal conformance.
 * Project input (.jcm) and system specification (.mrv) pointing to .csl, .istar, .xml, .ocl.
 * See META-INF/use4goal-workflow-provenance.json for source attribution. */
public final class ConformanceInputsPanel extends JPanel {
    public ConformanceInputsPanel(JTextField jcm, JTextField folder, JTextField csl, JTextField istar,
            JTextField mapping, JTextField ocl, Runnable chooseJcm, Runnable chooseFolder,
            JButton prepareButton, Runnable prepare) {
        super(new BorderLayout());
        setName("use4goal-conformance-inputs");
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        var inputs = new JPanel();
        inputs.setLayout(new BoxLayout(inputs, BoxLayout.Y_AXIS));

        // Row 0: Project input (.jcm)
        jcm.setColumns(42);
        inputs.add(row("Project input (.jcm):", jcm, browse("choose-jcm", chooseJcm)));

        // Row 1: System model (.mrv)
        folder.setEditable(false);
        folder.setColumns(42);
        folder.setToolTipText("A .mrv specification file pointing to .csl, .istar, .xml, and optional .ocl files");
        inputs.add(row("System model (.mrv):", folder, browse("choose-case-folder", chooseFolder)));

        // Non-visual holders for backend mapping/model logic
        var hidden = new JPanel();
        hidden.setVisible(false);
        for (var field : new JTextField[]{csl, istar, mapping, ocl}) {
            field.setEditable(false);
            hidden.add(field);
        }
        inputs.add(hidden);

        prepareButton.setText("Prepare + Import");
        prepareButton.setFont(prepareButton.getFont().deriveFont(Font.BOLD));
        prepareButton.setEnabled(true);
        prepareButton.setToolTipText("Validate the specification .mrv file and prepare the selected external JaCaMo project");
        prepareButton.addActionListener(e -> prepare.run());

        var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        actions.add(prepareButton);
        var reqLabel = new JLabel("Required in .mrv: .csl / .istar / .xml; optional: .ocl. Missing evidence remains unknown.");
        reqLabel.setFont(reqLabel.getFont().deriveFont(11f));
        reqLabel.setForeground(Color.GRAY);
        actions.add(reqLabel);

        add(inputs, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
    }

    private static JPanel row(String labelText, JTextField field, JButton browseButton) {
        var panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        var gbc = new GridBagConstraints();
        gbc.insets = new Insets(1, 4, 1, 4);

        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.weightx = 0.0;
        var label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(145, 22));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(label, gbc);

        gbc.gridx = 1; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(field, gbc);

        gbc.gridx = 2; gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;
        if (browseButton != null) {
            panel.add(browseButton, gbc);
        } else {
            var spacer = new JPanel();
            spacer.setPreferredSize(new Dimension(75, 22));
            panel.add(spacer, gbc);
        }
        return panel;
    }

    private static JButton browse(String name, Runnable action) {
        var btn = new JButton("Browse...");
        btn.setName(name);
        btn.setPreferredSize(new Dimension(75, 22));
        btn.setMargin(new Insets(1, 4, 1, 4));
        btn.addActionListener(e -> action.run());
        return btn;
    }
}
