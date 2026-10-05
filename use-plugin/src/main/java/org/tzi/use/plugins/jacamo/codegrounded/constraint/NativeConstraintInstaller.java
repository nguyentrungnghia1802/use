package org.tzi.use.plugins.jacamo.codegrounded.constraint;

import java.util.ArrayList;
import java.util.List;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.uml.mm.MClassInvariant;

/** Installs OCL directly into the native MModel through the supported USE API. */
public final class NativeConstraintInstaller {
    public List<MClassInvariant> install(UseModelApi api, List<NativeConstraintSpec> constraints) {
        return installWithReport(api, constraints).installed();
    }

    /** Installs native constraints and returns capability-gated specs without silently dropping them. */
    public InstallationResult installWithReport(UseModelApi api, List<NativeConstraintSpec> constraints) {
        List<MClassInvariant> installed = new ArrayList<>();
        List<NativeConstraintSpec> skipped = new ArrayList<>();
        try {
            for (NativeConstraintSpec constraint : constraints) {
                if (constraint.migrationStatus() == ConstraintMigrationStatus.SKIPPED_CAPABILITY) {
                    skipped.add(constraint);
                    continue;
                }
                var invariant=api.createInvariant(constraint.name(), constraint.targetContext(),constraint.oclBody(),false);
                invariant.addAnnotation(new org.tzi.use.uml.mm.MElementAnnotation("RuntimeConstraint",java.util.Map.of(
                        "origin",constraint.origin().startsWith("CORE:GOAL:")?"CORE":"TRANSLATED","provenance",constraint.origin(),
                        "capabilities",String.join(",",constraint.requiredCapabilities()),"severity",constraint.origin().startsWith("CORE:GOAL:")?"HARD":"SOFT",
                        "enforcement","REPORT_ONLY",
                        "bodyHash",ExternalOclConstraintService.sha256(invariant.bodyExpression().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
                installed.add(invariant);
            }
            return new InstallationResult(installed, skipped);
        } catch (UseApiException error) {
            throw new IllegalStateException("NATIVE_CONSTRAINT_INSTALL_FAILED: " + error.getMessage(), error);
        }
    }

    public record InstallationResult(List<MClassInvariant> installed, List<NativeConstraintSpec> skipped) {
        public InstallationResult {
            installed = List.copyOf(installed);
            skipped = List.copyOf(skipped);
        }
    }
}
