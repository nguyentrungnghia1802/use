# Phase 9 — production semantic authority

Status: PASS (completed before the Phase 10 retirement step).

`DefaultJaCaMoFacade` defaults to `SemanticAuthority.BRIDGE`. It accepts only a
validated loopback endpoint, a regular non-symlink secret file, the pinned
distribution SHA-256, required capabilities, and explicit frame/resource
limits. Missing Bridge configuration and every negotiation, project-key,
distribution, schema, transport, snapshot, or materialization error are
fail-closed diagnostics. No branch invokes a custom parser or in-process
connector as fallback.

The accepted candidate is built from official `ModelSnapshot` data through
`NativeSemanticAdapter`, frozen V2/Mapping V2.2, materialization, trace, runtime
projection, and verification. Workspace replacement is atomic: a failed
candidate keeps the previous accepted workspace. The UI reports authority,
endpoint (with secrets redacted), readiness, capability/completeness,
modelRevision, session/generation, stale/resync state, and the last diagnostic.

`DefaultBridgeAuthorityTest` proves default selection, missing configuration,
full official import, distribution mismatch, explicit upgraded distribution,
and project mismatch. The explicit legacy compatibility mode was exercised as
the rollback rehearsal during Phase 9. Phase 10 then removed that production
path; the same flag now produces the intentional
`SEMANTIC_AUTHORITY_REMOVED:legacy-compatibility` error. This sequential change
does not create a silent rollback path.

Supported claims remain bounded by the capability and projection matrices.
Neither Auction's original self-referencing plan/deadline semantics nor House's
unobserved dynamic runtime is promoted.
