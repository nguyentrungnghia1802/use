package org.tzi.use.plugins.jacamo.verification;

import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.sys.MSystemState;

public record OperationCheck(OperationRequest request, MOperation operation, MSystemState preState,
                             ConstraintRegistry registry, TraceIndex trace, VerificationReport preconditions) { }
