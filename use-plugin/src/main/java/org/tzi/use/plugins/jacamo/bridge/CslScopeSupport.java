package org.tzi.use.plugins.jacamo.bridge;

import org.tzi.use.api.*;
import org.tzi.use.plugins.jacamo.verify.use75.*;

/** Observed declaration scope and CArtAgO focus over the unified OrgCtx kernel. */
final class CslScopeSupport {
    static void prepare(UseModelApi api,Use75ModelBinding binding)throws UseApiException {
        CslObservationSchema.prepare(api,binding);
    }
}
