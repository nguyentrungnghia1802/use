package org.tzi.use.plugins.jacamo.runtime;

import cartago.CartagoException;
import cartago.ICartagoController;
import cartago.ICartagoLogger;

public interface CartagoRuntimeAccess {
    ICartagoController controller(String workspace) throws CartagoException;
    void registerLogger(String workspace, ICartagoLogger logger) throws CartagoException;
    void unregisterLogger(String workspace, ICartagoLogger logger) throws CartagoException;
}
