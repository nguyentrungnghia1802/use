package org.tzi.use.plugins.jacamo.ui.use4goal.istar;

import java.awt.Color;
import java.nio.file.Path;

import org.tzi.use.gui.views.diagrams.DiagramOptions;

public final class IStarDiagramOptions extends DiagramOptions {
    public IStarDiagramOptions() {}

    public IStarDiagramOptions(Path modelFile) {
        this.modelFileName = modelFile;
    }

    @Override
    protected void registerAdditionalColors() {
        registerTypeColor(NODE_COLOR, Color.WHITE, Color.WHITE);
        registerTypeColor(NODE_SELECTED_COLOR, Color.ORANGE, new Color(0xd0, 0xd0, 0xd0));
        registerTypeColor(NODE_FRAME_COLOR, Color.BLACK, Color.BLACK);
        registerTypeColor(NODE_LABEL_COLOR, Color.BLACK, Color.BLACK);
        registerTypeColor(EDGE_COLOR, Color.BLACK, Color.BLACK);
        registerTypeColor(EDGE_LABEL_COLOR, Color.BLACK, Color.BLACK);
        registerTypeColor(EDGE_SELECTED_COLOR, Color.ORANGE, new Color(0x50, 0x50, 0x50));
    }

    @Override
    public boolean isShowMutliplicities() {
        return false;
    }

    @Override
    public void setShowMutliplicities(boolean showMutliplicities) {}
}
