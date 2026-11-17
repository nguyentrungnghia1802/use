package org.vnu.sme.goal.dsl.csl_marking.mm;

/** Minimal typed CSL system-state protocol consumed by OCL evaluation. */
public interface CslOclState {
    Object property(Object base, String name);
    String identity(Object value);
}
