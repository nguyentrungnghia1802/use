package org.tzi.use.plugins.jacamo.codegrounded;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.FutureTask;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.gui.views.ObjectCountView;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.sys.events.AtomicStateChangedEvent;
import org.tzi.use.util.soil.StateDifference;

/** The native writer publishes one atomic notification, not per-object SOIL events. */
class NativeObjectCountViewTest {
    @Test void objectCountRefreshesOnAtomicCreateAndDeleteWithoutAggregatingOtherClasses() throws Exception {
        var task=new FutureTask<Void>(()->{
            var system=CodeGroundedTestFixtures.guiPipeline().state().system();
            var view=new ObjectCountView(system);
            try {
                var api=UseSystemApi.create(system,false);
                var artifact=api.createObjectEx(system.model().getClass("Artifact"),"actual_artifact");
                var helper=api.createObjectEx(system.model().getClass("ObservablePropertySnapshot"),"property_snapshot");
                var created=new StateDifference();created.addNewObject(artifact);created.addNewObject(helper);
                system.getEventBus().post(new AtomicStateChangedEvent(created));
                assertEquals(1,count(view,"Artifact"),"native atomic commit must refresh the count view");
                assertEquals(1,count(view,"ObservablePropertySnapshot"),"snapshot counts must stay separate from Artifact");
                api.deleteObjectEx(artifact);
                var deleted=new StateDifference();deleted.addDeletedObject(artifact);
                system.getEventBus().post(new AtomicStateChangedEvent(deleted));
                assertEquals(0,count(view,"Artifact"));assertEquals(1,count(view,"ObservablePropertySnapshot"));
                return null;
            } finally { view.detachModel(); }
        });
        SwingUtilities.invokeAndWait(task);task.get();
    }
    private static int count(ObjectCountView view,String className) throws Exception {
        var classesField=ObjectCountView.class.getDeclaredField("fClasses");classesField.setAccessible(true);
        var valuesField=ObjectCountView.class.getDeclaredField("fValues");valuesField.setAccessible(true);
        var classes=(MClass[])classesField.get(view);var values=(int[])valuesField.get(view);
        for(int index=0;index<classes.length;index++)if(classes[index].name().equals(className))return values[index];
        throw new AssertionError("Missing class: "+className);
    }
}
