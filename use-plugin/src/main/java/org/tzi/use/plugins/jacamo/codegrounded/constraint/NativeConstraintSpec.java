package org.tzi.use.plugins.jacamo.codegrounded.constraint;

import java.util.List;
import org.jacamo.bridge.contract.semantic.Fidelity;

/** Native constraint contract; it deliberately has no dependency on Mapping V2 or authored V2 profiles. */
public record NativeConstraintSpec(
        String name,
        String targetContext,
        String oclBody,
        List<String> requiredRuleIds,
        List<String> requiredCapabilities,
        Fidelity minimumFidelity,
        String origin,
        ConstraintMigrationStatus migrationStatus) {
    public NativeConstraintSpec {
        if (name == null || name.isBlank() || targetContext == null || targetContext.isBlank()
                || oclBody == null || oclBody.isBlank() || origin == null || origin.isBlank())
            throw new IllegalArgumentException("NATIVE_CONSTRAINT_METADATA_REQUIRED");
        requiredRuleIds = List.copyOf(requiredRuleIds);
        requiredCapabilities = List.copyOf(requiredCapabilities);
        java.util.Objects.requireNonNull(minimumFidelity, "minimumFidelity");
        java.util.Objects.requireNonNull(migrationStatus, "migrationStatus");
    }
}
