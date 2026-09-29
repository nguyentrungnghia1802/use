package org.tzi.use.plugins.jacamo.codegrounded.constraint;

import java.util.ArrayList;
import java.util.List;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.uml.mm.MClassInvariant;

/** Installs OCL directly into the native MModel through the supported USE API. */
public final class NativeConstraintInstaller {
    public List<MClassInvariant> install(UseModelApi api, List<NativeConstraintSpec> constraints) {
        List<MClassInvariant> installed = new ArrayList<>();
        try {
            for (NativeConstraintSpec constraint : constraints) {
                if (constraint.migrationStatus() == ConstraintMigrationStatus.SKIPPED_CAPABILITY) continue;
                installed.add(api.createInvariant(constraint.name(), constraint.targetContext(),
                        constraint.oclBody(), false));
            }
            return List.copyOf(installed);
        } catch (UseApiException error) {
            throw new IllegalStateException("NATIVE_CONSTRAINT_INSTALL_FAILED: " + error.getMessage(), error);
        }
    }
}
