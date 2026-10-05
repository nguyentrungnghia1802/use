package org.tzi.use.plugins.jacamo.runtime;

import cartago.CartagoEnvironment;
import cartago.CartagoException;
import cartago.ICartagoController;
import cartago.ICartagoLogger;

/** Thin boundary over the official CArtAgO 3.1 environment API. */
public final class OfficialCartagoRuntimeAccess implements CartagoRuntimeAccess {
    private final CartagoEnvironment environment;

    public OfficialCartagoRuntimeAccess(CartagoEnvironment environment) {
        if (environment == null) throw new IllegalArgumentException("CARTAGO_ENVIRONMENT_REQUIRED");
        this.environment = environment;
    }
    @Override public ICartagoController controller(String workspace) throws CartagoException {
        return environment.getController(workspace);
    }
    @Override public void registerLogger(String workspace, ICartagoLogger logger) throws CartagoException {
        environment.registerLogger(workspace, logger);
    }
    @Override public void unregisterLogger(String workspace, ICartagoLogger logger) throws CartagoException {
        environment.unregisterLogger(workspace, logger);
    }
}
