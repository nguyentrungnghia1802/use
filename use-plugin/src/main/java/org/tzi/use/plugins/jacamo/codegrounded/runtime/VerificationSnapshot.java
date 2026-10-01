package org.tzi.use.plugins.jacamo.codegrounded.runtime;

import java.time.Instant;
import org.tzi.use.plugins.jacamo.codegrounded.constraint.ExternalOclConstraintService;

/** One consistent read of a committed workspace. Profile interval survives history-tail eviction. */
public record VerificationSnapshot(long currentVersion, RuntimeVerificationResult result, ProfileInterval profile) {
    public record ProfileInterval(long loadedVersion, String intervalId, Instant installedAt, String sessionId,
            long generation, String installedModelRevision, ExternalOclConstraintService.Profile profile) { }
    public static VerificationSnapshot empty() { return new VerificationSnapshot(0, null, null); }
}
