package org.tzi.use.plugins.jacamo.semantics.binding;

import org.tzi.use.uml.mm.MModel;

/** Backend binding SPI over the native model. Runtime ownership/transactions remain with the native pipeline. */
public interface UseProjectionBinding {
    String profileId();
    MModel model();
    default ProjectionBindingRegistry inventory(){return new ProjectionBindingRegistry(model());}
    static UseProjectionBinding of(MModel model){return new ProjectionBindingRegistry(model);}
}
