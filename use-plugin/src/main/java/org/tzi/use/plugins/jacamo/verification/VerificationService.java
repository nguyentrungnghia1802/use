package org.tzi.use.plugins.jacamo.verification;

import java.util.List;
import org.tzi.use.plugins.jacamo.trace.TraceIndex;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

public interface VerificationService {
    VerificationReport runFullVerification(MSystem system, ConstraintRegistry registry, TraceIndex trace);
    OperationCheck beginOperation(MSystem system, ConstraintRegistry registry, TraceIndex trace, OperationRequest request);
    VerificationReport completeOperation(OperationCheck check, MSystemState postState, Value result,
                                         List<String> exitRuntimeEventIds);
}
