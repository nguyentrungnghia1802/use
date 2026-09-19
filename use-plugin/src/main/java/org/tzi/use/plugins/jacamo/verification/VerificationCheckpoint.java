package org.tzi.use.plugins.jacamo.verification;

/** Checkpoints describe evidence, not permission to control the observed runtime. */
public enum VerificationCheckpoint {
    SNAPSHOT, AFTER_MUTATION, OPERATION_PRE, OPERATION_POST, STREAM_BOUNDARY, DIAGNOSTIC
}
