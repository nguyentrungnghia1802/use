package org.tzi.use.plugins.jacamo.ui;
import org.tzi.use.plugins.jacamo.dsl.state.ModelStateDocument;
import org.tzi.use.plugins.jacamo.dsl.istarscn.view.IStarScenarioView;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.tzi.use.plugins.jacamo.verify.api.EngineSemanticsProfile;
import org.tzi.use.plugins.jacamo.dsl.istar.parser.IStarFrontend;
import static org.junit.jupiter.api.Assertions.*;

class IStarScenarioViewTest {
    @Test void independentExecutionsRetainDifferentMarkingsWhenSwitchingTheInspector()throws Exception {
        var defaults=new Properties();try(var stream=getClass().getResourceAsStream("/etc/use.properties")){assertNotNull(stream);defaults.load(stream);}
        var added=new HashSet<String>();for(var name:defaults.stringPropertyNames())if(System.getProperty(name)==null){added.add(name);System.setProperty(name,defaults.getProperty(name));}
        try {
            var model=new IStarFrontend().compileScoped("istar Example { agent Team { goal Complete : Achieve task Finish > Complete pre {[ true ]} post {[ true ]} } }","test.istar",EngineSemanticsProfile.ROLE_SPECIALIZATION_V1).requireModel();
            SwingUtilities.invokeAndWait(()->{
                var view=new IStarScenarioView(model);
                try {
                    view.update(java.util.List.of(new ModelStateDocument.Marking("a1","Team","Complete","FULFILLED"),new ModelStateDocument.Marking("a2","Team","Complete","PENDING")),Map.of("a1","First auction","a2","Second auction"));
                    var table=find(view,JTable.class);var selector=find(view,JComboBox.class);
                    assertEquals(1,table.getRowCount());assertEquals("a1",table.getValueAt(0,0));assertEquals("FULFILLED",table.getValueAt(0,3));
                    selector.setSelectedItem("a2");assertEquals("a2",table.getValueAt(0,0));assertEquals("PENDING",table.getValueAt(0,3));
                    selector.setSelectedItem("a1");assertEquals("FULFILLED",table.getValueAt(0,3));
                }finally{view.close();}
            });
        }finally{added.forEach(System::clearProperty);}
    }
    private static <T> T find(Component component,Class<T> type){if(type.isInstance(component))return type.cast(component);if(component instanceof Container container)for(var child:container.getComponents()){T result=find(child,type);if(result!=null)return result;}return null;}
}
