package org.tzi.use.plugins.jacamo.ui.use4goal.csl;

import org.tzi.use.gui.views.diagrams.classdiagram.ClassNode;
import org.tzi.use.uml.mm.MClass;

/** Entity rendered by USE's unchanged UML class-diagram component. */
public final class CslEntityNode extends ClassNode {
    private final String cslId;

    public CslEntityNode(MClass entity, CslDiagramOptions options) {
        super(entity, options);
        this.cslId = "entity::" + entity.name();
    }

    @Override public String getId() { return cslId; }
    @Override public String name() { return cslId; }
}
