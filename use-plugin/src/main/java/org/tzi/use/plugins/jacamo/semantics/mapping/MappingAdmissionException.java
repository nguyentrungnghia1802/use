package org.tzi.use.plugins.jacamo.semantics.mapping;

import java.util.List;

/** Structured import rejection, distinct from an evaluated FAIL. */
public final class MappingAdmissionException extends IllegalArgumentException {
    private final List<MappingDiagnostic> diagnostics;
    public MappingAdmissionException(List<MappingDiagnostic> diagnostics) {
        super(diagnostics.toString());this.diagnostics=List.copyOf(diagnostics);
        if(diagnostics.isEmpty())throw new IllegalArgumentException("MAPPING_DIAGNOSTICS_REQUIRED");
    }
    public List<MappingDiagnostic> diagnostics(){return diagnostics;}
}
