package org.tzi.use.plugins.jacamo.ui.use4goal.aol.view;

import java.awt.Color;
import java.nio.file.Path;

import org.tzi.use.gui.views.diagrams.DiagramOptions;

public final class AolDiagramOptions extends DiagramOptions {
    public static final String AGENT_FILL = "AOL_AGENT_FILL";
    public static final String GROUP_FILL = "AOL_GROUP_FILL";
    public static final String PLAY_FILL = "AOL_PLAY_FILL";
    public static final String ENTITY_FILL = "AOL_ENTITY_FILL";
    public static final String EDGE_COLOR = "AOL_EDGE_COLOR";

    public AolDiagramOptions() {}

    public AolDiagramOptions(Path modelFile) {
        this.modelFileName = modelFile;
    }

    @Override
    protected void registerAdditionalColors() {
        registerTypeColor(NODE_COLOR, Color.WHITE, Color.WHITE);
        registerTypeColor(NODE_SELECTED_COLOR, new Color(255, 235, 170), new Color(0xd0, 0xd0, 0xd0));
        registerTypeColor(NODE_FRAME_COLOR, Color.BLACK, Color.BLACK);
        registerTypeColor(NODE_LABEL_COLOR, Color.BLACK, Color.BLACK);
        registerTypeColor(EDGE_COLOR, Color.BLACK, Color.BLACK);
        registerTypeColor(EDGE_LABEL_COLOR, Color.BLACK, Color.BLACK);
        registerTypeColor(EDGE_SELECTED_COLOR, Color.ORANGE, new Color(0x50, 0x50, 0x50));
        registerTypeColor(AGENT_FILL, new Color(240, 240, 240), new Color(200, 200, 200));
        registerTypeColor(GROUP_FILL, Color.WHITE, new Color(235, 235, 235));
        registerTypeColor(PLAY_FILL, Color.WHITE, new Color(235, 235, 235));
        registerTypeColor(ENTITY_FILL, Color.WHITE, new Color(235, 235, 235));
    }

    @Override
    public boolean isShowMutliplicities() {
        return true;
    }

    @Override
    public void setShowMutliplicities(boolean showMutliplicities) {}

    private boolean showAssociationNames = true;
    public boolean isShowAssociationNames() { return showAssociationNames; }
    public void setShowAssociationNames(boolean v) { this.showAssociationNames = v; }
}
