package org.tzi.use.plugins.jacamo.verify.use75;

import org.tzi.use.api.*;

/** Optional source-neutral scope and observation relations over canonical OrgCtx subjects. */
public final class CslObservationSchema {
    private CslObservationSchema() { }
    public static void prepare(UseModelApi api, Use75ModelBinding binding) throws UseApiException {
        if (!binding.extension().unifiedOrgCtx()) throw new IllegalArgumentException("OBSERVATION_SCHEMA_REQUIRES_UNIFIED_ORGCTX");
        api.createAssociation("CslContextScopes", "OrgContext", "cslScopeParent", "0..1", 0, "OrgContext", "cslNestedContexts", "*", 0);
        api.createAssociation("CslEntityScopes", "OrgContext", "cslEntityScope", "0..1", 0, "DomainEntity", "cslScopedEntities", "*", 0);
        api.createAssociation("CslFocuses", "OrgContext", "focusedBy", "*", 0, "DomainEntity", "focusedArtifacts", "*", 0);
    }
}
