package org.tzi.use.state;

import org.tzi.use.api.*;
import org.tzi.use.semantic.engine.use75.*;

/** Observed declaration scope and CArtAgO focus over the unified OrgCtx kernel. */
final class CslScopeSupport {
    static void prepare(UseModelApi api,Use75ModelBinding binding)throws UseApiException {
        if(!binding.extension().unifiedOrgCtx())throw new IllegalArgumentException("STATE_MODE_REQUIRES_UNIFIED_ORGCTX");
        api.createAssociation("CslContextScopes","OrgContext","cslScopeParent","0..1",0,"OrgContext","cslNestedContexts","*",0);
        api.createAssociation("CslEntityScopes","OrgContext","cslEntityScope","0..1",0,"DomainEntity","cslScopedEntities","*",0);
        api.createAssociation("CslFocuses","OrgContext","focusedBy","*",0,"DomainEntity","focusedArtifacts","*",0);
    }
}
