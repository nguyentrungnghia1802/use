package org.tzi.use.plugins.jacamo.feature.bridge.view;

import java.awt.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.tzi.use.plugins.jacamo.bridge.StateRunConfiguration;

/** Only collects run inputs. Compilation and process startup happen off the EDT. */
public final class BridgeRunDialog {
    private BridgeRunDialog() { }
    public static Optional<StateRunConfiguration> show(Component owner) {
        var project=new JTextField(42);var csl=new JTextField(42);var istar=new JTextField(42);var mapping=new JTextField(42);
        project.setName("bridge-project");csl.setName("bridge-csl");istar.setName("bridge-istar");mapping.setName("bridge-mapping");
        var form=new JPanel(new GridBagLayout());int row=0;
        for(var item:java.util.List.of(new Input("JaCaMo project",project,"jcm"),new Input("CSL model",csl,"csl"),new Input("iStar model",istar,"istar"),new Input("Attribute mapping",mapping,"xml"))) {
            var constraints=new GridBagConstraints();constraints.gridy=row++;constraints.insets=new Insets(6,8,6,8);constraints.anchor=GridBagConstraints.WEST;
            constraints.gridx=0;form.add(new JLabel(item.label()),constraints);constraints.gridx=1;constraints.weightx=1;constraints.fill=GridBagConstraints.HORIZONTAL;form.add(item.field(),constraints);
            var browse=new JButton("Browse...");constraints.gridx=2;constraints.weightx=0;form.add(browse,constraints);
            browse.addActionListener(e->{var chooser=new JFileChooser();chooser.setFileFilter(new FileNameExtensionFilter(item.label()+" (*."+item.extension()+")",item.extension()));if(!project.getText().isBlank())chooser.setCurrentDirectory(Path.of(project.getText()).toAbsolutePath().getParent().toFile());if(chooser.showOpenDialog(owner)==JFileChooser.APPROVE_OPTION){item.field().setText(chooser.getSelectedFile().getAbsolutePath());if(item.field()==project)suggestModels(Path.of(project.getText()),csl,istar,mapping);}});
        }
        var panel=new JPanel(new BorderLayout(0,12));panel.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));panel.add(new JLabel("Choose the project and models to evaluate in USE."),BorderLayout.NORTH);panel.add(form);
        while(JOptionPane.showOptionDialog(owner,panel,"Run JaCaMo in USE",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Run","Cancel"},"Run")==JOptionPane.OK_OPTION) {
            try {
                for(var field:java.util.List.of(project,csl,istar,mapping))if(field.getText().isBlank())throw new IllegalArgumentException("Choose all four input files.");
                var value=new StateRunConfiguration(Path.of(project.getText()),Path.of(mapping.getText()),Path.of(csl.getText()),Path.of(istar.getText()));value.validate();return Optional.of(value);
            }catch(Exception failure){JOptionPane.showMessageDialog(owner,failure.getMessage(),"Check run inputs",JOptionPane.ERROR_MESSAGE);}
        }
        return Optional.empty();
    }
    private record Input(String label,JTextField field,String extension) { }
    private static void suggestModels(Path project,JTextField csl,JTextField istar,JTextField mapping) {
        String name=project.getFileName().toString().replaceFirst("\\.jcm$","");
        for(Path folder:java.util.List.of(project.getParent().resolve("state-only"),project.getParent())) {
            suggest(csl,folder.resolve(name+".csl"));suggest(istar,folder.resolve(name+".istar"));suggest(mapping,folder.resolve("attribute-mapping.xml"));
        }
    }
    private static void suggest(JTextField field,Path file){if(field.getText().isBlank()&&Files.isRegularFile(file))field.setText(file.toString());}
}
