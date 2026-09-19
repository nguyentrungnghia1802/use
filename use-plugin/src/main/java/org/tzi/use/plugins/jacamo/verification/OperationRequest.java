package org.tzi.use.plugins.jacamo.verification;

import java.util.List;
import org.tzi.use.uml.ocl.value.Value;

public record OperationRequest(String objectName, String operationName, List<Value> arguments,
                               String correlationId, List<String> runtimeEventIds) {
    public OperationRequest {
        arguments = List.copyOf(arguments);
        runtimeEventIds = List.copyOf(runtimeEventIds);
        if (objectName == null || objectName.isBlank() || operationName == null || operationName.isBlank()
                || correlationId == null || correlationId.isBlank())
            throw new IllegalArgumentException("OPERATION_REQUEST_INVALID");
    }
}
