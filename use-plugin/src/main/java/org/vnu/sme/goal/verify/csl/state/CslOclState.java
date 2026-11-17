package org.vnu.sme.goal.verify.csl.state;

/** Minimal typed CSL system-state protocol consumed by OCL evaluation. */
public interface CslOclState {
    Object property(Object base, String name);
    String identity(Object value);
}
