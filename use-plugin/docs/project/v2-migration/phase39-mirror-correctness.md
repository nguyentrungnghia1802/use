# Phase 39 V2 mirror correctness

Status: DONE. Focused 58/58, full reactor 356/356 and post-merge 31/31 PASS,
zero skips. Commit 58cd40bd is merged and pushed. V2 remains WORKING_BASELINE.

## State and authority contract

The facade exposes OFFLINE before import and MODEL_READY after a coherent static
workspace is installed with no runtime service. The existing transport lifecycle
continues CONNECTING -> SYNCING -> LIVE, disconnect -> STALE, failures -> ERROR,
and close -> OFFLINE. Only LIVE is eligible for current runtime verification.
Reimport/rebuild/profile changes replace the complete model/trace/registry/mutation
workspace, drain the old queue, transfer only exact current aliases and resnapshot.

Jason belief/goal/action and Moise organisation/group/scheme/role/mission/goal
observations retain the exact qualified identities and explicit trace-only boundary
from Phase 38. CArtAgO projected properties and operation lifecycles are authoritative
mutable state. Snapshot IDs, sequences and fingerprints remain connector-derived;
composite snapshots are sequential observations, not an atomic three-runtime cut.

## Correctness repairs

Initial subscription precedes fullSnapshot to capture racing deltas. Its bootstrap
buffer now shares the configured queue capacity. Overflow raises explicit
RUNTIME_QUEUE_BACKPRESSURE during acquisition; a latched failure prevents successful
synchronization even if the producer catches the callback exception. Every pending
event receives an explicit rejection on failed acquisition or stream close. No
unbounded bootstrap staging or silent drop is accepted. Heavy snapshot mutation and
verification remain outside the short buffer gate. The existing single-consumer
queue, monotonic sequencing, correlation and retired-stream isolation are reused.

Authoritative order comparison now checks each physical membership/support link,
association cardinality, order row existence/class, extra order rows and each rank.
Independent directional ranks remain source-authoritative. A negative control with
one deleted support link previously reported zero drift; it now identifies the
exact association/endpoints. Extra rows also produce detailed differences.

After applying any authoritative snapshot, the service compares its declared state
again before publishing it as synchronized. Residual drift throws
RUNTIME_SNAPSHOT_DRIFT and disconnects in ERROR. The new extra-row control proves a
successful rank application alone cannot make a corrupt projection LIVE. Restoring
the exact state and reconnecting gives zero drift. Rank updates never alter
membership; unsupported structural projection damage requires a validated workspace
rebuild, not guessed link deletion or ordering. Scalar drift auto-resync remains
supported; failed structural repair never claims success.

## Evidence matrix

| Requirement | Implementation | Executable evidence |
|---|---|---|
| exact snapshot aliases and V2 target | pinned connectors, TraceIndex, RuntimeMutationEngine | live Auction, CounterTeam, RuntimeIdentityHardeningTest |
| bounded queue/bootstrap, no silent drop, late callbacks | OrderedRuntimeEventQueue, RuntimeMirrorService | RuntimeFoundationTest overflow/ordering/race/retired-stream controls |
| object/create/destroy, typed scalar, undefined, links | generic validated actions, exact trace resolver | RuntimeFoundationTest all mutations, lifecycle restore, property removal and idempotent link replay |
| operation target/correlation/tombstones | mutation lifecycle map and accepted watermark cleanup | RuntimeFoundationTest stale/duplicate/backpressure controls; RuntimeTraceTest |
| states and coherent workspace replacement | DefaultJaCaMoFacade, RuntimeMirrorService | HotfixLifecycleTest reimport/rebuild/profile, preserved failed-build workspace, exactly one subscription |
| drift report-only/auto-resync | compareSnapshot and checkDrift | RuntimeFoundationTest scalar repair, V2RuntimeOrderTest link/rank/extra-row controls |
| live supported-subset correctness | same production mapping/mutation/queue | LiveJaCaMoAuctionIntegrationTest and CounterTeamIntegrationTest zero drift after actions and reconnect |
| unknown/unbound and duplicate authority | target adapter and RuntimeEventValidator | RuntimeFoundationTest, RuntimeAuthorityTest, RuntimeIdentityHardeningTest |

Unknown entities never gain mutation rights. Artifact disposal from raw callbacks
is observation-only; exact normalized destruction uses the established instance
policy and refuses order-bound endpoints without full authoritative reconstruction.
No JaCaMo execution is moved into USE. Operation history is correlation evidence,
not a claim that a snapshot enumerates all upstream in-flight operations.

Cross-layer consistency: unchanged Ecore/Structural Mapping -> unchanged semantic
IR/transformation -> exact active trace -> unchanged OCL definitions -> Phase 38
validated runtime actions -> checked MSystemState -> existing observer/report tests.
No Ecore, mapping, hash, golden or USE core change is required by these repairs.
Phase 40 may rely only on the supported mirror scope after this phase's gates close.
