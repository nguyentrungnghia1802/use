# Runtime event, trace and identity — Phase 17

RuntimeEvent V1 remains the only normalized event. Its ten fields and JSON wire
schema remain compatible. Payloads now own immutable nested maps/lists and reject
non-JSON mutable values. RuntimeEventValidator owns required fields and dimension
authority. RuntimeEventCodec retains structural schema validation and round trips.
No Ecore dependency is introduced.

RuntimeTrace is the engine-owned ledger: accepted event, then APPLIED/QUARANTINED/
FAILED outcome; rejected events use a separate disposition. Generation boundaries
identify initial/direct streams and authoritative snapshots. Event ID is unique
within a generation, sequence is strictly increasing; event/correlation/range
queries preserve append order. No eviction hides evidence. Long-running retention
requires explicit export/rotation and is not yet bounded. Transport closure
isolates callbacks; direct engine clients retain their baseline next-stream API.

Taxonomy and support are in the Phase 16 reconciliation matrix. Properties require
artifact/property bindings; absent bound properties become undefined. OP_ENTER
requires operation/arguments/correlation, OP_EXIT and OP_FAIL terminate that
correlation (failure requires error). Requested/suspended/resumed upstream hooks
are not OP_ENTER aliases and remain deferred. Signals, Jason mind/action/messages,
and Moise observations are trace-only until mapping explicitly authorizes a target.

## Exact identity contract

* Existing `jason:agent:<name>` aliases remain compatible. Explicit binding is
  required; ActionExec object identity supplies per-invocation correlation. Replacing
  a TransitionSystem detaches hooks from the old system, and old callbacks cannot
  publish. Goal/literal text is evidence, not a unique concurrent invocation key.
* Existing `cartago:artifact:<workspace>/<artifact>` remains the explicit semantic
  binding alias. The connector separately checks actual ArtifactId incarnation.
  Operation correlation includes connection generation, artifact UUID, OpId number,
  operation name and AgentId global ID. Property evidence includes upstream property
  ID and artifact-qualified key. Recreated artifacts require authoritative resync;
  a stale incarnation never silently inherits the active binding.
* Unknown CArtAgO artifacts and retired callbacks are retained in
  `quarantinedObservations()`. This channel is outside the configured supported
  mirror subset and does not invent semantic targets. Unknown properties have raw
  evidence but no projected attribute; Phase 19 controls their mutation disposition.
* Moise keys preserve organisation and group/scheme instance IDs, with specification
  IDs in payload. `runtimeFactKey` length-prefixes role-player, mission-player and
  goal-instance components. Two runtime instances of the same spec remain distinct;
  unbound instances have null semantic IDs. No static-instance equivalence is inferred.
* TraceIndex rejects duplicate runtime keys and non-resolved alias registration.
  `runtimeKeysFor` provides deterministic reverse lookup. Workspace replacement
  carries only resolved/projected records with identical semantic ID, kind and USE
  target. Composite subscriptions isolate retired child callbacks explicitly.

Phase 36 strengthens this transfer to require equal complete MappingModel
contracts, source kind/hash and mapping/projection rules as well. Persisted trace
indexes are archival: record inspection is supported, but runtime lookup/alias
registration/transfer and mutation-engine construction are rejected. Rebuild from
active sources, explicitly bind current runtime and resynchronize before LIVE.

Existing v1.0.1 replacement/reconnect gates remain applicable; full synchronization
is still required for LIVE. Global causal ordering, complete goal invocation IDs,
automatic incoming-message interception and full NPL lifecycle are not claimed.

Tests: RuntimeIdentityHardeningTest (nested immutability and old Jason hooks RED
before fixes), RuntimeTraceTest, RuntimeAliasTest, expanded CArtAgO and Moise real-API
tests, RuntimeFoundationTest, TraceBindingTest and HotfixLifecycleTest.
