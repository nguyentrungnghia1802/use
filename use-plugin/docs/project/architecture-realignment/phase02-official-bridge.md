# Phase 2 — official JaCaMo Bridge

Status: PASS for the implemented official-API capability set.

`jacamo-bridge-jacamo` is compiled against JaCaMo 1.3.1, Jason 3.3.2,
CArtAgO 3.1, Moise 1.1 and NPL 0.6.1. `JaCaMoBridgePlatform` receives the
official merged `JaCaMoProject`, creates a new session at each init, publishes a
new generation at start, attaches a bounded registry observer and detaches it on
stop. `BridgeAgArch` observes and delegates Jason actions, uses object-identity
correlation for start/terminal callbacks, and creates a new incarnation per init.

Static authority is exclusively official: `JaCaMoProjectParser`/`JaCaMoProject`,
`Agent.parseAS`, and `OS.loadOSFromURI`. The adapter preserves AST text,
source digests, focus/player relations, role hierarchy, groups, schemes,
missions, goals, plans, norms and relation-scoped cardinality. It also records
the official CArtAgO root workspace (`CartagoEnvironment.ROOT_WSP_DEFAULT_NAME`),
which is present even when a JCM declares no workspace.

`SnapshotCoordinator` attaches listeners before capture, records per-source
start/end watermarks, retries topology changes, rejects overflow and replays
only events after accepted watermarks. CArtAgO controller/logger, Moise board
snapshot and NPL listener adapters use official runtime identities. Facts for
which exact target binding is not yet present are deliberately `EVIDENCE_ONLY`.
There is no JaCaMo core patch and no custom parser fallback.

Evidence: `OfficialAdapterTest` (5 tests), `SnapshotCoordinatorTest` (3 tests),
and `CanonicalEvidenceTest`. Complete original Auction/House autonomous runtime
semantics are not claimed; their supported boundary is recorded in Phases 5–6.
