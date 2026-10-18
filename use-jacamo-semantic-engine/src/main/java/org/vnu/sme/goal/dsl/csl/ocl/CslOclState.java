package org.vnu.sme.goal.dsl.csl.ocl;

/** Minimal typed CSL system-state protocol consumed by OCL evaluation. */
public interface CslOclState {
    Object property(Object base, String name);
    String identity(Object value);
}
