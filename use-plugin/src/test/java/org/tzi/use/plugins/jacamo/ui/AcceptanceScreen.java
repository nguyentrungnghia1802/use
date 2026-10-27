package org.tzi.use.plugins.jacamo.ui;

import java.awt.*;
import java.util.Comparator;
import java.util.Arrays;

/** Optional test-run placement; preserves sizes/assertions and never changes desktop settings. */
public final class AcceptanceScreen {
    private AcceptanceScreen() { }
    public static void place(Window window) {
        String selected=System.getenv("USE_ACCEPTANCE_MONITOR");
        if(selected==null||selected.isBlank())return;
        if(!selected.equals("left"))throw new IllegalArgumentException("Unsupported acceptance monitor: "+selected);
        if(!javax.swing.SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Acceptance placement requires EDT");
        Rectangle bounds=Arrays.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
                .map(device->device.getDefaultConfiguration().getBounds()).min(Comparator.comparingInt(r->r.x)).orElseThrow();
        if(window.getWidth()>bounds.width)throw new IllegalStateException("Acceptance window wider than the selected monitor");
        window.setLocation(bounds.x+(bounds.width-window.getWidth())/2,bounds.y+Math.max(0,(bounds.height-window.getHeight())/2));
    }
}
