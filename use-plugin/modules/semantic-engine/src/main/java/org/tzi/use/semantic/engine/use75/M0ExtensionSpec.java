package org.tzi.use.semantic.engine.use75;

import java.util.Set;

/** Explicit neutral player/enactment extension. These helpers are not CSL M1 declarations. */
public record M0ExtensionSpec(boolean players, boolean enforceRoleCompatibility) {
    public static final Set<String> HELPER_TYPES = Set.of("RuntimePlayer", "AtomicAgent", "OrgContext",
            "RoleOccurrence", "DomainEntity", "WorkspaceObservation");
    public static M0ExtensionSpec schemaOnly() { return new M0ExtensionSpec(false, false); }
    public static M0ExtensionSpec observedPlayers() { return new M0ExtensionSpec(true, true); }
    public M0ExtensionSpec {
        if (!players && enforceRoleCompatibility) throw new IllegalArgumentException("COMPATIBILITY_REQUIRES_PLAYER_CONTEXT_EXTENSION");
    }
}
